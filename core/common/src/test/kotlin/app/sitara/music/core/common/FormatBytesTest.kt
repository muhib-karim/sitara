package app.sitara.music.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatBytesTest {
    @Test
    fun `counts in thousands like the system storage screen`() {
        assertEquals("0 kB", formatBytes(0))
        assertEquals("820 kB", formatBytes(820_000))
        assertEquals("4.2 MB", formatBytes(4_200_000))
        assertEquals("1.3 GB", formatBytes(1_300_000_000))
    }

    @Test
    fun `rounding never shows 1000 of a unit`() {
        assertEquals("1.0 MB", formatBytes(999_600))
        assertEquals("1.0 GB", formatBytes(999_960_000))
    }

    @Test
    fun `a negative count reads as nothing`() {
        assertEquals("0 kB", formatBytes(-5))
    }
}
