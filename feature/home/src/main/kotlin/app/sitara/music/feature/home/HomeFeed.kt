package app.sitara.music.feature.home

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Chart
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.Track

internal const val SHELF_SIZE = 20

/** The trending chart, which stands in for the feed when the feed does not load. */
sealed interface TrendingState {
    data object Loading : TrendingState

    data object Failed : TrendingState

    data class Ready(val chart: Chart) : TrendingState
}

/** Something saved to go back to: one of the listener's playlists, or an album they saved. */
sealed interface SavedCollection {
    data class OwnPlaylist(val playlist: PlaylistSummary) : SavedCollection

    data class SavedAlbum(val album: Album) : SavedCollection
}

/** Everything Home shows: the catalogue's feed (or the chart in its place), then shelves read from the listener's own library. */
data class HomeFeed(
    val online: OnlineHome = OnlineHome(),
    val recent: List<Track> = emptyList(),
    val jumpBackIn: List<SavedCollection> = emptyList(),
    val fromLikes: List<Track> = emptyList(),
    /** False until the library has been read, so a full library never flashes the fresh-install hint. */
    val libraryLoaded: Boolean = false,
) {
    val hasLibrary: Boolean get() = recent.isNotEmpty() || jumpBackIn.isNotEmpty() || fromLikes.isNotEmpty()
}

/**
 * Builds Home from the online part and the library. "Your likes" leaves out songs already in Listen again, so the
 * two shelves never repeat each other; empty playlists have nothing to jump back into.
 */
fun homeFeed(online: OnlineHome, recent: List<Track>, liked: List<Track>, playlists: List<PlaylistSummary>, albums: List<Album>): HomeFeed {
    val recentShelf = recent.take(SHELF_SIZE)
    val shown = recentShelf.mapTo(HashSet()) { it.id }
    return HomeFeed(
        online = online,
        recent = recentShelf,
        jumpBackIn = (playlists.filter { it.songCount > 0 }.map(SavedCollection::OwnPlaylist) + albums.map(SavedCollection::SavedAlbum)).take(SHELF_SIZE),
        fromLikes = liked.filter { it.id !in shown }.take(SHELF_SIZE),
        libraryLoaded = true,
    )
}

internal fun quickPickPages(tracks: List<Track>): List<List<Track>> = tracks.chunked(4)
