package app.sitara.music.core.data.library

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The listener's own library. A schema change bumps [version] and ships a migration; the exported schema is committed. */
@Database(
    entities = [LikedSongEntity::class, PlayEntity::class, SavedAlbumEntity::class, SavedArtistEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SitaraDatabase : RoomDatabase() {
    abstract fun likedSongs(): LikedSongsDao

    abstract fun playHistory(): PlayHistoryDao

    abstract fun saved(): SavedDao

    abstract fun playlists(): PlaylistDao

    companion object {
        fun open(context: Context): SitaraDatabase = Room.databaseBuilder(context, SitaraDatabase::class.java, "sitara.db").build()
    }
}
