package app.sitara.music.core.player.automix

import app.sitara.music.core.model.BeatGrid
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What one stretch of a song says about its beat and level. Times are song time in milliseconds. */
data class RegionAnalysis(
    /** The steady beat, null when there is none to speak of. */
    val grid: BeatGrid?,
    /** The beats as tracked, before they are fitted to a grid. */
    val beatsMs: List<Long>,
    /** The level with quiet passages gated out, in dBFS. */
    val loudnessDb: Float,
    val firstSoundMs: Long,
    val lastSoundMs: Long,
)

/**
 * Tempo, beats and bar lines of a stretch of decoded audio (mono, about 11 kHz; see [downsampleForAnalysis]).
 *
 * 1. Onset strength: spectral flux of log-compressed 512-point spectra every 128 samples (about 86 frames a second),
 *    less its local mean, half-wave rectified.
 * 2. Tempo: the autocorrelation of the onset strength between 60 and 190 BPM, weighted toward 120 BPM by a log-normal
 *    prior, refined between frames by a parabola.
 * 3. Beats: dynamic programming over the onset strength with a penalty for straying from the tempo (after D. Ellis,
 *    "Beat tracking by dynamic programming", 2007), then a least-squares grid through them.
 * 4. Bar lines: of the four ways to group beats into bars, the one whose first beats carry the most low-frequency onset
 *    energy (kick drums and bass notes mark the one).
 * 5. Confidence: how periodic the onsets are at the beat (autocorrelation at the beat over that at zero lag), scaled
 *    down when the onsets are faint (drums spread the onset strength 40 to 150 units, sustained pads about 1).
 */
object BeatTracker {
    /** Below this a grid is not trusted to line songs up. */
    const val MIN_CONFIDENCE = 0.35f

    private const val FFT = 512
    private const val HOP = 128
    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 190.0
    private const val PRIOR_BPM = 120.0
    private const val PRIOR_OCTAVES = 1.0
    private const val TIGHTNESS = 100.0
    private const val LOW_HZ = 200.0
    private const val SILENCE_DB = -55.0
    private const val SOUND_BELOW_PEAK_DB = 35.0
    private const val STRONG_ONSETS = 20.0

    fun analyse(samples: FloatArray, sampleRate: Int, startMs: Long = 0): RegionAnalysis {
        val frames = (samples.size - FFT) / HOP + 1
        val nothing = RegionAnalysis(null, emptyList(), SILENCE_DB.toFloat(), startMs, startMs)
        if (frames < 8) return nothing
        val fps = sampleRate.toDouble() / HOP
        val frameMs = { frame: Double -> startMs + ((frame * HOP + FFT / 2) * 1_000.0 / sampleRate).roundToInt() }

        val spectra = Spectra(samples, frames, sampleRate)
        val rmsDb = spectra.rmsDb
        val peakDb = rmsDb.max()
        if (peakDb < SILENCE_DB) return nothing
        val soundFloor = max(SILENCE_DB, peakDb - SOUND_BELOW_PEAK_DB)
        val first = rmsDb.indexOfFirst { it >= soundFloor }
        val last = rmsDb.indexOfLast { it >= soundFloor }
        val loudness = gatedLoudness(rmsDb)
        val firstSoundMs = frameMs(first.toDouble()) - FFT / 2 * 1_000L / sampleRate
        val lastSoundMs = frameMs(last.toDouble()) + FFT / 2 * 1_000L / sampleRate

        val onset = detrend(spectra.flux, (fps * 0.5).roundToInt())
        // Nothing before the first sound counts, so no beat lands in a silent intro.
        for (i in 0 until first) onset[i] = 0f
        val sd = sqrt(onset.map { it.toDouble() * it }.average())
        if (sd < 1e-9) return RegionAnalysis(null, emptyList(), loudness, firstSoundMs, lastSoundMs)
        for (i in onset.indices) onset[i] = (onset[i] / sd).toFloat()

        val (period, periodicity) = tempoPeriod(onset, fps)
        val beats = trackBeats(onset, period, from = first)
        if (beats.size < 4) return RegionAnalysis(null, beats.map { frameMs(it.toDouble()) }, loudness, firstSoundMs, lastSoundMs)

        // A least-squares line through the beats: beat i at a + i * p frames.
        val n = beats.size
        val meanI = (n - 1) / 2.0
        val meanB = beats.average()
        var sxy = 0.0
        var sxx = 0.0
        beats.forEachIndexed { i, b ->
            sxy += (i - meanI) * (b - meanB)
            sxx += (i - meanI) * (i - meanI)
        }
        val p = sxy / sxx
        val a = meanB - p * meanI
        val bpm = 60.0 * fps / p

        // Bar lines: the phase of four whose beats carry the most low-frequency onset energy.
        val low = detrend(spectra.lowFlux, (fps * 0.5).roundToInt())
        val phase = (0 until 4).maxBy { k ->
            beats.indices.filter { it % 4 == k }.sumOf { i -> peakAround(low, beats[i], 2).toDouble() }
        }
        val downbeatMs = frameMs(a + phase * p)
        // Steady but faint onsets (the ripple of sustained chords) are not a beat to mix on.
        val strength = (sd / STRONG_ONSETS).coerceIn(0.0, 1.0)
        val confidence = (periodicity * strength).toFloat().coerceIn(0f, 1f)
        return RegionAnalysis(BeatGrid(bpm, downbeatMs, confidence), beats.map { frameMs(it.toDouble()) }, loudness, firstSoundMs, lastSoundMs)
    }

