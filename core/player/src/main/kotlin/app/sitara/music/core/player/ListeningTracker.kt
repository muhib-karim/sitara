package app.sitara.music.core.player

import app.sitara.music.core.model.Listen
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** A play counts once heard this long, or for half the song when that is shorter. */
internal const val PLAY_COUNTS_AFTER_MS = 30_000L

/**
 * Measures how long each queue entry is heard: only time while the player plays counts, so pauses, buffering and a
 * failed stream add nothing and a seek adds only what is then played. Once a play counts, [onListen] gets it at every
 * checkpoint (a pause, the next song, a periodic save) with the time heard so far; the same play keeps its start time.
 */
internal class ListeningTracker(
    private val timeSource: TimeSource,
    private val wallClock: () -> Long,
    private val onListen: (Listen) -> Unit,
) {
    private var entry: QueueEntry? = null

    // When the entry first played; null until it does.
    private var startedAtMs: Long? = null
    private var heardMs = 0L
    private var playingSince: TimeMark? = null

    // The time heard last reported, so a checkpoint with nothing more heard (a pause, then the next song) stays quiet.
    private var reportedMs = 0L

    /** Follows the player: the current entry and whether it is playing right now. */
    fun update(current: QueueEntry?, playing: Boolean) {
        if (current?.uid != entry?.uid) {
            finish()
            entry = current
        }
        val since = playingSince
        if (playing && since == null && entry != null) {
            if (startedAtMs == null) startedAtMs = wallClock()
            playingSince = timeSource.markNow()
        } else if (!playing && since != null) {
            heardMs += since.elapsedNow().inWholeMilliseconds
            playingSince = null
            checkpoint()
        }
    }

    /** [uid] plays again from the top (repeat one, or play after the queue ended): if it is the entry being heard, that is a new play. */
    fun startOver(uid: Long) {
        val current = entry?.takeIf { it.uid == uid } ?: return
        finish()
        entry = current
    }

    /** Reports the current play with the time heard so far, once it counts. */
    fun checkpoint() {
        val track = entry?.track ?: return
        val startedAt = startedAtMs ?: return
        val heard = heardMs + (playingSince?.elapsedNow()?.inWholeMilliseconds ?: 0)
        val countsAfter = if (track.durationMs > 0) minOf(PLAY_COUNTS_AFTER_MS, track.durationMs / 2) else PLAY_COUNTS_AFTER_MS
        if (heard < countsAfter || heard == reportedMs) return
        reportedMs = heard
        onListen(Listen(track, startedAt, heard))
    }

    private fun finish() {
        playingSince?.let { heardMs += it.elapsedNow().inWholeMilliseconds }
        playingSince = null
        checkpoint()
        entry = null
        startedAtMs = null
        heardMs = 0
        reportedMs = 0
    }
}
