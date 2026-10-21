package app.sitara.music.core.player

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.Track
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException

/** A place in the library a car (or another media browser) can open; [mediaId] is how it travels. */
sealed interface BrowseId {
    val mediaId: String

    /** Places whose children are songs; a [Song] belongs to one of them. */
    sealed interface SongList : BrowseId

    data object Root : BrowseId {
        override val mediaId = "root"
    }

    data object Recent : SongList {
        override val mediaId = "recent"
    }

    data object Liked : SongList {
        override val mediaId = "liked"
    }

    data object Downloads : SongList {
        override val mediaId = "downloads"
    }

    data object Library : BrowseId {
        override val mediaId = "library"
    }

    data object Playlists : BrowseId {
        override val mediaId = "playlists"
    }

    data object Albums : BrowseId {
        override val mediaId = "albums"
    }

    data object Artists : BrowseId {
        override val mediaId = "artists"
    }

    data class Playlist(val id: Long) : SongList {
        override val mediaId get() = "playlist/$id"
    }

    data class Album(val id: String) : SongList {
        override val mediaId get() = "album/${escape(id)}"
    }

    data class Artist(val id: String) : SongList {
        override val mediaId get() = "artist/${escape(id)}"
    }

    data class Search(val query: String) : SongList {
        override val mediaId get() = "search/${escape(query)}"
    }

    /** A song as listed in [parent]; playing it queues that list from this song. */
    data class Song(val parent: SongList, val trackId: String) : BrowseId {
        override val mediaId get() = "song/${escape(parent.mediaId)}/${escape(trackId)}"
    }

