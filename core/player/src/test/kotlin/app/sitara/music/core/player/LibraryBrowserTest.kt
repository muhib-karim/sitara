package app.sitara.music.core.player

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.Track
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBrowserTest {
    private fun song(n: Int) = Track("id$n", "Song $n", "Artist $n", "Album", 200_000, Artwork(listOf(0), "https://i.example.org/$n.jpg"))

    private val songs = (1..3).map(::song)

    private class Content(
        var liked: List<Track> = emptyList(),
        val recent: List<Track> = emptyList(),
        val downloaded: List<Track> = emptyList(),
        val playlists: Map<PlaylistSummary, List<Track>> = emptyMap(),
        val albums: List<Album> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val online: Boolean = true,
    ) : LibraryContent {
        val searches = mutableListOf<String>()

        override suspend fun recentlyPlayed() = recent

        override suspend fun likedSongs() = liked

        override suspend fun downloadedSongs() = downloaded

        override suspend fun playlists() = playlists.keys.toList()

        override suspend fun playlistSongs(id: Long) = playlists.entries.firstOrNull { it.key.id == id }?.value.orEmpty()

        override suspend fun savedAlbums() = albums

        override suspend fun savedArtists() = artists

        override suspend fun albumSongs(id: String) = catalogue { albums.first { it.id == id }.tracks }

        override suspend fun artistSongs(id: String) = catalogue { listOf(Track("top", "Top song", id, "", 1, Artwork(listOf(0)))) }

        override suspend fun searchSongs(query: String) = catalogue {
            searches += query
            listOf(Track("hit", "$query hit", "Artist", "", 1, Artwork(listOf(0))))
        }

        private fun <T> catalogue(answer: () -> T): T = if (online) answer() else throw IOException("offline")
    }

    @Test
    fun `every place in the tree round-trips through its media id`() {
        val ids = listOf(
            BrowseId.Root, BrowseId.Recent, BrowseId.Liked, BrowseId.Downloads, BrowseId.Library, BrowseId.Playlists, BrowseId.Albums, BrowseId.Artists,
            BrowseId.Playlist(42), BrowseId.Album("MPREb_a/b|c"), BrowseId.Artist("UC x"), BrowseId.Search("lo-fi / beats?"),
            BrowseId.Song(BrowseId.Liked, "x7kGuhlXmFI"), BrowseId.Song(BrowseId.Playlist(7), "a/b"), BrowseId.Song(BrowseId.Search("a/b c"), "id%2F"),
        )
        ids.forEach { assertEquals(it, BrowseId.parse(it.mediaId)) }
        assertEquals("media ids are distinct", ids.size, ids.map { it.mediaId }.toSet().size)
    }

    @Test
    fun `media ids that name no place are refused`() {
        listOf("", "nowhere", "playlist/abc", "song/liked", "song/root/id1", "song/${BrowseId.Song(BrowseId.Liked, "a").mediaId}/b", "album/").forEach {
            assertNull(it, BrowseId.parse(it))
        }
    }

    @Test
    fun `the root holds four tabs, the library its playlists, albums and artists`() = runTest {
        val browser = LibraryBrowser(Content())
        assertEquals(
            listOf("Recently played", "Liked songs", "Downloads", "Library"),
            browser.children(BrowseId.Root)!!.map { it.title },
        )
        assertEquals(listOf(BrowseId.Playlists, BrowseId.Albums, BrowseId.Artists), browser.children(BrowseId.Library)!!.map { it.id })
        assertTrue("folders are not playable", browser.children(BrowseId.Root)!!.none { it.playable })
    }

    @Test
    fun `song lists hold playable songs with their artist and cover`() = runTest {
        val browser = LibraryBrowser(Content(liked = songs, recent = songs.reversed(), downloaded = songs.take(1)))
        val liked = browser.children(BrowseId.Liked)!!
        assertEquals(songs.map { BrowseId.Song(BrowseId.Liked, it.id) }, liked.map { it.id })
        assertEquals("Song 1", liked[0].title)
        assertEquals("Artist 1", liked[0].subtitle)
        assertEquals("https://i.example.org/1.jpg", liked[0].artworkUrl)
        assertTrue(liked.all { it.playable })
        assertEquals(listOf("id3", "id2", "id1"), browser.children(BrowseId.Recent)!!.map { (it.id as BrowseId.Song).trackId })
        assertEquals(listOf("id1"), browser.children(BrowseId.Downloads)!!.map { (it.id as BrowseId.Song).trackId })
    }

    @Test
    fun `playlists, saved albums and artists open on their songs`() = runTest {
        val mix = PlaylistSummary(7, "Road trip", 2, listOf(Artwork(listOf(0), "https://i.example.org/mix.jpg")))
        val album = Album("MPREb_1", "First album", "Artist 1", 2020, Artwork(listOf(0), "https://i.example.org/a.jpg"), songs)
        val artist = Artist("UC1", "Artist 1", Artwork(listOf(0), "https://i.example.org/artist.jpg"))
        val browser = LibraryBrowser(Content(playlists = mapOf(mix to songs.take(2)), albums = listOf(album), artists = listOf(artist)))

        val playlists = browser.children(BrowseId.Playlists)!!
        assertEquals(listOf(BrowseId.Playlist(7)), playlists.map { it.id })
        assertEquals("Road trip", playlists[0].title)
        assertEquals("2 songs", playlists[0].subtitle)
        assertEquals("https://i.example.org/mix.jpg", playlists[0].artworkUrl)
        assertEquals(listOf("id1", "id2"), browser.children(BrowseId.Playlist(7))!!.map { (it.id as BrowseId.Song).trackId })

        assertEquals(listOf(BrowseId.Album("MPREb_1")), browser.children(BrowseId.Albums)!!.map { it.id })
        assertEquals("Artist 1", browser.children(BrowseId.Albums)!![0].subtitle)
        assertEquals(3, browser.children(BrowseId.Album("MPREb_1"))!!.size)
        assertEquals(listOf(BrowseId.Artist("UC1")), browser.children(BrowseId.Artists)!!.map { it.id })
        assertEquals(listOf("top"), browser.children(BrowseId.Artist("UC1"))!!.map { (it.id as BrowseId.Song).trackId })
    }

    @Test
    fun `a song has no children and an item is found under its parent`() = runTest {
        val browser = LibraryBrowser(Content(liked = songs))
        assertNull(browser.children(BrowseId.Song(BrowseId.Liked, "id1")))
        assertEquals("Liked songs", browser.item(BrowseId.Liked)?.title)
        assertEquals("Albums", browser.item(BrowseId.Albums)?.title)
        assertEquals("Song 2", browser.item(BrowseId.Song(BrowseId.Liked, "id2"))?.title)
        assertNull("a song no longer liked is gone", browser.item(BrowseId.Song(BrowseId.Liked, "id9")))
        assertEquals(BrowseId.Root, browser.item(BrowseId.Root)?.id)
    }

    @Test
    fun `playing a song queues its list from that song`() = runTest {
        val content = Content(liked = songs)
        val browser = LibraryBrowser(content)
        assertEquals(songs to 1, browser.queueFor(BrowseId.Song(BrowseId.Liked, "id2")))
        content.liked = songs.drop(1)
        assertEquals("the list is read again when it changed", songs.drop(1) to 0, browser.queueFor(BrowseId.Song(BrowseId.Liked, "id2")))
        assertNull(browser.queueFor(BrowseId.Song(BrowseId.Liked, "id1")))
    }

    @Test
    fun `search finds songs and asks the catalogue once per query`() = runTest {
        val content = Content()
        val browser = LibraryBrowser(content)
        val results = browser.search("lo-fi")
        assertEquals(listOf(BrowseId.Song(BrowseId.Search("lo-fi"), "hit")), results.map { it.id })
        assertEquals(listOf(Track("hit", "lo-fi hit", "Artist", "", 1, Artwork(listOf(0)))) to 0, browser.queueFor(results[0].id as BrowseId.Song))
        browser.search("lo-fi")
        assertEquals(listOf("lo-fi"), content.searches)
        assertTrue("nothing to look for", browser.search("  ").isEmpty())
    }

    @Test
    fun `offline, search answers empty while catalogue pages report the failure`() = runTest {
        val album = Album("MPREb_1", "First album", "Artist 1", 2020, Artwork(listOf(0)), songs)
        val browser = LibraryBrowser(Content(liked = songs, albums = listOf(album), online = false))
        assertTrue(browser.search("lo-fi").isEmpty())
        assertEquals("the library still opens", 3, browser.children(BrowseId.Liked)!!.size)
        val failure = runCatching { browser.children(BrowseId.Album("MPREb_1")) }.exceptionOrNull()
        assertTrue(failure is IOException)
    }

    @Test
    fun `asked to play by voice, a query plays its search results and no query plays the liked songs`() = runTest {
        val browser = LibraryBrowser(Content(liked = songs))
        assertEquals(listOf("hit"), browser.voiceQueue("anything").map { it.id })
        assertEquals(songs, browser.voiceQueue(""))
        assertTrue(LibraryBrowser(Content(online = false)).voiceQueue("anything").isEmpty())
    }

    @Test
    fun `pages split a list and pages past its end are empty`() {
        val list = (1..5).toList()
        assertEquals(listOf(1, 2), list.page(0, 2))
        assertEquals(listOf(5), list.page(2, 2))
        assertEquals(emptyList<Int>(), list.page(3, 2))
        assertEquals("a browser that asks for everything", list, list.page(0, Int.MAX_VALUE))
        assertEquals(emptyList<Int>(), list.page(1, Int.MAX_VALUE))
    }
}
