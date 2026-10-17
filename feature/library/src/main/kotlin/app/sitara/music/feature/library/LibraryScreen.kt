package app.sitara.music.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkTile
import app.sitara.music.core.designsystem.CollectionRow
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.SelectionPill
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.Track

enum class LibraryFilter(val label: String) { PLAYLISTS("Playlists"), ALBUMS("Albums"), ARTISTS("Artists"), SONGS("Liked songs") }
private enum class LibraryLayout { GRID, LIST }

@Composable
fun LibraryScreen(
    library: Library,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    initialFilter: LibraryFilter = LibraryFilter.PLAYLISTS,
) {
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var layout by rememberSaveable { mutableStateOf(LibraryLayout.GRID) }
    val grid = layout == LibraryLayout.GRID
    val count = when (filter) {
        LibraryFilter.PLAYLISTS -> library.playlists.size
        LibraryFilter.ALBUMS -> library.albums.size
        LibraryFilter.ARTISTS -> library.artists.size
        LibraryFilter.SONGS -> library.likedTracks.size
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) 2 else 1),
        modifier = modifier.fillMaxSize().testTag(if (grid) "library-grid" else "library-list"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) { LargeTitle("Library") }
        item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(contentPadding = PaddingValues(horizontal = SitaraSpacing.Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LibraryFilter.entries.size) { index ->
                    val value = LibraryFilter.entries[index]
                    SelectionPill(value.label, value == filter, { filter = value })
                }
            }
        }
        item(key = "layout", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$count ${if (filter == LibraryFilter.SONGS) "songs" else filter.label.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                GlassIconButton(
                    if (grid) SitaraIcons.List else SitaraIcons.Grid,
                    if (grid) "Show list" else "Show grid",
                    onClick = { layout = if (grid) LibraryLayout.LIST else LibraryLayout.GRID },
                )
            }
        }
        if (filter == LibraryFilter.PLAYLISTS) {
            item(key = "liked") {
                LikedTile(library.likedTracks.size, grid, Modifier.padding(start = SitaraSpacing.Gutter, end = if (grid) 8.dp else SitaraSpacing.Gutter)) {
                    filter = LibraryFilter.SONGS
                }
            }
        }
        if (count == 0) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (filter == LibraryFilter.SONGS) "No liked songs yet" else "No ${filter.label.lowercase()} in your library yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(SitaraSpacing.Gutter),
                )
            }
        }
        when (filter) {
            LibraryFilter.PLAYLISTS -> items(library.playlists.size, key = { "playlist-${library.playlists[it].id}" }) { index ->
                val playlist = library.playlists[index]
                LibraryEntry(
                    playlist.title, "${playlist.tracks.size} songs · ${playlist.description}", playlist.artwork, grid, index + 1,
                    enabled = playlist.tracks.isNotEmpty(),
                ) {
                    onPlay(playlist.tracks, 0)
                }
            }
            LibraryFilter.ALBUMS -> items(library.albums.size, key = { "album-${library.albums[it].id}" }) { index ->
                val album = library.albums[index]
                LibraryEntry(album.title, "${album.artist} · ${album.year}", album.artwork, grid, index) { onAlbum(album) }
            }
            LibraryFilter.ARTISTS -> items(library.artists.size, key = { "artist-${library.artists[it].id}" }) { index ->
                val artist = library.artists[index]
                LibraryEntry(artist.name, "Artist", artist.artwork, grid, index, round = true) { onArtist(artist) }
            }
            LibraryFilter.SONGS -> items(library.likedTracks.size, key = { "track-${library.likedTracks[it].id}" }) { index ->
                val track = library.likedTracks[index]
                if (grid) {
                    LibraryEntry(track.title, track.artist, track.artwork, true, index) { onPlay(library.likedTracks, index) }
                } else {
                    TrackRow(track, { onPlay(library.likedTracks, index) })
                }
            }
        }
    }
}

@Composable
private fun LibraryEntry(
    title: String,
    subtitle: String,
    artwork: Artwork,
    grid: Boolean,
    index: Int,
    round: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val gutter = SitaraSpacing.Gutter
    val modifier = Modifier.padding(start = if (!grid || index % 2 == 0) gutter else 8.dp, end = if (!grid || index % 2 == 1) gutter else 8.dp)
    if (grid) {
        ArtworkTile(title, subtitle, artwork, onClick, modifier, round, enabled)
    } else {
        CollectionRow(title, subtitle, artwork, onClick, modifier, enabled)
    }
}

@Composable
private fun LikedTile(count: Int, grid: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(SitaraRadius.Tile)).clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Open liked songs" },
    ) {
        if (grid) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(SitaraRadius.Tile))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SitaraIcons.Heart, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            }
        }
        Text(
            "Liked songs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "$count songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}
