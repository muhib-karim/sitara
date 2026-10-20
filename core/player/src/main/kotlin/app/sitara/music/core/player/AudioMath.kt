package app.sitara.music.core.player

import app.sitara.music.core.model.Track
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

enum class CrossfadeCurve { LINEAR, EQUAL_POWER }

object Crossfade {
    /** (outgoing gain, incoming gain) for a fade [progress] in 0..1. */
    fun gains(progress: Float, curve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER): Pair<Float, Float> {
        val p = progress.coerceIn(0f, 1f)
        return when (curve) {
            CrossfadeCurve.LINEAR -> (1f - p) to p
            CrossfadeCurve.EQUAL_POWER -> cos(p * PI / 2).toFloat() to sin(p * PI / 2).toFloat()
        }
    }

    /**
     * How far into the fade window at the end of a track we are, 0 before it starts.
     * A window longer than the track shrinks to the track, so short tracks fade from their start.
     */
    fun progress(positionMs: Long, durationMs: Long, windowMs: Long): Float {
        if (windowMs <= 0 || durationMs <= 0) return 0f
        val window = min(windowMs, durationMs)
        val start = durationMs - window
        return ((positionMs - start).toFloat() / window).coerceIn(0f, 1f)
    }

    /** How long before the fade the outgoing song's tail is loaded, so its stream is ready in time. */
    const val PREPARE_LEAD_MS = 10_000L

    /**
     * Whether [current] fades into [next] over [windowMs]. Songs from one album play gapless instead, as do a song on
     * repeat, the song the sleep timer stops after, and songs shorter than two fades.
     */
    fun shouldOverlap(windowMs: Long, current: Track, next: Track?, durationMs: Long, repeatOne: Boolean, pauseAtEnd: Boolean): Boolean =
        windowMs > 0 && next != null && !repeatOne && !pauseAtEnd && durationMs >= 2 * windowMs &&
            !(current.album.isNotBlank() && current.album == next.album)

    /** Where the outgoing song's tail is loaded. */
    fun prepareAtMs(durationMs: Long, windowMs: Long): Long = (durationMs - windowMs - PREPARE_LEAD_MS).coerceAtLeast(0)
}

/** ReplayGain-style loudness matching against an integrated LUFS target. */
object Loudness {
    const val MAX_BOOST_DB = 6f
    const val PEAK_CEILING_DB = -1f

    /** Gain toward [targetLufs], capped at [MAX_BOOST_DB] and always low enough to keep [truePeakDb] under the ceiling. */
    fun gainDb(measuredLufs: Float, targetLufs: Float = -14f, truePeakDb: Float? = null): Float {
        val gain = min(targetLufs - measuredLufs, MAX_BOOST_DB)
        return if (truePeakDb == null) gain else min(gain, PEAK_CEILING_DB - truePeakDb)
    }

    fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()
}
