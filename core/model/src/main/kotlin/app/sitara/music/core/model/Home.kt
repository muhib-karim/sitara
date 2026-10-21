package app.sitara.music.core.model

/** One page of the catalogue's Home: mood chips, then shelves; [continuation] fetches the shelves after these, null on the last page. */
data class HomePage(
    val chips: List<MoodChip>,
    val shelves: List<HomeShelf>,
    val continuation: String?,
)

/** A mood or genre filter for Home ("Relax", "Workout"); [params] ask the source for Home filtered to it. */
data class MoodChip(
    val title: String,
    val params: String,
    val selected: Boolean,
)

/** A titled row of Home; [more] is where its "More" leads, when that is a page the app can open. */
data class HomeShelf(
    val title: String,
    val strapline: String?,
    val layout: ShelfLayout,
    val items: List<HomeItem>,
    val more: ShelfLink? = null,
)

/** Songs listed as rows (Quick picks), or a carousel of covers. */
enum class ShelfLayout { SONG_ROWS, CAROUSEL }

sealed interface HomeItem {
    val id: String

    data class Song(val track: Track) : HomeItem {
        override val id: String get() = track.id
    }

    /** An album preview: its songs load on its page. */
    data class AlbumCard(val album: Album) : HomeItem {
        override val id: String get() = album.id
    }

    /** A playlist preview: [Playlist.curator] carries the line the catalogue shows under it, its songs load on its page. */
    data class PlaylistCard(val playlist: Playlist) : HomeItem {
        override val id: String get() = playlist.id
    }

    data class ArtistCard(val artist: Artist) : HomeItem {
        override val id: String get() = artist.id
    }
}

sealed interface ShelfLink {
    data class ToPlaylist(val id: String) : ShelfLink

    data class ToArtist(val id: String) : ShelfLink
}

/** A group of the catalogue's moods and genres ("Moods & moments", "Genres"). */
data class MoodSection(val title: String, val moods: List<Mood>)

/** A mood or genre with a page of its own; [colorArgb] is its tile's colour and [params] ask the source for the page. */
data class Mood(val title: String, val colorArgb: Int, val params: String)
