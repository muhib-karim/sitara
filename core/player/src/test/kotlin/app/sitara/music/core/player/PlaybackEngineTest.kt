package app.sitara.music.core.player

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.PlaybackTuning
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.SavedQueue
import app.sitara.music.core.model.Track
import kotlin.random.Random
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
    private val started = mutableListOf<String>()
    private val prepared = mutableListOf<String>()

    private fun TestScope.engine() = PlaybackEngine(
        port = port,
        scope = backgroundScope,
        timeSource = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
            override fun read(): Long = testScheduler.currentTime
        },
        onTrackStarted = { started += it.id },
        prepareAhead = { prepared += it.id },
    )

    @Test
    fun `the song after the current one is prepared once playback starts`() = runTest {
        val engine = engine()
        engine.restore(SavedQueue(five, 0, 0, RepeatMode.OFF))
        assertTrue("a paused, restored queue prepares nothing", prepared.isEmpty())

        engine.togglePlay()
        assertEquals(listOf("t2"), prepared)
    }

    @Test
    fun `a skip prepares the song after the new one`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.refresh()
        engine.next()
        engine.playUpNext(1)
        engine.onItemTransition(port.entries[4].uid)
        assertEquals(listOf("t2", "t3", "t5"), prepared)
    }

    @Test
    fun `shuffle on and off prepares whatever now follows`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.refresh()
        engine.toggleShuffle(Random(7))
        val shuffledNext = engine.state.value.queue.entries[1].track.id
        engine.toggleShuffle()
        assertEquals(listOf("t2", shuffledNext, "t2").distinctConsecutive(), prepared)
    }

    @Test
    fun `queue edits prepare the new next song and leave a still-valid one alone`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.refresh()
        engine.moveUpNext(2, 3)
        engine.append(listOf(t(6)))
        engine.playNext(listOf(t(9)))
        engine.removeUpNext(0)
        engine.moveUpNext(0, 1)
        assertEquals(listOf("t2", "t9", "t2", "t3"), prepared)
    }

    @Test
    fun `repeat decides what follows the last song`() = runTest {
        val engine = engine()
        engine.play(five, 4)
        engine.refresh()
        assertTrue("the queue ends after the last song", prepared.isEmpty())

        engine.cycleRepeat() // all: wraps to the first song
        engine.cycleRepeat() // one: the playing song is already loaded
        engine.cycleRepeat() // off
        engine.cycleRepeat() // all again
        assertEquals(listOf("t1", "t1"), prepared)
    }

    @Test
    fun `nothing is prepared after the song the sleep timer stops on`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.sleepAtEndOfTrack()
        engine.refresh()
        assertTrue(prepared.isEmpty())

        engine.cancelSleepTimer()
        assertEquals(listOf("t2"), prepared)
    }

    private fun List<String>.distinctConsecutive() = filterIndexed { i, id -> i == 0 || this[i - 1] != id }

    @Test
    fun `speed and pitch go to the player and last through a new queue until reset`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.setSpeed(1.5f)
        engine.setPitch(-2)
        assertEquals(PlaybackTuning(speed = 1.5f, semitones = -2), port.applied)

        engine.play(five.take(2), 1)
        assertEquals(PlaybackTuning(speed = 1.5f, semitones = -2), engine.state.value.tuning)

        engine.setSpeed(9f)
        assertEquals("clamped to double speed", 2f, port.applied.speed, 0f)
        engine.resetTuning()
        assertEquals(PlaybackTuning(), port.applied)
        assertEquals(PlaybackTuning(), engine.state.value.tuning)
    }

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
    fun `play next and add to queue insert into the player without reloading`() = runTest {
        val engine = engine()
        engine.play(five.take(3), 0)
        engine.playNext(listOf(t(8), t(9)))
        engine.addToQueue(listOf(t(7)))

        val ids = listOf("t1", "t8", "t9", "t2", "t3", "t7")
        assertEquals(ids, engine.state.value.queue.items.map { it.id })
        assertEquals(ids, port.entries.map { it.track.id })
        assertEquals(engine.state.value.queue.entries.map { it.uid }, port.entries.map { it.uid })
        assertEquals(1, port.setQueueCalls)
    }

    @Test
    fun `add to queue starts playback when nothing is queued`() = runTest {
        val engine = engine()
        engine.addToQueue(listOf(t(7)))
        assertEquals("t7", engine.state.value.queue.current?.id)
        assertTrue(port.playing)
    }

    @Test
    fun `play next starts playback from the first song when nothing is queued`() = runTest {
        val engine = engine()
        engine.playNext(listOf(t(8), t(9)))
        assertEquals(listOf("t8", "t9"), port.entries.map { it.track.id })
        assertEquals("t8", engine.state.value.queue.current?.id)
    }

    @Test
    fun `moving and removing up next are mirrored into the player`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.moveUpNext(0, 2)
        engine.removeUpNext(0)

        val ids = listOf("t1", "t4", "t2", "t5")
        assertEquals(ids, engine.state.value.queue.items.map { it.id })
        assertEquals(ids, port.entries.map { it.track.id })
        assertEquals(0, port.index)
        assertEquals(1, port.setQueueCalls)
    }

    @Test
    fun `a song is recorded once when it actually starts playing`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        port.playing = false
        engine.refresh()
        assertEquals(emptyList<String>(), started)

        port.playing = true
        engine.refresh()
        engine.refresh()
        engine.onItemTransition(port.entries[1].uid)
        assertEquals(listOf("t1", "t2"), started)
    }

    @Test
    fun `a repeat of the same entry counts as another play`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.refresh()
        engine.onItemTransition(port.entries[0].uid)
        assertEquals(listOf("t1", "t1"), started)
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
    fun `an extended sleep timer pauses later`() = runTest {
        val engine = engine()
        engine.play(five, 0)
        engine.startSleepTimer(minutes = 1)
        engine.extendSleepTimer(minutes = 5)
        advanceTimeBy(60_001)
        runCurrent()
        assertTrue(port.playing)
        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertFalse(port.playing)
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

    @Test
    fun `a saved queue comes back paused at its song and position`() = runTest {
        val engine = engine()

        engine.restore(SavedQueue(five, 3, 61_000, RepeatMode.ALL))

        assertEquals(t(4), engine.state.value.queue.current)
        assertEquals(five.map { it.id }, port.entries.map { it.track.id })
        assertEquals(3, port.index)
        assertEquals(61_000, port.positionMs)
        assertEquals(RepeatMode.ALL, port.repeat)
        assertFalse(port.playing)
        assertTrue("a restored song has not started playing", started.isEmpty())
    }

    @Test
    fun `a saved queue never replaces music the listener already started`() = runTest {
        val engine = engine()
        engine.play(five.take(2), 0)

        engine.restore(SavedQueue(five, 3, 61_000, RepeatMode.OFF))

        assertEquals(listOf("t1", "t2"), port.entries.map { it.track.id })
        assertTrue(port.playing)
    }

    @Test
    fun `the queue to save reads the position from the player`() = runTest {
        val engine = engine()
        assertEquals(null, engine.savedQueue())
        engine.play(five, 1)
        port.positionMs = 12_345

        assertEquals(SavedQueue(five, 1, 12_345, RepeatMode.OFF), engine.savedQueue())
    }

    @Test
    fun `the queue is saved after a change settles, when playback pauses and while playing`() = runTest {
        val engine = engine()
        val saves = mutableListOf<SavedQueue>()
        backgroundScope.launch { keepSaving(engine) { saves += it } }
        runCurrent()
        assertTrue("an empty queue is never saved over the last one", saves.isEmpty())

        engine.play(five, 0)
        engine.refresh()
        advanceTimeBy(QUEUE_SAVE_DEBOUNCE_MS + 1)
        assertEquals(1, saves.size)

        port.positionMs = 30_000
        advanceTimeBy(QUEUE_SAVE_INTERVAL_MS)
        assertEquals(30_000, saves.last().positionMs)

        port.positionMs = 31_000
        engine.togglePlay()
        runCurrent()
        assertEquals(31_000, saves.last().positionMs)
        val afterPause = saves.size
        advanceTimeBy(QUEUE_SAVE_INTERVAL_MS * 3)
        assertEquals("nothing changes while paused", afterPause, saves.size)
    }
}
