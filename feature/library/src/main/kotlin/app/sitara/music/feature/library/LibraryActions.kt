package app.sitara.music.feature.library

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.DownloadState
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.Track

/** Changes to the listener's own library from any screen; the app backs them with the on-device store. */
interface LibraryActions {
    fun setLiked(track: Track, liked: Boolean)

    fun setSaved(album: Album, saved: Boolean)

    fun setSaved(artist: Artist, saved: Boolean)

    /** Creates a playlist named [name], with [first] as its first song when given. */
    fun createPlaylist(name: String, first: Track?)

    fun addToPlaylist(playlist: PlaylistSummary, track: Track)

    fun renamePlaylist(id: Long, name: String)

    fun deletePlaylist(id: Long)

    fun movePlaylistEntry(id: Long, from: Int, to: Int)

    fun removePlaylistEntry(id: Long, trackId: String)

    /** Downloads the songs in [tracks] that are not downloaded or on their way. */
    fun download(tracks: List<Track>)

    /** Cancels a running download or deletes a finished one. */
    fun removeDownload(trackId: String)

    object None : LibraryActions {
        override fun setLiked(track: Track, liked: Boolean) = Unit
        override fun setSaved(album: Album, saved: Boolean) = Unit
        override fun setSaved(artist: Artist, saved: Boolean) = Unit
        override fun createPlaylist(name: String, first: Track?) = Unit
        override fun addToPlaylist(playlist: PlaylistSummary, track: Track) = Unit
        override fun renamePlaylist(id: Long, name: String) = Unit
        override fun deletePlaylist(id: Long) = Unit
        override fun movePlaylistEntry(id: Long, from: Int, to: Int) = Unit
        override fun removePlaylistEntry(id: Long, trackId: String) = Unit
        override fun download(tracks: List<Track>) = Unit
        override fun removeDownload(trackId: String) = Unit
    }
}

/** Which songs, albums and artists are in the library and which songs are downloaded, so every control shows its live state. */
data class SavedIds(
    val tracks: Set<String> = emptySet(),
    val albums: Set<String> = emptySet(),
    val artists: Set<String> = emptySet(),
    val downloads: Map<String, DownloadState> = emptyMap(),
)

/** Everything the Library tab lists, read from the on-device store. */
data class SavedLibrary(
    val liked: List<Track>,
    val recent: List<Track>,
    val playlists: List<PlaylistSummary>,
    val albums: List<Album>,
    val artists: List<Artist>,
)

sealed interface LibraryState {
    data object Loading : LibraryState

    data object Failed : LibraryState

    data class Ready(val library: SavedLibrary) : LibraryState
}
