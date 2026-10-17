package app.sitara.music.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkPaletteTest {
    private val gold = 0xFFF5C451.toInt()
    private val grey = 0xFF808080.toInt()
    private val indigo = 0xFF1E1B4B.toInt()

    @Test
    fun `chromatic dominant swatch wins over a louder secondary swatch`() {
        val dominant = 0xFF567C82.toInt()
        val theme = ArtworkPalette.themeFrom(listOf(dominant, 0xFFFF0000.toInt()), true)
        assertEquals(ArtworkPalette.toHsl(dominant).first, ArtworkPalette.toHsl(theme.backdropTop).first, 3f)
    }

    @Test
    fun `grey artwork remains neutral and vivid artwork is restrained`() {
        val neutral = ArtworkPalette.themeFrom(listOf(grey), true)
        assertEquals(0f, ArtworkPalette.saturation(neutral.backdropTop), 0.01f)
        val vivid = ArtworkPalette.themeFrom(listOf(0xFFFF0000.toInt()), true)
        assertTrue(ArtworkPalette.saturation(vivid.backdropTop) <= 0.61f)
    }

    @Test
    fun `hsl round trips`() {
        for (c in listOf(gold, grey, indigo, 0xFFFF0000.toInt(), 0xFF00FF00.toInt())) {
            val (h, s, l) = ArtworkPalette.toHsl(c)
            assertEquals(c, ArtworkPalette.fromHsl(h, s, l))
        }
    }

    @Test
    fun `picks the saturated colour as the accent hue`() {
        val theme = ArtworkPalette.themeFrom(listOf(grey, gold), dark = true)
        val accentHue = ArtworkPalette.toHsl(theme.accent).first
        assertEquals(ArtworkPalette.toHsl(gold).first, accentHue, 3f)
    }

    @Test
    fun `dark theme backdrop is dark and content is readable`() {
        for (colors in listOf(listOf(gold), listOf(indigo), listOf(grey), listOf(0xFFFFFFFF.toInt()))) {
            val dark = ArtworkPalette.themeFrom(colors, dark = true)
            assertTrue(ArtworkPalette.contrast(dark.content, dark.backdropBottom) >= 4.5)
            assertTrue(ArtworkPalette.contrast(dark.content, dark.backdropTop) >= 4.5)
            val light = ArtworkPalette.themeFrom(colors, dark = false)
            assertTrue(ArtworkPalette.contrast(light.content, light.backdropBottom) >= 4.5)
            assertTrue(ArtworkPalette.contrast(light.content, light.backdropTop) >= 4.5)
        }
    }

    @Test
    fun `text on the accent meets AA contrast`() {
        for (c in listOf(gold, indigo, grey, 0xFF00CEC9.toInt(), 0xFFFF6B9A.toInt())) {
            for (dark in listOf(true, false)) {
                val t = ArtworkPalette.themeFrom(listOf(c), dark)
                assertTrue("accent ${Integer.toHexString(t.accent)}", ArtworkPalette.contrast(t.accent, t.onAccent) >= 4.5)
            }
        }
    }

    @Test
    fun `contrast of black and white is 21`() {
        assertEquals(21.0, ArtworkPalette.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }
}
