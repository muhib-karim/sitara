package app.sitara.music.core.player

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioMathTest {
    @Test
    fun `equal power crossfade keeps total power constant`() {
        for (i in 0..20) {
            val (out, inn) = Crossfade.gains(i / 20f, CrossfadeCurve.EQUAL_POWER)
            assertEquals(1f, out * out + inn * inn, 1e-4f)
        }
        assertEquals(1f to 0f, Crossfade.gains(0f, CrossfadeCurve.EQUAL_POWER).rounded())
        assertEquals(0f to 1f, Crossfade.gains(1f, CrossfadeCurve.EQUAL_POWER).rounded())
    }

    @Test
    fun `linear crossfade sums to one and clamps input`() {
        val (out, inn) = Crossfade.gains(0.25f, CrossfadeCurve.LINEAR)
        assertEquals(1f, out + inn, 1e-6f)
        assertEquals(1f to 0f, Crossfade.gains(-3f, CrossfadeCurve.LINEAR))
    }

    @Test
    fun `fade progress only starts inside the crossfade window`() {
        assertEquals(0f, Crossfade.progress(positionMs = 50_000, durationMs = 60_000, windowMs = 6_000), 0f)
        assertEquals(0.5f, Crossfade.progress(positionMs = 57_000, durationMs = 60_000, windowMs = 6_000), 1e-6f)
        assertEquals(1f, Crossfade.progress(positionMs = 61_000, durationMs = 60_000, windowMs = 6_000), 0f)
        assertEquals(0f, Crossfade.progress(positionMs = 59_000, durationMs = 60_000, windowMs = 0), 0f)
    }

    @Test
    fun `normalisation lowers loud tracks and boosts quiet ones within limits`() {
        assertEquals(-4f, Loudness.gainDb(measuredLufs = -10f, targetLufs = -14f), 1e-6f)
        assertEquals(4f, Loudness.gainDb(measuredLufs = -18f, targetLufs = -14f), 1e-6f)
        assertEquals(Loudness.MAX_BOOST_DB, Loudness.gainDb(measuredLufs = -40f, targetLufs = -14f), 1e-6f)
    }

    @Test
    fun `normalisation never pushes the true peak above the ceiling`() {
        // +4 dB wanted, but the peak at -2 dBTP only allows +1 dB below the -1 dBTP ceiling.
        assertEquals(1f, Loudness.gainDb(measuredLufs = -18f, targetLufs = -14f, truePeakDb = -2f), 1e-6f)
    }

    @Test
    fun `decibels convert to linear gain`() {
        assertEquals(1f, Loudness.dbToLinear(0f), 1e-6f)
        assertEquals(0.5012f, Loudness.dbToLinear(-6f), 1e-3f)
    }

    private fun Pair<Float, Float>.rounded() = (if (abs(first) < 1e-6f) 0f else first) to (if (abs(second) < 1e-6f) 0f else second)
}
