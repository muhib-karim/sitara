package app.sitara.music.core.data

import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.EqualiserPreset
import app.sitara.music.core.model.EqualiserSettings
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsCodecTest {
    @Test
    fun `round trips every field`() {
        val s = SitaraSettings(
            ThemeMode.AMOLED, DarkMode.DARK, reducedMotion = true, crossfadeSeconds = 6, normalisation = false, targetLufs = -16f, downloadOnWifiOnly = false, skipSilence = true,
            equaliser = EqualiserSettings(enabled = true, bandsDb = listOf(-12, -7, 0, 1, 2, 3, 4, 5, 11, 12), preampDb = -5),
        )
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun `every equaliser preset round trips and is still recognised`() {
        EqualiserPreset.entries.forEach { preset ->
            val s = SitaraSettings(equaliser = EqualiserSettings().withPreset(preset))
            assertEquals(preset, SettingsCodec.decode(SettingsCodec.encode(s)).equaliser.preset)
        }
    }

    @Test
    fun `a corrupt equaliser falls back to flat bands, clamping gains`() {
        val decoded = { bands: String -> SettingsCodec.decode(mapOf(SettingsCodec.EQ_BANDS to bands, SettingsCodec.EQ_PREAMP to "40")).equaliser }
        assertEquals(EqualiserSettings().bandsDb, decoded("1,2,3").bandsDb)
        assertEquals(EqualiserSettings().bandsDb, decoded("1,2,3,4,5,6,7,8,9,x").bandsDb)
        assertEquals(EqualiserSettings().bandsDb, decoded("1,2,3,4,5,6,7,8,9,10,11").bandsDb)
        assertEquals(listOf(12, -12, 0, 0, 0, 0, 0, 0, 0, 3), decoded("99,-99,0,0,0,0,0,0,0,3").bandsDb)
        assertEquals(12, decoded("").preampDb)
        assertEquals(false, decoded("").enabled)
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
    fun `skip silence is off unless the listener turned it on`() {
        assertEquals(false, SettingsCodec.decode(emptyMap()).skipSilence)
        assertEquals(false, SettingsCodec.decode(mapOf(SettingsCodec.SKIP_SILENCE to "1")).skipSilence)
        assertEquals(true, SettingsCodec.decode(mapOf(SettingsCodec.SKIP_SILENCE to "true")).skipSilence)
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
