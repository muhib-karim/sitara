package app.sitara.music.source.fake

import app.sitara.music.core.model.SearchFilter
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakeMusicSourceTest {
    private val source = FakeMusicSource()

    @Test
    fun `the sample chart ranks every sample song`() = runTest {
        assertEquals(SampleCatalog.tracks, source.trending().tracks)
    }

    @Test
    fun `search matches title, artist and album case-insensitively`() = runTest {
        assertEquals("Marigold", source.search("marigold").tracks.single().title)
        assertEquals(3, source.search("LUMEN").tracks.size)
        assertTrue(source.search("   ").isEmpty)
        assertTrue(source.search("zzzz").isEmpty)
    }

    @Test
    fun `search returns only the kind the filter asks for`() = runTest {
        val albums = source.search("lumen", SearchFilter.ALBUMS)
        assertEquals(listOf("Afterglow Atlas"), albums.albums.map { it.title })
        assertTrue(albums.tracks.isEmpty())
        assertEquals(listOf("Lumen Drift"), source.search("lumen", SearchFilter.ARTISTS).artists.map { it.name })
        assertEquals(listOf("Night Drive"), source.search("night", SearchFilter.PLAYLISTS).playlists.map { it.title })
    }

    @Test
    fun `sample pages open by id and unknown ids are left to other catalogues`() = runTest {
        val page = source.artist("ar-lumen")!!
        assertEquals(3, page.topSongs.size)
        assertEquals(listOf("al-afterglow"), page.releases.map { it.id })
        assertEquals("ar-lumen", source.album("al-afterglow")!!.artistId)
        assertEquals("Night Drive", source.playlist("pl-night")!!.title)
        assertNull(source.artist("UCabcdefghijklmnopqrstuv"))
        assertNull(source.album("OLAK5uy_album"))
        assertNull(source.playlist("PLmix"))
    }

    @Test
    fun `sample radio skips the seed and the queue and ignores real songs`() = runTest {
        val seed = SampleCatalog.tracks[0]
        val picks = source.radio(seed, exclude = setOf(SampleCatalog.tracks[1].id))!!
        assertEquals(SampleCatalog.tracks.drop(2), picks)
        assertNull(source.radio(seed.copy(id = "x7kGuhlXmFI"), emptySet()))
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
