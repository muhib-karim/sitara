package app.sitara.music

import android.app.Application
import android.util.Log
import app.sitara.music.core.data.SettingsRepository
import app.sitara.music.core.data.library.LibraryStore
import app.sitara.music.core.model.Track
import app.sitara.music.core.player.PlaybackDependencies
import app.sitara.music.core.player.PlayerController
import app.sitara.music.source.api.StreamResolver
import app.sitara.music.source.youtube.YouTubeMusicSource
import app.sitara.music.source.youtube.YouTubeStreamResolver
import app.sitara.music.source.youtube.installNewPipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Dependencies are wired by hand (see ADR 0001). Home's chart, search, detail pages, radio and streams come from
 * YouTube; the library is stored on the device.
 */
class SitaraApplication :
    Application(),
    PlaybackDependencies {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val youtube by lazy { YouTubeMusicSource() }
    val settings by lazy { SettingsRepository(this) }
    val library by lazy { LibraryStore(this) }

    override val streamResolver: StreamResolver by lazy { YouTubeStreamResolver() }
    val player by lazy { PlayerController(this, appScope) }

    override fun onTrackStarted(track: Track) = writeLibrary { library.history.record(track) }

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
