package app.sitara.music.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemEqualiserSessionTest {
    private val announced = mutableListOf<Pair<Boolean, Int>>()
    private val session = SystemEqualiserSession { open, id -> announced += open to id }

    @Test
    fun `opens once while the device's equaliser is chosen and something plays`() {
        session.update(wanted = true, sessionId = 41)
        session.update(wanted = true, sessionId = 41)
        assertEquals(listOf(true to 41), announced)
    }

    @Test
    fun `closes when playback stops or the listener switches back`() {
        session.update(wanted = true, sessionId = 41)
        session.update(wanted = false, sessionId = 41)
        session.update(wanted = false, sessionId = 41)
        assertEquals(listOf(true to 41, false to 41), announced)
    }

    @Test
    fun `a new audio session closes the old one first`() {
        session.update(wanted = true, sessionId = 41)
        session.update(wanted = true, sessionId = 42)
        assertEquals(listOf(true to 41, false to 41, true to 42), announced)
    }

    @Test
    fun `nothing opens without an audio session, and closing an unopened session says nothing`() {
        session.update(wanted = true, sessionId = 0)
        session.close()
        assertEquals(emptyList<Pair<Boolean, Int>>(), announced)
    }

    @Test
    fun `closing hands an open session back`() {
        session.update(wanted = true, sessionId = 41)
        session.close()
        assertEquals(listOf(true to 41, false to 41), announced)
    }
}
