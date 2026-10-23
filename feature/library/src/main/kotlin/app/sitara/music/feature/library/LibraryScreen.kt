package app.sitara.music.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.common.formatBytes
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.ArtworkTile
import app.sitara.music.core.designsystem.CollectionRow
import app.sitara.music.core.designsystem.EmptyCollectionArtwork
import app.sitara.music.core.designsystem.HeaderAction
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.PlaceholderBlock
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SelectionPill
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.designsystem.byline
import app.sitara.music.core.designsystem.displayName
import app.sitara.music.core.designsystem.exportSummary
import app.sitara.music.core.designsystem.preview
import app.sitara.music.core.designsystem.previewOnLongClick
import app.sitara.music.core.designsystem.rememberPlaceholderPulse
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.DownloadExport
import app.sitara.music.core.model.DownloadState
import app.sitara.music.core.model.LibraryLayout
import app.sitara.music.core.model.LibrarySort
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.SongDownload
import app.sitara.music.core.model.Track

/**
 * The listener's own library: Liked songs and Downloads pinned at the top with their counts, Your stats, Recently played, then one
 * kind at a time (playlists, songs, albums, artists or downloads) as a grid of covers or a list, in the chosen order.
 */
@Composable
fun LibraryScreen(
    state: LibraryState,
    downloads: List<SongDownload>,
    layout: LibraryLayout,
    sort: LibrarySort,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onLikedSongs: () -> Unit,
    onStats: () -> Unit,
    onImport: () -> Unit,
    onOnThisPhone: () -> Unit,
    onPlaylist: (PlaylistSummary) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onNewPlaylist: (name: String) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onFindMusic: () -> Unit,
    library: LibraryActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    /** Songs found on the phone, or null before the phone's music is read. */
    localSongs: Int? = null,
    export: DownloadExport = DownloadExport.Idle,
    onExportDownloads: () -> Unit = {},
) {
    var kind by rememberSaveable { mutableStateOf(LibraryKind.PLAYLISTS) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    val order = kind.sortFor(sort)
    if (naming) {
        PlaylistNameSheet(stringResource(R.string.library_new_playlist), "", stringResource(R.string.library_create), onConfirm = { naming = false; onNewPlaylist(it) }, onDismiss = { naming = false })
    }
    if (sorting) SortSheet(kind.orderTitle(), kind.sorts, order, library::setLibrarySort) { sorting = false }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // As many 150 dp covers as fit between the gutters, two at least.
        val columns = ((maxWidth - SitaraSpacing.Gutter * 2 + TileGap) / (TileWidth + TileGap)).toInt().coerceAtLeast(2)
        LazyColumn(Modifier.fillMaxSize().testTag("library-content"), contentPadding = contentPadding) {
            item(key = "title") { LargeTitle(stringResource(R.string.library_title)) }
            when (state) {
                LibraryState.Loading -> item(key = "loading", contentType = "skeleton") { LibrarySkeleton(columns) }
                LibraryState.Failed -> item(key = "failed") { LibraryMessage(stringResource(R.string.library_failed)) }
                is LibraryState.Ready -> if (state.library.isEmpty && downloads.isEmpty() && (localSongs ?: 0) == 0) {
                    item(key = "empty") { EmptyLibrary(onFindMusic, onImport, onOnThisPhone) }
                } else {
                    val saved = state.library
                    item(key = "pinned") {
                        Row(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                            PinnedTile(SitaraIcons.HeartFilled, stringResource(R.string.library_liked_songs), songCount(saved.liked.size), onLikedSongs, Modifier.weight(1f))
                            PinnedTile(SitaraIcons.Download, stringResource(R.string.library_downloads), songCount(downloads.size), { kind = LibraryKind.DOWNLOADS }, Modifier.weight(1f))
                        }
                    }
                    item(key = "stats") {
                        PinnedTile(
                            SitaraIcons.Stats, stringResource(R.string.library_your_stats), stringResource(R.string.library_stats_detail), onStats,
                            Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 4.dp).fillMaxWidth(),
                        )
                    }
                    item(key = "on-this-phone") {
                        PinnedTile(
                            SitaraIcons.Phone, stringResource(R.string.library_on_this_phone),
                            localSongs?.takeIf { it > 0 }?.let { songCount(it) } ?: stringResource(R.string.library_on_this_phone_detail), onOnThisPhone,
                            Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 4.dp).fillMaxWidth(),
                        )
                    }
                    item(key = "import") {
                        PinnedTile(
                            SitaraIcons.Import, stringResource(R.string.library_import_playlist), stringResource(R.string.library_import_detail), onImport,
                            Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 4.dp).fillMaxWidth(),
                        )
                    }
                    if (saved.recent.isNotEmpty()) {
                        item(key = "recent-heading") { SectionHeader(stringResource(R.string.library_recently_played), Modifier.padding(top = 12.dp)) }
                        item(key = "recent") {
                            LazyRow(
                                Modifier.testTag("rail-recent"),
                                contentPadding = PaddingValues(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(TileGap),
                            ) {
                                itemsIndexed(saved.recent, key = { _, track -> track.id }) { index, track ->
                                    ArtworkTile(track.title, track.artist, track.artwork, { onPlay(saved.recent, index) }, Modifier.width(TileWidth), onLongClick = { onTrackMenu(track) })
                                }
                            }
                        }
                    }
                    item(key = "kinds") {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = SitaraSpacing.Gutter).padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LibraryKind.entries.forEach { SelectionPill(it.title(), it == kind, { kind = it }) }
                        }
                    }
                    item(key = "controls") {
                        Controls(kind, order, layout, onSort = { sorting = true }, onNewPlaylist = { naming = true }, onLayout = library::setLibraryLayout)
                    }
                    val grid = if (kind == LibraryKind.DOWNLOADS) null else layout.takeIf { it == LibraryLayout.GRID }?.let { columns }
                    when (kind) {
                        LibraryKind.PLAYLISTS -> collection(
                            saved.playlists.inOrder(order), grid, R.string.library_empty_playlists, key = { "playlist-${it.id}" },
                            tile = { playlist, tile ->
                                ArtworkTile(playlist.name, songCount(playlist.songCount), EmptyCollectionArtwork, { onPlaylist(playlist) }, tile, covers = playlist.covers, onLongClick = previewOnLongClick(playlist.preview()))
                            },
                            row = { playlist ->
                                CollectionRow(
                                    playlist.name, songCount(playlist.songCount), EmptyCollectionArtwork, { onPlaylist(playlist) }, Modifier.libraryRow(), covers = playlist.covers,
                                    onLongClick = previewOnLongClick(playlist.preview()),
                                )
                            },
                        )
                        LibraryKind.SONGS -> {
                            val songs = saved.liked.inOrder(order, saved.counts)
                            collection(
                                songs.withIndex().toList(), grid, R.string.library_empty_songs, key = { "song-${it.value.id}" },
                                tile = { (index, track), tile ->
                                    ArtworkTile(track.title, track.artist, track.artwork, { onPlay(songs, index) }, tile, onLongClick = { onTrackMenu(track) })
                                },
                                row = { (index, track) -> TrackRow(track, { onPlay(songs, index) }, Modifier.testTag(ROW_TAG), onMore = { onTrackMenu(track) }) },
                            )
                        }
                        LibraryKind.ALBUMS -> collection(
                            saved.albums.inOrder(order), grid, R.string.library_empty_albums, key = { "album-${it.id}" },
                            tile = { album, tile -> ArtworkTile(album.title, album.byline, album.artwork, { onAlbum(album) }, tile, onLongClick = previewOnLongClick(album.preview())) },
                            row = { album -> CollectionRow(album.title, album.byline, album.artwork, { onAlbum(album) }, Modifier.libraryRow(), onLongClick = previewOnLongClick(album.preview())) },
                        )
                        LibraryKind.ARTISTS -> collection(
                            saved.artists.inOrder(order, saved.counts), grid, R.string.library_empty_artists, key = { "artist-${it.id}" },
                            tile = { artist, tile -> ArtworkTile(artist.name, stringResource(R.string.library_artist), artist.artwork, { onArtist(artist) }, tile, round = true, onLongClick = previewOnLongClick(artist.preview())) },
                            row = { artist -> CollectionRow(artist.name, stringResource(R.string.library_artist), artist.artwork, { onArtist(artist) }, Modifier.libraryRow(), onLongClick = previewOnLongClick(artist.preview())) },
                        )
                        LibraryKind.DOWNLOADS -> downloadsSection(downloads, onPlay, library, export, onExportDownloads)
                    }
                }
            }
        }
    }
}

