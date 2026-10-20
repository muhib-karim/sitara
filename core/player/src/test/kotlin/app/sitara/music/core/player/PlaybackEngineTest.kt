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
    fun `shuffle plays every track once in random order and unshuffle restores the list`() = runTest {
        val engine = engine()
        engine.shuffle(five, Random(7))

        assertTrue(engine.state.value.queue.shuffled)
        assertEquals(five.map { it.id }.sorted(), port.entries.map { it.track.id }.sorted())
        assertEquals(engine.state.value.queue.current, port.entries[port.index].track)
        assertTrue(port.playing)

        engine.toggleShuffle()
        assertEquals(five, engine.state.value.queue.items)
    }

    @Test
    fun `shuffling nothing leaves the queue alone`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.shuffle(emptyList())
        assertEquals(five, engine.state.value.queue.items)
    }

    @Test
    fun `append adds entries after the last one without reloading the playing item`() = runTest {
        val engine = engine()
        engine.play(five.take(2), 1)
        engine.append(listOf(t(6), t(7)))

        assertEquals(listOf("t1", "t2", "t6", "t7"), engine.state.value.queue.items.map { it.id })
        assertEquals(listOf("t1", "t2", "t6", "t7"), port.entries.map { it.track.id })
        assertEquals(engine.state.value.queue.entries.map { it.uid }, port.entries.map { it.uid })
        assertEquals(1, port.setQueueCalls)
        assertEquals("t2", engine.state.value.queue.current?.id)
    }

    @Test
    fun `append needs a queue to extend`() = runTest {
        val engine = engine()
        engine.append(listOf(t(6)))
        assertEquals(emptyList<Track>(), engine.state.value.queue.items)
        assertEquals(emptyList<QueueEntry>(), port.entries)
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
        engine.onPlaybackStateChanged(ended = true)
        assertTrue(engine.state.value.queue.ended)

        engine.togglePlay()
        assertEquals(0, port.index)
        assertEquals(0L, port.positionMs)
        assertTrue(port.playing)
        assertFalse(engine.state.value.queue.ended)
    }

    @Test
    fun `play after seeking from completion keeps the selected position`() = runTest {
        val engine = engine()
        engine.play(listOf(t(1)), 0)
        port.positionMs = 1_000
        port.playing = false
        engine.onPlaybackStateChanged(ended = true)

        engine.seekTo(400)
        assertFalse(engine.state.value.queue.ended)
        engine.togglePlay()

        assertEquals(400L, port.positionMs)
        assertTrue(engine.state.value.isPlaying)
    }

    @Test
    fun `toggle pauses playback that resumed after seeking from completion`() = runTest {
        val engine = engine()
        engine.play(listOf(t(1)), 0)
        port.playing = false
        engine.onPlaybackStateChanged(ended = true)

        engine.seekTo(400)
        port.playing = true
        engine.togglePlay()

        assertEquals(400L, engine.state.value.positionMs)
        assertFalse(engine.state.value.isPlaying)
        assertFalse(engine.state.value.queue.ended)
    }

    @Test
    fun `previous after completion clears the restart flag`() = runTest {
        val engine = engine()
        engine.play(listOf(t(1).copy(durationMs = 10_000)), 0)
        port.positionMs = 10_000
        port.playing = false
        engine.onPlaybackStateChanged(ended = true)

        engine.previous()
        assertEquals(0L, engine.state.value.positionMs)
        assertFalse(engine.state.value.queue.ended)
        port.positionMs = 500
        port.playing = true
        engine.togglePlay()

        assertEquals(500L, engine.state.value.positionMs)
        assertFalse(engine.state.value.isPlaying)
    }

    @Test
    fun `system seek leaving ended state clears completion without an item transition`() = runTest {
        val engine = engine()
        engine.play(listOf(t(1)), 0)
        val entry = engine.state.value.queue.currentEntry
        port.playing = false
        engine.onPlaybackStateChanged(ended = true)

        port.seekTo(400)
        engine.onPlaybackStateChanged(ended = false)
        assertFalse(engine.state.value.queue.ended)
        engine.togglePlay()

        assertEquals(entry, engine.state.value.queue.currentEntry)
        assertEquals(400L, engine.state.value.positionMs)
        assertTrue(engine.state.value.isPlaying)
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
