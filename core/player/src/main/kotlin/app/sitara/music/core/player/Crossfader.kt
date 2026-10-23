package app.sitara.music.core.player

import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import app.sitara.music.core.model.SongBeats
import app.sitara.music.core.model.Track
import app.sitara.music.core.player.automix.AutomixPlanner
import app.sitara.music.core.player.automix.Transition
import app.sitara.music.core.player.automix.automixGains
import app.sitara.music.core.player.automix.beatSyncCorrection
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Overlaps the end of one song with the start of the next. Shortly before the blend, a second player ([tail]) loads
 * the playing song at the fade point. When [main] gets there, the tail takes over the song (a short hand-over while
 * both play the same audio), [main] moves on to the next song at zero volume, and the two fade across, timed by the
 * tail's own position. [main] keeps the session, the queue and the saved position, so system controls, queue edits and
 * restore work as without crossfade. Pausing, seeking, skipping, a queue change before the blend or an interruption
 * such as a call ends a blend at once and puts [main] back at full volume. Main thread only.
 *
 * With [automix] on, each blend follows an [AutomixPlanner] plan from [beatsOf]: it starts on a bar line and lasts
 * whole bars, the next song starts on its first bar line, the outgoing song is stretched to the incoming tempo and
 * nudged onto its beat while both play, and the louder side is brought down; without beats it is a plain crossfade of
 * [windowMs] (or [AUTOMIX_FALLBACK_MS] when crossfade is off).
 */
