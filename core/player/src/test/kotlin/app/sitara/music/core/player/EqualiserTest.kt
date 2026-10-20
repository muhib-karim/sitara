package app.sitara.music.core.player

import app.sitara.music.core.model.EqualiserSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualiserTest {
    private val block = 1_024 // frames per call, as the audio sink hands them over

    /** [seconds] of a sine at [hz] in every channel, interleaved. */
    private fun sine(hz: Double, amplitude: Double, rate: Int, channels: Int, seconds: Double): FloatArray {
        val frames = (seconds * rate).toInt()
        return FloatArray(frames * channels) { i -> (amplitude * sin(2 * PI * hz * (i / channels) / rate)).toFloat() }
    }

    /** Runs [samples] through in blocks, in place; [atFrame] may change settings before the block starting at that frame. */
    private fun Equaliser.run(samples: FloatArray, rate: Int, channels: Int, atFrame: (Int) -> Unit = {}): FloatArray {
        var frame = 0
        while (frame * channels < samples.size) {
            atFrame(frame)
            val count = minOf(block * channels, samples.size - frame * channels)
            val chunk = samples.copyOfRange(frame * channels, frame * channels + count)
            process(chunk, count, channels, rate)
            chunk.copyInto(samples, frame * channels)
            frame += block
        }
        return samples
    }

    private fun rms(samples: FloatArray, from: Int) = samples.drop(from).let { tail -> kotlin.math.sqrt(tail.sumOf { it.toDouble() * it } / tail.size) }

    private fun maxStep(samples: FloatArray, channels: Int, fromFrame: Int, toFrame: Int): Double {
        var step = 0.0
        for (frame in fromFrame + 1 until toFrame) {
            for (c in 0 until channels) {
                step = max(step, abs(samples[frame * channels + c] - samples[(frame - 1) * channels + c]).toDouble())
            }
        }
        return step
    }

    private fun bands(index: Int, db: Int) = List(EqualiserSettings.BAND_COUNT) { if (it == index) db else 0 }

    @Test
    fun `flat settings pass audio through bit for bit`() {
        val noise = Random(7).let { r -> FloatArray(48_000 * 2) { r.nextFloat() * 2 - 1 } }
        listOf(EqualiserSettings(), EqualiserSettings(enabled = true), EqualiserSettings(enabled = false, bandsDb = bands(3, 9), preampDb = 4)).forEach { flat ->
            val out = Equaliser().apply { settings = flat }.run(noise.copyOf(), 48_000, 2)
            assertArrayEquals("$flat", noise, out, 0f)
        }
    }

    @Test
    fun `a single band measures its gain at its centre, mono and stereo, 44_1 and 48 kHz`() {
        for (rate in listOf(44_100, 48_000)) {
            for (channels in listOf(1, 2)) {
                for (band in 0 until EqualiserSettings.BAND_COUNT) {
                    for (db in listOf(12, -12, 5)) {
                        val hz = EqualiserSettings.BAND_CENTRES_HZ[band]
                        val input = sine(hz, 0.05, rate, channels, seconds = 1.0)
                        val equaliser = Equaliser().apply { settings = EqualiserSettings(enabled = true, bandsDb = bands(band, db)) }
                        val out = equaliser.run(input.copyOf(), rate, channels)
                        val settled = input.size / 2
                        val measured = 20 * log10(rms(out, settled) / rms(input, settled))
                        assertEquals("$hz Hz at $rate Hz, $channels ch, $db dB", db.toDouble(), measured, 0.5)
                    }
                }
            }
        }
    }

    @Test
    fun `the preamp shifts every frequency by its gain`() {
        val input = sine(3_000.0, 0.05, 48_000, 2, seconds = 0.5)
        val out = Equaliser().apply { settings = EqualiserSettings(enabled = true, preampDb = -6) }.run(input.copyOf(), 48_000, 2)
        val settled = input.size / 2
        assertEquals(-6.0, 20 * log10(rms(out, settled) / rms(input, settled)), 0.05)
    }

    @Test
    fun `stereo channels are filtered separately`() {
        val rate = 48_000
        val mono = sine(62.5, 0.5, rate, 1, seconds = 0.5)
        val leftOnly = FloatArray(mono.size * 2) { i -> if (i % 2 == 0) mono[i / 2] else 0f }
        val out = Equaliser().apply { settings = EqualiserSettings(enabled = true, bandsDb = bands(1, 12), preampDb = -12) }.run(leftOnly, rate, 2)
        assertTrue(out.filterIndexed { i, _ -> i % 2 == 1 }.all { it == 0f })
    }

    @Test
    fun `boosts and preamp at full never take a full-scale sweep or noise past the ceiling`() {
        val loudest = EqualiserSettings(enabled = true, bandsDb = List(EqualiserSettings.BAND_COUNT) { 12 }, preampDb = 12)
        for (rate in listOf(44_100, 48_000)) {
            for (channels in listOf(1, 2)) {
                val steps = 40
                val sweep = (0 until steps).map { step -> sine(20.0 * 1_000.0.pow(step / (steps - 1.0)), 0.99, rate, channels, seconds = 0.05) }
                val noise = Random(3).let { r -> FloatArray(rate * channels / 2) { r.nextFloat() * 2 - 1 } }
                val equaliser = Equaliser().apply { settings = loudest }
                val out = equaliser.run((sweep + listOf(noise)).reduce(FloatArray::plus), rate, channels)
                val peak = out.maxOf { abs(it) }
                assertTrue("peak $peak at $rate Hz, $channels ch", peak <= 1f)
            }
        }
    }

    @Test
    fun `moving a band while playing does not click`() {
        val rate = 48_000
        val channels = 2
        val input = sine(1_000.0, 0.1, rate, channels, seconds = 1.5)
        val change = rate / 2
        val boosted = EqualiserSettings(enabled = true, bandsDb = bands(5, 12))
        val equaliser = Equaliser()
        val out = equaliser.run(input, rate, channels) { frame -> if (frame >= change) equaliser.settings = boosted }
        val steady = maxStep(out, channels, out.size / channels - rate / 5, out.size / channels)
        val during = maxStep(out, channels, change - block, change + rate / 2)
        assertTrue("step $during against $steady once settled", during <= steady * 1.25)
    }

    @Test
    fun `moving the preamp while playing does not click`() {
        val rate = 44_100
        val channels = 1
        val input = sine(440.0, 0.05, rate, channels, seconds = 1.5)
        val change = rate / 2
        val equaliser = Equaliser()
        val out = equaliser.run(input, rate, channels) { frame -> if (frame >= change) equaliser.settings = EqualiserSettings(enabled = true, preampDb = 12) }
        val steady = maxStep(out, channels, out.size - rate / 5, out.size)
        val during = maxStep(out, channels, change - block, change + rate / 2)
        assertTrue("step $during against $steady once settled", during <= steady * 1.25)
    }

    @Test
    fun `turning it off glides back, then passes audio through untouched`() {
        val rate = 48_000
        val channels = 2
        val input = sine(125.0, 0.1, rate, channels, seconds = 2.0)
        val off = rate / 2
        val equaliser = Equaliser().apply { settings = EqualiserSettings(enabled = true, bandsDb = bands(2, 12), preampDb = -3) }
        val out = equaliser.run(input.copyOf(), rate, channels) { frame -> if (frame >= off) equaliser.settings = EqualiserSettings() }
        val loud = maxStep(out, channels, off - rate / 5, off)
        assertTrue(maxStep(out, channels, off - block, off + rate / 4) <= loud * 1.25)
        val tail = out.size - rate * channels / 2
        assertArrayEquals(input.copyOfRange(tail, input.size), out.copyOfRange(tail, out.size), 0f)
    }

    @Test
    fun `bands above the stream's highest frequency are left out`() {
        val input = sine(1_000.0, 0.1, 22_050, 1, seconds = 0.5)
        val out = Equaliser().apply { settings = EqualiserSettings(enabled = true, bandsDb = bands(9, 12)) }.run(input.copyOf(), 22_050, 1)
        assertTrue(out.all { it.isFinite() })
        val settled = input.size / 2
        assertEquals(0.0, 20 * log10(rms(out, settled) / rms(input, settled)), 0.5)
    }
}
