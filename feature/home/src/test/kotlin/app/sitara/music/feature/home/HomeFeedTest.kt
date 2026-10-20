package app.sitara.music.feature.home

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedTest {
    private val art = Artwork(listOf(0xFF101010.toInt()))
    private val tracks = (0..5).map { Track("$it", "Song $it", "Artist", "Album", 1_000, art) }

    @Test fun pagesKeepOrderAndIncludeThePartialLastPage() {
        assertEquals(listOf(tracks.take(4), tracks.drop(4)), quickPickPages(tracks))
        assertTrue(quickPickPages(emptyList()).isEmpty())
    }

    @Test fun fromYourLikesLeavesOutSongsAlreadyInRecentlyPlayed() {
        val feed = homeFeed(TrendingState.Loading, recent = tracks.take(2), liked = listOf(tracks[1], tracks[3], tracks[0], tracks[4]), playlists = emptyList(), albums = emptyList())

        assertEquals(tracks.take(2), feed.recent)
        assertEquals(listOf(tracks[3], tracks[4]), feed.fromLikes)
    }

    @Test fun jumpBackInListsPlaylistsWithSongsThenSavedAlbums() {
        val road = PlaylistSummary(1, "Road", songCount = 3, artwork = art)
        val draft = PlaylistSummary(2, "Draft", songCount = 0, artwork = null)
        val album = Album("al", "Album", "Artist", null, art, emptyList())

        val feed = homeFeed(TrendingState.Loading, emptyList(), emptyList(), playlists = listOf(road, draft), albums = listOf(album))

        assertEquals(listOf(SavedCollection.OwnPlaylist(road), SavedCollection.SavedAlbum(album)), feed.jumpBackIn)
    }

    @Test fun shelvesAreCapped() {
        val many = (0 until 40).map { Track("m$it", "Song $it", "Artist", "", 1_000, art) }

        val feed = homeFeed(TrendingState.Loading, recent = many.take(30), liked = many.drop(10), playlists = emptyList(), albums = emptyList())

        assertEquals(SHELF_SIZE, feed.recent.size)
        assertEquals(many.drop(20), feed.fromLikes)
    }

    @Test fun onlyABuiltFeedHasReadTheLibrary() {
        assertFalse(HomeFeed().libraryLoaded)
        assertTrue(homeFeed(TrendingState.Loading, emptyList(), emptyList(), emptyList(), emptyList()).libraryLoaded)
    }

    @Test fun aFreshInstallHasNoLibraryShelves() {
        assertFalse(homeFeed(TrendingState.Failed, emptyList(), emptyList(), emptyList(), emptyList()).hasLibrary)
        assertTrue(homeFeed(TrendingState.Failed, emptyList(), liked = tracks.take(1), emptyList(), emptyList()).hasLibrary)
    }
}
