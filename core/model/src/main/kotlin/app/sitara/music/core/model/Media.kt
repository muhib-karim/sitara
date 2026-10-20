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

data class Playlist(
    val id: String,
    val title: String,
    val description: String,
    val artwork: Artwork,
    val tracks: List<Track>,
    /** Who made the playlist, when the source says. */
    val curator: String = "",
)

/** A ranked list of songs, such as a country's trending chart; [label] names the chart and where it is from. */
data class Chart(
    val label: String,
    val tracks: List<Track>,
)

/** What a search looks for. */
enum class SearchFilter(val label: String) { SONGS("Songs"), ALBUMS("Albums"), ARTISTS("Artists"), PLAYLISTS("Playlists") }

data class SearchResults(
    val query: String,
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val playlists: List<Playlist> = emptyList(),
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()

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

/** A playlist the listener made on this device, as the library lists it; [artwork] is its first song's. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val songCount: Int,
    val artwork: Artwork?,
)

/** A listener's own playlist with its songs in order. */
data class UserPlaylist(
    val id: Long,
    val name: String,
    val tracks: List<Track>,
)