@OptIn(UnstableApi::class)
internal class Crossfader(
    private val main: ExoPlayer,
    private val tail: ExoPlayer,
    private val scope: CoroutineScope,
    private val trackOf: (MediaItem) -> Track?,
    private val beatsOf: (String) -> SongBeats? = { null },
    private val now: () -> Long = SystemClock::elapsedRealtime,
    /** The blend being played, or null; for the player screen. */
    private val onBlend: (Transition?) -> Unit = {},
    /** Called just before the tail loads, so it can match the main player's loudness. */
    private val onTailPrepare: () -> Unit,
) : Player.Listener {
    var windowMs = 0L
        set(value) {
            if (field == value) return
            field = value
            stop()
        }

    var automix = false
        set(value) {
            if (field == value) return
            field = value
            stop()
        }

    // The main player's item the tail is loaded for, the plan for it, and the message that starts the fade there.
    private var armedFor: MediaItem? = null
    private var armedPlan: Transition? = null
    private var startMessage: PlayerMessage? = null
    private var watching: Job? = null
    private var fading: Job? = null

    // The item the hand-over seeks the main player to; its transition is ours, not the listener's.
    private var handedTo: MediaItem? = null

    private val effectiveWindowMs get() = if (windowMs == 0L && automix) AUTOMIX_FALLBACK_MS else windowMs

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

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        // A queue edit before the blend may change the next song; the plan is made again on the next watch.
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && armedFor != null && fading == null) stop()
    }

    /** Ends any fade or prepared tail and puts the main player back at full volume. */
    fun stop() {
        startMessage?.cancel()
        startMessage = null
        fading?.cancel()
        fading = null
        val wasActive = armedFor != null || handedTo != null
        armedFor = null
        armedPlan = null
        handedTo = null
        tail.stop()
        tail.clearMediaItems()
        // A stretched tail goes back to the session's speed, so nothing of a blend outlives it.
        tail.playbackParameters = main.playbackParameters
        main.volume = 1f
        if (wasActive) onBlend(null)
    }

    fun release() {
        stop()
        main.removeListener(this)
        tail.release()
    }

    /** Loads the playing song's tail once playback nears its fade, and schedules the fade. */
    private fun arm() {
        val window = effectiveWindowMs
        if (window == 0L) return
        val item = main.currentMediaItem ?: return
        if (fading != null || item == armedFor) return
        val durationMs = main.duration.takeIf { it != C.TIME_UNSET } ?: return
        val positionMs = main.currentPosition
        val next = nextItem() ?: return
        if (!overlapsNext(item, next, durationMs, window)) return
        val plan = planFor(item, next, durationMs, window)
        if (positionMs < (plan.fadeAtMs - Crossfade.PREPARE_LEAD_MS).coerceAtLeast(0) || positionMs >= plan.fadeAtMs) return
        stop()
        armedFor = item
        armedPlan = plan
        onTailPrepare()
        tail.playbackParameters = main.playbackParameters
        tail.setMediaItem(item, plan.fadeAtMs)
        tail.volume = 0f
        tail.prepare()
        startMessage = main.createMessage { _, _ -> handOver(item, durationMs, plan) }
            .setLooper(Looper.getMainLooper())
            .setPosition(main.currentMediaItemIndex, plan.fadeAtMs)
            .setDeleteAfterDelivery(true)
            .send()
    }

    private fun planFor(item: MediaItem, next: MediaItem, durationMs: Long, window: Long): Transition {
        val plain = Transition((durationMs - window).coerceAtLeast(0), window.coerceAtMost(durationMs), 0, 1f, 0f, onBeat = false)
        if (!automix) return plain
        val current = trackOf(item) ?: return plain
        val following = trackOf(next) ?: return plain
        return AutomixPlanner.plan(beatsOf(current.id), durationMs, beatsOf(following.id), window)
    }

    private fun nextItem(): MediaItem? {
        val nextIndex = main.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || nextIndex == main.currentMediaItemIndex) return null
        return main.getMediaItemAt(nextIndex)
    }

    private fun overlapsNext(item: MediaItem, next: MediaItem, durationMs: Long, window: Long): Boolean {
        val current = trackOf(item) ?: return false
        return Crossfade.shouldOverlap(
            window, current, trackOf(next), durationMs,
            repeatOne = main.repeatMode == Player.REPEAT_MODE_ONE, pauseAtEnd = main.pauseAtEndOfMediaItems,
        )
    }

    /** At the fade point: if the tail is ready and the queue still calls for a fade, the tail takes over and the songs fade across. */
    private fun handOver(item: MediaItem, durationMs: Long, plan: Transition) {
        startMessage = null
        val next = nextItem()
        if (main.currentMediaItem != item || !main.isPlaying || tail.playbackState != Player.STATE_READY || next == null ||
            !overlapsNext(item, next, durationMs, effectiveWindowMs)
        ) {
            // Too late or no longer wanted: the songs meet gapless instead.
            stop()
            return
        }
        val session = main.playbackParameters
        tail.play()
        fading = scope.launch {
            val startBy = now() + TAIL_START_TIMEOUT_MS
            while (!tail.isPlaying) {
                if (now() > startBy) {
                    fading = null
                    stop()
                    return@launch
                }
                delay(FRAME_MS)
            }
            // Both play the same audio for a moment, a few milliseconds apart, so the switch has no gap.
            val handOverStart = now()
            while (true) {
                val share = ((now() - handOverStart).toFloat() / HAND_OVER_MS).coerceIn(0f, 1f)
                tail.volume = share
                main.volume = 1f - share
                if (share >= 1f) break
                delay(FRAME_MS)
            }
            handedTo = next
            main.seekTo(main.nextMediaItemIndex, plan.incomingStartMs)
            val baseSpeed = session.speed * plan.tailSpeed
            if (plan.tailSpeed != 1f) tail.playbackParameters = PlaybackParameters(baseSpeed, session.pitch)
            if (plan.onBeat) onBlend(plan)
            // A tail that stalls or fails stops moving; the fade still ends on time with the next song at full volume.
            // The window is song time, so at half speed it takes twice as long on the clock.
            val endBy = now() + (plan.windowMs / baseSpeed).toLong() + FADE_GRACE_MS
            var lastSync = now()
            var speed = baseSpeed
            while (true) {
                val progress = ((tail.currentPosition - plan.fadeAtMs).toFloat() / plan.windowMs).coerceIn(0f, 1f)
                val (out, incoming) = if (automix) automixGains(progress, plan.matchDb) else Crossfade.gains(progress)
                tail.volume = out
                main.volume = incoming
                val outGrid = plan.outgoingGrid
                val inGrid = plan.incomingGrid
                if (outGrid != null && inGrid != null && plan.tailSpeed != 1f && now() - lastSync >= SYNC_INTERVAL_MS) {
                    lastSync = now()
                    // The incoming song's beat phase at its tempo against the outgoing song's: nudge the tail toward it.
                    // At half or double time, one song's beat lines up with every other beat of the other.
                    val multiple = listOf(1.0, 2.0, 0.5).minBy { abs(inGrid.bpm * it / outGrid.bpm - plan.tailSpeed) }
                    val inPhase = inGrid.beatPhase(main.currentPosition.toDouble()).let { if (multiple == 2.0) (it * 2) % 1.0 else it }
                    val outPhase = outGrid.beatPhase(tail.currentPosition.toDouble()).let { if (multiple == 0.5) (it * 2) % 1.0 else it }
                    val target = baseSpeed * (1 + beatSyncCorrection(inPhase, outPhase)).toFloat()
                    if (abs(target - speed) > speed * 0.002f) {
                        speed = target
                        tail.playbackParameters = PlaybackParameters(speed, session.pitch)
                    }
                }
                val tailGone = tail.playbackState == Player.STATE_ENDED || tail.playbackState == Player.STATE_IDLE
                if (incoming >= 1f || tailGone || now() > endBy) break
                delay(FRAME_MS)
            }
            fading = null
            stop()
        }
    }

    companion object {
        /** An automix blend without beats to go by, when crossfade itself is off. */
        const val AUTOMIX_FALLBACK_MS = 6_000L
        private const val WATCH_INTERVAL_MS = 500L
        private const val TAIL_START_TIMEOUT_MS = 1_000L
        private const val HAND_OVER_MS = 80L
        private const val FRAME_MS = 30L
        private const val FADE_GRACE_MS = 2_000L
        private const val SYNC_INTERVAL_MS = 250L
    }
}
