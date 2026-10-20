package app.sitara.music

import app.sitara.music.feature.library.DetailState
import app.sitara.music.source.fake.FakeMusicSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

/** Debug and test fixture: sample ids open at once from the offline catalogue, any other id shows the failed state. */
internal class SampleDetailPages(private val source: FakeMusicSource = FakeMusicSource()) : DetailPages {
    override fun state(route: DetailRoute): StateFlow<DetailState> = MutableStateFlow(runBlocking { load(route) })

    override fun retry(route: DetailRoute) = Unit

    private suspend fun load(route: DetailRoute): DetailState = when (route) {
        is DetailRoute.Artist -> source.artist(route.id)?.let(DetailState::ArtistReady)
        is DetailRoute.Album -> source.album(route.id)?.let(DetailState::AlbumReady)
        is DetailRoute.Playlist -> source.playlist(route.id)?.let(DetailState::PlaylistReady)
    } ?: DetailState.Failed
}
