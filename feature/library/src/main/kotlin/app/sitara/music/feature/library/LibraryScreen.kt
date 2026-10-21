package app.sitara.music.feature.library

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
import app.sitara.music.core.designsystem.rememberPlaceholderPulse
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
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
    onPlaylist: (PlaylistSummary) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onNewPlaylist: (name: String) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onFindMusic: () -> Unit,
    library: LibraryActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var kind by rememberSaveable { mutableStateOf(LibraryKind.PLAYLISTS) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    val order = kind.sortFor(sort)
    if (naming) {
        PlaylistNameSheet("New playlist", "", "Create", onConfirm = { naming = false; onNewPlaylist(it) }, onDismiss = { naming = false })
    }
    if (sorting) SortSheet(kind, order, library::setLibrarySort) { sorting = false }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // As many 150 dp covers as fit between the gutters, two at least.
        val columns = ((maxWidth - SitaraSpacing.Gutter * 2 + TileGap) / (TileWidth + TileGap)).toInt().coerceAtLeast(2)
        LazyColumn(Modifier.fillMaxSize().testTag("library-content"), contentPadding = contentPadding) {
            item(key = "title") { LargeTitle("Library") }
            when (state) {
                LibraryState.Loading -> item(key = "loading", contentType = "skeleton") { LibrarySkeleton(columns) }
                LibraryState.Failed -> item(key = "failed") { LibraryMessage("Your library couldn’t be read. Restart Sitara to try again.") }
                is LibraryState.Ready -> if (state.library.isEmpty && downloads.isEmpty()) {
                    item(key = "empty") { EmptyLibrary(onFindMusic, onImport) }
                } else {
                    val saved = state.library
                    item(key = "pinned") {
                        Row(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                            PinnedTile(SitaraIcons.HeartFilled, "Liked songs", songCount(saved.liked.size), onLikedSongs, Modifier.weight(1f))
                            PinnedTile(SitaraIcons.Download, "Downloads", songCount(downloads.size), { kind = LibraryKind.DOWNLOADS }, Modifier.weight(1f))
                        }
                    }
                    item(key = "stats") {
                        PinnedTile(
                            SitaraIcons.Stats, "Your stats", "Top songs, artists and when you listen", onStats,
                            Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 4.dp).fillMaxWidth(),
                        )
                    }
                    item(key = "import") {
                        PinnedTile(
                            SitaraIcons.Import, "Import playlist", "From a YouTube link, M3U or CSV file", onImport,
                            Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 4.dp).fillMaxWidth(),
                        )
                    }
                    if (saved.recent.isNotEmpty()) {
                        item(key = "recent-heading") { SectionHeader("Recently played", Modifier.padding(top = 12.dp)) }
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
                            LibraryKind.entries.forEach { SelectionPill(it.label, it == kind, { kind = it }) }
                        }
                    }
                    item(key = "controls") {
                        Controls(kind, order, layout, onSort = { sorting = true }, onNewPlaylist = { naming = true }, onLayout = library::setLibraryLayout)
                    }
                    val grid = if (kind == LibraryKind.DOWNLOADS) null else layout.takeIf { it == LibraryLayout.GRID }?.let { columns }
                    when (kind) {
                        LibraryKind.PLAYLISTS -> collection(
                            saved.playlists.inOrder(order), grid, "Make a playlist to keep songs in your own order.", key = { "playlist-${it.id}" },
                            tile = { playlist, tile ->
                                ArtworkTile(playlist.name, songCount(playlist.songCount), EmptyCollectionArtwork, { onPlaylist(playlist) }, tile, covers = playlist.covers)
                            },
                            row = { playlist ->
                                CollectionRow(
                                    playlist.name, songCount(playlist.songCount), EmptyCollectionArtwork, { onPlaylist(playlist) }, Modifier.libraryRow(), covers = playlist.covers,
                                )
                            },
                        )
                        LibraryKind.SONGS -> {
                            val songs = saved.liked.inOrder(order, saved.counts)
                            collection(
                                songs.withIndex().toList(), grid, "Like a song and it shows up here.", key = { "song-${it.value.id}" },
                                tile = { (index, track), tile ->
                                    ArtworkTile(track.title, track.artist, track.artwork, { onPlay(songs, index) }, tile, onLongClick = { onTrackMenu(track) })
                                },
                                row = { (index, track) -> TrackRow(track, { onPlay(songs, index) }, Modifier.testTag(ROW_TAG), onMore = { onTrackMenu(track) }) },
                            )
                        }
                        LibraryKind.ALBUMS -> collection(
                            saved.albums.inOrder(order), grid, "Save an album from its page to keep it here.", key = { "album-${it.id}" },
                            tile = { album, tile -> ArtworkTile(album.title, album.byline, album.artwork, { onAlbum(album) }, tile) },
                            row = { album -> CollectionRow(album.title, album.byline, album.artwork, { onAlbum(album) }, Modifier.libraryRow()) },
                        )
                        LibraryKind.ARTISTS -> collection(
                            saved.artists.inOrder(order, saved.counts), grid, "Save an artist from their page to keep them here.", key = { "artist-${it.id}" },
                            tile = { artist, tile -> ArtworkTile(artist.name, "Artist", artist.artwork, { onArtist(artist) }, tile, round = true) },
                            row = { artist -> CollectionRow(artist.name, "Artist", artist.artwork, { onArtist(artist) }, Modifier.libraryRow()) },
                        )
                        LibraryKind.DOWNLOADS -> downloadsSection(downloads, onPlay, library)
                    }
                }
            }
        }
    }
}

