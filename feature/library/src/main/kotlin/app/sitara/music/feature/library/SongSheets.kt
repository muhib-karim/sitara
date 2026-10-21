package app.sitara.music.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.CollectionRow
import app.sitara.music.core.designsystem.EmptyCollectionArtwork
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.model.DownloadState
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.isLocal

/** What can be done with one song: like it, queue it, put it in a playlist, or download it ([download] is where its download stands). */
@Composable
fun SongActions(
    track: Track,
    liked: Boolean,
    download: DownloadState?,
    onToggleLike: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
        SheetTrackHeading(track)
        SheetAction(if (liked) SitaraIcons.HeartFilled else SitaraIcons.Heart, if (liked) "Remove from liked songs" else "Like", onToggleLike)
        SheetAction(SitaraIcons.Next, "Play next", onPlayNext)
        SheetAction(SitaraIcons.Queue, "Add to queue", onAddToQueue)
        SheetAction(SitaraIcons.Add, "Add to playlist", onAddToPlaylist)
        // A song on the phone is already a file; there is nothing to download.
        if (!track.isLocal) {
            when (download) {
                null, DownloadState.FAILED -> SheetAction(SitaraIcons.Download, "Download", onDownload)
                DownloadState.DOWNLOADED -> SheetAction(SitaraIcons.Delete, "Remove download", onRemoveDownload)
                else -> SheetAction(SitaraIcons.Close, "Cancel download", onRemoveDownload)
            }
        }
    }
}

/** Picks a playlist for [track], or names a new one that starts with it. */
@Composable
fun AddToPlaylist(track: Track, playlists: List<PlaylistSummary>, onPick: (PlaylistSummary) -> Unit, onCreate: (name: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp).testTag("playlist-picker")) {
        SheetTrackHeading(track)
        Text("Add to playlist", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(MAX_NAME_LENGTH) }, Modifier.weight(1f), singleLine = true, label = { Text("New playlist") })
            if (name.isNotBlank()) PillButton("Create", { onCreate(name.trim()) })
        }
        if (playlists.isEmpty()) {
            LibraryMessage("No playlists yet. Name one above and this song starts it.")
        } else {
            LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    CollectionRow(
                        playlist.name, songCount(playlist.songCount), EmptyCollectionArtwork, { onPick(playlist) },
                        Modifier.padding(horizontal = SitaraSpacing.Gutter), covers = playlist.covers,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetTrackHeading(track: Track) {
    Row(Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ArtworkImage(track.artwork, Modifier.size(48.dp), cornerRadius = SitaraRadius.Cover)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = SitaraSpacing.Gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
