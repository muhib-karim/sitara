package app.sitara.music.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.sitara.music.core.common.formatDuration
import app.sitara.music.core.designsystem.ArtworkBackdrop
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.ArtworkTile
import app.sitara.music.core.designsystem.EmptyCollectionArtwork
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.SitaraTheme
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.designsystem.dragHandle
import app.sitara.music.core.designsystem.preview
import app.sitara.music.core.designsystem.previewOnLongClick
import app.sitara.music.core.designsystem.rememberReorderState
import app.sitara.music.core.designsystem.reorderableRow
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.ArtistPage
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.DownloadState
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.ShareTarget
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.UserPlaylist
import app.sitara.music.core.model.shareTarget
import app.sitara.music.core.model.toDownload

/** An artist, album or playlist page as it loads. */
sealed interface DetailState {
    data object Loading : DetailState

    data object Failed : DetailState

    data class ArtistReady(val page: ArtistPage) : DetailState

    data class AlbumReady(val album: Album) : DetailState

    data class PlaylistReady(val playlist: Playlist) : DetailState

    data class LikedReady(val tracks: List<Track>) : DetailState

    data class UserPlaylistReady(val playlist: UserPlaylist) : DetailState

    /** The listener's playlist no longer exists. */
    data object Gone : DetailState
}

