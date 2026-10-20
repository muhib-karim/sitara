package app.sitara.music.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkTile
import app.sitara.music.core.designsystem.CollectionRow
import app.sitara.music.core.designsystem.EmptyCollectionArtwork
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.byline
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.Track

/** The listener's own library: liked songs, playlists, recent plays, saved albums and artists. */
@Composable
fun LibraryScreen(
    state: LibraryState,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onLikedSongs: () -> Unit,
    onPlaylist: (PlaylistSummary) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onNewPlaylist: (name: String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    if (naming) {
        PlaylistNameDialog("New playlist", "", "Create", onConfirm = { naming = false; onNewPlaylist(it) }, onDismiss = { naming = false })
    }
    LazyColumn(modifier.fillMaxSize().testTag("library-content"), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "title") { LargeTitle("Library") }
        when (state) {
            LibraryState.Loading -> item(key = "loading") { LibraryMessage("Loading your library…") }
            LibraryState.Failed -> item(key = "failed") { LibraryMessage("Your library couldn’t be read. Restart Sitara to try again.") }
            is LibraryState.Ready -> savedSections(state.library, onPlay, onLikedSongs, onPlaylist, onAlbum, onArtist, onNewPlaylist = { naming = true })
        }
    }
}

private fun LazyListScope.savedSections(
    library: SavedLibrary,
    onPlay: (List<Track>, Int) -> Unit,
    onLikedSongs: () -> Unit,
    onPlaylist: (PlaylistSummary) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    item(key = "liked") { LikedEntry(library.liked.size, onLikedSongs) }

    item(key = "playlists-heading") {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, end = SitaraSpacing.Gutter), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Playlists", Modifier.weight(1f))
            PillButton("New playlist", onNewPlaylist, primary = false)
        }
    }
    if (library.playlists.isEmpty()) {
        item(key = "playlists-empty") { LibraryMessage("Make a playlist to keep songs in your own order.") }
    }
    items(library.playlists, key = { "playlist-${it.id}" }) { playlist ->
        CollectionRow(
            playlist.name, songCount(playlist.songCount), playlist.artwork ?: EmptyCollectionArtwork, { onPlaylist(playlist) },
            Modifier.padding(horizontal = SitaraSpacing.Gutter),
        )
    }

    shelf("recent", "Recently played", library.recent, "Songs you play show up here.") { index, track ->
        ArtworkTile(track.title, track.artist, track.artwork, { onPlay(library.recent, index) }, Modifier.width(132.dp))
    }
    shelf("albums", "Albums", library.albums, "Save an album from its page to keep it here.") { _, album ->
        ArtworkTile(album.title, album.byline, album.artwork, { onAlbum(album) }, Modifier.width(132.dp))
    }
    shelf("artists", "Artists", library.artists, "Save an artist from their page to keep them here.") { _, artist ->
        ArtworkTile(artist.name, "Artist", artist.artwork, { onArtist(artist) }, Modifier.width(132.dp), round = true)
    }
}

private fun <T> LazyListScope.shelf(key: String, title: String, values: List<T>, emptyText: String, tile: @Composable (Int, T) -> Unit) {
    item(key = "$key-heading") { SectionHeader(title, modifier = Modifier.padding(top = 16.dp)) }
    item(key = key) {
        if (values.isEmpty()) {
            LibraryMessage(emptyText)
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = SitaraSpacing.Gutter), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(values) { index, value -> tile(index, value) }
            }
        }
    }
}

internal fun songCount(count: Int) = if (count == 1) "1 song" else "$count songs"

@Composable
private fun LikedEntry(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter).clip(RoundedCornerShape(SitaraRadius.Cover))
            .clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(SitaraRadius.Cover)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SitaraIcons.HeartFilled, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Column {
            Text("Liked songs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(songCount(count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LibraryMessage(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp))
}
