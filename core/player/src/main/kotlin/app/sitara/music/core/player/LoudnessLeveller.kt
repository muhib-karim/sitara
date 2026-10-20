package app.sitara.music.core.player

import app.sitara.music.core.model.SitaraSettings
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * Evens out loudness from song to song. The source exposes no loudness values (NewPipeExtractor does not pass on
 * YouTube's), so this is a slow automatic gain toward [targetLufs]: the level is the gated RMS of the last few seconds
 * (unweighted, so it approximates LUFS), the boost is capped at [Loudness.MAX_BOOST_DB], and a boost never lifts a
 * peak over [Loudness.PEAK_CEILING_DB]. Gain moves at most 3 dB/s down and 1 dB/s up, ramped across each block, so it
 * neither pumps nor clicks. [process] runs on the playback thread; [enabled] and [targetLufs] may be set from any thread.
 */
class LoudnessLeveller {
    @Volatile var enabled = true

    @Volatile var targetLufs = SitaraSettings.DEFAULT_TARGET_LUFS

    var gainDb = 0f
        private set

    // Mean square of recent blocks above the gate; negative until the first one.
    private var meanSquare = -1.0

    /** Audio passes through unchanged. */
    val isUnity: Boolean get() = !enabled && gainDb == 0f

    /** Carries on from where [other] is, for a second player that takes over the song [other] was playing. Call while this one is idle. */
    fun startFrom(other: LoudnessLeveller) {
        gainDb = other.gainDb
        meanSquare = other.meanSquare
    }

    /** Levels the first [count] interleaved samples of [samples] in place. */
    fun process(samples: FloatArray, count: Int, channels: Int, sampleRate: Int) {
        if (count == 0 || isUnity) return
        val seconds = count.toDouble() / channels / sampleRate
        var sum = 0.0
        var peak = 0f
        for (i in 0 until count) {
            val s = samples[i]
            sum += s * s
            peak = max(peak, abs(s))
        }
        val blockMeanSquare = sum / count
        // Silence and near-silence say nothing about how loud a song is.
        if (blockMeanSquare > GATE_MEAN_SQUARE) {
            meanSquare = if (meanSquare < 0) blockMeanSquare else meanSquare + (blockMeanSquare - meanSquare) * (1 - exp(-seconds / TIME_CONSTANT_S))
        }
        val wanted = if (!enabled || meanSquare < 0) 0f else Loudness.gainDb((10 * log10(meanSquare)).toFloat(), targetLufs).coerceAtLeast(-MAX_CUT_DB)
        val step = (wanted - gainDb).coerceIn((-DOWN_DB_PER_S * seconds).toFloat(), (UP_DB_PER_S * seconds).toFloat())
        // A boost may not push this block's peak over the ceiling; a cut never makes a peak louder, so 0 dB is always safe.
        val ceiling = if (peak > 0f) max(0f, Loudness.PEAK_CEILING_DB - 20 * log10(peak)) else Float.MAX_VALUE
        val next = min(gainDb + step, ceiling)
        val from = Loudness.dbToLinear(min(gainDb, ceiling))
        val to = Loudness.dbToLinear(next)
        val frames = count / channels
        for (frame in 0 until frames) {
            val gain = from + (to - from) * (frame + 1) / frames
            for (c in 0 until channels) samples[frame * channels + c] *= gain
        }
        gainDb = next
    }

    private companion object {
        const val GATE_MEAN_SQUARE = 1e-5 // -50 dBFS
        const val TIME_CONSTANT_S = 3.0
        const val DOWN_DB_PER_S = 3.0
        const val UP_DB_PER_S = 1.0
        const val MAX_CUT_DB = 15f
    }
}
