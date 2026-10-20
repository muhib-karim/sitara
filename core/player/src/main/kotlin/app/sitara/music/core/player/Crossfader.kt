package app.sitara.music.core.player

import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import app.sitara.music.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Overlaps the end of one song with the start of the next. Shortly before the last [windowMs] of the playing song, a
 * second player ([tail]) loads that song at the fade point. When [main] gets there, the tail takes over the song (a
 * short hand-over while both play the same audio), [main] moves on to the next song at zero volume, and the two fade
 * across ([Crossfade.gains], timed by the tail's own position). [main] keeps the session, the queue and the saved
 * position, so system controls, queue edits and restore work as without crossfade. Pausing, seeking, skipping or an
 * interruption such as a call ends a fade at once. Main thread only.
 */
@OptIn(UnstableApi::class)
internal class Crossfader(
    private val main: ExoPlayer,
    private val tail: ExoPlayer,
    private val scope: CoroutineScope,
    private val trackOf: (MediaItem) -> Track?,
    /** Called just before the tail loads, so it can match the main player's loudness. */
    private val onTailPrepare: () -> Unit,
) : Player.Listener {
    var windowMs = 0L
        set(value) {
            if (field == value) return
            field = value
            stop()
        }

    // The main player's item the tail is loaded for, and the message that starts the fade there.
    private var armedFor: MediaItem? = null
    private var startMessage: PlayerMessage? = null
    private var watching: Job? = null
    private var fading: Job? = null

    // The item the hand-over seeks the main player to; its transition is ours, not the listener's.
    private var handedTo: MediaItem? = null

    init {
        main.addListener(this)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying && watching == null) {
            watching = scope.launch {
                while (isActive) {
                    arm()
                    delay(WATCH_INTERVAL_MS)
                }
            }
        } else if (!isPlaying) {
            watching?.cancel()
            watching = null
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) stop()
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) stop()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (mediaItem == null || mediaItem != handedTo) stop()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK && newPosition.mediaItem != handedTo) stop()
    }

    /** Ends any fade or prepared tail and puts the main player back at full volume. */
    fun stop() {
        startMessage?.cancel()
        startMessage = null
        fading?.cancel()
        fading = null
        armedFor = null
        handedTo = null
        tail.stop()
        tail.clearMediaItems()
        main.volume = 1f
    }

    fun release() {
        stop()
        main.removeListener(this)
        tail.release()
    }

    /** Loads the playing song's tail once playback nears its fade, and schedules the fade. */
    private fun arm() {
        if (windowMs == 0L) return
        val item = main.currentMediaItem ?: return
        if (fading != null || item == armedFor) return
        val durationMs = main.duration.takeIf { it != C.TIME_UNSET } ?: return
        val fadeAtMs = durationMs - windowMs
        val positionMs = main.currentPosition
        if (positionMs < Crossfade.prepareAtMs(durationMs, windowMs) || positionMs >= fadeAtMs || !overlapsNext(item, durationMs)) return
        stop()
        armedFor = item
        onTailPrepare()
        tail.playbackParameters = main.playbackParameters
        tail.setMediaItem(item, fadeAtMs)
        tail.volume = 0f
        tail.prepare()
        startMessage = main.createMessage { _, _ -> handOver(item, durationMs) }
            .setLooper(Looper.getMainLooper())
            .setPosition(main.currentMediaItemIndex, fadeAtMs)
            .setDeleteAfterDelivery(true)
            .send()
    }

    private fun overlapsNext(item: MediaItem, durationMs: Long): Boolean {
        val nextIndex = main.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || nextIndex == main.currentMediaItemIndex) return false
        val current = trackOf(item) ?: return false
        return Crossfade.shouldOverlap(
            windowMs, current, trackOf(main.getMediaItemAt(nextIndex)), durationMs,
            repeatOne = main.repeatMode == Player.REPEAT_MODE_ONE, pauseAtEnd = main.pauseAtEndOfMediaItems,
        )
    }

    /** At the fade point: if the tail is ready and the queue still calls for a fade, the tail takes over and the songs fade across. */
    private fun handOver(item: MediaItem, durationMs: Long) {
        startMessage = null
        if (main.currentMediaItem != item || !main.isPlaying || tail.playbackState != Player.STATE_READY || !overlapsNext(item, durationMs)) {
            // Too late or no longer wanted: the songs meet gapless instead.
            stop()
            return
        }
        val window = windowMs
        tail.play()
        fading = scope.launch {
            val startBy = SystemClock.elapsedRealtime() + TAIL_START_TIMEOUT_MS
            while (!tail.isPlaying) {
                if (SystemClock.elapsedRealtime() > startBy) {
                    fading = null
                    stop()
                    return@launch
                }
                delay(FRAME_MS)
            }
            // Both play the same audio for a moment, a few milliseconds apart, so the switch has no gap.
            val handOverStart = SystemClock.elapsedRealtime()
            while (true) {
                val share = ((SystemClock.elapsedRealtime() - handOverStart).toFloat() / HAND_OVER_MS).coerceIn(0f, 1f)
                tail.volume = share
                main.volume = 1f - share
                if (share >= 1f) break
                delay(FRAME_MS)
            }
            val next = main.getMediaItemAt(main.nextMediaItemIndex)
            handedTo = next
            main.seekTo(main.nextMediaItemIndex, 0)
            // A tail that stalls or fails stops moving; the fade still ends on time with the next song at full volume.
            // The window is song time, so at half speed it takes twice as long on the clock.
            val endBy = SystemClock.elapsedRealtime() + (window / tail.playbackParameters.speed).toLong() + FADE_GRACE_MS
            while (true) {
                val (out, incoming) = Crossfade.gains(Crossfade.progress(tail.currentPosition, durationMs, window))
                tail.volume = out
                main.volume = incoming
                val tailGone = tail.playbackState == Player.STATE_ENDED || tail.playbackState == Player.STATE_IDLE
                if (incoming >= 1f || tailGone || SystemClock.elapsedRealtime() > endBy) break
                delay(FRAME_MS)
            }
            fading = null
            stop()
        }
    }

    private companion object {
        const val WATCH_INTERVAL_MS = 500L
        const val TAIL_START_TIMEOUT_MS = 1_000L
        const val HAND_OVER_MS = 80L
        const val FRAME_MS = 30L
        const val FADE_GRACE_MS = 2_000L
    }
}
