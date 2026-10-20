package app.sitara.music.source.fake

import app.sitara.music.core.model.Library
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.Shelf
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.HealthCheck
import app.sitara.music.source.api.HealthReport
import app.sitara.music.source.api.HealthStatus
import app.sitara.music.source.api.LyricsProvider
import app.sitara.music.source.api.MusicSource
import app.sitara.music.source.api.ResolvedStream
import app.sitara.music.source.api.StreamResolver
import app.sitara.music.source.lyrics.LrcParser
import kotlinx.coroutines.delay

/** An offline source backed by [SampleCatalog]; it powers Phase A, previews and tests. */
class FakeMusicSource(
    private val latencyMs: Long = 0,
) : MusicSource, StreamResolver, LyricsProvider, HealthCheck {
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

    override suspend fun search(query: String): SearchResults {
        simulateLatency()
        val q = query.trim()
        if (q.isEmpty()) return SearchResults.empty(query)
        return SearchResults(
            query = query,
            tracks = SampleCatalog.tracks.filter { it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) },
            albums = SampleCatalog.albums.filter { it.title.contains(q, true) || it.artist.contains(q, true) },
            artists = SampleCatalog.artists.filter { it.name.contains(q, true) },
        )
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
