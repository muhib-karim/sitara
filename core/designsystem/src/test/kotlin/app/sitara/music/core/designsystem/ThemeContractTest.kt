package app.sitara.music.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
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

    @Test
    fun `amoled remains black even with light preference`() {
        assertEquals(Color.Black, sitaraColorScheme(ThemeMode.AMOLED, false).background)
    }
}
