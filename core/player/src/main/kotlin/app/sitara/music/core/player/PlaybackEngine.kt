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
    private val onTrackStarted: (Track) -> Unit = {},
) {
    private val _state = MutableStateFlow(PlaybackSnapshot())
    val state: StateFlow<PlaybackSnapshot> = _state.asStateFlow()

    private val sleepTimer = SleepTimer(scope, timeSource) { port.pause() }
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state

    private val queue: QueueState get() = _state.value.queue

    // The entry whose start was last reported; cleared when that same entry starts over.
    private var startedUid: Long? = null

    fun play(tracks: List<Track>, index: Int) {
        val next = queue.setQueue(tracks, index)
        publish(next)
        if (next.currentIndex >= 0) port.setQueue(next.entries, next.currentIndex, play = true)
    }

    /** Plays [tracks] in random order from a random one; turning shuffle off restores the list order. */
    fun shuffle(tracks: List<Track>, random: Random = Random.Default) {
        if (tracks.isEmpty()) return
        val next = queue.setQueue(tracks, random.nextInt(tracks.size)).toggleShuffle(random)
        publish(next)
        port.setQueue(next.entries, next.currentIndex, play = true)
    }

    /** Extends a non-empty queue after its last entry, keeping the playing item and the ended flag as they are. */
    fun append(tracks: List<Track>) {
        val q = queue
        if (tracks.isEmpty() || q.currentEntry == null) return
        val next = tracks.fold(q) { acc, track -> acc.add(track) }.copy(ended = q.ended)
        publish(next)
        port.insert(q.entries.size, next.entries.takeLast(tracks.size))
    }

    /** Queues [tracks] after the last entry, or plays them when nothing is queued. */
    fun addToQueue(tracks: List<Track>) = if (queue.currentEntry == null) play(tracks, 0) else append(tracks)

    /** Queues [tracks] right after the current entry, or plays them when nothing is queued. */
    fun playNext(tracks: List<Track>) {
        val q = queue
        if (q.currentEntry == null) return play(tracks, 0)
        if (tracks.isEmpty()) return
        val next = q.playNext(tracks)
        publish(next)
        port.insert(q.currentIndex + 1, next.entries.subList(q.currentIndex + 1, q.currentIndex + 1 + tracks.size))
    }

    fun moveUpNext(from: Int, to: Int) {
        val q = queue
        val next = q.moveUpNext(from, to)
        if (next === q) return
        publish(next)
        port.move(q.currentIndex + 1 + from, q.currentIndex + 1 + to)
    }

    fun removeUpNext(index: Int) {
        val q = queue
        val next = q.removeUpNext(index)
        if (next === q) return
        publish(next)
        port.remove(q.currentIndex + 1 + index)
    }

    fun togglePlay() {
        val q = queue
        if (q.currentEntry == null) return
        when {
            q.ended -> {
                startedUid = null
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
        if (port.positionMs > RESTART_THRESHOLD_MS) {
            seekTo(0)
        } else {
            moveTo(queue.previous())
        }
        refresh()
    }

    fun playUpNext(upNextIndex: Int) = moveTo(queue.skipTo(queue.currentIndex + 1 + upNextIndex))

    fun seekTo(positionMs: Long) {
        publish(queue.copy(ended = false))
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
        if (uid == startedUid) startedUid = null // repeat-one, or a one-song repeat-all
        publish(queue.select(uid))
        refresh()
    }

    /** Mirrors completion in both directions, including same-item seeks from system controls. */
    fun onPlaybackStateChanged(ended: Boolean) {
        publish(queue.copy(ended = ended))
        refresh()
    }

    /** The player paused itself at the end of an item because end-of-track sleep was set. */
    fun onPausedAtEndOfItem() {
        if (sleepTimer.onTrackEnded()) port.setPauseAtEndOfItem(false)
        refresh()
    }

    /** Pulls position, duration and play state from the player, and reports an entry that just started playing. */
    fun refresh() {
        _state.update {
            it.copy(
                isPlaying = port.isPlaying,
                positionMs = port.positionMs.coerceAtLeast(0),
                durationMs = port.durationMs.takeIf { d -> d > 0 } ?: (it.queue.current?.durationMs ?: 0),
            )
        }
        val entry = queue.currentEntry
        if (port.isPlaying && entry != null && entry.uid != startedUid) {
            startedUid = entry.uid
            onTrackStarted(entry.track)
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
