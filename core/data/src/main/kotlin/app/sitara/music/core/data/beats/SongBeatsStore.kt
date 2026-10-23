package app.sitara.music.core.data.beats

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import app.sitara.music.core.model.BeatGrid
import app.sitara.music.core.model.RegionBeats
import app.sitara.music.core.model.SongBeats

/**
 * Automix's analysis of one song: the beat and level of its first and last half minute, each side null until it has
 * been analysed. [version] is the analyser's, so a better analyser can ignore older rows. A cache: it is rebuilt by
 * playing, so it is neither backed up nor restored.
 */
@Entity(tableName = "song_beats")
data class SongBeatsEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: String,
    val version: Int,
    @ColumnInfo(name = "intro_analysed") val introAnalysed: Boolean,
    @ColumnInfo(name = "intro_bpm") val introBpm: Double?,
    @ColumnInfo(name = "intro_downbeat_ms") val introDownbeatMs: Long?,
    @ColumnInfo(name = "intro_confidence") val introConfidence: Float?,
    @ColumnInfo(name = "intro_db") val introDb: Float?,
    @ColumnInfo(name = "intro_first_ms") val introFirstMs: Long?,
    @ColumnInfo(name = "intro_last_ms") val introLastMs: Long?,
    @ColumnInfo(name = "outro_analysed") val outroAnalysed: Boolean,
    @ColumnInfo(name = "outro_bpm") val outroBpm: Double?,
    @ColumnInfo(name = "outro_downbeat_ms") val outroDownbeatMs: Long?,
    @ColumnInfo(name = "outro_confidence") val outroConfidence: Float?,
    @ColumnInfo(name = "outro_db") val outroDb: Float?,
    @ColumnInfo(name = "outro_first_ms") val outroFirstMs: Long?,
    @ColumnInfo(name = "outro_last_ms") val outroLastMs: Long?,
    @ColumnInfo(name = "analysed_at") val analysedAt: Long,
)

@Dao
interface SongBeatsDao {
    @Query("SELECT * FROM song_beats WHERE track_id = :trackId")
    suspend fun get(trackId: String): SongBeatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SongBeatsEntity)

    /** Keeps [beats]' analysed sides and the stored row's other side, unless that row is from another analyser version. */
    @Transaction
    suspend fun merge(beats: SongBeats, version: Int, now: Long) {
        val old = get(beats.trackId)?.takeIf { it.version == version }?.toModel()
        put(SongBeats(beats.trackId, beats.intro ?: old?.intro, beats.outro ?: old?.outro).toEntity(version, now))
    }
}

/** The analyses automix keeps, by song id. */
class SongBeatsRepository(private val dao: SongBeatsDao) {
    /** The song's analysis by analyser [version], null when it has none. */
    suspend fun get(trackId: String, version: Int): SongBeats? = dao.get(trackId)?.takeIf { it.version == version }?.toModel()

    /** Adds the sides [beats] has to what is kept for the song. */
    suspend fun put(beats: SongBeats, version: Int, now: Long = System.currentTimeMillis()) = dao.merge(beats, version, now)
}

internal fun SongBeatsEntity.toModel(): SongBeats = SongBeats(
    trackId,
    intro = if (introAnalysed) region(introBpm, introDownbeatMs, introConfidence, introDb, introFirstMs, introLastMs) else null,
    outro = if (outroAnalysed) region(outroBpm, outroDownbeatMs, outroConfidence, outroDb, outroFirstMs, outroLastMs) else null,
)

private fun region(bpm: Double?, downbeat: Long?, confidence: Float?, db: Float?, first: Long?, last: Long?) = RegionBeats(
    grid = if (bpm != null && downbeat != null && confidence != null) BeatGrid(bpm, downbeat, confidence) else null,
    loudnessDb = db ?: SILENT_DB,
    firstSoundMs = first ?: 0,
    lastSoundMs = last ?: 0,
)

internal fun SongBeats.toEntity(version: Int, now: Long) = SongBeatsEntity(
    trackId, version,
    introAnalysed = intro != null, introBpm = intro?.grid?.bpm, introDownbeatMs = intro?.grid?.downbeatMs, introConfidence = intro?.grid?.confidence,
    introDb = intro?.loudnessDb, introFirstMs = intro?.firstSoundMs, introLastMs = intro?.lastSoundMs,
    outroAnalysed = outro != null, outroBpm = outro?.grid?.bpm, outroDownbeatMs = outro?.grid?.downbeatMs, outroConfidence = outro?.grid?.confidence,
    outroDb = outro?.loudnessDb, outroFirstMs = outro?.firstSoundMs, outroLastMs = outro?.lastSoundMs,
    analysedAt = now,
)

private const val SILENT_DB = -60f
