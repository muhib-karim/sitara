package app.sitara.music.core.player

import app.sitara.music.core.model.PlaybackTuning
import app.sitara.music.core.model.RepeatMode

/** In-memory player: holds the playlist the engine hands it and records what was asked of it. */
class FakePlayerPort : PlayerPort {
    var entries: List<QueueEntry> = emptyList()
    var index: Int = -1
    var playing: Boolean = false
    var repeat: RepeatMode = RepeatMode.OFF
    var pauseAtEnd: Boolean = false
    var tuning: PlaybackTuning = PlaybackTuning()
    var setQueueCalls = 0
    var reorderCalls = 0
    override var positionMs: Long = 0
    override var durationMs: Long = 0

    override val isPlaying: Boolean get() = playing

    override fun setQueue(entries: List<QueueEntry>, index: Int, play: Boolean) {
        setQueueCalls++
        this.entries = entries
        this.index = index
        positionMs = 0
        playing = play
    }

    override fun reorder(entries: List<QueueEntry>, index: Int) {
        reorderCalls++
        this.entries = entries
        this.index = index
    }

    override fun insert(index: Int, entries: List<QueueEntry>) {
        this.entries = this.entries.take(index) + entries + this.entries.drop(index)
        if (this.index >= index) this.index += entries.size
    }

    override fun move(from: Int, to: Int) {
        entries = entries.toMutableList().apply { add(to, removeAt(from)) }
        index = when {
            index == from -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
    }

    override fun remove(index: Int) {
        entries = entries.toMutableList().apply { removeAt(index) }
        if (index < this.index) this.index--
    }

    override fun seekToItem(index: Int, positionMs: Long) {
        this.index = index
        this.positionMs = positionMs
    }

    override fun seekTo(positionMs: Long) {
        this.positionMs = positionMs
    }

    override fun play() {
        playing = true
    }

    override fun pause() {
        playing = false
    }

    override fun setRepeatMode(mode: RepeatMode) {
        repeat = mode
    }

    override fun setPauseAtEndOfItem(enabled: Boolean) {
        pauseAtEnd = enabled
    }

    override fun setTuning(tuning: PlaybackTuning) {
        this.tuning = tuning
    }
}
