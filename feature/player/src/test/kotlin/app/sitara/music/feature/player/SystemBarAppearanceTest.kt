package app.sitara.music.feature.player

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("DEPRECATION")
class SystemBarAppearanceTest {
    @Test
    fun `opening dark player clears dark icons without changing layout flags`() {
        val layout = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        assertEquals(layout, systemBarFlags(layout or light, darkIcons = false))
        assertEquals(layout or light, systemBarFlags(layout, darkIcons = true))
    }
}
