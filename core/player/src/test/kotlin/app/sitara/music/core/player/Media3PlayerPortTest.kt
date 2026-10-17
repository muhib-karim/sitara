package app.sitara.music.core.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Media3PlayerPortTest {
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val port = Media3PlayerPort(player)
    private val loader = Executors.newSingleThreadExecutor()
    private val entries = (1..2).map {
        QueueEntry(it.toLong(), Track("t$it", "Song $it", "Artist", "Album", 1_000, Artwork(listOf(0))))
    }

    @After
    fun closeLoader() {
        loader.shutdownNow()
    }

    @Test
    fun `shuffle never hides a queued track from a concurrent loader`() {
        port.setQueue(entries, 0, play = false)
        every { player.currentMediaItemIndex } returns 0
        every { player.mediaItemCount } returns 2
        var lookups = 0
        val reordered = observeReads(entries.reversed()) {
            assertLoaderSees(entries)
            lookups++
        }

        port.reorder(reordered, 1)

        assertTrue("Lookup must overlap reorder", lookups > 0)
        assertLoaderSees(entries)
    }

    @Test
    fun `queue replacement publishes complete track membership atomically`() {
        port.setQueue(entries, 0, play = false)
        val replacement = entries.map { it.copy(track = it.track.copy(title = "Updated")) }
        var reads = 0
        val observed = observeReads(replacement) {
            // The first traversal builds membership; the second builds MediaItems after publication.
            if (reads < replacement.size) {
                assertLoaderSees(entries)
            } else {
                assertLoaderSees(replacement)
            }
            reads++
        }

        port.setQueue(observed, 0, play = false)

        assertTrue("Lookup must overlap replacement", reads >= replacement.size)
        assertLoaderSees(replacement)
    }

    private fun assertLoaderSees(expected: List<QueueEntry>) {
        for (entry in expected) {
            val actual = loader.submit<Track?> { port.track(entry.track.id) }.get(5, TimeUnit.SECONDS)
            assertEquals(entry.track, actual)
        }
    }

    private fun observeReads(entries: List<QueueEntry>, onRead: () -> Unit): List<QueueEntry> = object : AbstractList<QueueEntry>() {
        override val size: Int get() = entries.size

        override fun get(index: Int): QueueEntry {
            onRead()
            return entries[index]
        }
    }
}
