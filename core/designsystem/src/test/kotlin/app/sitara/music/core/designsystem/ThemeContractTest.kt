package app.sitara.music.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import app.sitara.music.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContractTest {
    @Test
    fun `brand chrome has neutral bases and readable foregrounds`() {
        for (dark in listOf(true, false)) {
            val base = sitaraColorScheme(ThemeMode.ARTWORK, dark)
            assertEquals(if (dark) Color.Black else Color.White, base.background)
            assertTrue(ArtworkPalette.contrast(base.onSurfaceVariant.toArgb(), base.surfaceContainerHigh.toArgb()) >= 4.5)
            assertTrue(ArtworkPalette.contrast(base.primary.toArgb(), base.surfaceContainerHigh.toArgb()) >= 3.0)
        }
    }

    @Test
    fun `wallpaper supplies accent but leaves neutral surfaces intact`() {
        val wallpaper = darkColorScheme(primary = Color.Cyan, background = Color.Red, surface = Color.Blue)
        val scheme = sitaraColorScheme(ThemeMode.DYNAMIC, true, wallpaper)
        assertEquals(Color.Cyan, scheme.primary)
        assertEquals(Color.Black, scheme.background)
        assertEquals(sitaraColorScheme(ThemeMode.ARTWORK, true).surface, scheme.surface)
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

    @Test
    fun `amoled remains black even with light preference`() {
        assertEquals(Color.Black, sitaraColorScheme(ThemeMode.AMOLED, false).background)
    }
}
