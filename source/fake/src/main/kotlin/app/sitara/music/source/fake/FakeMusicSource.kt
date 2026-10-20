package app.sitara.music.source.fake

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.ArtistPage
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.Shelf
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.BrowseSource
import app.sitara.music.source.api.HealthCheck
import app.sitara.music.source.api.HealthReport
import app.sitara.music.source.api.HealthStatus
import app.sitara.music.source.api.LyricsProvider
import app.sitara.music.source.api.MusicSource
import app.sitara.music.source.api.RadioSource
import app.sitara.music.source.api.ResolvedStream
import app.sitara.music.source.api.StreamResolver
import app.sitara.music.source.lyrics.LrcParser
import kotlinx.coroutines.delay

/** An offline source backed by [SampleCatalog]; it powers Phase A, previews and tests. */
class FakeMusicSource(
    private val latencyMs: Long = 0,
) : MusicSource, BrowseSource, RadioSource, StreamResolver, LyricsProvider, HealthCheck {
    override val id: String = "fake"

    override suspend fun home(): List<Shelf> {
        simulateLatency()
        val tracks = SampleCatalog.tracks
        return listOf(
            Shelf("quick", "Quick picks", tracks = tracks.take(6)),
            Shelf("albums", "New for you", albums = SampleCatalog.albums),
            Shelf("mixes", "Your mixes", playlists = SampleCatalog.playlists),
            Shelf("again", "Listen again", tracks = tracks.reversed().take(6)),
        )
    }

    override suspend fun search(query: String, filter: SearchFilter): SearchResults {
        simulateLatency()
        val q = query.trim()
        val empty = SearchResults.empty(query)
        if (q.isEmpty()) return empty
        return when (filter) {
            SearchFilter.SONGS -> empty.copy(tracks = SampleCatalog.tracks.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) })
            SearchFilter.ALBUMS -> empty.copy(albums = SampleCatalog.albums.filter { it.title.contains(q, true) || it.artist.contains(q, true) })
            SearchFilter.ARTISTS -> empty.copy(artists = SampleCatalog.artists.filter { it.name.contains(q, true) })
            SearchFilter.PLAYLISTS -> empty.copy(playlists = SampleCatalog.playlists.filter { it.title.contains(q, true) })
        }
    }

    override suspend fun artist(id: String): ArtistPage? {
        val artist = SampleCatalog.artists.find { it.id == id } ?: return null
        return ArtistPage(artist, bannerUrl = null, SampleCatalog.tracks.filter { it.artistId == id }, SampleCatalog.albums.filter { it.artistId == id })
    }

    override suspend fun album(id: String): Album? = SampleCatalog.albums.find { it.id == id }

    override suspend fun playlist(id: String): Playlist? = SampleCatalog.playlists.find { it.id == id }

    override suspend fun radio(seed: Track, exclude: Set<String>): List<Track>? {
        if (SampleCatalog.tracks.none { it.id == seed.id }) return null
        return SampleCatalog.tracks.filter { it.id != seed.id && it.id !in exclude }
    }

    override suspend fun library(): Library {
        simulateLatency()
        return Library(
            playlists = SampleCatalog.playlists,
            albums = SampleCatalog.albums,
            artists = SampleCatalog.artists,
            likedTracks = SampleCatalog.tracks.filterIndexed { i, _ -> i % 3 == 0 },
        )
    }

    override suspend fun resolve(track: Track): ResolvedStream {
        val index = SampleCatalog.tracks.indexOfFirst { it.id == track.id }
        require(index >= 0) { "Unknown track ${track.id}" }
        return ResolvedStream(SampleCatalog.demoStreamUrl(index), "audio/mpeg", bitrateKbps = 128)
    }

    override suspend fun lyrics(track: Track): Lyrics? =
        if (SampleCatalog.tracks.any { it.id == track.id }) LrcParser.parse(SampleCatalog.demoLyricsLrc, track.durationMs) else null

    override suspend fun check(): HealthReport = HealthReport(id, HealthStatus.HEALTHY, latencyMs)

    private suspend fun simulateLatency() {
        if (latencyMs > 0) delay(latencyMs)
    }
}
