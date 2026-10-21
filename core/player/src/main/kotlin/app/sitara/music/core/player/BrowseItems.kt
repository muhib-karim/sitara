package app.sitara.music.core.player

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants

/** How a car lays out the tree unless a folder says otherwise: everything as a list. */
@OptIn(UnstableApi::class)
internal fun defaultContentStyle(): Bundle = bundleOf(
    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE to MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE to MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
)

/** [node] as a Media3 item: a song is playable, everything else browsable, each typed and styled so cars can lay it out. */
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
            .setExtras(contentStyle(id))
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

/** Covers read best as a grid (playlists, albums, artists), songs as a list. */
@OptIn(UnstableApi::class)
private fun contentStyle(id: BrowseId): Bundle? = when (id) {
    BrowseId.Playlists, BrowseId.Albums, BrowseId.Artists -> bundleOf(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE to MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
    is BrowseId.SongList -> bundleOf(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE to MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
    else -> null
}
