package app.sitara.music.core.data.library

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.sitara.music.core.data.imports.ImportCandidateEntity
import app.sitara.music.core.data.imports.ImportDao
import app.sitara.music.core.data.imports.ImportJobEntity
import app.sitara.music.core.data.imports.ImportRowEntity
import app.sitara.music.core.data.scrobble.ScrobbleDao
import app.sitara.music.core.data.scrobble.ScrobbleEntity

/** The listener's library, the saved play queue, cached lyrics, per-song volume trims, playlist imports and plays waiting to be scrobbled. A schema change bumps [version] and ships a migration; the exported schema is committed. */
@Database(
    entities = [
        LikedSongEntity::class, PlayEntity::class, SavedAlbumEntity::class, SavedArtistEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class,
        QueueEntryEntity::class, QueueStateEntity::class, LyricsCacheEntity::class, SongGainEntity::class,
        ImportJobEntity::class, ImportRowEntity::class, ImportCandidateEntity::class, ScrobbleEntity::class,
    ],
    version = 6,
    exportSchema = true,
    // 2 adds the saved play queue and the lyrics cache, 3 per-song volume trims: new tables only. 4 adds each play's time heard (null for older plays) and an index by start; 5 playlist imports, 6 the scrobble outbox (new tables).
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6)],
)
abstract class SitaraDatabase : RoomDatabase() {
    abstract fun likedSongs(): LikedSongsDao

    abstract fun playHistory(): PlayHistoryDao

    abstract fun saved(): SavedDao

    abstract fun playlists(): PlaylistDao

    abstract fun playbackQueue(): PlaybackQueueDao

    abstract fun lyricsCache(): LyricsCacheDao

    abstract fun songGain(): SongGainDao

    abstract fun imports(): ImportDao

    abstract fun scrobbles(): ScrobbleDao

    companion object {
        fun open(context: Context): SitaraDatabase = Room.databaseBuilder(context, SitaraDatabase::class.java, "sitara.db").build()
    }
}
