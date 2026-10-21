package app.sitara.music.core.player

import app.sitara.music.core.model.Listen
import app.sitara.music.core.model.PlaybackTuning
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.SavedQueue
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
    val tuning: PlaybackTuning = PlaybackTuning(),
    /** The entry the player last stopped on with an error; cleared once it plays or is retried. */
    val failedUid: Long? = null,
) {
    /** The current song could not be played; the player waits for Retry or Skip. */
    val failed: Boolean get() = failedUid != null && failedUid == queue.currentEntry?.uid
}

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
    /** A play that counts (see [ListeningTracker]), reported again as more of it is heard. */
    onListen: (Listen) -> Unit = {},
    /** A play starting: a new song, or the same one from the top. */
    onPlayStart: (Track) -> Unit = {},
    /** Gets the song the player will move to next ready while the current one plays (gapless joins, instant skips). */
    private val prepareAhead: (Track) -> Unit = {},
) {
    private val _state = MutableStateFlow(PlaybackSnapshot())
    val state: StateFlow<PlaybackSnapshot> = _state.asStateFlow()

    private val sleepTimer = SleepTimer(scope, timeSource) { port.pause() }
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state

    private val queue: QueueState get() = _state.value.queue

    private val listening = ListeningTracker(timeSource, System::currentTimeMillis, onListen, onPlayStart)

    // The entry last handed to prepareAhead, so each following entry is prepared once.
    private var preparedUid: Long? = null

    // The Up next entry removed last and where it was, for Undo; a new queue forgets it.
    private var removed: Pair<Int, QueueEntry>? = null

    fun play(tracks: List<Track>, index: Int) {
        removed = null
        val next = queue.setQueue(tracks, index)
        publish(next)
        if (next.currentIndex >= 0) port.setQueue(next.entries, next.currentIndex, play = true)
    }

    /** Plays [tracks] in random order from a random one; turning shuffle off restores the list order. */
    fun shuffle(tracks: List<Track>, random: Random = Random.Default) {
        if (tracks.isEmpty()) return
        removed = null
        val next = queue.setQueue(tracks, random.nextInt(tracks.size)).toggleShuffle(random)
        publish(next)
        port.setQueue(next.entries, next.currentIndex, play = true)
    }

    /**
     * Extends a non-empty queue, keeping the playing item and the ended flag as they are: radio's songs ([fromRadio])
     * after the last entry, the listener's ahead of radio's.
     */
    fun append(tracks: List<Track>, fromRadio: Boolean = false) {
        val q = queue
        if (tracks.isEmpty() || q.currentEntry == null) return
        val at = if (fromRadio) q.entries.size else q.entries.size - q.upNextFromRadio
        val next = tracks.fold(q) { acc, track -> acc.add(track, fromRadio) }.copy(ended = q.ended)
        publish(next)
        port.insert(at, next.entries.subList(at, at + tracks.size))
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
        removed = index to q.entries[q.currentIndex + 1 + index]
        publish(next)
        port.remove(q.currentIndex + 1 + index)
    }

    /** Puts the Up next song removed last back where it was. */
    fun undoRemoveUpNext() {
        val (index, entry) = removed ?: return
        removed = null
        val q = queue
        val next = q.reinsertUpNext(index, entry)
        if (next === q) return
        publish(next)
        val at = next.entries.indexOfFirst { it.uid == entry.uid }
        port.insert(at, listOf(entry))
    }

    /** Puts back a queue saved before a restart, paused where it was; ignored once the listener has queued anything. */
    fun restore(saved: SavedQueue) {
        if (queue.currentEntry != null) return
        removed = null
        val restored = saved.toQueueState() ?: return
        publish(restored)
        port.setRepeatMode(restored.repeatMode)
        port.setQueue(restored.entries, restored.currentIndex, play = false)
        port.seekToItem(restored.currentIndex, saved.positionMs)
        refresh()
    }

    /** The queue to keep for after a restart, at the player's position; null when nothing is queued. */
    fun savedQueue(): SavedQueue? = queue.toSaved(port.positionMs)

    fun togglePlay() {
        val q = queue
        if (q.currentEntry == null) return
        if (_state.value.failed) return retry()
        when {
            q.ended -> {
                listening.startOver(q.entries[q.currentIndex].uid)
                publish(q.copy(ended = false))
                port.seekToItem(q.currentIndex, 0)
                port.play()
            }
            port.isPlaying -> port.pause()
            else -> port.play()
        }
        refresh()
    }

    /**
     * Loads the failed song again from where it stopped, which looks its stream up afresh. A failure never skips by
     * itself: when YouTube refuses one song it usually refuses the next too, and skipping would run through the queue.
     */
    fun retry() {
        if (queue.currentEntry == null) return
        _state.update { it.copy(failedUid = null) }
        port.play()
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

    /** Speed and pitch hold for the session: through new queues, until reset or the service stops. */
    fun setSpeed(speed: Float) = tune(_state.value.tuning.withSpeed(speed))

    fun setPitch(semitones: Int) = tune(_state.value.tuning.withSemitones(semitones))

    fun resetTuning() = tune(PlaybackTuning())

    fun startSleepTimer(minutes: Int) {
        port.setPauseAtEndOfItem(false)
        sleepTimer.start(minutes)
        prepareFollowing()
    }

    fun extendSleepTimer(minutes: Int) = sleepTimer.extend(minutes)

    fun sleepAtEndOfTrack() {
        sleepTimer.startEndOfTrack()
        port.setPauseAtEndOfItem(true)
        prepareFollowing()
    }

    fun cancelSleepTimer() {
        sleepTimer.cancel()
        port.setPauseAtEndOfItem(false)
        prepareFollowing()
    }

    /** The player moved to another entry by itself (auto-advance or a system control). */
    fun onItemTransition(uid: Long) {
        listening.startOver(uid) // repeat-one, or a one-song repeat-all; another entry is a plain move
        publish(queue.select(uid))
        refresh()
    }

    /** Mirrors completion in both directions, including same-item seeks from system controls. */
    fun onPlaybackStateChanged(ended: Boolean) {
        publish(queue.copy(ended = ended))
        refresh()
    }

    /** The player stopped because the current entry could not be loaded or played. */
    fun onPlayerError() {
        _state.update { it.copy(failedUid = queue.currentEntry?.uid) }
        refresh()
    }

    /** The player paused itself at the end of an item because end-of-track sleep was set. */
    fun onPausedAtEndOfItem() {
        if (sleepTimer.onTrackEnded()) port.setPauseAtEndOfItem(false)
        refresh()
    }

    /** Reports the play being heard with the time heard so far, if it counts yet; for periodic saves and shutdown. */
    fun checkpointListening() = listening.checkpoint()

    /** Pulls position, duration and play state from the player, and follows how long the current entry is heard. */
    fun refresh() {
        _state.update {
            it.copy(
                isPlaying = port.isPlaying,
                positionMs = port.positionMs.coerceAtLeast(0),
                durationMs = port.durationMs.takeIf { d -> d > 0 } ?: (it.queue.current?.durationMs ?: 0),
                failedUid = it.failedUid.takeUnless { port.isPlaying },
            )
        }
        listening.update(queue.currentEntry, port.isPlaying)
        prepareFollowing()
    }

    /** Once the current entry plays, hands over the one that follows it, unless playback stops after this one. */
    private fun prepareFollowing() {
        if (!port.isPlaying) return
        val following = queue.followingEntry.takeUnless { sleepTimer.state.value == SleepTimerState.EndOfTrack }
        if (following?.uid == preparedUid) return
        preparedUid = following?.uid
        following?.let { prepareAhead(it.track) }
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

    private fun tune(tuning: PlaybackTuning) {
        _state.update { it.copy(tuning = tuning) }
        port.setTuning(tuning)
    }

    private fun publish(next: QueueState) {
        _state.update { it.copy(queue = next) }
        prepareFollowing()
    }

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
