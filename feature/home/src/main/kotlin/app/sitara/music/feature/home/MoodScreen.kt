package app.sitara.music.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.ShelfLink
import app.sitara.music.core.model.Track

/** A mood or genre's page from the catalogue: its shelves drawn like Home's, with placeholders while it loads. */
@Composable
fun MoodScreen(
    title: String,
    state: FeedState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onAlbum: (Album) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onArtist: (Artist) -> Unit,
    onShelfLink: (ShelfLink) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("mood-content"), contentPadding = contentPadding) {
        item(key = "header") {
            Row(
                Modifier.padding(start = 12.dp, end = SitaraSpacing.Gutter, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassIconButton(SitaraIcons.Back, "Back", onBack)
                Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
            }
        }
        when (state) {
            FeedState.Loading -> {
                item(key = "loading", contentType = "skeleton") { RowsSkeleton(label = "Loading $title") }
                items(2, key = { "loading-$it" }, contentType = { "skeleton" }) { CarouselSkeleton() }
            }
            FeedState.Failed -> item(key = "failed") {
                Column(Modifier.padding(SitaraSpacing.Gutter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "$title didn’t load. Check your connection, then try again.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
                    )
                    PillButton("Try again", onRetry)
                }
            }
            is FeedState.Ready -> if (state.shelves.isEmpty()) {
                item(key = "nothing") { HomeMessage("Nothing under $title that Sitara can play yet.") }
            } else {
                state.shelves.forEachIndexed { index, shelf -> feedShelf("$title-$index", index, shelf, onPlay, onTrackMenu, onAlbum, onPlaylist, onArtist, onShelfLink) }
            }
        }
    }
}
