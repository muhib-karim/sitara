package app.sitara.music.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.model.CollectionPreview
import app.sitara.music.core.model.Track

/** The songs a detail page holds, or null while it loads or when it failed. */
fun DetailState.songs(): List<Track>? = when (this) {
    is DetailState.AlbumReady -> album.tracks
    is DetailState.PlaylistReady -> playlist.tracks
    is DetailState.ArtistReady -> page.topSongs
    is DetailState.UserPlaylistReady -> playlist.tracks
    is DetailState.LikedReady -> tracks
    DetailState.Loading, DetailState.Failed, DetailState.Gone -> null
}

/**
 * A long press on an album, playlist or artist: its cover and name at once, its first songs as they load, and Play,
 * Shuffle, Open and Share, without leaving the screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionPreviewSheet(
    preview: CollectionPreview,
    state: DetailState,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onShare: (() -> Unit)?,
) {
    val songs = state.songs()
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp).testTag("collection-preview")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val round = preview.kind == CollectionPreview.Kind.ARTIST
            ArtworkImage(preview.artwork, Modifier.size(72.dp), cornerRadius = if (round) 36.dp else SitaraRadius.Cover)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    preview.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
                Text(preview.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!songs.isNullOrEmpty()) {
                PillButton("Play", { onPlay(songs, 0) })
                PillButton("Shuffle", { onShuffle(songs) }, primary = false)
            }
            PillButton("Open", onOpen, primary = false)
            if (onShare != null) PillButton("Share", onShare, primary = false)
        }
        when {
            state == DetailState.Failed -> Column(Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Couldn’t load the songs. Check your connection, then try again.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PillButton("Try again", onRetry, primary = false)
            }
            songs == null -> Text(
                "Loading songs…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 12.dp),
            )
            songs.isEmpty() -> Text(
                "No songs here yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 12.dp),
            )
            else -> songs.take(PREVIEW_SONGS).forEachIndexed { index, track -> TrackRow(track, { onPlay(songs, index) }, moreButton = false) }
        }
        if (songs != null && songs.size > PREVIEW_SONGS) {
            Text(
                "and ${songs.size - PREVIEW_SONGS} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
            )
        }
    }
}

private const val PREVIEW_SONGS = 5
