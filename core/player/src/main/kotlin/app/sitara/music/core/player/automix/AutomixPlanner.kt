package app.sitara.music.core.player.automix

import app.sitara.music.core.model.BeatGrid
import app.sitara.music.core.model.SongBeats
import app.sitara.music.core.player.Crossfade
import app.sitara.music.core.player.Loudness
import kotlin.math.abs
import kotlin.math.floor

/**
 * How one song hands over to the next. Times are song time: [fadeAtMs] and [windowMs] in the outgoing song,
 * [incomingStartMs] in the incoming one.
 */
data class Transition(
    val fadeAtMs: Long,
    val windowMs: Long,
    val incomingStartMs: Long,
    /** The outgoing song's speed during the blend, as a share of the session's speed (1 when not stretched). */
    val tailSpeed: Float,
    /** Outgoing level minus incoming level in dB (capped), so neither side jumps out of the blend. */
    val matchDb: Float,
    /** Whether the blend is lined up on the two songs' bar lines. */
    val onBeat: Boolean,
    val bars: Int = 0,
    val outgoingGrid: BeatGrid? = null,
    val incomingGrid: BeatGrid? = null,
)

/**
 * Plans an automix transition. With a confident beat at the end of the outgoing song and the start of the incoming
 * one, the blend starts on an outgoing bar line, lasts whole bars (16, 8 or 4, whichever first fits between
 * [MIN_WINDOW_MS] and [MAX_WINDOW_MS]), ends before the outgoing song falls silent, and the incoming song starts on
 * its first bar line (skipping at most [MAX_INTRO_SKIP_MS] of quiet intro). The outgoing song is time-stretched to the
 * incoming tempo when they are within [MAX_STRETCH] of each other (half or double time counts); further apart, the
 * blend is kept to [UNMATCHED_WINDOW_MS] or less so beats have no time to drift. Without confident beats on both
 * sides it is a plain crossfade over [fallbackWindowMs] at the end of the song.
 */
object AutomixPlanner {
    const val MIN_WINDOW_MS = 5_000L
    const val MAX_WINDOW_MS = 16_000L
    const val UNMATCHED_WINDOW_MS = 8_000L
    const val MAX_INTRO_SKIP_MS = 8_000L
    const val MAX_STRETCH = 0.08
    const val MAX_MATCH_DB = 6f

    fun plan(outgoing: SongBeats?, outDurationMs: Long, incoming: SongBeats?, fallbackWindowMs: Long): Transition {
        val outro = outgoing?.outro
        val intro = incoming?.intro
        val matchDb = if (outro != null && intro != null) (outro.loudnessDb - intro.loudnessDb).coerceIn(-MAX_MATCH_DB, MAX_MATCH_DB) else 0f
        val fallback = Transition(
            fadeAtMs = (outDurationMs - fallbackWindowMs).coerceAtLeast(0), windowMs = fallbackWindowMs.coerceAtMost(outDurationMs),
            incomingStartMs = 0, tailSpeed = 1f, matchDb = matchDb, onBeat = false,
        )
        val outGrid = outro?.grid?.takeIf { it.confidence >= BeatTracker.MIN_CONFIDENCE } ?: return fallback
        val inGrid = intro?.grid?.takeIf { it.confidence >= BeatTracker.MIN_CONFIDENCE } ?: return fallback

        val stretch = listOf(1.0, 2.0, 0.5).map { inGrid.bpm * it / outGrid.bpm }.minBy { abs(it - 1) }
        val matched = abs(stretch - 1) <= MAX_STRETCH
        val longest = if (matched) MAX_WINDOW_MS else UNMATCHED_WINDOW_MS
        val bars = listOf(16, 8, 4, 2, 1).firstOrNull { (it * outGrid.barMs).toLong() in MIN_WINDOW_MS..longest }
            ?: listOf(16, 8, 4, 2, 1).first { (it * outGrid.barMs).toLong() <= longest }
        val windowMs = (bars * outGrid.barMs).toLong()
        val end = minOf(outro.lastSoundMs, outDurationMs)
        val fadeAtMs = outGrid.downbeatAtOrBefore(end - windowMs)
        if (fadeAtMs <= 0 || fadeAtMs + windowMs > outDurationMs) return fallback

        val firstSound = (intro.firstSoundMs - ONSET_SLACK_MS).coerceAtLeast(0)
        val start = inGrid.downbeatAtOrAfter(firstSound).let { if (it > MAX_INTRO_SKIP_MS) inGrid.downbeatAtOrAfter(0) else it }
        val incomingStartMs = if (start > MAX_INTRO_SKIP_MS) 0 else start
        return Transition(
            fadeAtMs, windowMs, incomingStartMs, if (matched) stretch.toFloat() else 1f, matchDb, onBeat = true,
            bars = bars, outgoingGrid = outGrid, incomingGrid = inGrid,
        )
    }

    /** A bar line found a few milliseconds before the first sound still starts the song there. */
    private const val ONSET_SLACK_MS = 30L
}

/** The two sides' volumes through a blend: equal power, with the louder side brought down by [matchDb] while both play. */
fun automixGains(progress: Float, matchDb: Float): Pair<Float, Float> {
    val p = progress.coerceIn(0f, 1f)
    val (out, incoming) = Crossfade.gains(p)
    return if (matchDb >= 0f) {
        // The outgoing song is louder: it comes down to the incoming level over the first half.
        val share = (p / 0.5f).coerceAtMost(1f)
        out * Loudness.dbToLinear(-matchDb * share * share * (3 - 2 * share)) to incoming
    } else {
        // The incoming song is louder: it comes in at the outgoing level and reaches its own by the end.
        out to incoming * Loudness.dbToLinear(matchDb * (1 - p))
    }
}

/**
 * The outgoing song's speed correction (a share, ±[MAX_SYNC_CORRECTION]) that pulls its beats onto the incoming
 * song's: the incoming beat phase ahead means the outgoing must hurry.
 */
fun beatSyncCorrection(incomingPhase: Double, outgoingPhase: Double): Double {
    var error = incomingPhase - outgoingPhase
    error -= floor(error + 0.5) // wrapped into -0.5..0.5 beats
    return (error * SYNC_GAIN).coerceIn(-MAX_SYNC_CORRECTION, MAX_SYNC_CORRECTION)
}

const val MAX_SYNC_CORRECTION = 0.03
private const val SYNC_GAIN = 0.2