private val SavedLibrary.isEmpty: Boolean get() = liked.isEmpty() && recent.isEmpty() && playlists.isEmpty() && albums.isEmpty() && artists.isEmpty()

/** [values] as a grid of [columns] covers a row, or as rows when [columns] is null; [emptyText] says how to fill an empty kind (null: says nothing). */
internal fun <T> LazyListScope.collection(
    values: List<T>,
    columns: Int?,
    @StringRes emptyText: Int?,
    key: (T) -> String,
    tile: @Composable (T, Modifier) -> Unit,
    row: @Composable (T) -> Unit,
) {
    when {
        values.isEmpty() -> item(key = "kind-empty") { LibraryMessage(emptyText?.let { stringResource(it) } ?: "") }
        columns == null -> items(values, key = key) { row(it) }
        else -> items(values.chunked(columns), key = { "grid-${key(it.first())}" }) { chunk ->
            Row(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                chunk.forEach { tile(it, Modifier.weight(1f).testTag(TILE_TAG)) }
                repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

internal fun Modifier.libraryRow() = padding(horizontal = SitaraSpacing.Gutter).testTag(ROW_TAG)

private const val TILE_TAG = "library-tile"
internal const val ROW_TAG = "library-row"
internal val TileWidth = 150.dp
internal val TileGap = 12.dp

/** The order (when this kind has more than one), New playlist on playlists, and grid or list (not for downloads, which are rows). */
@Composable
private fun Controls(kind: LibraryKind, order: LibrarySort, layout: LibraryLayout, onSort: () -> Unit, onNewPlaylist: () -> Unit, onLayout: (LibraryLayout) -> Unit) {
    OrderAndLayout(kind.sorts.size > 1, order, layout.takeIf { kind != LibraryKind.DOWNLOADS }, onSort, onLayout) {
        if (kind == LibraryKind.PLAYLISTS) PillButton(stringResource(R.string.library_new_playlist), onNewPlaylist, primary = false)
    }
}

/** The order button when there is a choice, then [action] and the grid or list switch when [layout] applies. */
@Composable
internal fun OrderAndLayout(sortable: Boolean, order: LibrarySort, layout: LibraryLayout?, onSort: () -> Unit, onLayout: (LibraryLayout) -> Unit, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (sortable) {
            val orderName = order.displayName()
            val orderDescription = stringResource(R.string.library_order_description, orderName)
            Row(
                Modifier.defaultMinSize(minHeight = SitaraSpacing.TouchTarget).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.library_change_order), onClick = onSort)
                    .semantics(mergeDescendants = true) { contentDescription = orderDescription }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SitaraIcons.Sort, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(orderName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        action()
        if (layout != null) {
            val grid = layout == LibraryLayout.GRID
            IconButton(onClick = { onLayout(if (grid) LibraryLayout.LIST else LibraryLayout.GRID) }) {
                Icon(if (grid) SitaraIcons.List else SitaraIcons.Grid, stringResource(if (grid) R.string.library_show_list else R.string.library_show_grid), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun SortSheet(title: String, sorts: List<LibrarySort>, order: LibrarySort, onSort: (LibrarySort) -> Unit, onDismiss: () -> Unit) {
    SitaraSheet(onDismiss) { close ->
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp).selectableGroup()) {
            Text(
                title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
            )
            sorts.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                        .selectable(option == order, role = Role.RadioButton) { close { onSort(option) } }
                        .padding(horizontal = SitaraSpacing.Gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(option.displayName(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    RadioButton(selected = option == order, onClick = null)
                }
            }
        }
    }
}

/** Liked songs or Downloads, always at the top, with how many songs each holds. */
@Composable
internal fun PinnedTile(icon: ImageVector, title: String, detail: String, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(SitaraRadius.Tile)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(SitaraRadius.Cover)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Three covers fanned out in the theme's colours, for a page with nothing to show yet; decoration only. */
@Composable
internal fun FannedCovers() {
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val deep = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()
    Box(Modifier.size(180.dp, 128.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        listOf(-12f to listOf(deep, accent), 12f to listOf(accent, deep), 0f to listOf(accent, deep, accent)).forEach { (turn, colors) ->
            ArtworkImage(Artwork(colors), Modifier.size(104.dp).offset(x = (turn * 3).dp).rotate(turn), cornerRadius = SitaraRadius.Tile)
        }
    }
}

/** A fresh library: three fanned covers in the theme's colours, what fills it, and one way to start. */
@Composable
private fun EmptyLibrary(onFindMusic: () -> Unit, onImport: () -> Unit, onOnThisPhone: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FannedCovers()
        Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(R.string.library_empty_body),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        PillButton(stringResource(R.string.library_find_music), onFindMusic, Modifier.padding(top = 8.dp))
        PillButton(stringResource(R.string.library_import_a_playlist), onImport, primary = false)
        PillButton(stringResource(R.string.library_music_on_this_phone), onOnThisPhone, primary = false)
    }
}

/** Library while it loads, in its own layout: the pinned tiles, the kinds and a first row of covers. */
@Composable
private fun LibrarySkeleton(columns: Int) {
    val pulse = rememberPlaceholderPulse()
    val loading = stringResource(R.string.library_loading)
    Column(Modifier.clearAndSetSemantics { contentDescription = loading }) {
        Row(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
            repeat(2) { PlaceholderBlock(pulse, Modifier.weight(1f).height(68.dp), RoundedCornerShape(SitaraRadius.Tile)) }
        }
        Row(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(96.dp, 76.dp, 80.dp, 82.dp).forEach { PlaceholderBlock(pulse, Modifier.size(it, SitaraSpacing.TouchTarget), CircleShape) }
        }
        repeat(2) {
            Row(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                repeat(columns) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlaceholderBlock(pulse, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(SitaraRadius.Tile))
                        PlaceholderBlock(pulse, Modifier.fillMaxWidth(0.8f).height(14.dp))
                        PlaceholderBlock(pulse, Modifier.fillMaxWidth(0.5f).height(12.dp))
                    }
                }
            }
        }
    }
}

private fun LazyListScope.downloadsSection(
    downloads: List<SongDownload>,
    onPlay: (List<Track>, Int) -> Unit,
    actions: LibraryActions,
    export: DownloadExport,
    onExport: () -> Unit,
) {
    item(key = "downloads-heading") {
        val summary = exportSummary(export)
            ?: downloads.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.library_joined, songCount(it.size), formatBytes(it.sumOf { download -> download.bytes })) }
        val canExport = downloads.any { it.state == DownloadState.DOWNLOADED } && export !is DownloadExport.Working
        val exportLabel = stringResource(R.string.library_export)
        val exportDescription = stringResource(R.string.library_export_description)
        SectionHeader(
            stringResource(R.string.library_downloads), Modifier.padding(top = 16.dp), subtitle = summary,
            action = if (canExport) {
                { HeaderAction(exportLabel, onExport, description = exportDescription) }
            } else {
                null
            },
        )
    }
    if (downloads.isEmpty()) {
        item(key = "downloads-empty") { LibraryMessage(stringResource(R.string.library_downloads_empty)) }
    }
    val tracks = downloads.map { it.track }
    itemsIndexed(downloads, key = { _, download -> "download-${download.track.id}" }) { index, download ->
        DownloadRow(download, { onPlay(tracks, index) }, { actions.download(listOf(download.track)) }, { actions.removeDownload(download.track.id) })
    }
}

@Composable
private fun DownloadRow(download: SongDownload, onPlay: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit) {
    val title = download.track.title
    val colors = MaterialTheme.colorScheme
    TrackRow(download.track, onPlay, Modifier.testTag("download-row")) {
        Text(
            downloadLabel(download), style = MaterialTheme.typography.bodySmall,
            color = if (download.state == DownloadState.FAILED) colors.error else colors.onSurfaceVariant,
        )
        if (download.state == DownloadState.FAILED) {
            IconButton(onClick = onRetry) { Icon(SitaraIcons.Download, stringResource(R.string.library_download_again, title), tint = colors.onSurfaceVariant) }
        }
        val (icon, label) = when (download.state) {
            DownloadState.DOWNLOADED -> SitaraIcons.Delete to stringResource(R.string.library_delete_download_of, title)
            DownloadState.FAILED -> SitaraIcons.Close to stringResource(R.string.library_remove_from_downloads, title)
            else -> SitaraIcons.Close to stringResource(R.string.library_cancel_download_of, title)
        }
        IconButton(onClick = onRemove) { Icon(icon, label, tint = colors.onSurfaceVariant) }
    }
}

@Composable
private fun downloadLabel(download: SongDownload): String = when (download.state) {
    DownloadState.DOWNLOADED -> formatBytes(download.bytes)
    DownloadState.DOWNLOADING -> download.percent?.let { stringResource(R.string.library_download_percent, it) } ?: stringResource(R.string.library_download_downloading)
    DownloadState.WAITING -> stringResource(R.string.library_download_waiting)
    DownloadState.WAITING_FOR_WIFI -> stringResource(R.string.library_download_waiting_wifi)
    DownloadState.WAITING_FOR_NETWORK -> stringResource(R.string.library_download_waiting_network)
    DownloadState.FAILED -> stringResource(R.string.library_download_failed)
}

/** "1 song", "12 songs", in the app's language. */
@Composable
internal fun songCount(count: Int): String = pluralStringResource(R.plurals.library_song_count, count, count)

/** The kind's name on its selection pill. */
@Composable
internal fun LibraryKind.title(): String = stringResource(
    when (this) {
        LibraryKind.PLAYLISTS -> R.string.library_kind_playlists
        LibraryKind.SONGS -> R.string.library_kind_songs
        LibraryKind.ALBUMS -> R.string.library_kind_albums
        LibraryKind.ARTISTS -> R.string.library_kind_artists
        LibraryKind.DOWNLOADS -> R.string.library_kind_downloads
    },
)

/** The order sheet's title for this kind: "Order playlists by". */
@Composable
private fun LibraryKind.orderTitle(): String = stringResource(
    when (this) {
        LibraryKind.PLAYLISTS -> R.string.library_order_playlists_by
        LibraryKind.SONGS -> R.string.library_order_songs_by
        LibraryKind.ALBUMS -> R.string.library_order_albums_by
        LibraryKind.ARTISTS -> R.string.library_order_artists_by
        LibraryKind.DOWNLOADS -> R.string.library_order_downloads_by
    },
)

@Composable
internal fun LibraryMessage(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp))
}
