package app.sitara.music.core.player.automix

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The beat tracker against annotated fixtures. Measured tolerance: tempo within 2 %, beats within 35 ms with an
 * F-measure of at least 0.9, downbeats within 35 ms of a marked bar line.
 */
class BeatTrackerTest {
    private val tempos = listOf(85.0, 100.0, 120.0, 128.0, 140.0)

    private fun fMeasure(found: List<Long>, truth: List<Long>, toleranceMs: Long = 35): Double {
        val unmatched = truth.toMutableList()
        var hits = 0
        for (beat in found) {
            val match = unmatched.firstOrNull { abs(it - beat) <= toleranceMs } ?: continue
            unmatched.remove(match)
            hits++
        }
        if (hits == 0) return 0.0
        val precision = hits.toDouble() / found.size
        val recall = hits.toDouble() / truth.size
        return 2 * precision * recall / (precision + recall)
    }

    @Test
    fun `tempo is within two percent for every fixture, with no octave errors from 85 to 140`() {
        for (bpm in tempos) {
            val fixture = BeatFixture(bpm)
            val grid = BeatTracker.analyse(fixture.samples, fixture.sampleRate).grid
            assertNotNull("grid at $bpm", grid)
            assertEquals("bpm for $bpm", bpm, grid!!.bpm, bpm * 0.02)
        }
    }

    @Test
    fun `measured errors across the fixtures are printed for the report`() {
        val rows = tempos.map { bpm ->
            val fixture = BeatFixture(bpm, seed = 11)
            val analysis = BeatTracker.analyse(fixture.samples, fixture.sampleRate)
            val tempoError = abs(analysis.grid!!.bpm - bpm) / bpm * 100
            val offsets = analysis.beatsMs.map { beat -> fixture.beatsMs.minOf { abs(it - beat) } }
            Triple(tempoError, offsets.average(), offsets.max())
        }
        println("beat tracker: worst tempo error %.2f %%, mean beat offset %.1f ms, worst beat offset %d ms".format(rows.maxOf { it.first }, rows.map { it.second }.average(), rows.maxOf { it.third }))
        assertTrue(rows.all { it.first <= 2.0 })
    }

    @Test
    fun `a fast drum and bass tempo may be heard at half time but nowhere else`() {
        val fixture = BeatFixture(174.0)
        val bpm = BeatTracker.analyse(fixture.samples, fixture.sampleRate).grid!!.bpm
        assertTrue("bpm $bpm", abs(bpm - 174.0) <= 174 * 0.02 || abs(bpm - 87.0) <= 87 * 0.02)
    }

    @Test
    fun `beats land within 35 ms of the marked ones`() {
        for (bpm in tempos) {
            val fixture = BeatFixture(bpm)
            val beats = BeatTracker.analyse(fixture.samples, fixture.sampleRate).beatsMs
            val f = fMeasure(beats, fixture.beatsMs)
            assertTrue("F-measure $f at $bpm", f >= 0.9)
        }
    }

    @Test
    fun `swing on the off-beats does not move the beat`() {
        val fixture = BeatFixture(100.0, swing = 0.33, seed = 3)
        val analysis = BeatTracker.analyse(fixture.samples, fixture.sampleRate)
        assertEquals(100.0, analysis.grid!!.bpm, 2.0)
        assertTrue(fMeasure(analysis.beatsMs, fixture.beatsMs) >= 0.9)
    }

    @Test
    fun `the downbeat falls on a marked bar line`() {
        for (bpm in tempos) {
            val fixture = BeatFixture(bpm, seed = bpm.toInt())
            val grid = BeatTracker.analyse(fixture.samples, fixture.sampleRate).grid!!
            val barMs = 4 * 60_000.0 / bpm
            val off = fixture.downbeatsMs.minOf { mark ->
                val cycles = (grid.downbeatMs - mark) / barMs
                abs(cycles - Math.round(cycles)) * barMs
            }
            assertTrue("downbeat ${grid.downbeatMs} is $off ms off a bar line at $bpm", off <= 35)
        }
    }

    @Test
    fun `slow pads without attacks have no confident beat`() {
        val grid = BeatTracker.analyse(padFixture(), 11_025).grid
        assertTrue("grid $grid", grid == null || grid.confidence < BeatTracker.MIN_CONFIDENCE)
    }

    @Test
    fun `drums have a confident beat`() {
        val fixture = BeatFixture(120.0)
        assertTrue(BeatTracker.analyse(fixture.samples, fixture.sampleRate).grid!!.confidence >= BeatTracker.MIN_CONFIDENCE)
    }

    @Test
    fun `a silent intro is found and no beat is placed in it`() {
        val fixture = BeatFixture(120.0, introSeconds = 3.0, noise = 0.004f)
        val analysis = BeatTracker.analyse(fixture.samples, fixture.sampleRate)
        assertEquals(3_000.0, analysis.firstSoundMs.toDouble(), 60.0)
        assertTrue(analysis.beatsMs.first() >= 2_965)
        assertTrue(analysis.grid!!.downbeatMs >= 2_965)
    }

    @Test
    fun `times are song time when the region starts later in the song`() {
        val fixture = BeatFixture(128.0)
        val analysis = BeatTracker.analyse(fixture.samples, fixture.sampleRate, startMs = 200_000)
        assertTrue(fMeasure(analysis.beatsMs, fixture.beatsMs.map { it + 200_000 }) >= 0.9)
        assertTrue(analysis.grid!!.downbeatMs >= 200_000)
        assertTrue(analysis.lastSoundMs in 229_000..230_000)
    }

    @Test
    fun `loudness is the region's level in dBFS, and quieter audio reads quieter`() {
        val fixture = BeatFixture(120.0, noise = 0f)
        val loud = BeatTracker.analyse(fixture.samples, fixture.sampleRate).loudnessDb
        val quiet = BeatTracker.analyse(FloatArray(fixture.samples.size) { fixture.samples[it] * 0.5f }, fixture.sampleRate).loudnessDb
        assertEquals(6.02, (loud - quiet).toDouble(), 0.2)
        assertTrue(loud < 0f)
    }

    @Test
    fun `silence gives no grid and no sound`() {
        val analysis = BeatTracker.analyse(FloatArray(11_025 * 10), 11_025)
        assertEquals(null, analysis.grid)
        assertTrue(analysis.beatsMs.isEmpty())
    }

    @Test
    fun `audio at 48 kHz brought down for analysis keeps its tempo`() {
        val fixture = BeatFixture(124.0, sampleRate = 48_000, seconds = 20.0)
        val (mono, rate) = downsampleForAnalysis(fixture.samples, 48_000)
        assertEquals(12_000, rate)
        assertEquals(124.0, BeatTracker.analyse(mono, rate).grid!!.bpm, 124 * 0.02)
    }
}
