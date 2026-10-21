package app.sitara.music.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A song file the index found, by its content URI; [folder] is the picked folder's tree URI, null for the phone's music (MediaStore). */
@Entity(tableName = "local_songs", indices = [Index("folder")])
data class LocalSongEntity(
    @PrimaryKey val uri: String,
    val folder: String?,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "track_number") val trackNumber: Int?,
    @ColumnInfo(name = "art_uri") val artUri: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "modified_ms") val modifiedMs: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** A folder the listener picked; [linked] turns false when its read permission is gone, and its songs stay until it is relinked or removed. */
@Entity(tableName = "local_folders")
data class LocalFolderEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val linked: Boolean,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** A picked folder with how many songs it holds. */
data class LocalFolderRow(
    val uri: String,
    val name: String,
    val linked: Boolean,
    @ColumnInfo(name = "song_count") val songCount: Int,
)
