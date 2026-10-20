package app.sitara.music.core.designsystem

import app.sitara.music.core.model.Artwork
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
    fun `the accent reads as text on the stage backdrop for any cover colour`() {
        val sweep = (0 until 360 step 15).flatMap { h ->
            listOf(0.3f, 0.7f, 1f).flatMap { s -> listOf(0.2f, 0.5f, 0.8f).map { l -> ArtworkPalette.fromHsl(h.toFloat(), s, l) } }
        }
        val fixtures = listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF777777.toInt(), 0xFFFFE000.toInt(), 0xFF2040FF.toInt())
        for (c in sweep + fixtures) {
            for (dark in listOf(true, false)) {
                val t = ArtworkPalette.themeFrom(listOf(c), dark)
                val label = "${Integer.toHexString(c)} dark=$dark accent ${Integer.toHexString(t.accent)}"
                assertTrue(label, ArtworkPalette.contrast(t.accent, t.backdropTop) >= 4.5)
                assertTrue(label, ArtworkPalette.contrast(t.accent, t.backdropBottom) >= 4.5)
                assertTrue(label, ArtworkPalette.contrast(t.accent, t.onAccent) >= 4.5)
            }
        }
    }

    @Test
    fun `mid grey fails AA with both text colours, so the accent is moved off it`() {
        // #777777 is 4.48:1 on white and 4.47:1 on near-black: picking the better text colour alone would ship it.
        val midGrey = 0xFF777777.toInt()
        assertTrue(ArtworkPalette.contrast(midGrey, 0xFFFFFFFF.toInt()) < 4.5)
        assertTrue(ArtworkPalette.contrast(midGrey, 0xFF080808.toInt()) < 4.5)
        for (dark in listOf(true, false)) {
            val t = ArtworkPalette.themeFrom(listOf(midGrey), dark)
            assertTrue(ArtworkPalette.contrast(t.accent, t.onAccent) >= 4.5)
        }
    }

    @Test
    fun `the stage takes its colours from the cover palette once it has loaded`() {
        val artwork = Artwork(listOf(indigo), imageUrl = "https://lh3.googleusercontent.com/c=w544-h544")
        assertEquals(ArtworkPalette.themeFrom(listOf(gold, grey), dark = true), ArtworkPalette.stageTheme(artwork, listOf(gold, grey)))
    }

    @Test
    fun `a cover that cannot load keeps the artwork's own colours`() {
        val artwork = Artwork(listOf(indigo, gold), imageUrl = "https://lh3.googleusercontent.com/c=w544-h544")
        val fallback = ArtworkPalette.themeFrom(listOf(indigo, gold), dark = true)
        assertEquals(fallback, ArtworkPalette.stageTheme(artwork, null))
        assertEquals(fallback, ArtworkPalette.stageTheme(artwork, emptyList()))
    }

    @Test
    fun `contrast of black and white is 21`() {
        assertEquals(21.0, ArtworkPalette.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }
}
