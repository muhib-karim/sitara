package app.sitara.music.core.data.library

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The listener's library, the saved play queue and cached lyrics. A schema change bumps [version] and ships a migration; the exported schema is committed. */
@Database(
    entities = [
        LikedSongEntity::class, PlayEntity::class, SavedAlbumEntity::class, SavedArtistEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class,
        QueueEntryEntity::class, QueueStateEntity::class, LyricsCacheEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // 2 adds the saved play queue and the lyrics cache: new tables only.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class SitaraDatabase : RoomDatabase() {
    abstract fun likedSongs(): LikedSongsDao

    abstract fun playHistory(): PlayHistoryDao

    abstract fun saved(): SavedDao

    abstract fun playlists(): PlaylistDao

    abstract fun playbackQueue(): PlaybackQueueDao

    abstract fun lyricsCache(): LyricsCacheDao

    companion object {
        fun open(context: Context): SitaraDatabase = Room.databaseBuilder(context, SitaraDatabase::class.java, "sitara.db").build()
    }
}
