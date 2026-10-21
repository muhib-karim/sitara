package app.sitara.music.source.fake

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.ArtistPage
import app.sitara.music.core.model.Chart
import app.sitara.music.core.model.HomeItem
import app.sitara.music.core.model.HomePage
import app.sitara.music.core.model.HomeShelf
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.Mood
import app.sitara.music.core.model.MoodChip
import app.sitara.music.core.model.MoodSection
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.ShelfLayout
import app.sitara.music.core.model.ShelfLink
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.BrowseSource
import app.sitara.music.source.api.ChartsSource
import app.sitara.music.source.api.HealthCheck
import app.sitara.music.source.api.HealthReport
import app.sitara.music.source.api.HealthStatus
import app.sitara.music.source.api.HomeFeedSource
import app.sitara.music.source.api.LyricsProvider
import app.sitara.music.source.api.MoodsSource
import app.sitara.music.source.api.MusicSource
import app.sitara.music.source.api.RadioSource
import app.sitara.music.source.api.ResolvedStream
import app.sitara.music.source.api.SearchSuggestionSource
import app.sitara.music.source.api.StreamResolver
import app.sitara.music.source.lyrics.LrcParser
import kotlinx.coroutines.delay

/** An offline source backed by [SampleCatalog], for tests, screenshot fixtures and debug capture screens only. */
class FakeMusicSource(
    private val latencyMs: Long = 0,
) : MusicSource, SearchSuggestionSource, ChartsSource, HomeFeedSource, MoodsSource, BrowseSource, RadioSource, StreamResolver, LyricsProvider, HealthCheck {
    override val id: String = "fake"
    override val name: String = "Sample lyrics"

    override suspend fun trending(): Chart {
        simulateLatency()
        return Chart("Sample chart", SampleCatalog.tracks)
    }

    /** A Home shaped like YouTube Music's: mood chips, Quick picks, then albums, playlists and artists; a mood keeps the playlists. */
    override suspend fun home(chip: MoodChip?): HomePage {
        simulateLatency()
        val playlists = HomeShelf(
            "Mixed for you", null, ShelfLayout.CAROUSEL, SampleCatalog.playlists.map(HomeItem::PlaylistCard), ShelfLink.ToPlaylist(SampleCatalog.playlists.first().id),
        )
        val shelves = if (chip != null) {
            listOf(playlists.copy(title = "${chip.title} mixes"))
        } else {
            listOf(
                HomeShelf("Quick picks", null, ShelfLayout.SONG_ROWS, SampleCatalog.tracks.map(HomeItem::Song)),
                HomeShelf("New releases", "Fresh this week", ShelfLayout.CAROUSEL, SampleCatalog.albums.map(HomeItem::AlbumCard)),
                playlists,
                HomeShelf("Artists you might like", null, ShelfLayout.CAROUSEL, SampleCatalog.artists.map(HomeItem::ArtistCard)),
            )
        }
        return HomePage(SAMPLE_MOODS.map { MoodChip(it, "sample:$it", selected = it == chip?.title) }, shelves, continuation = null)
    }

    override suspend fun moreHome(continuation: String): HomePage = HomePage(emptyList(), emptyList(), continuation = null)

    /** Titles and artists that start with the typed text, as a catalogue completes a half-typed search. */
    override suspend fun suggestions(query: String): List<String> {
        simulateLatency()
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return (SampleCatalog.tracks.map { it.title } + SampleCatalog.artists.map { it.name }).distinct().filter { it.startsWith(q, ignoreCase = true) }
    }

    override suspend fun moods(): List<MoodSection> {
        simulateLatency()
        return listOf(MoodSection("Moods & moments", SAMPLE_MOOD_TILES.take(4)), MoodSection("Genres", SAMPLE_MOOD_TILES.drop(4)))
    }

    override suspend fun moodPage(params: String): HomePage {
        val mood = SAMPLE_MOOD_TILES.first { it.params == params }
        return home(MoodChip(mood.title, params, selected = true)).copy(chips = emptyList())
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

private val SAMPLE_MOODS = listOf("Relax", "Energise", "Workout", "Focus", "Commute", "Sleep")

private val SAMPLE_MOOD_TILES = listOf(
    "Chill" to 0xFF7FA8D9, "Energise" to 0xFFE8A33D, "Focus" to 0xFF6FBF8E, "Romance" to 0xFFD9668B,
    "Ambient" to 0xFF8C7FD9, "Jazz" to 0xFFC7894B, "Electronic" to 0xFF4BB8C7, "Classical" to 0xFFB5A6CF,
).map { (title, color) -> Mood(title, color.toInt(), "sample:mood:$title") }