    /** The beat period in frames and how periodic the onsets are at it (0 to 1). */
    private fun tempoPeriod(onset: FloatArray, fps: Double): Pair<Double, Double> {
        val minLag = (fps * 60 / MAX_BPM).toInt()
        val maxLag = min(onset.size / 2, (fps * 60 / MIN_BPM).toInt() + 1)
        val ac = DoubleArray(maxLag + 2)
        for (lag in 0..maxLag + 1) {
            var sum = 0.0
            for (t in 0 until onset.size - lag) sum += onset[t] * onset[t + lag]
            ac[lag] = sum / (onset.size - lag)
        }
        var best = minLag
        var bestScore = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            val bpm = 60 * fps / lag
            val weight = exp(-0.5 * (log2(bpm / PRIOR_BPM) / PRIOR_OCTAVES).let { it * it })
            val score = ac[lag] * weight
            if (score > bestScore && ac[lag] >= ac[lag - 1] && ac[lag] >= ac[lag + 1]) {
                bestScore = score
                best = lag
            }
        }
        val (y0, y1, y2) = Triple(ac[best - 1], ac[best], ac[best + 1])
        val denominator = y0 - 2 * y1 + y2
        val shift = if (denominator != 0.0) (0.5 * (y0 - y2) / denominator).coerceIn(-0.5, 0.5) else 0.0
        return (best + shift) to (y1 / ac[0])
    }

    /** Beat frames by dynamic programming: each beat's score is its onset plus the best earlier beat's, less a tempo penalty. */
    private fun trackBeats(onset: FloatArray, period: Double, from: Int): List<Int> {
        val n = onset.size
        val score = DoubleArray(n)
        val back = IntArray(n) { -1 }
        val earliest = (2 * period).roundToInt()
        val latest = max(1, (period / 2).roundToInt())
        for (t in from until n) {
            var bestPrev = -1
            var bestValue = 0.0
            for (tau in max(from, t - earliest)..t - latest) {
                val gap = (t - tau) / period
                val value = score[tau] - TIGHTNESS * ln(gap).let { it * it }
                if (bestPrev < 0 || value > bestValue) {
                    bestValue = value
                    bestPrev = tau
                }
            }
            score[t] = onset[t] + if (bestPrev >= 0 && bestValue > 0) bestValue else 0.0
            back[t] = if (bestPrev >= 0 && bestValue > 0) bestPrev else -1
        }
        // End on the best-scoring frame within the last beat period.
        val tailStart = max(from, n - period.roundToInt())
        var t = (tailStart until n).maxByOrNull { score[it] } ?: return emptyList()
        val beats = mutableListOf<Int>()
        while (t >= 0) {
            beats += t
            t = back[t]
        }
        return beats.reversed()
    }

    /** Mean energy in dBFS of the frames no more than 20 dB below the plain mean: quiet passages do not drag it down. */
    private fun gatedLoudness(rmsDb: FloatArray): Float {
        fun meanDb(values: List<Float>): Double = 10 * log10(values.map { 10.0.pow(it / 10.0) }.average().coerceAtLeast(1e-12))
        val plain = meanDb(rmsDb.toList())
        return meanDb(rmsDb.filter { it >= plain - 20 }.ifEmpty { rmsDb.toList() }).toFloat()
    }

    private fun Double.pow(x: Double) = Math.pow(this, x)

    private fun detrend(values: FloatArray, radius: Int): FloatArray {
        val prefix = DoubleArray(values.size + 1)
        for (i in values.indices) prefix[i + 1] = prefix[i] + values[i]
        return FloatArray(values.size) { i ->
            val lo = max(0, i - radius)
            val hi = min(values.size, i + radius + 1)
            max(0.0, values[i] - (prefix[hi] - prefix[lo]) / (hi - lo)).toFloat()
        }
    }

    private fun peakAround(values: FloatArray, at: Int, radius: Int): Float {
        var peak = 0f
        for (i in max(0, at - radius)..min(values.size - 1, at + radius)) peak = max(peak, values[i])
        return peak
    }

    /** Spectral flux (all bins and the low bins) and the level of every frame. */
    private class Spectra(samples: FloatArray, frames: Int, sampleRate: Int) {
        val flux = FloatArray(frames)
        val lowFlux = FloatArray(frames)
        val rmsDb = FloatArray(frames)

        init {
            val window = FloatArray(FFT) { (0.5 - 0.5 * cos(2 * PI * it / FFT)).toFloat() }
            val lowBins = max(2, (LOW_HZ * FFT / sampleRate).toInt())
            val re = FloatArray(FFT)
            val im = FloatArray(FFT)
            var previous = FloatArray(FFT / 2 + 1)
            var current = FloatArray(FFT / 2 + 1)
            for (f in 0 until frames) {
                val offset = f * HOP
                var energy = 0.0
                for (i in 0 until FFT) {
                    val s = samples[offset + i]
                    energy += s * s
                    re[i] = s * window[i]
                    im[i] = 0f
                }
                rmsDb[f] = (10 * log10((energy / FFT).coerceAtLeast(1e-12))).toFloat()
                fft(re, im)
                var all = 0f
                var lows = 0f
                for (k in 0..FFT / 2) {
                    current[k] = ln(1f + 100f * sqrt(re[k] * re[k] + im[k] * im[k]))
                    val rise = current[k] - previous[k]
                    if (rise > 0 && f > 0) {
                        all += rise
                        if (k in 1..lowBins) lows += rise
                    }
                }
                flux[f] = all
                lowFlux[f] = lows
                val swap = previous
                previous = current
                current = swap
            }
        }
    }
}

