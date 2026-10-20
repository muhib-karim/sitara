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
}
