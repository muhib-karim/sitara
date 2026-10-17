package app.sitara.music.core.player

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

    /** How far into the fade window at the end of a track we are, 0 before it starts. */
    fun progress(positionMs: Long, durationMs: Long, windowMs: Long): Float {
        if (windowMs <= 0 || durationMs <= 0) return 0f
        val start = durationMs - windowMs
        return ((positionMs - start).toFloat() / windowMs).coerceIn(0f, 1f)
    }
}

/** ReplayGain-style loudness matching against an integrated LUFS target. */
object Loudness {
    const val MAX_BOOST_DB = 6f
    const val PEAK_CEILING_DB = -1f

    fun gainDb(measuredLufs: Float, targetLufs: Float = -14f, truePeakDb: Float? = null): Float {
        var gain = min(targetLufs - measuredLufs, MAX_BOOST_DB)
        if (truePeakDb != null && gain > 0f) {
            gain = min(gain, PEAK_CEILING_DB - truePeakDb).coerceAtLeast(0f)
        }
        return gain
    }

    fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()
}
