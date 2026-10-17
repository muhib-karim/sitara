package app.sitara.music.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OutcomeTest {
    @Test
    fun `wraps success and failure`() {
        assertEquals(Outcome.Success(2), outcomeOf { 1 + 1 })
        assertTrue(outcomeOf { error("boom") } is Outcome.Failure)
    }

    @Test
    fun `formats durations`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("3:07", formatDuration(187_400))
        assertEquals("1:01:01", formatDuration(3_661_000))
        assertEquals("0:00", formatDuration(-5))
    }
}
