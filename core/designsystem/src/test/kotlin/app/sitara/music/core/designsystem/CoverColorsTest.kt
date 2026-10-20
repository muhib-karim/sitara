package app.sitara.music.core.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cover colours read from bitmaps drawn here, the way a decoded cover reaches the stage. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CoverColorsTest {
    private val navy = 0xFF1A2340.toInt()
    private val saffron = 0xFFFF9F1C.toInt()

    private fun cover(background: Int, draw: Canvas.(Paint) -> Unit = {}): Bitmap =
        Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).apply {
                drawColor(background)
                draw(Paint())
            }
        }

    private fun hue(argb: Int) = ArtworkPalette.toHsl(argb).first

    @Test
    fun `a mostly navy cover with a saffron detail washes navy and accents saffron`() {
        val colors = coverColorsOf(cover(navy) { paint -> paint.color = saffron; drawRect(80f, 80f, 120f, 120f, paint) })
        assertEquals(hue(navy), hue(colors.swatches[0]), 8f)
        assertEquals(hue(saffron), hue(colors.swatches[1]), 8f)
        val theme = ArtworkPalette.themeFrom(colors.swatches, dark = true)
        assertEquals(hue(navy), hue(theme.backdropTop), 8f)
        assertEquals(hue(saffron), hue(theme.accent), 8f)
    }

    @Test
    fun `a grey cover keeps a neutral accent`() {
        val colors = coverColorsOf(cover(0xFF6E6E6E.toInt()) { paint -> paint.color = 0xFF9A9A9A.toInt(); drawCircle(64f, 64f, 30f, paint) })
        val theme = ArtworkPalette.themeFrom(colors.swatches, dark = true)
        assertEquals(0f, ArtworkPalette.saturation(theme.accent), 0.01f)
    }

    @Test
    fun `the brightest colour is what a large white area blurs to`() {
        val colors = coverColorsOf(cover(navy) { paint -> paint.color = 0xFFFFFFFF.toInt(); drawRect(0f, 0f, 64f, 64f, paint) })
        assertTrue(ArtworkPalette.relativeLuminance(colors.brightest) > 0.9)
    }

    @Test
    fun `an all-white cover, which the palette filters out, still has colours`() {
        val colors = coverColorsOf(cover(0xFFFFFFFF.toInt()))
        assertTrue(colors.swatches.isNotEmpty())
        assertTrue(ArtworkPalette.relativeLuminance(colors.brightest) > 0.9)
    }
}
