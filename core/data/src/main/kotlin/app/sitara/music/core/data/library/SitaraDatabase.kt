package app.sitara.music.core.data.library

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.sitara.music.core.data.backup.BackupDao
import app.sitara.music.core.data.beats.SongBeatsDao
import app.sitara.music.core.data.beats.SongBeatsEntity
import app.sitara.music.core.data.imports.ImportCandidateEntity
import app.sitara.music.core.data.imports.ImportDao
import app.sitara.music.core.data.imports.ImportJobEntity
import app.sitara.music.core.data.imports.ImportRowEntity
import app.sitara.music.core.data.local.LocalDao
import app.sitara.music.core.data.local.LocalFolderEntity
import app.sitara.music.core.data.local.LocalSongEntity
import app.sitara.music.core.data.scrobble.ScrobbleDao
import app.sitara.music.core.data.scrobble.ScrobbleEntity

/** The listener's library, songs on the phone, the saved play queue, cached lyrics and their translations, per-song volume trims, playlist imports, plays waiting to be scrobbled and automix's beat analyses. A schema change bumps [version] and ships a migration; the exported schema is committed. */
@Database(
    entities = [
        LikedSongEntity::class, PlayEntity::class, SavedAlbumEntity::class, SavedArtistEntity::class, PlaylistEntity::class, PlaylistEntryEntity::class,
        QueueEntryEntity::class, QueueStateEntity::class, LyricsCacheEntity::class, LyricsChoiceEntity::class, LyricsTranslationEntity::class, SongGainEntity::class,
        ImportJobEntity::class, ImportRowEntity::class, ImportCandidateEntity::class, ScrobbleEntity::class, LocalSongEntity::class, LocalFolderEntity::class,
        SongBeatsEntity::class,
    ],
    version = 10,
    exportSchema = true,
    // 2 adds the saved play queue and the lyrics cache, 3 per-song volume trims: new tables only. 4 adds each play's time heard (null for older plays) and an index by start; 5 playlist imports, 6 the scrobble outbox (new tables). 7 keys cached lyrics by source as well (old rows become LRCLIB's) with the matched song and licensor, and keeps the source picked per song; 8 keeps lyric translations, 9 the index of songs on the phone and picked folders, 10 automix's beat analyses (new tables).
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9), AutoMigration(from = 9, to = 10)],
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

    abstract fun local(): LocalDao

    abstract fun backup(): BackupDao

    abstract fun songBeats(): SongBeatsDao

    companion object {
        fun open(context: Context): SitaraDatabase = Room.databaseBuilder(context, SitaraDatabase::class.java, "sitara.db").build()
    }
}
