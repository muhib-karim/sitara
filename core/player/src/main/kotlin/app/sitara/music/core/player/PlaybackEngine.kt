package app.sitara.music.core.player

import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.Track
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaybackSnapshot(
    val queue: QueueState = QueueState(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/**
 * Playback logic that lives as long as the [PlaybackService], not as long as a screen.
 *
 * [QueueState] decides order, shuffle and repeat; the whole queue is mirrored into the
 * player as a playlist, so the player advances on its own and system controls (notification,
 * Bluetooth, Android Auto) can skip. Player-driven moves come back through [onItemTransition].
 * Runs on the main thread, like the player it drives.
 */
class PlaybackEngine(
    private val port: PlayerPort,
    scope: CoroutineScope,
    timeSource: TimeSource,
) {
    private val _state = MutableStateFlow(PlaybackSnapshot())
    val state: StateFlow<PlaybackSnapshot> = _state.asStateFlow()

    private val sleepTimer = SleepTimer(scope, timeSource) { port.pause() }
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state

    private val queue: QueueState get() = _state.value.queue

    fun play(tracks: List<Track>, index: Int) {
        val next = queue.setQueue(tracks, index)
        publish(next)
        if (next.currentIndex >= 0) port.setQueue(next.entries, next.currentIndex, play = true)
    }

    fun togglePlay() {
        val q = queue
        if (q.currentEntry == null) return
        when {
            q.ended -> {
                publish(q.copy(ended = false))
                port.seekToItem(q.currentIndex, 0)
                port.play()
            }
            port.isPlaying -> port.pause()
            else -> port.play()
        }
        refresh()
    }

    fun next() = moveTo(queue.skipNext())

    fun previous() {
        if (port.positionMs > RESTART_THRESHOLD_MS) port.seekTo(0) else moveTo(queue.previous())
        refresh()
    }

    fun playUpNext(upNextIndex: Int) = moveTo(queue.skipTo(queue.currentIndex + 1 + upNextIndex))

    fun seekTo(positionMs: Long) {
        port.seekTo(positionMs)
        refresh()
    }

    fun toggleShuffle(random: Random = Random.Default) {
        val next = queue.toggleShuffle(random)
        publish(next)
        if (next.currentIndex >= 0) port.reorder(next.entries, next.currentIndex)
    }

    fun cycleRepeat() {
        val mode = when (queue.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        publish(queue.withRepeat(mode))
        port.setRepeatMode(mode)
    }

    fun startSleepTimer(minutes: Int) {
        port.setPauseAtEndOfItem(false)
        sleepTimer.start(minutes)
    }

    fun sleepAtEndOfTrack() {
        sleepTimer.startEndOfTrack()
        port.setPauseAtEndOfItem(true)
    }

    fun cancelSleepTimer() {
        sleepTimer.cancel()
        port.setPauseAtEndOfItem(false)
    }

    /** The player moved to another entry by itself (auto-advance or a system control). */
    fun onItemTransition(uid: Long) {
        publish(queue.select(uid))
        refresh()
    }

    /** The player reached the end of the last item with repeat off. */
    fun onPlaybackEnded() {
        publish(queue.copy(ended = true))
        refresh()
    }

    /** The player paused itself at the end of an item because end-of-track sleep was set. */
    fun onPausedAtEndOfItem() {
        if (sleepTimer.onTrackEnded()) port.setPauseAtEndOfItem(false)
        refresh()
    }

    /** Pulls position, duration and play state from the player. */
    fun refresh() {
        _state.update {
            it.copy(
                isPlaying = port.isPlaying,
                positionMs = port.positionMs.coerceAtLeast(0),
                durationMs = port.durationMs.takeIf { d -> d > 0 } ?: (it.queue.current?.durationMs ?: 0),
            )
        }
    }

    private fun moveTo(next: QueueState) {
        publish(next)
        if (next.currentIndex < 0) return
        if (next.ended) {
            port.pause()
        } else {
            port.seekToItem(next.currentIndex, 0)
            port.play()
        }
        refresh()
    }

    private fun publish(next: QueueState) = _state.update { it.copy(queue = next) }

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
