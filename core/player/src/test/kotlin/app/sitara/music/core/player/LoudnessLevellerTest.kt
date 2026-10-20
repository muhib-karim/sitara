package app.sitara.music.core.player

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessLevellerTest {
    private val rate = 48_000
    private val frames = 960 // 20 ms, stereo

    /** A stereo sine block whose RMS level is [rmsDb] dBFS, continuing from block [index]. */
    private fun tone(rmsDb: Float, index: Int): FloatArray {
        val amplitude = 10f.pow(rmsDb / 20f) * sqrt(2f)
        return FloatArray(frames * 2) { i ->
            val frame = index * frames + i / 2
            amplitude * sin(2 * PI * 440 * frame / rate).toFloat()
        }
    }

    private fun rmsDb(block: FloatArray) = 10 * log10(block.sumOf { it.toDouble() * it } / block.size).toFloat()

    /** Feeds [seconds] of a steady tone and returns the last block as played. */
    private fun LoudnessLeveller.play(rmsDb: Float, seconds: Int): FloatArray {
        var block = FloatArray(0)
        repeat(seconds * rate / frames) { i -> block = tone(rmsDb, i).also { process(it, it.size, 2, rate) } }
        return block
    }

    @Test
    fun `a loud song is brought down to the target`() {
        val out = LoudnessLeveller().play(rmsDb = -6f, seconds = 20)
        assertEquals(-14f, rmsDb(out), 0.5f)
    }

    @Test
    fun `a quiet song is lifted by at most 6 dB`() {
        val out = LoudnessLeveller().play(rmsDb = -30f, seconds = 30)
        assertEquals(-24f, rmsDb(out), 0.5f)
    }

    @Test
    fun `a song already at the target is left alone`() {
        val leveller = LoudnessLeveller()
        leveller.play(rmsDb = -14f, seconds = 10)
        assertEquals(0f, leveller.gainDb, 0.2f)
    }

    @Test
    fun `turned off, audio passes through untouched`() {
        val leveller = LoudnessLeveller().apply { enabled = false }
        val block = tone(-6f, 0)
        val original = block.copyOf()
        leveller.process(block, block.size, 2, rate)
        assertArrayEquals(original, block, 0f)
    }

    @Test
    fun `gain moves gradually, never in a jump`() {
        val leveller = LoudnessLeveller()
        leveller.play(rmsDb = -6f, seconds = 1)
        assertTrue("after one second ${leveller.gainDb} dB", leveller.gainDb > -4f && leveller.gainDb < 0f)
    }

    @Test
    fun `silence and quiet gaps never raise the gain`() {
        val leveller = LoudnessLeveller()
        repeat(30 * rate / frames) { leveller.process(FloatArray(frames * 2), frames * 2, 2, rate) }
        leveller.play(rmsDb = -70f, seconds = 10)
        assertEquals(0f, leveller.gainDb, 0f)
    }

    @Test
    fun `a boost never pushes peaks over -1 dBFS`() {
        val leveller = LoudnessLeveller()
        leveller.play(rmsDb = -30f, seconds = 30)
        // A sudden near-full-scale hit after a quiet passage.
        val hit = tone(-30f, 0).also { it[100] = 0.95f }
        leveller.process(hit, hit.size, 2, rate)
        val ceiling = 10f.pow(Loudness.PEAK_CEILING_DB / 20f)
        assertTrue(hit.all { abs(it) <= ceiling.coerceAtLeast(0.95f) + 1e-4f })
        assertTrue("the hit itself is not boosted", abs(hit[100]) <= 0.95f + 1e-4f)
    }

    @Test
    fun `turning it off returns smoothly to the original level`() {
        val leveller = LoudnessLeveller()
        leveller.play(rmsDb = -6f, seconds = 20)
        leveller.enabled = false
        leveller.play(rmsDb = -6f, seconds = 1)
        assertTrue("one second later ${leveller.gainDb} dB", leveller.gainDb < -1f)
        leveller.play(rmsDb = -6f, seconds = 10)
        assertEquals(0f, leveller.gainDb, 0f)
    }

    @Test
    fun `a player taking over a song carries on at the same level`() {
        val main = LoudnessLeveller().apply { play(rmsDb = -6f, seconds = 20) }
        val tail = LoudnessLeveller().apply { startFrom(main) }
        assertEquals(main.gainDb, tail.gainDb, 0f)
        tail.play(rmsDb = -6f, seconds = 1)
        assertEquals(main.gainDb, tail.gainDb, 0.1f)
    }
}
