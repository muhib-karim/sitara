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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.sitara.music.core.common.formatDuration
import app.sitara.music.core.designsystem.ArtworkBackdrop
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.ArtworkTile
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.SitaraTheme
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.ArtistPage
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track

/** An artist, album or playlist page as it loads. */
sealed interface DetailState {
    data object Loading : DetailState

    data object Failed : DetailState

    data class ArtistReady(val page: ArtistPage) : DetailState

    data class AlbumReady(val album: Album) : DetailState

    data class PlaylistReady(val playlist: Playlist) : DetailState
}

/** One detail page: loading, failed with a retry, or the loaded artist, album or playlist. */
@Composable
fun DetailScreen(
    state: DetailState,
    settings: SitaraSettings,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    onArtist: (artistId: String) -> Unit,
    onAlbum: (albumId: String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("detail-content"),
        contentPadding = contentPadding,
    ) {
        when (state) {
            DetailState.Loading -> item(key = "loading") { PageMessage("Loading…", onBack) }
            DetailState.Failed -> item(key = "failed") {
                PageMessage("This page didn’t load. Check your connection, then try again.", onBack) { PillButton("Try again", onRetry) }
            }
            is DetailState.ArtistReady -> {
                val page = state.page
                val songs = page.topSongs
                item(key = "header") {
                    DetailHeader(
                        page.artist.artwork, page.artist.name, "Artist", artistSummary(page), null,
                        songs, settings, onBack, onPlay, onShuffle, round = true,
                    )
                }
                if (songs.isEmpty() && page.releases.isEmpty()) {
                    item(key = "empty") { EmptyMessage("Nothing from this artist yet") }
                }
                if (songs.isNotEmpty()) {
                    item(key = "songs-heading") { SectionHeader("Top songs", modifier = Modifier.padding(top = 16.dp)) }
                    trackRows(songs, onPlay)
                }
                if (page.releases.isNotEmpty()) {
                    item(key = "releases-heading") { SectionHeader("Albums and singles", modifier = Modifier.padding(top = 16.dp)) }
                    item(key = "releases") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(page.releases, key = { it.id }) { album ->
                                ArtworkTile(album.title, album.year?.toString() ?: "Album", album.artwork, { onAlbum(album.id) }, Modifier.width(156.dp))
                            }
                        }
                    }
                }
            }
            is DetailState.AlbumReady -> {
                val album = state.album
                val artistId = album.artistId
                item(key = "header") {
                    DetailHeader(
                        album.artwork, album.title, listOfNotNull("Album", album.year?.toString()).joinToString(" · "), album.artist,
                        artistId?.let { { onArtist(it) } }, album.tracks, settings, onBack, onPlay, onShuffle,
                    )
                }
                collectionTracks(album.tracks, "No songs in this album", onPlay)
            }
            is DetailState.PlaylistReady -> {
                val playlist = state.playlist
                item(key = "header") {
                    DetailHeader(
                        playlist.artwork, playlist.title, "Playlist", playlist.curator.ifEmpty { playlist.description }, null,
                        playlist.tracks, settings, onBack, onPlay, onShuffle,
                    )
                }
                collectionTracks(playlist.tracks, "No songs in this playlist", onPlay)
            }
        }
    }
}

private fun artistSummary(page: ArtistPage): String = listOfNotNull(
    page.topSongs.size.takeIf { it > 0 }?.let { if (it == 1) "1 song" else "$it songs" },
    page.releases.size.takeIf { it > 0 }?.let { if (it == 1) "1 release" else "$it releases" },
).joinToString(" · ")

private fun LazyListScope.collectionTracks(tracks: List<Track>, emptyText: String, onPlay: (List<Track>, Int) -> Unit) {
    if (tracks.isEmpty()) {
        item(key = "empty") { EmptyMessage(emptyText) }
        return
    }
    val total = formatDuration(tracks.sumOf { it.durationMs })
    item(key = "songs-heading") {
        SectionHeader("Songs", subtitle = "${if (tracks.size == 1) "1 song" else "${tracks.size} songs"} · $total", modifier = Modifier.padding(top = 16.dp))
    }
    trackRows(tracks, onPlay)
}

private fun LazyListScope.trackRows(tracks: List<Track>, onPlay: (List<Track>, Int) -> Unit) {
    itemsIndexed(tracks, key = { _, track -> "track-${track.id}" }) { index, track ->
        TrackRow(track, { onPlay(tracks, index) }) {
            if (track.durationMs > 0) {
                Text(formatDuration(track.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DetailHeader(
    artwork: Artwork,
    title: String,
    kind: String,
    subtitle: String,
    onSubtitle: (() -> Unit)?,
    tracks: List<Track>,
    settings: SitaraSettings,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onShuffle: (List<Track>) -> Unit,
    round: Boolean = false,
) {
    // The shared listening stage supplies atmosphere, reduced-motion and AMOLED behavior.
    SitaraTheme(settings.themeMode, DarkMode.DARK, settings.reducedMotion) {
        Box(Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter).clip(RoundedCornerShape(SitaraRadius.Artwork))) {
            ArtworkBackdrop(artwork, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassIconButton(SitaraIcons.Back, "Back", onBack)
                ArtworkImage(artwork, Modifier.size(152.dp).align(Alignment.CenterHorizontally), cornerRadius = if (round) 76.dp else SitaraRadius.Tile)
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineLarge)
                Text(kind, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                if (subtitle.isNotEmpty()) {
                    val link = onSubtitle?.let { Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClickLabel = "Open artist", onClick = it) } ?: Modifier
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = link)
                }
                if (tracks.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton("Play all", { onPlay(tracks, 0) })
                        PillButton("Shuffle", { onShuffle(tracks) }, primary = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageMessage(message: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GlassIconButton(SitaraIcons.Back, "Back", onBack)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 8.dp))
        Box(Modifier.padding(horizontal = 8.dp)) { action() }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(SitaraSpacing.Gutter))
}
