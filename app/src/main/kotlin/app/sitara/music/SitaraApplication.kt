package app.sitara.music

import android.app.Application
import android.util.Log
import androidx.media3.datasource.cache.Cache
import app.sitara.music.core.data.SettingsRepository
import app.sitara.music.core.data.library.LibraryStore
import app.sitara.music.core.model.CachedLyrics
import app.sitara.music.core.model.SavedQueue
import app.sitara.music.core.model.Track
import app.sitara.music.core.player.DownloadDependencies
import app.sitara.music.core.player.PlaybackDependencies
import app.sitara.music.core.player.PlayerController
import app.sitara.music.core.player.SongDownloads
import app.sitara.music.source.api.LyricsProvider
import app.sitara.music.source.api.StreamResolver
import app.sitara.music.source.lyrics.LyricsCache
import app.sitara.music.source.lyrics.lrclibLyrics
import app.sitara.music.source.youtube.YouTubeMusicSource
import app.sitara.music.source.youtube.YouTubeStreamResolver
import app.sitara.music.source.youtube.installNewPipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Dependencies are wired by hand (see ADR 0001). Home's chart, search, detail pages, radio and streams come from
 * YouTube, lyrics from LRCLIB; the library, the play queue, fetched lyrics and downloaded songs are stored on the device.
 */
class SitaraApplication :
    Application(),
    PlaybackDependencies,
    DownloadDependencies {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val youtube by lazy { YouTubeMusicSource() }
    val settings by lazy { SettingsRepository(this) }
    val library by lazy { LibraryStore(this) }
    val lyrics: LyricsProvider by lazy {
        lrclibLyrics(
            object : LyricsCache {
                override suspend fun get(trackId: String) = library.lyrics.get(trackId)

                override suspend fun put(trackId: String, lyrics: CachedLyrics) = library.lyrics.put(trackId, lyrics)
            },
        )
    }

    override val streamResolver: StreamResolver by lazy { YouTubeStreamResolver() }
    override val songDownloads: SongDownloads by lazy { SongDownloads(this, streamResolver, appScope, settings.settings.map { it.downloadOnWifiOnly }.distinctUntilChanged()) }
    override val downloadCache: Cache get() = songDownloads.cache
    override val playbackSettings get() = settings.settings
    val player by lazy { PlayerController(this, appScope) }

    override fun onTrackStarted(track: Track) = writeLibrary { library.history.record(track) }

    override suspend fun savedQueue(): SavedQueue? = library.queue.load()

    override fun saveQueue(queue: SavedQueue) = writeLibrary { library.queue.save(queue) }

    /** Runs a library write past the screen that asked for it; a failed write (disk full, a playlist deleted meanwhile) is logged, never fatal. */
    fun writeLibrary(write: suspend () -> Unit) {
        appScope.launch {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Sitara", "Library write failed", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        installNewPipe()
    }
}
