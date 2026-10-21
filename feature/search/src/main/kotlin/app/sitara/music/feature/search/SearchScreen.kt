package app.sitara.music.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkPalette
import app.sitara.music.core.designsystem.CollectionRow
import app.sitara.music.core.designsystem.GlassIconButton
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
import app.sitara.music.core.designsystem.rememberPlaceholderPulse
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Mood
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.Track

/**
 * Search. With nothing typed: recent searches (or how to start one) and the catalogue's Moods & genres. While typing:
 * suggestions, the filter chips and the results. A search is kept as recent once it is sent from the keyboard, picked
 * from a list, or one of its results is played or opened.
 */
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    state: SearchState,
    onRetry: () -> Unit,
    landing: SearchLanding,
    actions: SearchActions,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onMood: (Mood) -> Unit,
    onTrackMenu: (Track) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    /** Songs on the phone that match the query, listed first with a badge; they need no connection. */
    onThisPhone: List<Track> = emptyList(),
) {
    val field = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    if (confirmClear) ClearRecentSheet(onConfirm = actions::clearRecentSearches, onDismiss = { confirmClear = false })
    // Sending a search closes the keyboard itself, so no part of it stays open over the dock.
    val send = { searched: String ->
        keyboard?.hide()
        focus.clearFocus()
        if (searched.isNotBlank()) actions.saveSearch(searched.trim())
    }
    val pick = { picked: String ->
        onQueryChange(picked)
        send(picked)
    }
    LazyColumn(modifier.fillMaxSize().testTag("search-content"), contentPadding = contentPadding) {
        item(key = "title") { LargeTitle("Search") }
        item(key = "field") { SearchField(query, onQueryChange, onSearch = { send(query) }, Modifier.focusRequester(field)) }
        if (query.isBlank()) {
            recentSearches(landing.recent, pick, actions::removeRecentSearch, onClearAll = { confirmClear = true }, onStart = { field.requestFocus() })
            moods(landing.moods, onMood, actions::retryMoods)
        } else {
            if (landing.suggestions.isNotEmpty()) item(key = "suggestions") { Suggestions(landing.suggestions, pick) }
            item(key = "filters") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = SitaraSpacing.Gutter, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchFilter.entries.forEach { SelectionPill(it.label, it == filter, { onFilterChange(it) }) }
                }
            }
            val current = query.trim()
            val kept = { actions.saveSearch(current) }
            if (filter == SearchFilter.SONGS) {
                itemsIndexed(onThisPhone, key = { _, track -> "phone-${track.id}" }) { index, track ->
                    TrackRow(track, { kept(); onPlay(onThisPhone, index) }, onMore = { onTrackMenu(track) }) { OnThisPhoneBadge() }
                }
            }
            when {
                state is SearchState.Loaded && state.query == current && state.filter == filter -> searchResults(
                    state.results, filter,
                    onPlay = { tracks, index -> kept(); onPlay(tracks, index) },
                    onAlbum = { kept(); onAlbum(it) },
                    onArtist = { kept(); onArtist(it) },
                    onPlaylist = { kept(); onPlaylist(it) },
                    onTrackMenu = onTrackMenu,
                )
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

private fun LazyListScope.recentSearches(recent: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit, onClearAll: () -> Unit, onStart: () -> Unit) {
    if (recent.isEmpty()) {
        item(key = "recent-empty") { NoRecentSearches(onStart) }
        return
    }
    item(key = "recent-title") {
        SectionHeader("Recent searches", Modifier.padding(top = 12.dp), action = { HeaderAction("Clear all", onClearAll, description = "Clear all recent searches") })
    }
    items(recent.take(SHOWN_RECENT_SEARCHES), key = { "recent-$it" }) { searched ->
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Search again") { onPick(searched) }
                .padding(start = SitaraSpacing.Gutter, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SitaraIcons.Recent, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(
                searched, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 16.dp),
            )
            IconButton(onClick = { onRemove(searched) }) {
                Icon(SitaraIcons.Close, "Remove $searched from recent searches", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun NoRecentSearches(onStart: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(SitaraRadius.Tile)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SitaraIcons.Recent, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your searches show up here", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Find a song, artist or album, then come back to it in one tap.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PillButton("Start a search", onStart, Modifier.padding(top = 8.dp), primary = false)
        }
    }
}

@Composable
private fun ClearRecentSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    SitaraSheet(onDismiss) { close ->
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Clear recent searches?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text("They are only kept on this phone, so they can’t be brought back.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Clear", { close(onConfirm) })
                PillButton("Keep them", { close {} }, primary = false)
            }
        }
    }
}

private fun LazyListScope.moods(moods: MoodsState, onMood: (Mood) -> Unit, onRetry: () -> Unit) {
    when (moods) {
        MoodsState.Loading -> item(key = "moods-loading", contentType = "skeleton") { MoodsSkeleton() }
        MoodsState.Failed -> item(key = "moods-failed") {
            Column(Modifier.padding(top = 12.dp)) {
                SectionHeader("Moods & genres")
                Text(
                    "Moods and genres didn’t load. Check your connection, then try again.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter),
                )
                PillButton("Try again", onRetry, Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 12.dp))
            }
        }
        is MoodsState.Ready -> moods.sections.forEach { section ->
            item(key = "moods-h-${section.title}") { SectionHeader(section.title, Modifier.padding(top = 12.dp)) }
            items(section.moods.chunked(2), key = { "moods-${section.title}-${it.first().params}" }) { pair ->
                Row(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { mood -> MoodTile(mood, { onMood(mood) }, Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** A colour block in the catalogue's own colour for the mood, its name in black or white, whichever reads better. */
@Composable
private fun MoodTile(mood: Mood, onClick: () -> Unit, modifier: Modifier) {
    val block = mood.colorArgb or OPAQUE
    Box(
        modifier.height(MOOD_TILE_HEIGHT).clip(RoundedCornerShape(SitaraRadius.Tile)).background(Color(block))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            mood.title, color = Color(ArtworkPalette.inkOn(block)), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun MoodsSkeleton() {
    val pulse = rememberPlaceholderPulse()
    Column(Modifier.padding(top = 12.dp).clearAndSetSemantics { contentDescription = "Loading moods and genres" }) {
        PlaceholderBlock(pulse, Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 12.dp).size(160.dp, 20.dp))
        repeat(3) {
            Row(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { PlaceholderBlock(pulse, Modifier.weight(1f).height(MOOD_TILE_HEIGHT), RoundedCornerShape(SitaraRadius.Tile)) }
            }
        }
    }
}

/** Completions of what is typed, as a row of pills above the filters, so the results stay in view. */
@Composable
private fun Suggestions(suggestions: List<String>, onPick: (String) -> Unit) {
    LazyRow(
        Modifier.padding(top = 12.dp).testTag("suggestions"),
        contentPadding = PaddingValues(horizontal = SitaraSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(suggestions, key = { it }) { suggestion ->
            Row(
                Modifier.defaultMinSize(minHeight = SitaraSpacing.TouchTarget).clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(role = Role.Button) { onPick(suggestion) }.semantics { contentDescription = "Search for $suggestion" }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SitaraIcons.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(suggestion, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private const val SHOWN_RECENT_SEARCHES = 8
private const val OPAQUE = 0xFF000000.toInt()
private val MOOD_TILE_HEIGHT = 64.dp

private fun LazyListScope.searchResults(
    results: SearchResults,
    filter: SearchFilter,
    onPlay: (List<Track>, Int) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onTrackMenu: (Track) -> Unit,
) {
    if (results.isEmpty) {
        item(key = "no-results") { SearchMessage("No ${filter.label.lowercase()} found for “${results.query}”.") }
        return
    }
    if (results.tracks.isNotEmpty()) {
        item(key = "songs-title") { SectionHeader("Songs", subtitle = if (results.tracks.size == 1) "1 result" else "${results.tracks.size} results", modifier = Modifier.padding(top = 20.dp)) }
        itemsIndexed(results.tracks, key = { _, track -> "song-${track.id}" }) { index, track ->
            TrackRow(track, { onPlay(results.tracks, index) }, onMore = { onTrackMenu(track) })
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

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier) {
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
            // The search key and a hardware Enter both send the search.
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = modifier.weight(1f).padding(vertical = 16.dp).semantics { contentDescription = "Search" }
                .onPreviewKeyEvent { event ->
                    val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (enter && event.type == KeyEventType.KeyUp) onSearch()
                    enter
                },
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

/** Marks a search result that is a file on the phone rather than a catalogue song. */
@Composable
private fun OnThisPhoneBadge() {
    Row(
        Modifier.padding(start = 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(SitaraIcons.Phone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        Text("On this phone", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}
