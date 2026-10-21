package app.sitara.music.core.data.scrobble

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.sitara.music.core.model.Scrobble
import app.sitara.music.core.model.ScrobbleService
import app.sitara.music.source.api.ScrobbleClient
import app.sitara.music.source.api.SendOutcome
import kotlinx.coroutines.flow.Flow

/** Last.fm's limit per scrobble call; ListenBrainz takes more but gains nothing from bigger batches. */
private const val BATCH = 50

// A sent play is kept this long, so a play still reporting more time heard is recognised and not sent twice.
private const val KEEP_SENT_MS = 30L * 24 * 60 * 60 * 1_000

/** A play waiting for, or already taken by, one service; the service, song and start name it, so a play is kept once. */
@Entity(tableName = "scrobble_outbox", primaryKeys = ["service", "track_id", "started_at"])
data class ScrobbleEntity(
    val service: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "started_at") val startedAtMs: Long,
    /** When the service took it; null while it waits. */
    @ColumnInfo(name = "sent_at") val sentAtMs: Long? = null,
)

@Dao
interface ScrobbleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rows: List<ScrobbleEntity>)

    @Query("SELECT * FROM scrobble_outbox WHERE service = :service AND sent_at IS NULL ORDER BY started_at, track_id LIMIT :limit")
    suspend fun pending(service: String, limit: Int): List<ScrobbleEntity>

    @Query("SELECT COUNT(*) FROM scrobble_outbox WHERE service = :service AND sent_at IS NULL")
    fun pendingCount(service: String): Flow<Int>

    @Query("SELECT MAX(sent_at) FROM scrobble_outbox WHERE service = :service")
    fun lastSentAt(service: String): Flow<Long?>

    @Query("UPDATE scrobble_outbox SET sent_at = :sentAtMs WHERE service = :service AND track_id = :trackId AND started_at = :startedAtMs")
    suspend fun markSent(service: String, trackId: String, startedAtMs: Long, sentAtMs: Long)

    @Query("DELETE FROM scrobble_outbox WHERE service = :service AND track_id = :trackId AND started_at = :startedAtMs")
    suspend fun delete(service: String, trackId: String, startedAtMs: Long)

    @Query("DELETE FROM scrobble_outbox WHERE sent_at IS NOT NULL AND started_at < :beforeMs")
    suspend fun deleteSentBefore(beforeMs: Long)

    @Query("DELETE FROM scrobble_outbox WHERE service = :service")
    suspend fun deleteAll(service: String)
}

/**
 * Plays waiting to be scrobbled, kept in Room across restarts and while offline. Each play is kept once per service
 * and marked sent once its service took it, so it goes out once; a send cut off before the mark resends it with the
 * same start time, which the services take as the same listen.
 */
class ScrobbleOutbox internal constructor(private val dao: ScrobbleDao, private val clock: () -> Long = System::currentTimeMillis) {
    /** Keeps [scrobble] for each of [services]; one already kept (the same play reported again) stays as it is. */
    suspend fun add(scrobble: Scrobble, services: Collection<ScrobbleService>) {
        dao.deleteSentBefore(clock() - KEEP_SENT_MS)
        dao.insert(services.map { scrobble.toEntity(it) })
    }

    fun pendingCount(service: ScrobbleService): Flow<Int> = dao.pendingCount(service.name)

    /** When [service] last took a play this month; null when it has not. */
    fun lastSentAt(service: ScrobbleService): Flow<Long?> = dao.lastSentAt(service.name)

    /**
     * Sends [service]'s waiting plays through [client], oldest first, 50 at a time, until none wait ([SendOutcome.SENT])
     * or the service stops taking them (try later, sign in again). A refused batch is sent again play by play, and only
     * a play refused on its own is dropped.
     */
    suspend fun send(service: ScrobbleService, client: ScrobbleClient): SendOutcome {
        while (true) {
            val batch = dao.pending(service.name, BATCH).ifEmpty { return SendOutcome.SENT }
            when (val outcome = client.submit(batch.map { it.toScrobble() })) {
                SendOutcome.SENT -> for (row in batch) markSent(row)
                SendOutcome.REFUSED -> for (row in batch) {
                    val alone = if (batch.size == 1) outcome else client.submit(listOf(row.toScrobble()))
                    when (alone) {
                        SendOutcome.SENT -> markSent(row)
                        SendOutcome.REFUSED -> dao.delete(row.service, row.trackId, row.startedAtMs)
                        else -> return alone
                    }
                }
                else -> return outcome
            }
        }
    }

    /** Drops [service]'s plays, for when the listener disconnects it. */
    suspend fun forget(service: ScrobbleService) = dao.deleteAll(service.name)

    private suspend fun markSent(row: ScrobbleEntity) = dao.markSent(row.service, row.trackId, row.startedAtMs, clock())

    private fun Scrobble.toEntity(service: ScrobbleService) = ScrobbleEntity(service.name, trackId, title, artist, album, durationMs, startedAtMs)

    private fun ScrobbleEntity.toScrobble() = Scrobble(trackId, title, artist, album, durationMs, startedAtMs)
}
