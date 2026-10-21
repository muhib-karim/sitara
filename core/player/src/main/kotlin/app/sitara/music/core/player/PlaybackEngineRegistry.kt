package app.sitara.music.core.player

import androidx.media3.datasource.cache.Cache
import app.sitara.music.core.model.Listen
import app.sitara.music.core.model.SavedQueue
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.StreamResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands the running service's engine to in-process UI. The engine only exists while
 * [PlaybackService] is alive; screens come and go without affecting it.
 */
object PlaybackEngineRegistry {
    private val _engine = MutableStateFlow<PlaybackEngine?>(null)
    val engine: StateFlow<PlaybackEngine?> = _engine.asStateFlow()

    internal fun attach(engine: PlaybackEngine) {
        _engine.value = engine
    }

    internal fun detach(engine: PlaybackEngine) {
        _engine.compareAndSet(engine, null)
    }

    /** The running service's audio session, for the device's equaliser panel; [SystemEqualiserSession.NO_SESSION] while none runs. */
    @Volatile var audioSessionId = SystemEqualiserSession.NO_SESSION
        internal set
}

/** Implemented by the Application so the service can reach app-level dependencies without a DI framework. */
interface PlaybackDependencies {
    val streamResolver: StreamResolver

    /** Downloaded songs; the player plays from it before it streams. */
    val downloadCache: Cache

    /** The listener's settings; the player follows normalisation and crossfade as they change. */
    val playbackSettings: Flow<SitaraSettings>

    /** A play that counts, or one already reported heard for longer (same song and start); called on the main thread, so implementations hand slow work off. */
    fun onListened(listen: Listen)

    /** A play starting (for scrobbling's "now playing"); called on the main thread, so implementations hand slow work off. */
    fun onPlayStarted(track: Track)

    /** The song's volume trim in dB, 0 when none is set; the player follows it while the song plays. */
    fun songGainDb(trackId: String): Flow<Int>

    /** Whether the song is among the liked songs, following changes; the notification's heart shows it. */
    fun isLiked(trackId: String): Flow<Boolean>

    /** Likes or unlikes [track] from the notification; implementations hand the write off. */
    fun setLiked(track: Track, liked: Boolean)

    /** The library as a car browses it. */
    val libraryContent: LibraryContent

    /** The queue saved before the process last ended, or null. */
    suspend fun savedQueue(): SavedQueue?

    /** Keeps [queue] for after a restart; called on the main thread and must outlive the service, so implementations hand the write off. */
    fun saveQueue(queue: SavedQueue)
}
