package app.sitara.music.core.player

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.SavedQueue
import app.sitara.music.core.model.Track
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedQueueTest {
    private fun t(n: Int) = Track("t$n", "Song $n", "Artist", "Album", 1000, Artwork(listOf(0)))
    private val five = (1..5).map(::t)

    @Test
    fun `nothing queued saves nothing`() {
        assertNull(QueueState().toSaved(positionMs = 0))
    }

    @Test
    fun `a queue keeps its songs, current entry, position and repeat mode`() {
        val queue = QueueState().setQueue(five, 2).withRepeat(RepeatMode.ONE)

        assertEquals(SavedQueue(five, 2, 42_000, RepeatMode.ONE), queue.toSaved(positionMs = 42_000))
    }

    @Test
    fun `a finished queue restarts its last song from the beginning`() {
        val ended = QueueState().setQueue(five, 4).skipNext()

        assertEquals(0, ended.toSaved(positionMs = 1_000)!!.positionMs)
    }

    @Test
    fun `a restored queue plays the same songs from the same entry`() {
        val restored = SavedQueue(five + t(1), 3, 9_000, RepeatMode.ALL).toQueueState()!!

        assertEquals(five + t(1), restored.items)
        assertEquals(3, restored.currentIndex)
        assertEquals(RepeatMode.ALL, restored.repeatMode)
        assertEquals(restored.entries.size, restored.entries.map { it.uid }.toSet().size)
        assertEquals(restored.entries.size.toLong(), restored.nextUid)
    }

    @Test
    fun `a shuffled queue still unshuffles to the original order after a restart`() {
        val shuffled = QueueState().setQueue(five, 1).toggleShuffle(Random(3))

        val restored = shuffled.toSaved(positionMs = 0)!!.toQueueState()!!

        assertTrue(restored.shuffled)
        assertEquals(shuffled.items, restored.items)
        val unshuffled = restored.toggleShuffle()
        assertEquals(five, unshuffled.items)
        assertEquals(t(2), unshuffled.current)
    }

    @Test
    fun `a saved queue that does not add up restores nothing or drops the broken shuffle`() {
        assertNull(SavedQueue(emptyList(), 0, 0, RepeatMode.OFF).toQueueState())
        assertNull(SavedQueue(five, 5, 0, RepeatMode.OFF).toQueueState())

        val badOrder = SavedQueue(five, 0, 0, RepeatMode.OFF, unshuffledOrder = listOf(0, 0, 1, 2, 3)).toQueueState()!!
        assertFalse(badOrder.shuffled)
        assertEquals(five, badOrder.items)
    }
}