/** In-place radix-2 FFT; [re] and [im] have a power-of-two length. */
internal fun fft(re: FloatArray, im: FloatArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit shr 1
        }
        j = j or bit
        if (i < j) {
            re[i] = re[j].also { re[j] = re[i] }
            im[i] = im[j].also { im[j] = im[i] }
        }
    }
    var length = 2
    while (length <= n) {
        val angle = -2 * PI / length
        val wRe = cos(angle).toFloat()
        val wIm = kotlin.math.sin(angle).toFloat()
        var start = 0
        while (start < n) {
            var curRe = 1f
            var curIm = 0f
            for (k in 0 until length / 2) {
                val a = start + k
                val b = a + length / 2
                val tRe = re[b] * curRe - im[b] * curIm
                val tIm = re[b] * curIm + im[b] * curRe
                re[b] = re[a] - tRe
                im[b] = im[a] - tIm
                re[a] += tRe
                im[a] += tIm
                val nextRe = curRe * wRe - curIm * wIm
                curIm = curRe * wIm + curIm * wRe
                curRe = nextRe
            }
            start += length
        }
        length = length shl 1
    }
}

/**
 * Mono audio brought down to about 11 kHz for [BeatTracker] by averaging each run of samples (a box low-pass); the
 * rate it ends at is returned with it.
 */
fun downsampleForAnalysis(mono: FloatArray, sampleRate: Int): Pair<FloatArray, Int> {
    val factor = max(1, sampleRate / ANALYSIS_RATE)
    if (factor == 1) return mono to sampleRate
    val out = FloatArray(mono.size / factor)
    for (i in out.indices) {
        var sum = 0f
        for (k in 0 until factor) sum += mono[i * factor + k]
        out[i] = sum / factor
    }
    return out to sampleRate / factor
}

private const val ANALYSIS_RATE = 11_025
