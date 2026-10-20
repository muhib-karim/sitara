package app.sitara.music.core.data.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Room-backed repositories against an in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SitaraDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var now = 1_000L
    private val clock = { now++ }

    private fun song(n: Int) = Track(
        id = "id$n", title = "Song $n", artist = "Artist", album = "Album", durationMs = 200_000L + n,
        artwork = Artwork(listOf(0xFF9A7BFF.toInt(), n), imageUrl = "https://example.org/$n.jpg"), artistId = "UC$n",
    )

    @After
    fun close() = db.close()

    @Test
    fun `a song survives storage with all its details`() = runTest {
        val liked = RoomLikedSongs(db.likedSongs(), clock)
        val odd = song(1).copy(artwork = Artwork(listOf(-1, 0, Int.MIN_VALUE)), explicit = true, artistId = null)
        liked.setLiked(odd, true)
        assertEquals(listOf(odd), liked.tracks.first())
    }

    @Test
    fun `liked songs are newest first, once each, and unliking removes them`() = runTest {
        val liked = RoomLikedSongs(db.likedSongs(), clock)
        liked.setLiked(song(1), true)
        liked.setLiked(song(2), true)
        liked.setLiked(song(1), true)
        assertEquals(listOf(song(2), song(1)), liked.tracks.first())
        assertEquals(setOf("id1", "id2"), liked.ids.first())

        liked.setLiked(song(2), false)
        assertEquals(listOf(song(1)), liked.tracks.first())
    }

    @Test
    fun `history keeps only the newest plays and lists each song once by its last play`() = runTest {
        val history = RoomPlayHistory(db.playHistory(), clock, limit = 3)
        listOf(1, 2, 3, 2, 4).forEach { history.record(song(it)) }
        // Plays kept: 3, 2, 4. Song 1 fell off the end.
        assertEquals(listOf(song(4), song(2), song(3)), history.recent(10).first())
        assertEquals(listOf(song(4)), history.recent(1).first())
    }

    @Test
    fun `saved albums and artists keep their details and can be removed`() = runTest {
        val saved = RoomSavedCollections(db.saved(), clock)
        val album = Album("OLAK1", "Paper Lanterns", "Orchid Lane", null, Artwork(listOf(1, 2), "https://example.org/a.jpg"), emptyList(), "UC1")
        val older = Album("MPRE2", "Low Tide", "Orchid Lane", 2024, Artwork(listOf(3)), emptyList())
        val artist = Artist("UC1", "Orchid Lane", Artwork(listOf(4), "https://example.org/b.jpg"))
        saved.setSaved(older, true)
        saved.setSaved(album, true)
        saved.setSaved(artist, true)

        assertEquals(listOf(album, older), saved.albums.first())
        assertEquals(setOf("OLAK1", "MPRE2"), saved.albumIds.first())
        assertEquals(listOf(artist), saved.artists.first())
        assertEquals(setOf("UC1"), saved.artistIds.first())

        saved.setSaved(album, false)
        saved.setSaved(artist, false)
        assertEquals(listOf(older), saved.albums.first())
        assertTrue(saved.artistIds.first().isEmpty())
    }

    @Test
    fun `a playlist keeps songs in order, once each`() = runTest {
        val playlists = RoomPlaylists(db.playlists(), clock)
        val id = playlists.create("  Night drive ")
        assertTrue(playlists.add(id, song(1)))
        assertTrue(playlists.add(id, song(2)))
        assertFalse(playlists.add(id, song(1)))

        val playlist = playlists.playlist(id).first()!!
        assertEquals("Night drive", playlist.name)
        assertEquals(listOf(song(1), song(2)), playlist.tracks)
    }

    @Test
    fun `moving and removing entries keeps positions contiguous`() = runTest {
        val playlists = RoomPlaylists(db.playlists(), clock)
        val id = playlists.create("Mix")
        (1..4).forEach { playlists.add(id, song(it)) }

        playlists.move(id, from = 0, to = 2)
        assertEquals(listOf(2, 3, 1, 4).map(::song), playlists.playlist(id).first()!!.tracks)

        playlists.remove(id, "id3")
        playlists.add(id, song(5))
        assertEquals(listOf(2, 1, 4, 5).map(::song), playlists.playlist(id).first()!!.tracks)

        playlists.move(id, from = 3, to = 0)
        playlists.move(id, from = 0, to = 9)
        assertEquals(listOf(5, 2, 1, 4).map(::song), playlists.playlist(id).first()!!.tracks)
    }

    @Test
    fun `the library lists playlists by last change with count and first cover`() = runTest {
        val playlists = RoomPlaylists(db.playlists(), clock)
        val empty = playlists.create("Empty")
        val mix = playlists.create("Mix")
        playlists.add(mix, song(1))
        playlists.add(mix, song(2))

        assertEquals(
            listOf(PlaylistSummary(mix, "Mix", 2, song(1).artwork), PlaylistSummary(empty, "Empty", 0, null)),
            playlists.all.first(),
        )

        playlists.rename(empty, "Renamed")
        assertEquals("Renamed", playlists.all.first().first().name)
    }

    @Test
    fun `deleting a playlist deletes its entries`() = runTest {
        val playlists = RoomPlaylists(db.playlists(), clock)
        val id = playlists.create("Gone")
        playlists.add(id, song(1))
        playlists.delete(id)

        assertNull(playlists.playlist(id).first())
        assertTrue(playlists.all.first().isEmpty())
        assertEquals(0, db.query("SELECT * FROM playlist_entries", null).use { it.count })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a playlist needs a name`() = runTest {
        RoomPlaylists(db.playlists(), clock).create("   ")
    }
}
