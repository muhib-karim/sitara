package app.sitara.music.feature.library

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.LibrarySort
import app.sitara.music.core.model.PlayCount
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrderTest {
    private val art = Artwork(listOf(0))
    private fun song(id: String, title: String, artistId: String) = Track(id, title, "Artist", "Album", 0, art, artistId = artistId)

    // Liked newest first: Zephyr, then apple, then Moth.
    private val liked = listOf(song("z", "Zephyr", "UCa"), song("a", "apple", "UCb"), song("m", "Moth", "UCa"))
    private val counts = listOf(
        PlayCount("m", "UCa", lastPlayedAt = 300, plays = 1),
        PlayCount("a", "UCb", lastPlayedAt = 200, plays = 5),
        PlayCount("x", "UCb", lastPlayedAt = 100, plays = 1),
    )

    @Test
    fun songsCanBeOrderedEachOfTheFourWays() {
        assertEquals(listOf("z", "a", "m"), liked.inOrder(LibrarySort.RECENTLY_ADDED, counts).map { it.id })
        assertEquals(listOf("m", "a", "z"), liked.inOrder(LibrarySort.RECENTLY_PLAYED, counts).map { it.id })
        assertEquals(listOf("a", "m", "z"), liked.inOrder(LibrarySort.A_TO_Z, counts).map { it.id })
        assertEquals(listOf("a", "m", "z"), liked.inOrder(LibrarySort.MOST_PLAYED, counts).map { it.id })
    }

    @Test
    fun songsNeverPlayedKeepTheirOrderAfterThePlayedOnes() {
        val unplayed = listOf(song("p", "P", "UCc"), song("q", "Q", "UCc"))

        assertEquals(listOf("m", "p", "q"), (unplayed + liked.last()).inOrder(LibrarySort.RECENTLY_PLAYED, counts).map { it.id })
        assertEquals(listOf("m", "p", "q"), (unplayed + liked.last()).inOrder(LibrarySort.MOST_PLAYED, counts).map { it.id })
    }

    @Test
    fun artistsGoByThePlaysOfAllTheirSongs() {
        // UCb: 6 plays, last at 200. UCa: 1 play, last at 300. UCc: none.
        val artists = listOf(Artist("UCc", "Calm", art), Artist("UCa", "Aurora", art), Artist("UCb", "birch", art))

        assertEquals(listOf("UCc", "UCa", "UCb"), artists.inOrder(LibrarySort.RECENTLY_ADDED, counts).map { it.id })
        assertEquals(listOf("UCa", "UCb", "UCc"), artists.inOrder(LibrarySort.RECENTLY_PLAYED, counts).map { it.id })
        assertEquals(listOf("UCa", "UCb", "UCc"), artists.inOrder(LibrarySort.A_TO_Z, counts).map { it.id })
        assertEquals(listOf("UCb", "UCa", "UCc"), artists.inOrder(LibrarySort.MOST_PLAYED, counts).map { it.id })
    }

    @Test
    fun playlistsAreNewestMadeFirstOrByName() {
        // Listed as the store gives them: last changed first.
        val playlists = listOf(PlaylistSummary(1, "Road", 3, emptyList()), PlaylistSummary(3, "autumn", 1, emptyList()), PlaylistSummary(2, "Zoo", 2, emptyList()))

        assertEquals(listOf(3L, 2L, 1L), playlists.inOrder(LibrarySort.RECENTLY_ADDED).map { it.id })
        assertEquals(listOf(3L, 1L, 2L), playlists.inOrder(LibrarySort.A_TO_Z).map { it.id })
    }

    @Test
    fun albumsAreNewestSavedFirstOrByTitle() {
        val albums = listOf(Album("1", "Tides", "A", null, art, emptyList()), Album("2", "echoes", "B", null, art, emptyList()))

        assertEquals(listOf("1", "2"), albums.inOrder(LibrarySort.RECENTLY_ADDED).map { it.id })
        assertEquals(listOf("2", "1"), albums.inOrder(LibrarySort.A_TO_Z).map { it.id })
    }

    @Test
    fun eachKindOffersOnlyTheOrdersItCanFollowAndFallsBackToRecentlyAdded() {
        assertEquals(LibrarySort.entries, LibraryKind.SONGS.sorts)
        assertEquals(LibrarySort.entries, LibraryKind.ARTISTS.sorts)
        assertEquals(listOf(LibrarySort.RECENTLY_ADDED, LibrarySort.A_TO_Z), LibraryKind.PLAYLISTS.sorts)
        assertEquals(listOf(LibrarySort.RECENTLY_ADDED, LibrarySort.A_TO_Z), LibraryKind.ALBUMS.sorts)
        assertEquals(emptyList<LibrarySort>(), LibraryKind.DOWNLOADS.sorts)
        assertEquals(LibrarySort.RECENTLY_ADDED, LibraryKind.PLAYLISTS.sortFor(LibrarySort.MOST_PLAYED))
        assertEquals(LibrarySort.MOST_PLAYED, LibraryKind.SONGS.sortFor(LibrarySort.MOST_PLAYED))
    }
}
