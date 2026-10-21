package app.sitara.music.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkTile
import app.sitara.music.core.designsystem.CollectionRow
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SelectionPill
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.model.LibraryLayout
import app.sitara.music.core.model.LibrarySort
import app.sitara.music.core.model.LocalFolder
import app.sitara.music.core.model.LocalGroup
import app.sitara.music.core.model.LocalSong
import app.sitara.music.core.model.PlayCount
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.albums
import app.sitara.music.core.model.artists
import app.sitara.music.core.model.folders
import java.text.Collator

/** Library → On this phone, as the app reads it from the local index. */
data class LocalLibraryState(
    val songs: List<LocalSong> = emptyList(),
    val folders: List<LocalFolder> = emptyList(),
    /** Whether Sitara may read the phone's music; picked folders work either way. */
    val musicAccess: Boolean = false,
    val scanning: Boolean = false,
)

/** What On this phone lists; one kind at a time. */
enum class LocalKind(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders"),
}

/** Songs order like liked songs; albums, artists and folders by their newest song or by name. */
val LocalKind.sorts: List<LibrarySort>
    get() = if (this == LocalKind.SONGS) LibrarySort.entries else listOf(LibrarySort.RECENTLY_ADDED, LibrarySort.A_TO_Z)

/** The groups of [kind] (not songs) in [sort]. */
fun List<LocalSong>.groups(kind: LocalKind, sort: LibrarySort): List<LocalGroup> {
    val groups = when (kind) {
        LocalKind.SONGS -> return emptyList()
        LocalKind.ALBUMS -> albums()
        LocalKind.ARTISTS -> artists()
        LocalKind.FOLDERS -> folders()
    }
    return if (sort == LibrarySort.A_TO_Z) groups.sortedWith(compareBy(Collator.getInstance()) { it.title }) else groups
}

/** Changes to what On this phone reads; the app backs them with the local index. The shell runs the system's permission and folder pickers. */
interface LocalActions {
    /** Adds the folder the system folder picker returned, by its tree URI. */
    fun addFolder(uri: String)

    /** Reads a folder Sitara lost access to again from what the picker returned; another folder takes its place. */
    fun relinkFolder(folder: LocalFolder, uri: String)

    /** Forgets a picked folder and the songs found in it; likes and playlists keep theirs. */
    fun removeFolder(folder: LocalFolder)

    /** Reads the phone's music and the picked folders again, after the permission changed too. */
    fun rescan()

    object None : LocalActions {
        override fun addFolder(uri: String) = Unit
        override fun relinkFolder(folder: LocalFolder, uri: String) = Unit
        override fun removeFolder(folder: LocalFolder) = Unit
        override fun rescan() = Unit
    }
}

/**
 * On this phone: the music saved on the phone and in folders the listener picked, as songs, albums, artists or folders,
 * in a grid or a list and the chosen order. Without access to the phone's music it says so and offers both ways in;
 * a folder Sitara can no longer read keeps its songs and offers Relink folder.
 */
@Composable
fun LocalScreen(
    state: LocalLibraryState,
    layout: LibraryLayout,
    sort: LibrarySort,
    counts: List<PlayCount>,
    onBack: () -> Unit,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onGroup: (LocalKind, LocalGroup) -> Unit,
    onAllowAccess: () -> Unit,
    onChooseFolder: () -> Unit,
    onRelink: (LocalFolder) -> Unit,
    actions: LocalActions,
    library: LibraryActions,
    contentPadding: PaddingValues,
) {
    var kind by rememberSaveable { mutableStateOf(LocalKind.SONGS) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    val order = sort.takeIf { it in kind.sorts } ?: LibrarySort.RECENTLY_ADDED
    if (sorting) SortSheet("Order ${kind.label.lowercase()} by", kind.sorts, order, library::setLibrarySort) { sorting = false }
    val songs = remember(state.songs, order, counts) { state.songs.map { it.track }.inOrder(order, counts) }
    val groups = remember(state.songs, kind, order) { state.songs.groups(kind, order) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = ((maxWidth - SitaraSpacing.Gutter * 2 + TileGap) / (TileWidth + TileGap)).toInt().coerceAtLeast(2)
        LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("local-content"), contentPadding = contentPadding) {
            item(key = "header") { PageHeader("On this phone", onBack, if (state.scanning) "Looking for music…" else null) }
            if (state.songs.isEmpty()) {
                item(key = "empty") { NoLocalMusic(state.musicAccess, onAllowAccess, onChooseFolder) }
            } else {
                if (!state.musicAccess) item(key = "access") { AccessCard(onAllowAccess) }
                item(key = "play") {
                    Row(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Play", { onPlay(songs, 0) })
                        PillButton("Shuffle", { onShuffle(songs) }, primary = false)
                    }
                }
                item(key = "kinds") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = SitaraSpacing.Gutter).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LocalKind.entries.forEach { SelectionPill(it.label, it == kind, { kind = it }) }
                    }
                }
                item(key = "controls") { OrderAndLayout(sortable = true, order, layout, onSort = { sorting = true }, onLayout = library::setLibraryLayout) }
                val grid = layout.takeIf { it == LibraryLayout.GRID }?.let { columns }
                if (kind == LocalKind.SONGS) {
                    collection(
                        songs.withIndex().toList(), grid, "", key = { "local-song-${it.value.id}" },
                        tile = { (index, track), tile -> ArtworkTile(track.title, track.artist, track.artwork, { onPlay(songs, index) }, tile, onLongClick = { onTrackMenu(track) }) },
                        row = { (index, track) -> TrackRow(track, { onPlay(songs, index) }, Modifier.testTag(ROW_TAG), onMore = { onTrackMenu(track) }) },
                    )
                } else {
                    collection(
                        groups, grid, "", key = { "local-${kind.name}-${it.key}" },
                        tile = { group, tile -> ArtworkTile(group.title, group.subtitle, group.artwork, { onGroup(kind, group) }, tile, round = kind == LocalKind.ARTISTS) },
                        row = { group -> CollectionRow(group.title, group.subtitle, group.artwork, { onGroup(kind, group) }, Modifier.libraryRow()) },
                    )
                }
            }
            if (state.folders.isNotEmpty() || state.songs.isNotEmpty()) pickedFolders(state.folders, onChooseFolder, onRelink, actions::removeFolder)
        }
    }
}

