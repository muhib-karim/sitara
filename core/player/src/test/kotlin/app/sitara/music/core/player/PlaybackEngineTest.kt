package app.sitara.music.core.player

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.Track
import kotlin.random.Random
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackEngineTest {
    private fun t(n: Int) = Track("t$n", "Song $n", "Artist", "Album", 1000, Artwork(listOf(0)))
    private val five = (1..5).map(::t)
    private val port = FakePlayerPort()

    private fun TestScope.engine() = PlaybackEngine(
        port = port,
        scope = backgroundScope,
        timeSource = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
            override fun read(): Long = testScheduler.currentTime
        },
    )

    @Test
    fun `the whole queue is handed to the player`() = runTest {
        engine().play(five, 1)
        assertEquals(five.map { it.id }, port.entries.map { it.track.id })
        assertEquals(1, port.index)
        assertTrue(port.playing)
    }

    @Test
    fun `sleep timer pauses the player with no UI attached`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.startSleepTimer(minutes = 1)
        advanceTimeBy(60_001)
        runCurrent()
        assertFalse(port.playing)
        assertEquals(SleepTimerState.Off, engine.sleepTimerState.value)
    }

    @Test
    fun `end-of-track sleep asks the player to pause at the item end`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.sleepAtEndOfTrack()
        assertTrue(port.pauseAtEnd)
        engine.onPausedAtEndOfItem()
        assertEquals(SleepTimerState.Off, engine.sleepTimerState.value)
        assertFalse(port.pauseAtEnd)
    }

    @Test
    fun `player-driven transitions update the queue`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.onItemTransition(port.entries[2].uid)
        assertEquals("t3", engine.state.value.queue.current?.id)
    }

    @Test
    fun `rapid selections leave the player on the latest entry`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.playUpNext(2)
        engine.next()
        assertEquals("t5", engine.state.value.queue.current?.id)
        assertEquals(engine.state.value.queue.currentIndex, port.index)
        assertEquals(1, port.setQueueCalls)
    }

    @Test
    fun `play after the queue ended restarts the current item`() = runTest {
        val engine = engine()
        engine.play(listOf(t(1)), 0)
        port.positionMs = 1_000
        port.playing = false
        engine.onPlaybackEnded()
        assertTrue(engine.state.value.queue.ended)

        engine.togglePlay()
        assertEquals(0, port.index)
        assertEquals(0L, port.positionMs)
        assertTrue(port.playing)
        assertFalse(engine.state.value.queue.ended)
    }

    @Test
    fun `toggle play on an empty queue is a no-op`() = runTest {
        engine().togglePlay()
        assertFalse(port.playing)
        assertEquals(0, port.setQueueCalls)
    }

    @Test
    fun `shuffle reorders around the playing entry without reloading it`() = runTest {
        val engine = engine()
        engine.play(five, 2)
        engine.toggleShuffle(Random(1))
        assertEquals("t3", port.entries[port.index].track.id)
        assertEquals(1, port.reorderCalls)
        assertEquals(1, port.setQueueCalls)
    }

    @Test
    fun `repeat mode is mirrored to the player`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.cycleRepeat()
        assertEquals(RepeatMode.ALL, port.repeat)
    }

    @Test
    fun `previous restarts the track after three seconds`() = runTest {
        val engine = engine()
        engine.play(five, 2)
        port.positionMs = 5_000
        engine.previous()
        assertEquals(2, port.index)
        assertEquals(0L, port.positionMs)
    }
}
