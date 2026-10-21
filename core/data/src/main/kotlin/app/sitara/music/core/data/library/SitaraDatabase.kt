package app.sitara.music.core.data.library

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The listener's library, the saved play queue, cached lyrics and per-song volume trims. A schema change bumps [version] and ships a migration; the exported schema is committed. */
@Database(
    entities = [
        LikedSongEntity::class, PlayEntity::class, SavedAlbumEntity::class, SavedArtistEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class,
        QueueEntryEntity::class, QueueStateEntity::class, LyricsCacheEntity::class, SongGainEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // 2 adds the saved play queue and the lyrics cache, 3 per-song volume trims: new tables only. 4 adds each play's time heard (null for older plays) and an index by start.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class SitaraDatabase : RoomDatabase() {
    abstract fun likedSongs(): LikedSongsDao

    abstract fun playHistory(): PlayHistoryDao

    abstract fun saved(): SavedDao

    abstract fun playlists(): PlaylistDao

    abstract fun playbackQueue(): PlaybackQueueDao

    abstract fun lyricsCache(): LyricsCacheDao

    abstract fun songGain(): SongGainDao

    companion object {
        fun open(context: Context): SitaraDatabase = Room.databaseBuilder(context, SitaraDatabase::class.java, "sitara.db").build()
    }
}
