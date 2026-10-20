package app.sitara.music.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackTuningTest {
    @Test
    fun `speed snaps to five hundredths and stays between half and double`() {
        assertEquals(1.25f, PlaybackTuning().withSpeed(1.26f).speed)
        assertEquals(1.3f, PlaybackTuning().withSpeed(1.31f).speed)
        assertEquals(0.5f, PlaybackTuning().withSpeed(0.1f).speed)
        assertEquals(2f, PlaybackTuning().withSpeed(3f).speed)
    }

    @Test
    fun `pitch moves in semitones, independently of speed`() {
        val tuning = PlaybackTuning(speed = 1.5f).withSemitones(12 + 3)
        assertEquals(PlaybackTuning.MAX_SEMITONES, tuning.semitones)
        assertEquals(1.5f, tuning.speed)
        assertEquals(1f, PlaybackTuning(speed = 2f).pitch)
        assertEquals(0.7071068f, PlaybackTuning(semitones = -6).pitch, 1e-6f, "half an octave down")
        assertEquals(1.059463f, PlaybackTuning(semitones = 1).pitch, 1e-6f)
    }

    @Test
    fun `only normal speed and pitch count as the default`() {
        assertTrue(PlaybackTuning().isDefault)
        assertFalse(PlaybackTuning(semitones = -1).isDefault)
        assertFalse(PlaybackTuning(speed = 0.95f).isDefault)
    }

    @Test
    fun `out of range values are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { PlaybackTuning(speed = 2.5f) }
        assertThrows(IllegalArgumentException::class.java) { PlaybackTuning(semitones = 7) }
    }
}
