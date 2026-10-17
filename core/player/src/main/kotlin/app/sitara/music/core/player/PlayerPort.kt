package app.sitara.music.core.player

import app.sitara.music.core.model.RepeatMode

/**
 * The slice of a media player the [PlaybackEngine] drives. The service backs it with
 * ExoPlayer; tests back it with an in-memory fake. Indices refer to the queue entries
 * last handed over with [setQueue] or [reorder].
 */
interface PlayerPort {
    val isPlaying: Boolean
    val positionMs: Long

    /** Duration of the current item, or 0 when unknown. */
    val durationMs: Long

    /** Replaces the playlist and starts at [index]. */
    fun setQueue(entries: List<QueueEntry>, index: Int, play: Boolean)

    /** Same entries in a new order; the playing entry keeps playing without a reload. */
    fun reorder(entries: List<QueueEntry>, index: Int)

    fun seekToItem(index: Int, positionMs: Long)

    fun seekTo(positionMs: Long)

    fun play()

    fun pause()

    fun setRepeatMode(mode: RepeatMode)

    /** Pause when the current item finishes instead of moving to the next one. */
    fun setPauseAtEndOfItem(enabled: Boolean)
}
