package app.sitara.music.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatListeningTest {
    @Test
    fun `under an hour is whole minutes`() {
        assertEquals("0 min", formatListening(0))
        assertEquals("0 min", formatListening(59_999))
        assertEquals("42 min", formatListening(42 * 60_000L + 30_000))
    }

    @Test
    fun `an hour or more is hours and minutes, dropping a zero`() {
        assertEquals("1 h 5 min", formatListening(65 * 60_000L))
        assertEquals("12 h", formatListening(12 * 3_600_000L + 20_000))
        assertEquals("130 h 2 min", formatListening(130 * 3_600_000L + 2 * 60_000L))
    }
}
