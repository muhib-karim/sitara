package app.sitara.music

import app.sitara.music.core.model.HomePage
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.UserPlaylist
import app.sitara.music.feature.home.FeedState
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.home.OnlineHome
import app.sitara.music.feature.home.homeFeed
import app.sitara.music.feature.library.DetailState
import app.sitara.music.feature.library.SavedLibrary
import app.sitara.music.source.fake.FakeMusicSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

/**
 * Debug and test fixture: sample ids open at once from the offline catalogue, any other id shows the failed state.
 * The listener's own pages come from [sampleSavedLibrary].
 */
internal class SampleDetailPages(private val source: FakeMusicSource = FakeMusicSource()) : DetailPages {
    override fun state(route: DetailRoute): StateFlow<DetailState> = MutableStateFlow(runBlocking { load(route) })

    override fun retry(route: DetailRoute) = Unit

    private suspend fun load(route: DetailRoute): DetailState = when (route) {
        is DetailRoute.Artist -> source.artist(route.id)?.let(DetailState::ArtistReady)
        is DetailRoute.Album -> source.album(route.id)?.let(DetailState::AlbumReady)
        is DetailRoute.Playlist -> source.playlist(route.id)?.let(DetailState::PlaylistReady)
        DetailRoute.LikedSongs -> DetailState.LikedReady(source.library().likedTracks)
        is DetailRoute.UserPlaylist -> source.library().playlists.getOrNull(route.id.toInt() - 1)
            ?.let { DetailState.UserPlaylistReady(UserPlaylist(route.id, it.title, it.tracks)) } ?: DetailState.Gone
    } ?: DetailState.Failed
}

/** Debug and test fixture: the sample catalogue shaped as a listener's saved library, playlists numbered from 1. */
internal fun sampleSavedLibrary(sample: Library) = SavedLibrary(
    liked = sample.likedTracks,
    recent = sample.playlists.flatMap { it.tracks }.distinctBy { it.id }.take(8),
    playlists = sample.playlists.mapIndexed { index, playlist -> PlaylistSummary(index + 1L, playlist.title, playlist.tracks.size, playlist.artwork) },
    albums = sample.albums,
    artists = sample.artists,
)

/** Debug and test fixture: Home with the sample feed [page] for a listener with [saved] in their library. */
internal fun sampleHomeFeed(page: HomePage, saved: SavedLibrary): HomeFeed =
    homeFeed(OnlineHome(page.chips, FeedState.Ready(page.shelves, page.continuation)), saved.recent, saved.liked, saved.playlists, saved.albums)
