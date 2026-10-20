package app.sitara.music.core.player

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeTest {
    private fun song(id: String, album: String = "") = Track(id, "Song $id", "Artist", album, 200_000, Artwork(listOf(0)))

    private val a = song("a")
    private val b = song("b")

    @Test
    fun `songs from different places fade into each other`() {
        assertTrue(Crossfade.shouldOverlap(6_000, a, b, 200_000, repeatOne = false, pauseAtEnd = false))
        assertTrue("singles carry no album", Crossfade.shouldOverlap(6_000, song("x", ""), song("y", ""), 200_000, false, false))
        assertTrue(Crossfade.shouldOverlap(6_000, song("x", "One"), song("y", "Two"), 200_000, false, false))
    }

    @Test
    fun `an album plays gapless`() {
        assertFalse(Crossfade.shouldOverlap(6_000, song("x", "Paper Lanterns"), song("y", "Paper Lanterns"), 200_000, false, false))
    }

    @Test
    fun `nothing fades when crossfade is off, at the end of the queue, on repeat one or before a sleep stop`() {
        assertFalse("off", Crossfade.shouldOverlap(0, a, b, 200_000, false, false))
        assertFalse("last song", Crossfade.shouldOverlap(6_000, a, null, 200_000, false, false))
        assertFalse("repeat one", Crossfade.shouldOverlap(6_000, a, b, 200_000, repeatOne = true, pauseAtEnd = false))
        assertFalse("sleep at end of song", Crossfade.shouldOverlap(6_000, a, b, 200_000, repeatOne = false, pauseAtEnd = true))
    }

    @Test
    fun `a song shorter than two fades is not cut into`() {
        assertFalse(Crossfade.shouldOverlap(12_000, a, b, 23_999, false, false))
        assertTrue(Crossfade.shouldOverlap(12_000, a, b, 24_000, false, false))
    }

    @Test
    fun `the tail loads ten seconds before the fade, never before the song starts`() {
        assertEquals(184_000, Crossfade.prepareAtMs(durationMs = 200_000, windowMs = 6_000))
        assertEquals(0, Crossfade.prepareAtMs(durationMs = 15_000, windowMs = 6_000))
    }

    @Test
    fun `over a 6 s fade the outgoing song falls as the next rises, at constant power`() {
        val duration = 200_000L
        val window = 6_000L
        val curve = (0..6).map { s -> Crossfade.gains(Crossfade.progress(duration - window + s * 1_000, duration, window)) }
        assertEquals(1f to 0f, curve.first().let { (out, inn) -> out.round() to inn.round() })
        assertEquals(0f to 1f, curve.last().let { (out, inn) -> out.round() to inn.round() })
        assertEquals(0.7071f, curve[3].first, 1e-3f)
        assertEquals(0.7071f, curve[3].second, 1e-3f)
        curve.zipWithNext().forEach { (before, after) ->
            assertTrue("outgoing only falls", after.first <= before.first)
            assertTrue("incoming only rises", after.second >= before.second)
        }
        curve.forEach { (out, inn) -> assertEquals(1f, out * out + inn * inn, 1e-4f) }
    }

    private fun Float.round() = Math.round(this * 1e4f) / 1e4f
}
