package app.sitara.music.source.youtube

import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.ArtistPage
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * An artist page from the artist's channel, the items of its Releases tab, and a YouTube Music song search for the
 * artist's name. The search ranks by popularity; only songs credited to this channel are kept.
 */
internal fun artistPage(channel: ChannelInfo, releases: List<InfoItem>, songs: List<InfoItem>): ArtistPage {
    val name = artistName(channel.name)
    return ArtistPage(
        artist = Artist(channel.id, name, artwork(channel.id, channel.avatars)),
        bannerUrl = channel.banners.largest(),
        topSongs = songs.filterIsInstance<StreamInfoItem>().mapNotNull { it.toTrack() }.filter { it.artistId == channel.id }.distinctBy { it.id },
        releases = releases.filterIsInstance<PlaylistInfoItem>().mapNotNull { it.toAlbum(fallbackArtist = name, artistId = channel.id) }.distinctBy { it.id },
    )
}

/** An album preview (no tracks yet) from a release or album search item; the extractor exposes no release year. */
internal fun PlaylistInfoItem.toAlbum(fallbackArtist: String = "", artistId: String? = channelIdFromUrl(uploaderUrl)): Album? {
    val id = playlistIdFromUrl(url) ?: return null
    return Album(
        id = id,
        title = name,
        artist = artistName(uploaderName).ifEmpty { fallbackArtist },
        year = null,
        artwork = artwork(id, thumbnails),
        tracks = emptyList(),
        artistId = artistId,
    )
}
