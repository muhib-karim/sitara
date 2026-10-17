package app.sitara.music.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkTile
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SelectionPill
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.ListeningMood
import app.sitara.music.core.model.Shelf
import app.sitara.music.core.model.Track

@Composable
fun HomeScreen(
    greeting: String,
    shelves: List<Shelf>,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onAlbum: (Album) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
) {
    var mood by rememberSaveable { mutableStateOf<ListeningMood?>(null) }
    val available = remember(shelves) { shelves.flatMap { it.playlists }.flatMap { it.moods }.toSet() }
    val selectedMood = mood?.takeIf { it in available }
    val visible = remember(shelves, selectedMood) { shelvesForMood(shelves, selectedMood) }
    LazyColumn(modifier.fillMaxSize().testTag("home-content"), state = listState, contentPadding = contentPadding) {
        item(key = "title") { LargeTitle(greeting) }
        item(key = "moods") {
            LazyRow(contentPadding = PaddingValues(horizontal = SitaraSpacing.Gutter, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SelectionPill("For you", selectedMood == null, { mood = null }) }
                items(ListeningMood.entries.filter { it in available }) { value ->
                    SelectionPill(value.label, selectedMood == value, { mood = value })
                }
            }
        }
        if (visible.isEmpty()) {
            item { SectionHeader("A little quiet here", subtitle = "Your music collections will appear here.") }
        }
        for (shelf in visible) {
            item(key = "h-${shelf.id}") {
                SectionHeader(shelf.title, subtitle = shelf.subtitle, modifier = Modifier.padding(top = 12.dp))
            }
            item(key = "r-${shelf.id}") {
                when {
                    shelf.id == "quick" && shelf.tracks.isNotEmpty() -> key(selectedMood) { QuickPicks(shelf.tracks, onPlay) }
                    else -> ArtworkRail(shelf, onPlay, onAlbum)
                }
            }
        }
    }
}

@Composable
private fun QuickPicks(tracks: List<Track>, onPlay: (List<Track>, Int) -> Unit) {
    val pages = remember(tracks) { quickPickPages(tracks) }
    val pager = rememberPagerState { pages.size }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val columns = if (availableWidth / LocalDensity.current.fontScale < 320.dp) 1 else 2
        Column {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().testTag("quick-picks")) { page ->
                Column(Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter)) {
                    pages[page].chunked(columns).forEachIndexed { row, pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEachIndexed { column, track ->
                                TrackRow(
                                    track,
                                    onClick = { onPlay(tracks, page * 4 + row * columns + column) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                )
                            }
                            if (pair.size < columns) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (pages.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${pages.size} · Swipe for more",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtworkRail(shelf: Shelf, onPlay: (List<Track>, Int) -> Unit, onAlbum: (Album) -> Unit) {
    LazyRow(
        modifier = Modifier.testTag("rail-${shelf.id}"),
        contentPadding = PaddingValues(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            shelf.tracks.isNotEmpty() -> items(shelf.tracks.size, key = { shelf.tracks[it].id }) { index ->
                val track = shelf.tracks[index]
                ArtworkTile(track.title, track.artist, track.artwork, { onPlay(shelf.tracks, index) }, Modifier.width(156.dp))
            }
            shelf.albums.isNotEmpty() -> items(shelf.albums, key = { it.id }) { album ->
                ArtworkTile(album.title, "${album.artist} · ${album.year}", album.artwork, { onAlbum(album) }, Modifier.width(164.dp))
            }
            shelf.playlists.isNotEmpty() -> items(shelf.playlists, key = { it.id }) { playlist ->
                ArtworkTile(
                    playlist.title, playlist.description, playlist.artwork,
                    { onPlay(playlist.tracks, 0) }, Modifier.width(164.dp),
                    enabled = playlist.tracks.isNotEmpty(),
                )
            }
        }
    }
}
