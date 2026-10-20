package app.sitara.music.source.youtube

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.Track
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
import org.schabi.newpipe.extractor.stream.StreamInfoItem

internal val SearchFilter.extractorFilter: String
    get() = when (this) {
        SearchFilter.SONGS -> MUSIC_SONGS
        SearchFilter.ALBUMS -> MUSIC_ALBUMS
        SearchFilter.ARTISTS -> MUSIC_ARTISTS
        SearchFilter.PLAYLISTS -> MUSIC_PLAYLISTS
    }

/** One YouTube Music search page as results of the kind [filter] asked for; rows are keyed by id, so repeats are dropped. */
internal fun musicResults(query: String, filter: SearchFilter, items: List<InfoItem>): SearchResults {
    val empty = SearchResults.empty(query)
    return when (filter) {
        SearchFilter.SONGS -> empty.copy(tracks = items.filterIsInstance<StreamInfoItem>().tracks())
        SearchFilter.ALBUMS -> empty.copy(albums = items.filterIsInstance<PlaylistInfoItem>().mapNotNull { it.toAlbum() }.distinctBy { it.id })
        SearchFilter.ARTISTS -> empty.copy(artists = items.filterIsInstance<ChannelInfoItem>().mapNotNull { it.toArtist() }.distinctBy { it.id })
        SearchFilter.PLAYLISTS -> empty.copy(playlists = items.filterIsInstance<PlaylistInfoItem>().mapNotNull { it.toPlaylist() }.distinctBy { it.id })
    }
}

/**
 * A YouTube Music album, which is an auto-generated `OLAK5uy…` playlist. YouTube titles those "Album - <title>" and
 * credits the artist's topic channel; when no uploader is given, the first track's artist stands in.
 */
internal fun albumPage(id: String, title: String, uploaderName: String?, uploaderUrl: String?, thumbnails: List<Image>, items: List<StreamInfoItem>): Album {
    val albumTitle = title.removePrefix("Album - ")
    val tracks = items.tracks().map { it.copy(album = albumTitle) }
    return Album(
        id = id,
        title = albumTitle,
        artist = artistName(uploaderName).ifEmpty { tracks.firstOrNull()?.artist.orEmpty() },
        year = null,
        artwork = artwork(id, thumbnails),
        tracks = tracks,
        artistId = channelIdFromUrl(uploaderUrl) ?: tracks.firstOrNull()?.artistId,
    )
}

internal fun playlistPage(id: String, title: String, uploaderName: String?, thumbnails: List<Image>, items: List<StreamInfoItem>) = Playlist(
    id = id,
    title = title,
    description = "",
    artwork = artwork(id, thumbnails),
    tracks = items.tracks(),
    curator = artistName(uploaderName),
)

private fun List<StreamInfoItem>.tracks(): List<Track> = mapNotNull { it.toTrack() }.distinctBy { it.id }

private fun ChannelInfoItem.toArtist(): Artist? {
    val id = channelIdFromUrl(url) ?: return null
    return Artist(id, artistName(name), artwork(id, thumbnails))
}

/** A playlist preview from a search item; its tracks load on its own page. */
private fun PlaylistInfoItem.toPlaylist(): Playlist? {
    val id = playlistIdFromUrl(url) ?: return null
    return Playlist(id = id, title = name, description = "", artwork = artwork(id, thumbnails), tracks = emptyList(), curator = artistName(uploaderName))
}