    companion object {
        private val fixed = listOf(Root, Recent, Liked, Downloads, Library, Playlists, Albums, Artists).associateBy { it.mediaId }

        /** The place [mediaId] names, or null for anything this app did not hand out. */
        fun parse(mediaId: String): BrowseId? {
            fixed[mediaId]?.let { return it }
            val parts = mediaId.split('/')
            val value = parts.getOrNull(1)?.let(::unescape)?.takeIf { it.isNotEmpty() } ?: return null
            return when {
                parts.size == 2 && parts[0] == "playlist" -> value.toLongOrNull()?.let(::Playlist)
                parts.size == 2 && parts[0] == "album" -> Album(value)
                parts.size == 2 && parts[0] == "artist" -> Artist(value)
                parts.size == 2 && parts[0] == "search" -> Search(value)
                parts.size == 3 && parts[0] == "song" -> {
                    val parent = parse(value) as? SongList ?: return null
                    unescape(parts[2])?.takeIf { it.isNotEmpty() }?.let { Song(parent, it) }
                }
                else -> null
            }
        }

        // Escaped, a segment never holds the '/' that separates them.
        private fun escape(segment: String): String = URLEncoder.encode(segment, "UTF-8")

        private fun unescape(segment: String): String? = try {
            URLDecoder.decode(segment, "UTF-8")
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** One entry of a browse list: a folder, a playlist, an album, an artist or a playable song. */
data class BrowseNode(val id: BrowseId, val title: String, val subtitle: String? = null, val artworkUrl: String? = null) {
    val playable: Boolean get() = id is BrowseId.Song
}

/** What the browse tree reads. Library reads come from the phone; album, artist and search answers need the catalogue and throw without it. */
interface LibraryContent {
    suspend fun recentlyPlayed(): List<Track>

    suspend fun likedSongs(): List<Track>

    /** Songs downloaded in full, which play with no network. */
    suspend fun downloadedSongs(): List<Track>

    suspend fun playlists(): List<PlaylistSummary>

    suspend fun playlistSongs(id: Long): List<Track>

    suspend fun savedAlbums(): List<Album>

    suspend fun savedArtists(): List<Artist>

    suspend fun albumSongs(id: String): List<Track>

    suspend fun artistSongs(id: String): List<Track>

    suspend fun searchSongs(query: String): List<Track>
}

/**
 * The library as a tree for a car: four tabs (Recently played, Liked songs, Downloads, Library, since cars show at most
 * four), and Library's playlists, saved albums and saved artists. Call from one thread.
 */
class LibraryBrowser(private val content: LibraryContent) {
    // The song list served last, so a song picked from it is queued without asking again.
    private var lastList: Pair<BrowseId.SongList, List<Track>>? = null

    val root = BrowseNode(BrowseId.Root, "Sitara")

    /** What [parent] holds, or null when it holds nothing (a song). */
    suspend fun children(parent: BrowseId): List<BrowseNode>? = when (parent) {
        BrowseId.Root -> listOf(
            BrowseNode(BrowseId.Recent, "Recently played"),
            BrowseNode(BrowseId.Liked, "Liked songs"),
            BrowseNode(BrowseId.Downloads, "Downloads", "Play with no connection"),
            BrowseNode(BrowseId.Library, "Library"),
        )
        BrowseId.Library -> listOf(BrowseNode(BrowseId.Playlists, "Playlists"), BrowseNode(BrowseId.Albums, "Albums"), BrowseNode(BrowseId.Artists, "Artists"))
        BrowseId.Playlists -> content.playlists().map { BrowseNode(BrowseId.Playlist(it.id), it.name, if (it.songCount == 1) "1 song" else "${it.songCount} songs", it.artwork?.imageUrl) }
        BrowseId.Albums -> content.savedAlbums().map { BrowseNode(BrowseId.Album(it.id), it.title, it.artist, it.artwork.imageUrl) }
        BrowseId.Artists -> content.savedArtists().map { BrowseNode(BrowseId.Artist(it.id), it.name, artworkUrl = it.artwork.imageUrl) }
        is BrowseId.SongList -> songs(parent, reuse = parent is BrowseId.Search).map { BrowseNode(BrowseId.Song(parent, it.id), it.title, it.artist, it.artwork.imageUrl) }
        is BrowseId.Song -> null
    }

    /** [id] as an entry of its parent's list, or null once it is no longer there. */
    suspend fun item(id: BrowseId): BrowseNode? {
        val parent = when (id) {
            BrowseId.Root -> return root
            BrowseId.Recent, BrowseId.Liked, BrowseId.Downloads, BrowseId.Library -> BrowseId.Root
            BrowseId.Playlists, BrowseId.Albums, BrowseId.Artists -> BrowseId.Library
            is BrowseId.Playlist -> BrowseId.Playlists
            is BrowseId.Album -> BrowseId.Albums
            is BrowseId.Artist -> BrowseId.Artists
            is BrowseId.Search -> return BrowseNode(id, id.query)
            is BrowseId.Song -> id.parent
        }
        return children(parent)?.firstOrNull { it.id == id }
    }

    /** The songs to queue when [song] is played and where it is among them; null when it is no longer in its list. */
    suspend fun queueFor(song: BrowseId.Song): Pair<List<Track>, Int>? {
        // Library lists are read again (they change as songs are liked or played); catalogue answers are not asked twice.
        val parent = song.parent
        val reused = songs(parent, reuse = parent is BrowseId.Album || parent is BrowseId.Artist || parent is BrowseId.Search)
        val tracks = if (reused.any { it.id == song.trackId }) reused else songs(parent, reuse = false)
        return tracks.indexOfFirst { it.id == song.trackId }.takeIf { it >= 0 }?.let { tracks to it }
    }

    /** Songs matching [query]; empty when there is nothing to look for or the catalogue cannot be reached. */
    suspend fun search(query: String): List<BrowseNode> = children(BrowseId.Search(query.trim())).orEmpty()

    /** What "play [query] on Sitara" plays: its search results, or the liked songs when nothing was named. */
    suspend fun voiceQueue(query: String): List<Track> = if (query.isBlank()) content.likedSongs() else songs(BrowseId.Search(query.trim()), reuse = false)

    private suspend fun songs(list: BrowseId.SongList, reuse: Boolean): List<Track> {
        if (reuse) lastList?.let { (id, tracks) -> if (id == list) return tracks }
        val tracks = when (list) {
            BrowseId.Recent -> content.recentlyPlayed()
            BrowseId.Liked -> content.likedSongs()
            BrowseId.Downloads -> content.downloadedSongs()
            is BrowseId.Playlist -> content.playlistSongs(list.id)
            is BrowseId.Album -> content.albumSongs(list.id)
            is BrowseId.Artist -> content.artistSongs(list.id)
            is BrowseId.Search -> searchOrNothing(list.query)
        }
        lastList = list to tracks
        return tracks
    }

    private suspend fun searchOrNothing(query: String): List<Track> {
        if (query.isBlank()) return emptyList()
        return try {
            content.searchSongs(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/** Page [page] of this list, [pageSize] long; browsers that want everything ask for one page of Int.MAX_VALUE. */
internal fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
    val start = page.toLong() * pageSize
    if (start >= size) return emptyList()
    return subList(start.toInt(), minOf(size.toLong(), start + pageSize).toInt())
}
