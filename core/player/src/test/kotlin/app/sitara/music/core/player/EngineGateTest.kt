package app.sitara.music.core.player

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import kotlin.time.TimeSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineGateTest {
    private fun t(n: Int) = Track("t$n", "Song $n", "Artist", "Album", 1000, Artwork(listOf(0)))
    private val port = FakePlayerPort()

    @Test
    fun `commands before attach are replayed in order`() = runTest {
        val gate = EngineGate()
        gate.run { it.play(listOf(t(1), t(2), t(3)), 0) }
        gate.run { it.next() }
        val engine = PlaybackEngine(port, backgroundScope, TimeSource.Monotonic)
        gate.attach(engine)
        assertEquals("t2", engine.state.value.queue.current?.id)
        assertTrue(port.playing)
    }

    @Test
    fun `attaching never reloads the player`() = runTest {
        val engine = PlaybackEngine(port, backgroundScope, TimeSource.Monotonic)
        engine.play(listOf(t(1)), 0)
        val gate = EngineGate()
        gate.attach(engine)
        gate.attach(engine)
        assertEquals(1, port.setQueueCalls)
    }

    @Test
    fun `buffered commands run once`() = runTest {
        val gate = EngineGate()
        var runs = 0
        gate.run { runs++ }
        val engine = PlaybackEngine(port, backgroundScope, TimeSource.Monotonic)
        gate.attach(engine)
        gate.attach(null)
        gate.attach(engine)
        assertEquals(1, runs)
    }
}
