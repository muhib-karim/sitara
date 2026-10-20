package app.sitara.music.core.model

/**
 * Abstract cover art described by colours instead of an image, so sample content
 * ships without any copyrighted artwork. Colours are packed ARGB ints.
 */
data class Artwork(
    val colors: List<Int>,
    val imageUrl: String? = null,
) {
    init {
        require(colors.isNotEmpty()) { "Artwork needs at least one colour" }
    }
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artwork: Artwork,
    val explicit: Boolean = false,
    /** The artist's page id in the catalogue the track came from, when known. */
    val artistId: String? = null,
)

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int?,
    val artwork: Artwork,
    val tracks: List<Track>,
    val artistId: String? = null,
)

data class Artist(
    val id: String,
    val name: String,
    val artwork: Artwork,
)

/** Everything an artist's page shows; [releases] are album previews whose tracks load on their own page. */
data class ArtistPage(
    val artist: Artist,
    val bannerUrl: String?,
    val topSongs: List<Track>,
    val releases: List<Album>,
)

enum class ListeningMood(val label: String) { FOCUS("Focus"), UNWIND("Unwind"), UPLIFT("Uplift") }

data class Playlist(
    val id: String,
    val title: String,
    val description: String,
    val artwork: Artwork,
    val tracks: List<Track>,
    val moods: Set<ListeningMood> = emptySet(),
)

/** A titled horizontal row on the home screen. */
data class Shelf(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
)

data class SearchResults(
    val query: String,
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()

    companion object {
        fun empty(query: String = "") = SearchResults(query, emptyList(), emptyList(), emptyList())
    }
}

data class Library(
    val playlists: List<Playlist>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val likedTracks: List<Track>,
)
