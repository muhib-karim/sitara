package app.sitara.music.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LocalDao {
    @Query("SELECT * FROM local_songs ORDER BY added_at DESC, uri")
    abstract fun songs(): Flow<List<LocalSongEntity>>

    /** The songs found in one place: a picked folder, or the phone's music when [folder] is null. */
    @Query("SELECT * FROM local_songs WHERE folder IS :folder")
    abstract suspend fun songsIn(folder: String?): List<LocalSongEntity>

    @Upsert
    abstract suspend fun upsert(songs: List<LocalSongEntity>)

    @Query("DELETE FROM local_songs WHERE uri IN (:uris)")
    abstract suspend fun delete(uris: List<String>)

    /** One scan's result for one place, all or nothing. */
    @Transaction
    open suspend fun applyScan(found: List<LocalSongEntity>, gone: List<String>) {
        upsert(found)
        // Older SQLite allows 999 bound values a statement.
        gone.chunked(500).forEach { delete(it) }
    }

    @Query("SELECT f.uri, f.name, f.linked, COUNT(s.uri) AS song_count FROM local_folders f LEFT JOIN local_songs s ON s.folder = f.uri GROUP BY f.uri ORDER BY f.added_at")
    abstract fun folders(): Flow<List<LocalFolderRow>>

    @Query("SELECT * FROM local_folders")
    abstract suspend fun folderList(): List<LocalFolderEntity>

    @Query("SELECT * FROM local_folders WHERE uri = :uri")
    abstract suspend fun folder(uri: String): LocalFolderEntity?

    @Upsert
    abstract suspend fun upsertFolder(folder: LocalFolderEntity)

    @Query("UPDATE local_folders SET linked = :linked WHERE uri = :uri")
    abstract suspend fun setLinked(uri: String, linked: Boolean)

    @Query("DELETE FROM local_songs WHERE folder = :uri")
    abstract suspend fun deleteSongsIn(uri: String)

    @Query("DELETE FROM local_folders WHERE uri = :uri")
    abstract suspend fun deleteFolder(uri: String)

    @Transaction
    open suspend fun removeFolder(uri: String) {
        deleteSongsIn(uri)
        deleteFolder(uri)
    }
}
