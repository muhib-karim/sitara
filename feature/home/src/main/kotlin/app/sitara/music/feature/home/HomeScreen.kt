package app.sitara.music.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Shelf
import app.sitara.music.core.model.Track

@Composable
fun HomeScreen(
    greeting: String,
    shelves: List<Shelf>,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        item(key = "title") { LargeTitle(greeting) }
        for (shelf in shelves) {
            item(key = "h-${shelf.id}") { SectionHeader(shelf.title, subtitle = shelf.subtitle, modifier = Modifier.padding(top = 12.dp)) }
            when {
                shelf.id == "quick" -> items(shelf.tracks, key = { "q-${it.id}" }) { t ->
                    TrackRow(t, onClick = { onPlay(shelf.tracks, shelf.tracks.indexOf(t)) })
                }
                shelf.tracks.isNotEmpty() -> item(key = "r-${shelf.id}") {
                    CardRow(shelf.tracks.map { CardItem(it.title, it.artist, it.artwork) }) { i -> onPlay(shelf.tracks, i) }
                }
                shelf.albums.isNotEmpty() -> item(key = "r-${shelf.id}") {
                    CardRow(shelf.albums.map { CardItem(it.title, "${it.artist} · ${it.year}", it.artwork) }, size = 168.dp) { i -> onPlay(shelf.albums[i].tracks, 0) }
                }
                shelf.playlists.isNotEmpty() -> item(key = "r-${shelf.id}") {
                    CardRow(shelf.playlists.map { CardItem(it.title, it.description, it.artwork) }, size = 168.dp) { i -> onPlay(shelf.playlists[i].tracks, 0) }
                }
            }
        }
    }
}

private data class CardItem(val title: String, val subtitle: String, val artwork: Artwork)

@Composable
private fun CardRow(cards: List<CardItem>, size: Dp = 140.dp, onClick: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp)) {
        items(cards.size) { i ->
            val card = cards[i]
            Column(
                Modifier
                    .width(size)
                    .clickable(role = Role.Button) { onClick(i) }
                    .padding(end = 14.dp),
            ) {
                ArtworkImage(card.artwork, Modifier.fillMaxWidth(), cornerRadius = 14.dp)
                Spacer(Modifier.height(8.dp))
                Text(card.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Text(card.subtitle, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
