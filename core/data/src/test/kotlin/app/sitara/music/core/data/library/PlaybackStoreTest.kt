package app.sitara.music.core.data.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.CachedLyrics
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.SavedQueue
import app.sitara.music.core.model.Track
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The saved play queue and the lyrics cache against an in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SitaraDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private fun song(n: Int) = Track("id$n", "Song $n", "Artist", "Album", 200_000L + n, Artwork(listOf(n, -n), "https://example.org/$n.jpg"), artistId = "UC$n")

    @After
    fun close() = db.close()

    @Test
    fun `nothing is saved on a fresh install`() = runTest {
        assertNull(RoomPlaybackQueue(db.playbackQueue()).load())
    }

    @Test
    fun `a queue comes back as it was saved, including the same song twice`() = runTest {
        val queue = RoomPlaybackQueue(db.playbackQueue())
        val saved = SavedQueue(listOf(song(1), song(2), song(1)), currentIndex = 2, positionMs = 61_500, repeatMode = RepeatMode.ALL)

        queue.save(saved)

        assertEquals(saved, queue.load())
    }

    @Test
    fun `a shuffled queue keeps its unshuffled order`() = runTest {
        val queue = RoomPlaybackQueue(db.playbackQueue())
        val saved = SavedQueue(listOf(song(3), song(1), song(2)), currentIndex = 0, positionMs = 0, repeatMode = RepeatMode.OFF, unshuffledOrder = listOf(1, 2, 0))

        queue.save(saved)

        assertEquals(saved, queue.load())
    }

    @Test
    fun `saving replaces the previous queue and null clears it`() = runTest {
        val queue = RoomPlaybackQueue(db.playbackQueue())
        queue.save(SavedQueue(listOf(song(1), song(2), song(3)), 1, 5_000, RepeatMode.ONE))
        val shorter = SavedQueue(listOf(song(4)), 0, 0, RepeatMode.OFF)

        queue.save(shorter)
        assertEquals(shorter, queue.load())

        queue.save(null)
        assertNull(queue.load())
    }

    @Test
    fun `saving the same songs again keeps the new position and current song`() = runTest {
        val queue = RoomPlaybackQueue(db.playbackQueue())
        val first = SavedQueue(listOf(song(1), song(2)), 0, 1_000, RepeatMode.OFF)
        queue.save(first)

        queue.save(first.copy(currentIndex = 1, positionMs = 45_000))

        assertEquals(first.copy(currentIndex = 1, positionMs = 45_000), queue.load())
        assertEquals("a fresh store reads the same", first.copy(currentIndex = 1, positionMs = 45_000), RoomPlaybackQueue(db.playbackQueue()).load())
    }

    @Test
    fun `cached lyrics are kept per song, and a later lookup replaces them`() = runTest {
        val cache = RoomLyricsCache(db.lyricsCache())
        val notFound = CachedLyrics(synced = null, plain = null, fetchedAtMs = 1_000)
        val found = CachedLyrics(synced = "[00:01.00]Hello", plain = "Hello", fetchedAtMs = 2_000)

        cache.put("id1", "lrclib", notFound)
        assertEquals(notFound, cache.get("id1", "lrclib"))
        assertNull(cache.get("id2", "lrclib"))

        cache.put("id1", "lrclib", found)
        assertEquals(found, cache.get("id1", "lrclib"))
    }
}