/** One detail page: loading, failed with a retry, a catalogue artist, album or playlist, or one of the listener's own collections. */
@Composable
fun DetailScreen(
    state: DetailState,
    settings: SitaraSettings,
    saved: SavedIds,
    library: LibraryActions,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onArtist: (artistId: String) -> Unit,
    onAlbum: (albumId: String) -> Unit,
    onTrackMenu: (Track) -> Unit,
    contentPadding: PaddingValues,
    /** Opens the share sheet (link and QR code) for the page. */
    onShare: (ShareTarget) -> Unit = {},
) {
    var dialog by rememberSaveable { mutableStateOf(PlaylistDialog.NONE) }
    val reorder = rememberReorderState()
    // A dropped song shows in its new place at once; the store's next emission resets this to the saved order.
    var entries by remember(state) { mutableStateOf((state as? DetailState.UserPlaylistReady)?.playlist?.tracks.orEmpty()) }
    if (state is DetailState.UserPlaylistReady) {
        val playlist = state.playlist
        when (dialog) {
            PlaylistDialog.NONE -> Unit
            PlaylistDialog.RENAME -> PlaylistNameSheet(
                stringResource(R.string.library_rename_playlist), playlist.name, stringResource(R.string.library_save),
                onConfirm = { dialog = PlaylistDialog.NONE; library.renamePlaylist(playlist.id, it) },
                onDismiss = { dialog = PlaylistDialog.NONE },
            )
            PlaylistDialog.DELETE -> DeletePlaylistSheet(
                playlist.name,
                onConfirm = { dialog = PlaylistDialog.NONE; onBack(); library.deletePlaylist(playlist.id) },
                onDismiss = { dialog = PlaylistDialog.NONE },
            )
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("detail-content"),
        contentPadding = contentPadding,
    ) {
        when (state) {
            DetailState.Loading -> item(key = "loading") { PageMessage(stringResource(R.string.library_page_loading), onBack) }
            DetailState.Failed -> item(key = "failed") {
                PageMessage(stringResource(R.string.library_page_failed), onBack) { PillButton(stringResource(R.string.library_try_again), onRetry) }
            }
            DetailState.Gone -> item(key = "gone") { PageMessage(stringResource(R.string.library_playlist_gone), onBack) }
            is DetailState.ArtistReady -> {
                val page = state.page
                val songs = page.topSongs
                item(key = "header") {
                    val isSaved = page.artist.id in saved.artists
                    DetailHeader(
                        page.artist.artwork, page.artist.name, stringResource(R.string.library_artist), artistSummary(page), null,
                        songs, settings, onBack, onPlay, onShuffle, round = true, share = page.artist.shareTarget(), onShare = onShare,
                    ) { SaveButton(isSaved) { library.setSaved(page.artist, !isSaved) } }
                }
                if (songs.isEmpty() && page.releases.isEmpty()) {
                    item(key = "empty") { EmptyMessage(stringResource(R.string.library_artist_empty)) }
                }
                if (songs.isNotEmpty()) {
                    item(key = "songs-heading") { SectionHeader(stringResource(R.string.library_top_songs), modifier = Modifier.padding(top = 16.dp)) }
                    trackRows(songs, saved.downloads, onPlay, onTrackMenu)
                }
                if (page.releases.isNotEmpty()) {
                    item(key = "releases-heading") { SectionHeader(stringResource(R.string.library_albums_and_singles), modifier = Modifier.padding(top = 16.dp)) }
                    item(key = "releases") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(page.releases, key = { it.id }) { album ->
                                ArtworkTile(album.title, album.year?.toString() ?: stringResource(R.string.library_album), album.artwork, { onAlbum(album.id) }, Modifier.width(156.dp), onLongClick = previewOnLongClick(album.preview()))
                            }
                        }
                    }
                }
            }
            is DetailState.AlbumReady -> {
                val album = state.album
                val artistId = album.artistId
                item(key = "header") {
                    val isSaved = album.id in saved.albums
                    DetailHeader(
                        album.artwork, album.title, listOfNotNull(stringResource(R.string.library_album), album.year?.toString()).joinToString(" · "), album.artist,
                        artistId?.let { { onArtist(it) } }, album.tracks, settings, onBack, onPlay, onShuffle,
                        share = album.shareTarget(), onShare = onShare,
                    ) {
                        SaveButton(isSaved) { library.setSaved(album, !isSaved) }
                        DownloadAllButton(album.tracks, saved.downloads, library)
                    }
                }
                collectionTracks(album.tracks, saved.downloads, R.string.library_album_empty, onPlay, onTrackMenu)
            }
            is DetailState.PlaylistReady -> {
                val playlist = state.playlist
                item(key = "header") {
                    DetailHeader(
                        playlist.artwork, playlist.title, stringResource(R.string.library_playlist), playlist.curator.ifEmpty { playlist.description }, null,
                        playlist.tracks, settings, onBack, onPlay, onShuffle, share = playlist.shareTarget(), onShare = onShare,
                    ) { DownloadAllButton(playlist.tracks, saved.downloads, library) }
                }
                collectionTracks(playlist.tracks, saved.downloads, R.string.library_playlist_empty, onPlay, onTrackMenu)
            }
            is DetailState.LikedReady -> {
                val tracks = state.tracks
                item(key = "header") {
                    DetailHeader(
                        tracks.firstOrNull()?.artwork ?: EmptyCollectionArtwork, stringResource(R.string.library_liked_songs), stringResource(R.string.library_your_library), "", null,
                        tracks, settings, onBack, onPlay, onShuffle,
                    ) { DownloadAllButton(tracks, saved.downloads, library) }
                }
                collectionTracks(tracks, saved.downloads, R.string.library_liked_empty, onPlay, onTrackMenu)
            }
            is DetailState.UserPlaylistReady -> {
                val playlist = state.playlist
                item(key = "header") {
                    DetailHeader(
                        entries.firstOrNull()?.artwork ?: EmptyCollectionArtwork, playlist.name, stringResource(R.string.library_your_playlist), "", null,
                        entries, settings, onBack, onPlay, onShuffle,
                    ) {
                        PillButton(stringResource(R.string.library_rename), { dialog = PlaylistDialog.RENAME }, primary = false)
                        PillButton(stringResource(R.string.library_delete), { dialog = PlaylistDialog.DELETE }, primary = false)
                        DownloadAllButton(entries, saved.downloads, library)
                    }
                }
                if (entries.isEmpty()) {
                    item(key = "empty") { EmptyMessage(stringResource(R.string.library_user_playlist_empty)) }
                } else {
                    songsHeading(entries)
                    val move = { from: Int, to: Int ->
                        entries = entries.toMutableList().apply { add(to, removeAt(from)) }
                        library.movePlaylistEntry(playlist.id, from, to)
                    }
                    itemsIndexed(entries, key = { _, track -> "track-${track.id}" }) { index, track ->
                        TrackRow(
                            track, { onPlay(entries, index) }, Modifier.reorderableRow(reorder, index, entries.size, move),
                            onMore = { onTrackMenu(track) },
                        ) {
                            if (saved.downloads[track.id] == DownloadState.DOWNLOADED) DownloadedBadge()
                            IconButton(onClick = { library.removePlaylistEntry(playlist.id, track.id) }) {
                                Icon(SitaraIcons.Close, stringResource(R.string.library_remove_from_playlist, track.title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(
                                SitaraIcons.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp).dragHandle(reorder, index, entries.size, move).padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class PlaylistDialog { NONE, RENAME, DELETE }

@Composable
private fun SaveButton(saved: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (saved) R.string.library_in_your_library else R.string.library_not_in_your_library)
    PillButton(stringResource(if (saved) R.string.library_saved else R.string.library_save), onToggle, Modifier.semantics { stateDescription = state }, primary = false)
}

/** Shown while any song on the page is neither downloaded nor on its way. */
@Composable
private fun DownloadAllButton(tracks: List<Track>, downloads: Map<String, DownloadState>, library: LibraryActions) {
    if (tracks.toDownload(downloads).isNotEmpty()) PillButton(stringResource(R.string.library_download_all), { library.download(tracks) }, primary = false)
}

@Composable
private fun DownloadedBadge() {
    Icon(SitaraIcons.Downloaded, stringResource(R.string.library_downloaded), Modifier.padding(horizontal = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.primary)
}

@Composable
private fun artistSummary(page: ArtistPage): String = listOfNotNull(
    page.topSongs.size.takeIf { it > 0 }?.let { songCount(it) },
    page.releases.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.library_release_count, it, it) },
).joinToString(" · ")

private fun LazyListScope.collectionTracks(
    tracks: List<Track>,
    downloads: Map<String, DownloadState>,
    @StringRes emptyText: Int,
    onPlay: (List<Track>, Int) -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    if (tracks.isEmpty()) {
        item(key = "empty") { EmptyMessage(stringResource(emptyText)) }
        return
    }
    songsHeading(tracks)
    trackRows(tracks, downloads, onPlay, onTrackMenu)
}

private fun LazyListScope.songsHeading(tracks: List<Track>) {
    val total = formatDuration(tracks.sumOf { it.durationMs })
    item(key = "songs-heading") {
        SectionHeader(stringResource(R.string.library_songs), subtitle = stringResource(R.string.library_joined, songCount(tracks.size), total), modifier = Modifier.padding(top = 16.dp))
    }
}

private fun LazyListScope.trackRows(tracks: List<Track>, downloads: Map<String, DownloadState>, onPlay: (List<Track>, Int) -> Unit, onTrackMenu: (Track) -> Unit) {
    itemsIndexed(tracks, key = { _, track -> "track-${track.id}" }) { index, track ->
        TrackRow(track, { onPlay(tracks, index) }, onMore = { onTrackMenu(track) }) {
            if (downloads[track.id] == DownloadState.DOWNLOADED) DownloadedBadge()
            if (track.durationMs > 0) {
                Text(formatDuration(track.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeader(
    artwork: Artwork,
    title: String,
    kind: String,
    subtitle: String,
    onSubtitle: (() -> Unit)?,
    tracks: List<Track>,
    settings: SitaraSettings,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    round: Boolean = false,
    share: ShareTarget? = null,
    onShare: (ShareTarget) -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    // The shared listening stage supplies atmosphere, reduced-motion and pure black behaviour; the accent follows this page's cover.
    SitaraTheme(settings, nowPlaying = artwork, dark = true) {
        Box(Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter).clip(RoundedCornerShape(SitaraRadius.Artwork))) {
            ArtworkBackdrop(artwork, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    GlassIconButton(SitaraIcons.Back, stringResource(R.string.library_back), onBack)
                    Spacer(Modifier.weight(1f))
                    if (share != null) GlassIconButton(SitaraIcons.Share, stringResource(R.string.library_share_page, title), { onShare(share) })
                }
                ArtworkImage(artwork, Modifier.size(152.dp).align(Alignment.CenterHorizontally), cornerRadius = if (round) 76.dp else SitaraRadius.Tile)
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineLarge)
                Text(kind, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                if (subtitle.isNotEmpty()) {
                    val openArtist = stringResource(R.string.library_open_artist)
                    val link = onSubtitle?.let { Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClickLabel = openArtist, onClick = it) } ?: Modifier
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = link)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (tracks.isNotEmpty()) {
                        PillButton(stringResource(R.string.library_play_all), { onPlay(tracks, 0) })
                        PillButton(stringResource(R.string.library_shuffle), { onShuffle(tracks) }, primary = false)
                    }
                    actions()
                }
            }
        }
    }
}

@Composable
private fun PageMessage(message: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GlassIconButton(SitaraIcons.Back, stringResource(R.string.library_back), onBack)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 8.dp))
        Box(Modifier.padding(horizontal = 8.dp)) { action() }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(SitaraSpacing.Gutter))
}