/** An album, artist or folder of songs on the phone: Play, Shuffle and its songs. */
@Composable
fun LocalGroupScreen(
    group: LocalGroup?,
    onBack: () -> Unit,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onTrackMenu: (Track) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("local-group"), contentPadding = contentPadding) {
        item(key = "header") { PageHeader(group?.title ?: "On this phone", onBack, group?.subtitle) }
        if (group == null) {
            item(key = "gone") { LibraryMessage("These songs are no longer on this phone.") }
            return@LazyColumn
        }
        item(key = "play") {
            Row(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Play", { onPlay(group.songs, 0) })
                PillButton("Shuffle", { onShuffle(group.songs) }, primary = false)
            }
        }
        itemsIndexed(group.songs, key = { _, track -> "song-${track.id}" }) { index, track ->
            TrackRow(track, { onPlay(group.songs, index) }, Modifier.testTag(ROW_TAG), onMore = { onTrackMenu(track) })
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit, subtitle: String?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassIconButton(SitaraIcons.Back, "Back", onBack)
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** Nothing found yet: why, and the ways in that are left (access to the phone's music, a folder). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoLocalMusic(musicAccess: Boolean, onAllowAccess: () -> Unit, onChooseFolder: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FannedCovers()
        Text(
            if (musicAccess) "No music found on this phone" else "Play the music on your phone",
            style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
        )
        Text(
            if (musicAccess) {
                "Songs saved on this phone show up here. Keep music somewhere else, like a card or a downloads folder? Choose that folder."
            } else {
                "Let Sitara see the songs saved on this phone, or choose just the folders it may read. Your files stay where they are."
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!musicAccess) PillButton("Allow access", onAllowAccess)
            PillButton("Choose a folder", onChooseFolder, primary = musicAccess)
        }
    }
}

/** Songs from picked folders are listed, the phone's own are not: one way to add them. */
@Composable
private fun AccessCard(onAllowAccess: () -> Unit) {
    Row(
        Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(SitaraRadius.Tile))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Songs saved elsewhere on this phone aren’t listed yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        PillButton("Allow access", onAllowAccess)
    }
}

/** Folders the listener picked, each with its songs or a way to relink it, and Choose a folder. */
private fun LazyListScope.pickedFolders(folders: List<LocalFolder>, onChooseFolder: () -> Unit, onRelink: (LocalFolder) -> Unit, onRemove: (LocalFolder) -> Unit) {
    item(key = "picked-heading") {
        SectionHeader("Picked folders", Modifier.padding(top = 16.dp), subtitle = "Folders Sitara reads besides the phone’s music")
    }
    items(folders, key = { "picked-${it.uri}" }) { folder -> PickedFolderRow(folder, { onRelink(folder) }, { onRemove(folder) }) }
    item(key = "choose-folder") {
        PillButton("Choose a folder", onChooseFolder, Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), primary = false)
    }
}

@Composable
private fun PickedFolderRow(folder: LocalFolder, onRelink: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = SitaraSpacing.Gutter, end = 8.dp, top = 4.dp, bottom = 4.dp).testTag("picked-folder"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(SitaraIcons.Folder, null, tint = if (folder.linked) colors.primary else colors.error, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (folder.linked) songCount(folder.songCount) else "Sitara can no longer read this folder · ${songCount(folder.songCount)} kept",
                style = MaterialTheme.typography.bodySmall, color = if (folder.linked) colors.onSurfaceVariant else colors.error,
            )
        }
        if (!folder.linked) PillButton("Relink folder", onRelink, primary = false)
        IconButton(onClick = onRemove) { Icon(SitaraIcons.Close, "Remove ${folder.name} and its songs from Sitara", tint = colors.onSurfaceVariant) }
    }
}
