package app.sitara.music.core.player

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** [node] as a Media3 item: a song is playable, everything else browsable, each typed so cars can lay it out. */
internal fun BrowseNode.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.mediaId)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setArtworkUri(artworkUrl?.toUri())
            .setIsPlayable(playable)
            .setIsBrowsable(!playable)
            .setMediaType(mediaType(id))
            .build(),
    )
    .build()

private fun mediaType(id: BrowseId): Int = when (id) {
    BrowseId.Root, BrowseId.Library -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
    BrowseId.Playlists -> MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
    BrowseId.Albums -> MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
    BrowseId.Artists -> MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
    BrowseId.Recent, BrowseId.Liked, BrowseId.Downloads, is BrowseId.Playlist, is BrowseId.Search -> MediaMetadata.MEDIA_TYPE_PLAYLIST
    is BrowseId.Album -> MediaMetadata.MEDIA_TYPE_ALBUM
    is BrowseId.Artist -> MediaMetadata.MEDIA_TYPE_ARTIST
    is BrowseId.Song -> MediaMetadata.MEDIA_TYPE_MUSIC
}
