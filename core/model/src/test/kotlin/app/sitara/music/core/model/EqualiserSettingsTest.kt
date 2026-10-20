package app.sitara.music.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EqualiserSettingsTest {
    @Test
    fun `ten bands an octave apart from 31 Hz to 16 kHz`() {
        assertEquals(listOf(31.25, 62.5, 125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0), EqualiserSettings.BAND_CENTRES_HZ)
    }

    @Test
    fun `starts off and flat`() {
        assertTrue(EqualiserSettings().isFlat)
        assertEquals(EqualiserPreset.FLAT, EqualiserSettings().preset)
    }

    @Test
    fun `every preset adds no level and is told apart by its bands`() {
        EqualiserPreset.entries.forEach { preset ->
            val settings = EqualiserSettings().withPreset(preset)
            assertTrue(settings.enabled)
            assertEquals(preset.bandsDb, settings.bandsDb)
            assertEquals(-maxOf(0, preset.bandsDb.max()), settings.preampDb, "$preset preamp cancels its largest boost")
            assertEquals(preset, settings.preset)
        }
        assertEquals(EqualiserPreset.entries.size, EqualiserPreset.entries.map { it.bandsDb }.toSet().size)
    }

    @Test
    fun `moving a band or the preamp turns the equaliser on and leaves the preset`() {
        val bass = EqualiserSettings().withPreset(EqualiserPreset.BASS_BOOST)
        val custom = bass.copy(enabled = false).withBand(4, 3)
        assertTrue(custom.enabled)
        assertEquals(3, custom.bandsDb[4])
        assertNull(custom.preset)
        assertTrue(EqualiserSettings().withPreamp(-4).enabled)
        assertNull(bass.withPreamp(0).preset)
    }

    @Test
    fun `gains stay within 12 dB`() {
        assertEquals(12, EqualiserSettings().withBand(0, 20).bandsDb[0])
        assertEquals(-12, EqualiserSettings().withPreamp(-30).preampDb)
        assertThrows(IllegalArgumentException::class.java) { EqualiserSettings(bandsDb = List(10) { 13 }) }
        assertThrows(IllegalArgumentException::class.java) { EqualiserSettings(bandsDb = List(9) { 0 }) }
    }

    @Test
    fun `reset flattens the bands and preamp and leaves the switch alone`() {
        assertEquals(EqualiserSettings(enabled = true), EqualiserSettings().withPreset(EqualiserPreset.ROCK).reset())
    }
}
