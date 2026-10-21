package app.sitara.music.core.data.imports

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.sitara.music.core.data.library.PlaylistEntity
import app.sitara.music.core.data.library.PlaylistEntryEntity
import app.sitara.music.core.data.library.TrackColumns
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ImportDao {
    @Query("SELECT * FROM import_jobs WHERE finished = 0 ORDER BY id DESC LIMIT 1")
    abstract fun unfinished(): Flow<ImportJobEntity?>

    @Query("SELECT * FROM import_jobs WHERE id = :id")
    abstract fun observeJob(id: Long): Flow<ImportJobEntity?>

    @Query("SELECT * FROM import_jobs WHERE id = :id")
    abstract suspend fun job(id: Long): ImportJobEntity?

    /** Newest first. */
    @Query("SELECT * FROM import_jobs WHERE source_key = :key ORDER BY id DESC")
    abstract suspend fun jobs(key: String): List<ImportJobEntity>

    @Query("SELECT state, COUNT(*) AS count FROM import_rows WHERE job_id = :id GROUP BY state")
    abstract fun counts(id: Long): Flow<List<StateCountRow>>

    @Query("SELECT * FROM import_rows WHERE job_id = :id AND needs_review = 1 ORDER BY position")
    abstract fun reviewRows(id: Long): Flow<List<ImportRowEntity>>

    @Query("SELECT * FROM import_candidates WHERE job_id = :id ORDER BY position, ordinal")
    abstract fun candidates(id: Long): Flow<List<ImportCandidateEntity>>

    @Query("SELECT * FROM import_rows WHERE job_id = :id AND state = 'PENDING' ORDER BY position LIMIT 1")
    abstract suspend fun nextPending(id: Long): ImportRowEntity?

    @Query("SELECT * FROM import_rows WHERE job_id = :id ORDER BY position")
    abstract suspend fun rows(id: Long): List<ImportRowEntity>

    @Query("SELECT * FROM import_rows WHERE job_id = :id AND position = :position")
    abstract suspend fun row(id: Long, position: Int): ImportRowEntity?

    @Insert
    abstract suspend fun insert(job: ImportJobEntity): Long

    @Insert
    abstract suspend fun insertRows(rows: List<ImportRowEntity>)

    @Update
    abstract suspend fun update(row: ImportRowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCandidates(candidates: List<ImportCandidateEntity>)

    @Query("DELETE FROM import_jobs WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM import_jobs WHERE source_key = :key AND id != :keep")
    abstract suspend fun deleteOthers(key: String, keep: Long)

    @Query("UPDATE import_jobs SET finished = 1, playlist_id = :playlistId WHERE id = :id")
    abstract suspend fun markFinished(id: Long, playlistId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM playlists WHERE id = :id)")
    abstract suspend fun playlistExists(id: Long): Boolean

    @Insert
    abstract suspend fun insert(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlist_entries WHERE playlist_id = :id")
    abstract suspend fun clearEntries(id: Long)

    @Insert
    abstract suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Query("UPDATE playlists SET updated_at = :at WHERE id = :id")
    abstract suspend fun touch(id: Long, at: Long)

    /** Writes the job and its rows together, so a job never exists half read. */
    @Transaction
    open suspend fun create(job: ImportJobEntity, rows: List<ImportRowEntity>): Long {
        val id = insert(job)
        insertRows(rows.map { it.copy(jobId = id) })
        return id
    }

    /** A looked-up row and its candidates together, so a resumed import never looks a row up twice or half. */
    @Transaction
    open suspend fun record(row: ImportRowEntity, candidates: List<ImportCandidateEntity>) {
        update(row)
        insertCandidates(candidates)
    }

    /**
     * Gives the job's playlist exactly [tracks], making the playlist when it does not exist (any more), marks the job
     * finished and forgets older imports of the same source. Returns the playlist's id.
     */
    @Transaction
    open suspend fun finish(job: ImportJobEntity, tracks: List<TrackColumns>, at: Long): Long {
        val playlistId = job.playlistId?.takeIf { playlistExists(it) } ?: insert(PlaylistEntity(name = job.name, createdAt = at, updatedAt = at))
        clearEntries(playlistId)
        insertEntries(tracks.mapIndexed { position, track -> PlaylistEntryEntity(playlistId, position, track, at) })
        touch(playlistId, at)
        markFinished(job.id, playlistId)
        deleteOthers(job.sourceKey, job.id)
        return playlistId
    }
}
