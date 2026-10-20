package app.sitara.music.source.fake

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeMusicSourceTest {
    private val source = FakeMusicSource()

    @Test
    fun `home has shelves with content`() = runTest {
        val shelves = source.home()
        assertTrue(shelves.size >= 3)
        assertTrue(shelves.all { it.tracks.isNotEmpty() || it.albums.isNotEmpty() || it.playlists.isNotEmpty() })
    }

    @Test
    fun `search matches title, artist and album case-insensitively`() = runTest {
        assertEquals("Marigold", source.search("marigold").tracks.single().title)
        assertEquals(3, source.search("LUMEN").tracks.size)
        assertTrue(source.search("   ").isEmpty)
        assertTrue(source.search("zzzz").isEmpty)
    }

    @Test
    fun `every track resolves to a distinct https demo stream`() = runTest {
        val urls = SampleCatalog.tracks.map { source.resolve(it).url }
        assertTrue(urls.all { it.startsWith("https://") })
        assertEquals(urls.size, urls.toSet().size)
    }

    @Test
    fun `sample lyrics are word synced`() = runTest {
        val lyrics = source.lyrics(SampleCatalog.tracks.first())!!
        assertTrue(lyrics.synced)
        assertTrue(lyrics.lines.all { it.isWordSynced })
    }

    @Test
    fun `tracks outside the sample catalogue get no sample lyrics`() = runTest {
        assertNull(source.lyrics(SampleCatalog.tracks.first().copy(id = "dQw4w9WgXcQ", title = "A real song")))
    }
}
