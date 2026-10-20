package app.sitara.music.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.CollectionRow
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SelectionPill
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.designsystem.byline
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.Track

private enum class BrowseCategory(val label: String) { ALBUMS("Albums"), ARTISTS("Artists"), PLAYLISTS("Playlists"), LIKED("Liked songs") }

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    state: SearchState,
    onRetry: () -> Unit,
    suggestions: List<String>,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    library: Library,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var category by rememberSaveable { mutableStateOf<BrowseCategory?>(null) }
    LazyColumn(modifier.fillMaxSize().testTag("search-content"), contentPadding = contentPadding) {
        item(key = "title") { LargeTitle("Search") }
        item(key = "field") { SearchField(query, onQueryChange) }
        if (query.isBlank()) {
            val selected = category
            if (selected == null) {
                item(key = "browse-title") { SectionHeader("Browse the sample library", modifier = Modifier.padding(top = 20.dp)) }
                item(key = "categories") {
                    Column(Modifier.padding(horizontal = SitaraSpacing.Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BrowseCategory.entries.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { value -> CategoryTile(value, categoryCount(value, library), Modifier.weight(1f)) { category = value } }
                            }
                        }
                    }
                }
                if (suggestions.isNotEmpty()) {
                    item(key = "suggestions-title") { SectionHeader("Try a search", modifier = Modifier.padding(top = 20.dp)) }
                    items(suggestions.distinct(), key = { "suggestion-$it" }) { suggestion ->
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { onQueryChange(suggestion) }
                                .padding(horizontal = SitaraSpacing.Gutter, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(SitaraIcons.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Text(
                                suggestion, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            } else {
                item(key = "category-title") {
                    Row(Modifier.padding(start = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(SitaraIcons.Back, "All browse categories", { category = null })
                        SectionHeader(selected.label)
                    }
                }
                browseItems(selected, library, onPlay, onAlbum, onArtist, onPlaylist)
            }
        } else {
            item(key = "filters") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = SitaraSpacing.Gutter, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchFilter.entries.forEach { SelectionPill(it.label, it == filter, { onFilterChange(it) }) }
                }
            }
            val current = query.trim()
            when {
                state is SearchState.Loaded && state.query == current && state.filter == filter -> searchResults(state.results, filter, onPlay, onAlbum, onArtist, onPlaylist)
                state is SearchState.Failed && state.query == current && state.filter == filter -> item(key = "failed") {
                    Column(Modifier.padding(SitaraSpacing.Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Search isn’t working right now. Check your connection, then try again.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
                        )
                        PillButton("Try again", onRetry)
                    }
                }
                else -> item(key = "searching") { SearchMessage("Searching…") }
            }
        }
    }
}

private fun LazyListScope.searchResults(
    results: SearchResults,
    filter: SearchFilter,
    onPlay: (List<Track>, Int) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onPlaylist: (Playlist) -> Unit,
) {
    if (results.isEmpty) {
        item(key = "no-results") { SearchMessage("No ${filter.label.lowercase()} found for “${results.query}”.") }
        return
    }
    if (results.tracks.isNotEmpty()) {
        item(key = "songs-title") { SectionHeader("Songs", subtitle = if (results.tracks.size == 1) "1 result" else "${results.tracks.size} results", modifier = Modifier.padding(top = 20.dp)) }
        itemsIndexed(results.tracks, key = { _, track -> "song-${track.id}" }) { index, track ->
            TrackRow(track, { onPlay(results.tracks, index) })
        }
    }
    if (results.albums.isNotEmpty()) {
        item(key = "albums-title") { SectionHeader("Albums", modifier = Modifier.padding(top = 20.dp)) }
        items(results.albums, key = { "album-${it.id}" }) { album ->
            CollectionRow(album.title, album.byline, album.artwork, { onAlbum(album) }, Modifier.padding(horizontal = SitaraSpacing.Gutter))
        }
    }
    if (results.artists.isNotEmpty()) {
        item(key = "artists-title") { SectionHeader("Artists", modifier = Modifier.padding(top = 20.dp)) }
        items(results.artists, key = { "artist-${it.id}" }) { artist ->
            CollectionRow(artist.name, "Artist", artist.artwork, { onArtist(artist) }, Modifier.padding(horizontal = SitaraSpacing.Gutter))
        }
    }
    if (results.playlists.isNotEmpty()) {
        item(key = "playlists-title") { SectionHeader("Playlists", modifier = Modifier.padding(top = 20.dp)) }
        items(results.playlists, key = { "playlist-${it.id}" }) { playlist ->
            CollectionRow(playlist.title, playlist.curator.ifEmpty { "Playlist" }, playlist.artwork, { onPlaylist(playlist) }, Modifier.padding(horizontal = SitaraSpacing.Gutter))
        }
    }
}

private fun categoryCount(category: BrowseCategory, library: Library): Int = when (category) {
    BrowseCategory.ALBUMS -> library.albums.size
    BrowseCategory.ARTISTS -> library.artists.size
    BrowseCategory.PLAYLISTS -> library.playlists.size
    BrowseCategory.LIKED -> library.likedTracks.size
}

private fun LazyListScope.browseItems(
    category: BrowseCategory,
    library: Library,
    onPlay: (List<Track>, Int) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onPlaylist: (Playlist) -> Unit,
) {
    if (categoryCount(category, library) == 0) {
        item { SearchMessage("No ${category.label.lowercase()} in your library yet") }
    }
    when (category) {
        BrowseCategory.ALBUMS -> items(library.albums, key = { "album-${it.id}" }) { album ->
            CollectionRow(album.title, album.artist, album.artwork, { onAlbum(album) }, Modifier.padding(horizontal = SitaraSpacing.Gutter))
        }
        BrowseCategory.ARTISTS -> items(library.artists, key = { "artist-${it.id}" }) { artist ->
            CollectionRow(artist.name, "Artist", artist.artwork, { onArtist(artist) }, Modifier.padding(horizontal = SitaraSpacing.Gutter))
        }
        BrowseCategory.PLAYLISTS -> items(library.playlists, key = { "playlist-${it.id}" }) { playlist ->
            CollectionRow(playlist.title, "${playlist.tracks.size} songs", playlist.artwork, { onPlaylist(playlist) }, Modifier.padding(horizontal = SitaraSpacing.Gutter))
        }
        BrowseCategory.LIKED -> itemsIndexed(library.likedTracks, key = { _, track -> "liked-${track.id}" }) { index, track ->
            TrackRow(track, { onPlay(library.likedTracks, index) })
        }
    }
}

@Composable
private fun CategoryTile(category: BrowseCategory, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.defaultMinSize(minHeight = 124.dp).clip(RoundedCornerShape(SitaraRadius.Tile))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            when (category) {
                BrowseCategory.ALBUMS -> SitaraIcons.Grid
                BrowseCategory.ARTISTS -> SitaraIcons.Star
                BrowseCategory.PLAYLISTS -> SitaraIcons.Library
                BrowseCategory.LIKED -> SitaraIcons.Heart
            },
            null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp),
        )
        Text(category.label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        Text("$count in your library", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    Row(
        Modifier.padding(horizontal = SitaraSpacing.Gutter).fillMaxWidth().defaultMinSize(minHeight = 56.dp)
            .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SitaraIcons.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = query, onValueChange = onQueryChange, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.weight(1f).padding(vertical = 16.dp).semantics { contentDescription = "Search" },
            decorationBox = { input ->
                Box {
                    if (query.isEmpty()) Text("Songs, albums, artists", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    input()
                }
            },
        )
        if (query.isNotEmpty()) {
            GlassIconButton(SitaraIcons.Close, "Clear search", { onQueryChange("") })
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun SearchMessage(message: String) {
    Text(
        message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(SitaraSpacing.Gutter),
    )
}
