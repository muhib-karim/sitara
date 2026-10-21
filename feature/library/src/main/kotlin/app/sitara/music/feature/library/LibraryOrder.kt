package app.sitara.music.feature.library

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.LibrarySort
import app.sitara.music.core.model.PlayCount
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.Track
import java.text.Collator

/** What Library lists below its pinned tiles; one kind at a time. */
enum class LibraryKind(val label: String) {
    PLAYLISTS("Playlists"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    DOWNLOADS("Downloads"),
}

/** The orders this kind can follow: plays are known per song and per artist, never per playlist or album, and downloads keep the queue's order. */
val LibraryKind.sorts: List<LibrarySort>
    get() = when (this) {
        LibraryKind.SONGS, LibraryKind.ARTISTS -> LibrarySort.entries
        LibraryKind.PLAYLISTS, LibraryKind.ALBUMS -> listOf(LibrarySort.RECENTLY_ADDED, LibrarySort.A_TO_Z)
        LibraryKind.DOWNLOADS -> emptyList()
    }

/** The listener's chosen order where this kind offers it, otherwise newest first. */
fun LibraryKind.sortFor(chosen: LibrarySort): LibrarySort = chosen.takeIf { it in sorts } ?: LibrarySort.RECENTLY_ADDED

// A to Z the way the listener's language sorts it, so case and accents do not split the list.
private fun <T> byName(name: (T) -> String): Comparator<T> = compareBy(Collator.getInstance(), name)

/** Liked songs, which come newest first. Songs never played keep that order after the played ones. */
fun List<Track>.inOrder(sort: LibrarySort, counts: List<PlayCount>): List<Track> {
    val byId = counts.associateBy { it.trackId }
    return when (sort) {
        LibrarySort.RECENTLY_ADDED -> this
        LibrarySort.RECENTLY_PLAYED -> sortedByDescending { byId[it.id]?.lastPlayedAt ?: Long.MIN_VALUE }
        LibrarySort.A_TO_Z -> sortedWith(byName { it.title })
        LibrarySort.MOST_PLAYED -> sortedByDescending { byId[it.id]?.plays ?: 0 }
    }
}

/** Saved artists, which come newest first, by the plays of all their songs. */
@JvmName("artistsInOrder")
fun List<Artist>.inOrder(sort: LibrarySort, counts: List<PlayCount>): List<Artist> {
    val byArtist = counts.groupBy { it.artistId }
    return when (sort) {
        LibrarySort.RECENTLY_ADDED -> this
        LibrarySort.RECENTLY_PLAYED -> sortedByDescending { artist -> byArtist[artist.id]?.maxOf { it.lastPlayedAt } ?: Long.MIN_VALUE }
        LibrarySort.A_TO_Z -> sortedWith(byName { it.name })
        LibrarySort.MOST_PLAYED -> sortedByDescending { artist -> byArtist[artist.id]?.sumOf { it.plays } ?: 0 }
    }
}

/** Playlists: newest made first (ids grow as playlists are made) or by name; plays are not kept per playlist. */
@JvmName("playlistsInOrder")
fun List<PlaylistSummary>.inOrder(sort: LibrarySort): List<PlaylistSummary> =
    if (sort == LibrarySort.A_TO_Z) sortedWith(byName { it.name }) else sortedByDescending { it.id }

/** Saved albums, which come newest first, or by title. */
@JvmName("albumsInOrder")
fun List<Album>.inOrder(sort: LibrarySort): List<Album> = if (sort == LibrarySort.A_TO_Z) sortedWith(byName { it.title }) else this
