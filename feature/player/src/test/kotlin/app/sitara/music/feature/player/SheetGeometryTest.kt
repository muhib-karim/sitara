package app.sitara.music.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetGeometryTest {
    private fun at(progress: Float) = SheetGeometry.at(
        progress = progress,
        collapsedTop = 1_800f,
        fullHeight = 2_400f,
        miniHeight = 192f,
        collapsedSide = 36f,
    )

    @Test
    fun `an overshooting spring never produces a negative corner radius`() {
        val over = at(1.3f)
        assertTrue(over.cornerRadiusDp >= 0f)
        assertTrue(over.y >= 0f)
        assertTrue(over.height <= 2_400f)
        assertEquals(at(1f), over)
    }

    @Test
    fun `progress below zero stays docked`() {
        assertEquals(at(0f), at(-0.2f))
        assertEquals(1_800f, at(0f).y)
        assertEquals(192f, at(0f).height)
    }

    @Test
    fun `full expansion fills the screen with square corners`() {
        val full = at(1f)
        assertEquals(0f, full.y)
        assertEquals(2_400f, full.height)
        assertEquals(0f, full.side)
        assertEquals(0f, full.cornerRadiusDp)
    }
}
