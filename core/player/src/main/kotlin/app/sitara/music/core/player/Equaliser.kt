package app.sitara.music.core.player

import app.sitara.music.core.model.EqualiserSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh

/**
 * The built-in 10-band equaliser: one peaking biquad per band (RBJ cookbook, an octave wide) and a preamp. Gains glide at
 * [SLEW_DB_PER_S], the filters re-designed every [CHUNK_FRAMES] frames and the preamp ramped per sample, so moving a
 * slider never clicks. Headroom is the listener's (the presets carry a preamp that cancels their largest boost); a peak
 * limiter with instant attack keeps every sample under [CEILING], so a boost squashes a loud master rather than
 * clipping it. Flat settings, once the gains have glided back, pass audio through untouched. [process] runs on the
 * playback thread; [settings] may be set from any thread.
 */
class Equaliser {
    @Volatile var settings = EqualiserSettings()

    private var sampleRate = 0
    private var channels = 0
    private val gainDb = DoubleArray(BANDS)
    private var preampDb = 0.0

    // b0, b1, b2, a1, a2 per band, normalised by a0.
    private val coefficients = DoubleArray(BANDS * 5)

    // x[n-1], x[n-2], y[n-1], y[n-2] per band and channel.
    private var memory = DoubleArray(0)
    private var frame = DoubleArray(0)
    private var limiterGain = 1.0
    private var limiterRelease = 1.0

    /** Audio passes through unchanged. */
    val isUnity: Boolean get() = settings.isFlat && preampDb == 0.0 && limiterGain == 1.0 && gainDb.all { it == 0.0 }

    /** Forgets recent audio, for a seek or a new stream. */
    fun reset() {
        memory.fill(0.0)
        limiterGain = 1.0
    }

    /** Equalises the first [count] interleaved samples of [samples] in place. */
    fun process(samples: FloatArray, count: Int, channels: Int, sampleRate: Int) {
        if (sampleRate != this.sampleRate || channels != this.channels) configure(sampleRate, channels)
        if (count == 0 || isUnity) return
        val target = settings
        val step = SLEW_DB_PER_S * CHUNK_FRAMES / sampleRate
        val frames = count / channels
        var start = 0
        while (start < frames) {
            val end = min(frames, start + CHUNK_FRAMES)
            for (band in 0 until BANDS) {
                // A band at or past the highest frequency the stream carries cannot be designed; it stays flat.
                val wanted = if (target.enabled && EqualiserSettings.BAND_CENTRES_HZ[band] < MAX_CENTRE_OF_RATE * sampleRate) target.bandsDb[band].toDouble() else 0.0
                if (gainDb[band] == wanted) continue
                gainDb[band] = approach(gainDb[band], wanted, step)
                if (gainDb[band] == 0.0) memory.fill(0.0, band * channels * 4, (band + 1) * channels * 4) else design(band)
            }
            val preampFrom = dbToGain(preampDb)
            preampDb = approach(preampDb, if (target.enabled) target.preampDb.toDouble() else 0.0, step)
            val preampTo = dbToGain(preampDb)
            for (f in start until end) {
                val preamp = preampFrom + (preampTo - preampFrom) * (f - start + 1) / (end - start)
                var peak = 0.0
                for (c in 0 until channels) {
                    var x = samples[f * channels + c] * preamp
                    for (band in 0 until BANDS) if (gainDb[band] != 0.0) x = filter(band, c, x)
                    frame[c] = x
                    peak = max(peak, abs(x))
                }
                if (peak * limiterGain > CEILING) limiterGain = CEILING / peak
                for (c in 0 until channels) samples[f * channels + c] = (frame[c] * limiterGain).toFloat()
                limiterGain = min(1.0, limiterGain * limiterRelease)
            }
            start = end
        }
    }

    private fun configure(sampleRate: Int, channels: Int) {
        this.sampleRate = sampleRate
        this.channels = channels
        memory = DoubleArray(BANDS * channels * 4)
        frame = DoubleArray(channels)
        limiterGain = 1.0
        limiterRelease = 10.0.pow(LIMITER_RELEASE_DB_PER_S / 20 / sampleRate)
        for (band in 0 until BANDS) if (gainDb[band] != 0.0) design(band)
    }

    private fun design(band: Int) {
        val a = 10.0.pow(gainDb[band] / 40)
        val w0 = 2 * PI * EqualiserSettings.BAND_CENTRES_HZ[band] / sampleRate
        val alpha = sin(w0) / (2 * Q)
        val a0 = 1 + alpha / a
        val k = band * 5
        coefficients[k] = (1 + alpha * a) / a0
        coefficients[k + 1] = -2 * cos(w0) / a0
        coefficients[k + 2] = (1 - alpha * a) / a0
        coefficients[k + 3] = coefficients[k + 1]
        coefficients[k + 4] = (1 - alpha / a) / a0
    }

    // Direct form I: it stays well-behaved while the coefficients glide.
    private fun filter(band: Int, channel: Int, x: Double): Double {
        val m = (band * channels + channel) * 4
        val k = band * 5
        val y = coefficients[k] * x + coefficients[k + 1] * memory[m] + coefficients[k + 2] * memory[m + 1] -
            coefficients[k + 3] * memory[m + 2] - coefficients[k + 4] * memory[m + 3]
        memory[m + 1] = memory[m]
        memory[m] = x
        memory[m + 3] = memory[m + 2]
        memory[m + 2] = y
        return y
    }

    private fun approach(from: Double, to: Double, step: Double) = if (abs(to - from) <= step) to else from + step * (if (to > from) 1 else -1)

    private fun dbToGain(db: Double) = 10.0.pow(db / 20)

    private companion object {
        const val BANDS = EqualiserSettings.BAND_COUNT
        const val CHUNK_FRAMES = 32
        const val SLEW_DB_PER_S = 240.0
        const val LIMITER_RELEASE_DB_PER_S = 40.0
        const val CEILING = 0.99
        const val MAX_CENTRE_OF_RATE = 0.45

        // An octave between the -3 dB points of a full-height band.
        val Q = 1 / (2 * sinh(ln(2.0) / 2))
    }
}
