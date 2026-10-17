package app.sitara.music.core.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.StreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackSnapshot(
    val queue: QueueState = QueueState(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/**
 * App-side facade over the MediaController. Our [QueueState] is the source of truth
 * for order, shuffle and repeat; the player only ever holds the current item, which
 * keeps shuffle-restore and end-of-track sleep logic in tested Kotlin.
 */
class PlayerController(
    private val context: Context,
    private val resolver: StreamResolver,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlaybackSnapshot())
    val state: StateFlow<PlaybackSnapshot> = _state.asStateFlow()
    private var controller: MediaController? = null

    val sleepTimer = SleepTimer(scope) { controller?.pause() }

    fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) onTrackEnded()
                    refresh()
                }
            })
            _state.value.queue.current?.let { load(it, play = false) }
        }, ContextCompat.getMainExecutor(context))
        scope.launch {
            while (isActive) {
                if (_state.value.isPlaying) refresh()
                delay(250)
            }
        }
    }

    fun release() {
        controller?.release()
        controller = null
    }

    fun play(tracks: List<Track>, index: Int) = setQueue(_state.value.queue.setQueue(tracks, index), play = true)

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = setQueue(_state.value.queue.skipNext(), play = true)

    fun previous() {
        val c = controller
        if (c != null && c.currentPosition > RESTART_THRESHOLD_MS) c.seekTo(0) else setQueue(_state.value.queue.previous(), play = true)
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        refresh()
    }

    fun toggleShuffle() = setQueue(_state.value.queue.toggleShuffle(), play = null)

    fun cycleRepeat() {
        val q = _state.value.queue
        val next = when (q.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.update { it.copy(queue = q.withRepeat(next)) }
    }

    fun playUpNext(upNextIndex: Int) {
        val q = _state.value.queue
        setQueue(q.skipTo(q.currentIndex + 1 + upNextIndex), play = true)
    }

    private fun onTrackEnded() {
        if (sleepTimer.onTrackEnded()) return
        val q = _state.value.queue
        if (q.repeatMode == RepeatMode.ONE) {
            controller?.seekTo(0)
            controller?.play()
            return
        }
        setQueue(q.next(), play = true)
    }

    /** play: true = start, false = load paused, null = keep the current item untouched. */
    private fun setQueue(queue: QueueState, play: Boolean?) {
        val before = _state.value.queue.current
        _state.update { it.copy(queue = queue) }
        val current = queue.current ?: return
        if (queue.ended) {
            controller?.pause()
            return
        }
        if (play != null && (current != before || play)) load(current, play)
    }

    private fun load(track: Track, play: Boolean) {
        scope.launch {
            val stream = runCatching { resolver.resolve(track) }.getOrNull() ?: return@launch
            val c = controller ?: return@launch
            val item = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(stream.url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .build(),
                )
                .build()
            c.setMediaItem(item)
            c.prepare()
            if (play) c.play()
            refresh()
        }
    }

    private fun refresh() {
        val c = controller ?: return
        _state.update {
            it.copy(
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = c.duration.takeIf { d -> d > 0 } ?: (it.queue.current?.durationMs ?: 0),
            )
        }
    }

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
