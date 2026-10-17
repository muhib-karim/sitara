package app.sitara.music.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.Track

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: SearchResults,
    suggestions: List<String>,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { LargeTitle("Search") }
        item {
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SitaraIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Songs, albums, artists", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search" },
                    )
                }
            }
        }
        if (query.isBlank()) {
            item { SectionHeader("Try", modifier = Modifier.padding(top = 16.dp)) }
            items(suggestions) { s ->
                Text(
                    s,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        } else if (results.isEmpty) {
            item {
                Text(
                    "Nothing found for “$query”",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        } else {
            if (results.artists.isNotEmpty()) {
                item { SectionHeader("Artists", modifier = Modifier.padding(top = 16.dp)) }
                item {
                    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        results.artists.take(4).forEach { a ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ArtworkImage(a.artwork, Modifier.size(72.dp), cornerRadius = 36.dp)
                                Text(a.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (results.tracks.isNotEmpty()) {
                item { SectionHeader("Songs", modifier = Modifier.padding(top = 16.dp)) }
                items(results.tracks, key = { it.id }) { t -> TrackRow(t, onClick = { onPlay(results.tracks, results.tracks.indexOf(t)) }) }
            }
            if (results.albums.isNotEmpty()) {
                item { SectionHeader("Albums", modifier = Modifier.padding(top = 16.dp)) }
                items(results.albums, key = { it.id }) { a ->
                    TrackRow(a.tracks.first().copy(title = a.title, artist = "${a.artist} · ${a.year}", artwork = a.artwork), onClick = { onPlay(a.tracks, 0) })
                }
            }
        }
    }
}
