package app.sitara.music.core.data.imports

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.sitara.music.core.data.library.TrackColumns
import app.sitara.music.core.model.SourceSong

/**
 * One import of a source into the library. [sourceKey] names the source, so importing it again finds this job;
 * [playlistId] is the playlist it made, or for a repeat import the playlist it will update.
 */
@Entity(tableName = "import_jobs", indices = [Index("source_key")])
data class ImportJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    val name: String,
    @ColumnInfo(name = "playlist_id") val playlistId: Long?,
    val finished: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** The source showed only part of a longer list (Spotify's first 100 songs). */
    @ColumnInfo(defaultValue = "0") val truncated: Boolean = false,
)

/** One song of an import, as the source named it, with its state (a [app.sitara.music.core.model.MatchState] name) and its match so far. */
@Entity(
    tableName = "import_rows",
    primaryKeys = ["job_id", "position"],
    foreignKeys = [ForeignKey(entity = ImportJobEntity::class, parentColumns = ["id"], childColumns = ["job_id"], onDelete = ForeignKey.CASCADE)],
)
data class ImportRowEntity(
    @ColumnInfo(name = "job_id") val jobId: Long,
    val position: Int,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    val link: String?,
    val state: String,
    /** Set when the match was uncertain, so the row stays in review after the listener decides. */
    @ColumnInfo(name = "needs_review") val needsReview: Boolean,
    @Embedded(prefix = "match_") val match: TrackColumns?,
) {
    fun source() = SourceSong(title, artist, album, durationMs, link)
}

/** A search result offered for an uncertain row, best first by [ordinal]. */
@Entity(
    tableName = "import_candidates",
    primaryKeys = ["job_id", "position", "ordinal"],
    foreignKeys = [ForeignKey(entity = ImportJobEntity::class, parentColumns = ["id"], childColumns = ["job_id"], onDelete = ForeignKey.CASCADE)],
)
data class ImportCandidateEntity(
    @ColumnInfo(name = "job_id") val jobId: Long,
    val position: Int,
    val ordinal: Int,
    @Embedded(prefix = "track_") val track: TrackColumns,
)

/** How many of a job's rows are in one state. */
data class StateCountRow(val state: String, val count: Int)
