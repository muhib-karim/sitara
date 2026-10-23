package app.sitara.music.core.player.automix

import app.sitara.music.core.model.BeatGrid
import app.sitara.music.core.model.RegionBeats
import app.sitara.music.core.model.SongBeats
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomixPlannerTest {
    private val duration = 240_000L

    private fun outgoing(bpm: Double = 124.0, downbeat: Long = 210_321, level: Float = -10f, lastSound: Long = 239_000, confidence: Float = 0.8f) =
        SongBeats("out", outro = RegionBeats(BeatGrid(bpm, downbeat, confidence), level, 210_000, lastSound))

    private fun incoming(bpm: Double = 126.0, downbeat: Long = 1_480, level: Float = -12f, firstSound: Long = 1_450, confidence: Float = 0.8f) =
        SongBeats("in", intro = RegionBeats(BeatGrid(bpm, downbeat, confidence), level, firstSound, 30_000))

    private fun onGrid(ms: Long, grid: BeatGrid): Boolean {
        val bars = (ms - grid.downbeatMs) / grid.barMs
        return abs(bars - Math.round(bars)) * grid.barMs < 2
    }

    @Test
    fun `the blend starts on an outgoing bar line`() {
        val plan = AutomixPlanner.plan(outgoing(), duration, incoming(), 6_000)
        assertTrue(plan.onBeat)
        assertTrue(onGrid(plan.fadeAtMs, outgoing().outro!!.grid!!))
    }

    @Test
    fun `the blend lasts whole bars and ends before the outgoing song falls silent`() {
        val plan = AutomixPlanner.plan(outgoing(), duration, incoming(), 6_000)
        val bar = outgoing().outro!!.grid!!.barMs
        assertEquals(8, plan.bars) // 16 bars at 124 BPM is 31 s, too long; 8 bars is 15.5 s
        assertEquals((8 * bar).toLong(), plan.windowMs)
        assertTrue(plan.fadeAtMs + plan.windowMs <= 239_000)
        assertTrue("ends within a bar of the silence", plan.fadeAtMs + plan.windowMs > 239_000 - bar)
    }

    @Test
    fun `slow songs blend over fewer bars`() {
        val plan = AutomixPlanner.plan(outgoing(bpm = 80.0), duration, incoming(bpm = 82.0), 6_000)
        assertEquals(4, plan.bars) // 8 bars at 80 BPM is 24 s
    }

    @Test
    fun `the incoming song starts on its first bar line`() {
        val plan = AutomixPlanner.plan(outgoing(), duration, incoming(downbeat = 1_480, firstSound = 1_450), 6_000)
        assertEquals(1_480, plan.incomingStartMs)
    }

    @Test
    fun `a long quiet intro is skipped no further than eight seconds`() {
        val plan = AutomixPlanner.plan(outgoing(), duration, incoming(downbeat = 1_000, firstSound = 12_000), 6_000)
        assertTrue(plan.incomingStartMs <= AutomixPlanner.MAX_INTRO_SKIP_MS)
        assertTrue(onGrid(plan.incomingStartMs, incoming(downbeat = 1_000).intro!!.grid!!))
    }

    @Test
    fun `tempos within eight percent are matched by stretching the outgoing song`() {
        val plan = AutomixPlanner.plan(outgoing(bpm = 124.0), duration, incoming(bpm = 128.0), 6_000)
        assertEquals(128f / 124f, plan.tailSpeed, 1e-4f)
    }

    @Test
    fun `half time counts as the same tempo`() {
        val plan = AutomixPlanner.plan(outgoing(bpm = 170.0), duration, incoming(bpm = 87.0), 6_000)
        assertEquals(174f / 170f, plan.tailSpeed, 1e-4f)
    }

    @Test
    fun `far apart tempos are not stretched and blend briefly`() {
        val plan = AutomixPlanner.plan(outgoing(bpm = 100.0), duration, incoming(bpm = 128.0), 6_000)
        assertEquals(1f, plan.tailSpeed)
        assertTrue(plan.windowMs <= AutomixPlanner.UNMATCHED_WINDOW_MS)
        assertTrue(plan.onBeat)
    }

    @Test
    fun `the louder side is matched, capped at six dB`() {
        assertEquals(2f, AutomixPlanner.plan(outgoing(level = -10f), duration, incoming(level = -12f), 6_000).matchDb)
        assertEquals(6f, AutomixPlanner.plan(outgoing(level = -4f), duration, incoming(level = -20f), 6_000).matchDb)
        assertEquals(-6f, AutomixPlanner.plan(outgoing(level = -30f), duration, incoming(level = -8f), 6_000).matchDb)
    }

    @Test
    fun `without confident beats on both sides it is a plain crossfade at the end`() {
        for ((out, inc) in listOf(null to incoming(), outgoing() to null, outgoing(confidence = 0.2f) to incoming(), outgoing() to incoming(confidence = 0.1f))) {
            val plan = AutomixPlanner.plan(out, duration, inc, 6_000)
            assertFalse(plan.onBeat)
            assertEquals(234_000, plan.fadeAtMs)
            assertEquals(6_000, plan.windowMs)
            assertEquals(0, plan.incomingStartMs)
            assertEquals(1f, plan.tailSpeed)
        }
    }

    @Test
    fun `a beat grid that would not fit in the song falls back`() {
        val plan = AutomixPlanner.plan(outgoing(lastSound = 3_000), duration, incoming(), 6_000)
        assertFalse(plan.onBeat)
    }

    @Test
    fun `gains start with only the outgoing song, end with only the incoming one, and never exceed full`() {
        for (match in listOf(-6f, -2f, 0f, 3f, 6f)) {
            val (startOut, startIn) = automixGains(0f, match)
            val (endOut, endIn) = automixGains(1f, match)
            assertEquals(1f, startOut, 1e-4f)
            assertEquals(0f, startIn, 1e-4f)
            assertEquals(0f, endOut, 1e-4f)
            assertEquals(1f, endIn, 1e-4f)
            for (step in 0..20) {
                val (o, i) = automixGains(step / 20f, match)
                assertTrue(o in 0f..1f && i in 0f..1f)
            }
        }
    }

    @Test
    fun `the louder side is brought down while both play`() {
        val (outLouder, _) = automixGains(0.5f, 6f)
        assertEquals(automixGains(0.5f, 0f).first * 0.501f, outLouder, 1e-3f)
        val (_, inLouder) = automixGains(0.5f, -6f)
        assertEquals(automixGains(0.5f, 0f).second * 0.708f, inLouder, 1e-3f)
    }

    @Test
    fun `beat sync hurries the outgoing song when the incoming one is ahead, within three percent`() {
        assertTrue(beatSyncCorrection(0.1, 0.0) > 0)
        assertTrue(beatSyncCorrection(0.0, 0.1) < 0)
        assertEquals(0.0, beatSyncCorrection(0.3, 0.3), 1e-9)
        // 0.95 against 0.05 is 0.1 behind, not 0.9 ahead.
        assertTrue(beatSyncCorrection(0.95, 0.05) < 0)
        assertEquals(MAX_SYNC_CORRECTION, beatSyncCorrection(0.45, 0.0), 1e-9)
    }
}
