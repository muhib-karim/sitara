package app.sitara.music.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkBackdrop
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.SitaraTheme
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track

@Composable
fun AlbumScreen(album: Album, settings: SitaraSettings, onPlay: (List<Track>, Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues) {
    CollectionScreen(
        album.title, "Album · ${album.year}", album.artist, album.artwork, album.tracks,
        "Play album", settings, onPlay, onBack, contentPadding,
    )
}

@Composable
fun ArtistScreen(artist: Artist, tracks: List<Track>, settings: SitaraSettings, onPlay: (List<Track>, Int) -> Unit, onBack: () -> Unit, contentPadding: PaddingValues) {
    CollectionScreen(
        artist.name, "Artist", "${tracks.size} songs in this collection", artist.artwork, tracks,
        "Play artist", settings, onPlay, onBack, contentPadding, round = true,
    )
}

@Composable
private fun CollectionScreen(
    title: String,
    kind: String,
    subtitle: String,
    artwork: Artwork,
    tracks: List<Track>,
    playLabel: String,
    settings: SitaraSettings,
    onPlay: (List<Track>, Int) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    round: Boolean = false,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("collection-content"),
        contentPadding = contentPadding,
    ) {
        item(key = "header") {
            // The shared listening stage supplies atmosphere, reduced-motion and AMOLED behavior.
            SitaraTheme(settings.themeMode, DarkMode.DARK, settings.reducedMotion) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter)
                        .clip(RoundedCornerShape(SitaraRadius.Artwork)),
                ) {
                    ArtworkBackdrop(artwork, Modifier.matchParentSize())
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassIconButton(SitaraIcons.Back, "Back to browse", onBack)
                        ArtworkImage(
                            artwork, Modifier.size(152.dp).align(Alignment.CenterHorizontally),
                            cornerRadius = if (round) 76.dp else SitaraRadius.Tile,
                        )
                        Text(title, color = Color.White, style = MaterialTheme.typography.headlineLarge)
                        Text(kind, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        if (tracks.isNotEmpty()) {
                            PillButton(playLabel, { onPlay(tracks, 0) })
                        }
                    }
                }
            }
        }
        item(key = "songs-heading") {
            SectionHeader(
                if (round) "Songs" else "Track list",
                subtitle = if (tracks.isEmpty()) "No songs available yet" else "${tracks.size} songs",
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(track, { onPlay(tracks, index) })
        }
    }
}
