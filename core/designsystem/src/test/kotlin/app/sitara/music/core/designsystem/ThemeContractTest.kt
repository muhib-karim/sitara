package app.sitara.music.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import app.sitara.music.core.model.AccentColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContractTest {
    private fun contrast(a: Color, b: Color) = ArtworkPalette.contrast(a.toArgb(), b.toArgb())

    /** Primary, body and secondary text must read at AA on every surface the app draws them on, and ink on primary. */
    private fun assertReadable(scheme: ColorScheme, label: String) {
        val surfaces = scheme.run { listOf(background, surface, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest) }
        for (surface in surfaces) {
            assertTrue("$label: primary on $surface", contrast(scheme.primary, surface) >= 4.5)
            assertTrue("$label: text on $surface", contrast(scheme.onSurface, surface) >= 4.5)
            assertTrue("$label: secondary text on $surface", contrast(scheme.onSurfaceVariant, surface) >= 4.5)
        }
        assertTrue("$label: ink on primary", contrast(scheme.onPrimary, scheme.primary) >= 4.5)
    }

    @Test
    fun `brand chrome has neutral bases and readable foregrounds`() {
        for (dark in listOf(true, false)) {
            val base = sitaraColorScheme(dark)
            assertEquals(if (dark) Color.Black else Color.White, base.background)
            assertReadable(base, "brand dark=$dark")
        }
    }

    @Test
    fun `every swatch is its own tuned tone and reads at AA on light, dark and pure black`() {
        for (accent in AccentColor.entries) {
            for (dark in listOf(true, false)) {
                val tone = accentTone(accent, dark) ?: continue
                for (pureBlack in listOf(false, true)) {
                    val scheme = sitaraColorScheme(dark, pureBlack, accent = tone)
                    assertEquals("$accent dark=$dark", tone, scheme.primary)
                    assertReadable(scheme, "$accent dark=$dark black=$pureBlack")
                }
            }
        }
        assertEquals(null, accentTone(AccentColor.ARTWORK, dark = true))
    }

    @Test
    fun `saffron is the brand primary, so the default look is unchanged`() {
        assertEquals(sitaraColorScheme(true).primary, accentTone(AccentColor.SAFFRON, dark = true))
        assertEquals(sitaraColorScheme(false).primary, accentTone(AccentColor.SAFFRON, dark = false))
    }

    @Test
    fun `any wallpaper or cover accent is walked to AA on light and dark`() {
        for (seed in coverSweep) {
            for (dark in listOf(true, false)) {
                assertReadable(sitaraColorScheme(dark, accent = Color(seed)), "accent ${Integer.toHexString(seed)} dark=$dark")
            }
        }
    }

    @Test
    fun `artwork tinted surfaces keep every text colour readable`() {
        for (tint in coverSweep) {
            for (dark in listOf(true, false)) {
                for (accent in listOf(Color(tint), accentTone(AccentColor.SAFFRON, dark)!!)) {
                    assertReadable(sitaraColorScheme(dark, accent = accent, tint = Color(tint)), "tint ${Integer.toHexString(tint)} dark=$dark")
                }
            }
        }
    }

    @Test
    fun `a coloured cover tints the surfaces, a grey one leaves them neutral`() {
        val red = Color(0xFFC62828)
        assertNotEquals(sitaraColorScheme(true).surfaceContainer, sitaraColorScheme(true, tint = red).surfaceContainer)
        val tinted = sitaraColorScheme(false, tint = red).surfaceContainerHigh
        assertTrue("red tint reads warm", tinted.red > tinted.blue)
        val grey = sitaraColorScheme(true, tint = Color(0xFF777777)).surfaceContainerHigh
        assertEquals(grey.red, grey.blue, 0.01f)
    }

    @Test
    fun `pure black turns dark surfaces black and leaves the light theme alone`() {
        val black = sitaraColorScheme(dark = true, pureBlack = true)
        assertEquals(Color.Black, black.background)
        assertEquals(Color.Black, black.surface)
        assertEquals(Color.Black, black.surfaceContainer)
        assertEquals(sitaraColorScheme(dark = false), sitaraColorScheme(dark = false, pureBlack = true))
        assertEquals(Color.Black, sitaraColorScheme(dark = true, pureBlack = true, tint = Color(0xFFC62828)).surfaceContainer)
    }

    private val coverSweep = (0 until 360 step 30).flatMap { h ->
        listOf(0.4f, 1f).flatMap { sat -> listOf(0.15f, 0.5f, 0.85f).map { l -> ArtworkPalette.fromHsl(h.toFloat(), sat, l) } }
    } + listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFE000.toInt(), 0xFF777777.toInt())

    @Test
    fun `stage text and lyrics stay readable over any cover, even a white one`() {
        for (cover in coverSweep) {
            val theme = ArtworkPalette.themeFrom(listOf(cover), dark = true)
            val scrim = ArtworkPalette.stageScrim(cover, theme, SitaraColors.StageSecondary.toArgb())
            val backdrop = Color(ArtworkPalette.stageBrightest(cover, theme, scrim))
            val label = "cover ${Integer.toHexString(cover)} scrim $scrim"
            val inactive = Color.White.copy(alpha = ArtworkScrim.InactiveLyric).compositeOver(backdrop)
            assertTrue(label, ArtworkPalette.contrast(SitaraColors.StageContent.toArgb(), backdrop.toArgb()) >= 4.5)
            assertTrue(label, ArtworkPalette.contrast(SitaraColors.StageSecondary.toArgb(), backdrop.toArgb()) >= 4.5)
            assertTrue(label, ArtworkPalette.contrast(inactive.toArgb(), backdrop.toArgb()) >= 4.5)
        }
    }

    @Test
    fun `a dark cover keeps the lightest scrim, so its colour shows, and a white one gets a heavier one`() {
        val navy = 0xFF1A2340.toInt()
        val white = 0xFFFFFFFF.toInt()
        val text = SitaraColors.StageSecondary.toArgb()
        assertEquals(ArtworkScrim.Floor, ArtworkPalette.stageScrim(navy, ArtworkPalette.themeFrom(listOf(navy), dark = true), text), 0.001f)
        assertTrue(ArtworkPalette.stageScrim(white, ArtworkPalette.themeFrom(listOf(white), dark = true), text) > ArtworkScrim.Floor + 0.2f)
    }
}
