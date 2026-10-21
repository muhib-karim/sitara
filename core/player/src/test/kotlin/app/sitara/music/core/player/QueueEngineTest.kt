package app.sitara.music.core.player

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.Track
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueEngineTest {
    private fun t(n: Int) = Track("t$n", "Song $n", "Artist", "Album", 1000, Artwork(listOf(0)))
    private val five = (1..5).map(::t)

    @Test
    fun `set queue starts at the given index`() {
        val q = QueueState().setQueue(five, startIndex = 2)
        assertEquals("t3", q.current?.id)
    }

    @Test
    fun `add appends and addNext inserts after current`() {
        val q = QueueState().setQueue(five, 1).add(t(9)).addNext(t(8))
        assertEquals(listOf("t1", "t2", "t8", "t3", "t4", "t5", "t9"), q.items.map { it.id })
        assertEquals("t2", q.current?.id)
    }

    @Test
    fun `play next inserts songs after the current one in their order`() {
        val q = QueueState().setQueue(five, 1).playNext(listOf(t(8), t(9)))
        assertEquals(listOf("t1", "t2", "t8", "t9", "t3", "t4", "t5"), q.items.map { it.id })
        assertEquals("t2", q.current?.id)
    }

    @Test
    fun `songs played next while shuffled stay next after unshuffle`() {
        val q = QueueState().setQueue(five, 0).toggleShuffle(Random(3)).playNext(listOf(t(8), t(9))).toggleShuffle()
        assertEquals(listOf("t1", "t8", "t9", "t2", "t3", "t4", "t5"), q.items.map { it.id })
    }

    @Test
    fun `up next positions count from the song after the current one`() {
        val moved = QueueState().setQueue(five, 1).moveUpNext(0, 2)
        assertEquals(listOf("t1", "t2", "t4", "t5", "t3"), moved.items.map { it.id })
        assertEquals("t2", moved.current?.id)

        val removed = moved.removeUpNext(1)
        assertEquals(listOf("t1", "t2", "t4", "t3"), removed.items.map { it.id })
        assertEquals("t2", removed.current?.id)
    }

    @Test
    fun `up next edits outside up next change nothing`() {
        val q = QueueState().setQueue(five, 1)
        assertEquals(q, q.moveUpNext(-1, 0))
        assertEquals(q, q.moveUpNext(0, 3))
        assertEquals(q, q.removeUpNext(-1))
        assertEquals(q, q.removeUpNext(3))
    }

    @Test
    fun `the queue is running out on its last two entries unless it repeats`() {
        assertFalse(QueueState().runningOut)
        assertFalse(QueueState().setQueue(five, 2).runningOut)
        assertTrue(QueueState().setQueue(five, 3).runningOut)
        assertTrue(QueueState().setQueue(five, 4).runningOut)
        assertFalse(QueueState().setQueue(five, 4).withRepeat(RepeatMode.ALL).runningOut)
        assertFalse(QueueState().setQueue(five, 4).withRepeat(RepeatMode.ONE).runningOut)
    }

    @Test
    fun `move keeps the current track selected`() {
        val q = QueueState().setQueue(five, 1).move(from = 1, to = 4)
        assertEquals(listOf("t1", "t3", "t4", "t5", "t2"), q.items.map { it.id })
        assertEquals("t2", q.current?.id)
        val q2 = QueueState().setQueue(five, 2).move(from = 0, to = 4)
        assertEquals("t3", q2.current?.id)
    }

    @Test
    fun `remove before current shifts index and removing current picks the next`() {
        val q = QueueState().setQueue(five, 2).remove(0)
        assertEquals("t3", q.current?.id)
        val q2 = QueueState().setQueue(five, 2).remove(2)
        assertEquals("t4", q2.current?.id)
        val q3 = QueueState().setQueue(five, 4).remove(4)
        assertEquals("t4", q3.current?.id)
        assertNull(QueueState().setQueue(listOf(t(1)), 0).remove(0).current)
    }

    @Test
    fun `shuffle puts current first and unshuffle restores the original order`() {
        val q = QueueState().setQueue(five, 2).toggleShuffle(Random(7))
        assertTrue(q.shuffled)
        assertEquals("t3", q.items.first().id)
        assertEquals(0, q.currentIndex)
        assertEquals(five.toSet(), q.items.toSet())

        val restored = q.next().toggleShuffle(Random(7))
        assertFalse(restored.shuffled)
        assertEquals(five.map { it.id }, restored.items.map { it.id })
        assertEquals(q.items[1].id, restored.current?.id)
    }

    @Test
    fun `unshuffle restores the duplicate entry that was playing`() {
        val a = t(1)
        val b = t(2)
        val q = QueueState().setQueue(listOf(a, b, a), startIndex = 2)
        val restored = q.toggleShuffle(Random(3)).toggleShuffle()
        assertEquals(listOf("t1", "t2", "t1"), restored.items.map { it.id })
        assertEquals(2, restored.currentIndex)
    }

    @Test
    fun `tracks added while shuffled survive unshuffle`() {
        val q = QueueState().setQueue(five, 0).toggleShuffle(Random(1)).add(t(9)).toggleShuffle()
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5", "t9"), q.items.map { it.id })
    }

    @Test
    fun `repeat modes drive next and previous`() {
        val end = QueueState().setQueue(five, 4)
        assertTrue(end.next().ended)
        assertEquals("t1", end.withRepeat(RepeatMode.ALL).next().current?.id)
        assertEquals("t5", end.withRepeat(RepeatMode.ONE).next().current?.id)
        assertEquals("t5", QueueState().setQueue(five, 0).withRepeat(RepeatMode.ALL).previous().current?.id)
        assertEquals("t1", QueueState().setQueue(five, 0).previous().current?.id)
    }

    @Test
    fun `skipping to next with repeat one still advances when user asks`() {
        val q = QueueState().setQueue(five, 1).withRepeat(RepeatMode.ONE)
        assertEquals("t3", q.skipNext().current?.id)
    }

    @Test
    fun `radio songs wait behind the songs you queue`() {
        val q = QueueState().setQueue(five, 1).add(t(8), fromRadio = true).add(t(9), fromRadio = true).add(t(7))
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5", "t7", "t8", "t9"), q.items.map { it.id })
        assertEquals(2, q.upNextFromRadio)
        assertEquals(listOf("t8", "t9"), q.upNext.takeLast(q.upNextFromRadio).map { it.id })
    }

    @Test
    fun `once radio plays, a queued song goes right after it and no radio song counts as up next twice`() {
        val q = QueueState().setQueue(five.take(2), 1).add(t(8), fromRadio = true).add(t(9), fromRadio = true).skipNext().add(t(7))
        assertEquals("t8", q.current?.id)
        assertEquals(listOf("t7", "t9"), q.upNext.map { it.id })
        assertEquals(1, q.upNextFromRadio)
    }

    @Test
    fun `shuffling keeps radio songs after yours`() {
        val q = QueueState().setQueue(five, 0).add(t(8), fromRadio = true).add(t(9), fromRadio = true).toggleShuffle(Random(7))
        assertEquals("t1", q.current?.id)
        assertEquals(setOf("t8", "t9"), q.items.takeLast(2).map { it.id }.toSet())
        assertEquals(2, q.upNextFromRadio)
        assertEquals("unshuffling restores the order", listOf("t1", "t2", "t3", "t4", "t5", "t8", "t9"), q.toggleShuffle().items.map { it.id })
    }

    @Test
    fun `a song queued while shuffled still goes ahead of radio's once shuffle is off`() {
        val q = QueueState().setQueue(five.take(2), 0).add(t(8), fromRadio = true).toggleShuffle(Random(5)).add(t(7)).toggleShuffle()
        assertEquals(listOf("t1", "t2", "t7", "t8"), q.items.map { it.id })
        assertEquals(1, q.upNextFromRadio)
    }

    @Test
    fun `a removed song comes back where it was, as the same entry`() {
        val q = QueueState().setQueue(five, 1).add(t(8), fromRadio = true)
        val removed = q.entries[3]
        assertEquals(q, q.removeUpNext(1).reinsertUpNext(1, removed))
        val radio = q.entries.last()
        assertEquals(q, q.removeUpNext(q.upNext.lastIndex).reinsertUpNext(q.upNext.lastIndex, radio))
    }

    @Test
    fun `a song put back after the queue moved on lands as near its place as the queue allows`() {
        val q = QueueState().setQueue(five, 1)
        val removed = q.entries[4]
        val back = q.removeUpNext(2).skipNext().skipNext().reinsertUpNext(2, removed)
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5"), back.items.map { it.id })
        assertEquals("t4", back.current?.id)
    }

    @Test
    fun `a removed song comes back into the unshuffled order too`() {
        val q = QueueState().setQueue(five, 0).toggleShuffle(Random(3))
        val removed = q.entries[2]
        val back = q.removeUpNext(1).reinsertUpNext(1, removed)
        assertEquals(q.entries, back.entries)
        assertEquals(five.map { it.id }.toSet(), back.toggleShuffle().items.map { it.id }.toSet())
    }
}
