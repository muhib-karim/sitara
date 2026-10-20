package app.sitara.music.core.data

import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsCodecTest {
    @Test
    fun `round trips every field`() {
        val s = SitaraSettings(ThemeMode.AMOLED, DarkMode.DARK, reducedMotion = true, crossfadeSeconds = 6, normalisation = false, targetLufs = -16f, downloadOnWifiOnly = false)
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun `empty storage gives defaults`() {
        assertEquals(SitaraSettings(), SettingsCodec.decode(emptyMap()))
    }

    @Test
    fun `downloads wait for wifi unless the listener allows mobile data`() {
        assertEquals(true, SettingsCodec.decode(emptyMap()).downloadOnWifiOnly)
        assertEquals(true, SettingsCodec.decode(mapOf(SettingsCodec.WIFI_ONLY to "maybe")).downloadOnWifiOnly)
        assertEquals(false, SettingsCodec.decode(mapOf(SettingsCodec.WIFI_ONLY to "false")).downloadOnWifiOnly)
    }

    @Test
    fun `corrupt values fall back or clamp`() {
        val s = SettingsCodec.decode(
            mapOf(
                SettingsCodec.THEME to "NEON",
                SettingsCodec.REDUCED_MOTION to "yes",
                SettingsCodec.CROSSFADE to "99",
                SettingsCodec.TARGET_LUFS to "3",
            ),
        )
        assertEquals(ThemeMode.ARTWORK, s.themeMode)
        assertEquals(false, s.reducedMotion)
        assertEquals(12, s.crossfadeSeconds)
        assertEquals(-14f, s.targetLufs)
    }

    @Test
    fun `model rejects out of range crossfade`() {
        assertThrows(IllegalArgumentException::class.java) { SitaraSettings(crossfadeSeconds = 13) }
    }
}