private val SavedLibrary.isEmpty: Boolean get() = liked.isEmpty() && recent.isEmpty() && playlists.isEmpty() && albums.isEmpty() && artists.isEmpty()

/** [values] as a grid of [columns] covers a row, or as rows when [columns] is null; [emptyText] says how to fill an empty kind. */
private fun <T> LazyListScope.collection(
    values: List<T>,
    columns: Int?,
    emptyText: String,
    key: (T) -> String,
    tile: @Composable (T, Modifier) -> Unit,
    row: @Composable (T) -> Unit,
) {
    when {
        values.isEmpty() -> item(key = "kind-empty") { LibraryMessage(emptyText) }
        columns == null -> items(values, key = key) { row(it) }
        else -> items(values.chunked(columns), key = { "grid-${key(it.first())}" }) { chunk ->
            Row(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                chunk.forEach { tile(it, Modifier.weight(1f).testTag(TILE_TAG)) }
                repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun Modifier.libraryRow() = padding(horizontal = SitaraSpacing.Gutter).testTag(ROW_TAG)

private const val TILE_TAG = "library-tile"
private const val ROW_TAG = "library-row"
internal val TileWidth = 150.dp
internal val TileGap = 12.dp

/** The order (when this kind has more than one), New playlist on playlists, and grid or list (not for downloads, which are rows). */
@Composable
private fun Controls(kind: LibraryKind, order: LibrarySort, layout: LibraryLayout, onSort: () -> Unit, onNewPlaylist: () -> Unit, onLayout: (LibraryLayout) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (kind.sorts.size > 1) {
            Row(
                Modifier.defaultMinSize(minHeight = SitaraSpacing.TouchTarget).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Change the order", onClick = onSort)
                    .semantics(mergeDescendants = true) { contentDescription = "Order: ${order.label}" }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SitaraIcons.Sort, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(order.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        if (kind == LibraryKind.PLAYLISTS) PillButton("New playlist", onNewPlaylist, primary = false)
        if (kind != LibraryKind.DOWNLOADS) {
            val grid = layout == LibraryLayout.GRID
            IconButton(onClick = { onLayout(if (grid) LibraryLayout.LIST else LibraryLayout.GRID) }) {
                Icon(if (grid) SitaraIcons.List else SitaraIcons.Grid, if (grid) "Show as a list" else "Show as a grid", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SortSheet(kind: LibraryKind, order: LibrarySort, onSort: (LibrarySort) -> Unit, onDismiss: () -> Unit) {
    SitaraSheet(onDismiss) { close ->
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp).selectableGroup()) {
            Text(
                "Order ${kind.label.lowercase()} by", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
            )
            kind.sorts.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                        .selectable(option == order, role = Role.RadioButton) { close { onSort(option) } }
                        .padding(horizontal = SitaraSpacing.Gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(option.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    RadioButton(selected = option == order, onClick = null)
                }
            }
        }
    }
}

/** Liked songs or Downloads, always at the top, with how many songs each holds. */
@Composable
private fun PinnedTile(icon: ImageVector, title: String, detail: String, onClick: () -> Unit, modifier: Modifier) {
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
private fun EmptyLibrary(onFindMusic: () -> Unit, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FannedCovers()
        Text("Your library starts here", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Like songs, save albums and artists, make playlists and download for offline. They all gather here.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        PillButton("Find music", onFindMusic, Modifier.padding(top = 8.dp))
        PillButton("Import a playlist", onImport, primary = false)
    }
}

/** Library while it loads, in its own layout: the pinned tiles, the kinds and a first row of covers. */
@Composable
private fun LibrarySkeleton(columns: Int) {
    val pulse = rememberPlaceholderPulse()
    Column(Modifier.clearAndSetSemantics { contentDescription = "Loading your library" }) {
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

private fun LazyListScope.downloadsSection(downloads: List<SongDownload>, onPlay: (List<Track>, Int) -> Unit, actions: LibraryActions) {
    item(key = "downloads-heading") {
        val summary = downloads.takeIf { it.isNotEmpty() }?.let { "${songCount(it.size)} · ${formatBytes(it.sumOf { download -> download.bytes })}" }
        SectionHeader("Downloads", Modifier.padding(top = 16.dp), subtitle = summary)
    }
    if (downloads.isEmpty()) {
        item(key = "downloads-empty") { LibraryMessage("Download a song from its menu, or a whole album with Download all, to play it offline.") }
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
            IconButton(onClick = onRetry) { Icon(SitaraIcons.Download, "Download $title again", tint = colors.onSurfaceVariant) }
        }
        val (icon, label) = when (download.state) {
            DownloadState.DOWNLOADED -> SitaraIcons.Delete to "Delete the download of $title"
            DownloadState.FAILED -> SitaraIcons.Close to "Remove $title from downloads"
            else -> SitaraIcons.Close to "Cancel the download of $title"
        }
        IconButton(onClick = onRemove) { Icon(icon, label, tint = colors.onSurfaceVariant) }
    }
}

private fun downloadLabel(download: SongDownload): String = when (download.state) {
    DownloadState.DOWNLOADED -> formatBytes(download.bytes)
    DownloadState.DOWNLOADING -> download.percent?.let { "$it%" } ?: "Downloading"
    DownloadState.WAITING -> "Waiting"
    DownloadState.WAITING_FOR_WIFI -> "Waiting for Wi-Fi"
    DownloadState.WAITING_FOR_NETWORK -> "Waiting for a connection"
    DownloadState.FAILED -> "Failed"
}

internal fun songCount(count: Int) = if (count == 1) "1 song" else "$count songs"

@Composable
internal fun LibraryMessage(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp))
}
