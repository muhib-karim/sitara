package app.sitara.music.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artwork

/** Cover for a collection with no songs yet: a quiet dusk gradient. */
val EmptyCollectionArtwork = Artwork(listOf(0xFF34303F.toInt(), 0xFF17151D.toInt()))

/** "Artist · 2021", or just the artist when the source gives no year. */
val Album.byline: String get() = listOfNotNull(artist, year?.toString()).joinToString(" · ")

@Composable
fun SelectionPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        modifier = modifier.defaultMinSize(minHeight = SitaraSpacing.TouchTarget).clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtworkTile(
    title: String,
    subtitle: String,
    artwork: Artwork,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    covers: List<Artwork>? = null,
) {
    Column(
        // Only the top corners round: a full round clip cut the first letter of the subtitle's last line.
        modifier.clip(RoundedCornerShape(topStart = SitaraRadius.Tile, topEnd = SitaraRadius.Tile))
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick, onLongClickLabel = onLongClick?.let { "More options" }, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (covers != null) {
            PlaylistCover(covers, Modifier.fillMaxWidth(), SitaraRadius.Tile)
        } else {
            ArtworkImage(artwork, Modifier.fillMaxWidth(), cornerRadius = if (round) 500.dp else SitaraRadius.Tile)
        }
        Text(
            title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Collection metadata is not a synthetic Track; empty collections can still open. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionRow(
    title: String,
    subtitle: String,
    artwork: Artwork,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    covers: List<Artwork>? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(SitaraRadius.Cover))
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick, onLongClickLabel = onLongClick?.let { "Preview" }, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (covers != null) {
            PlaylistCover(covers, Modifier.size(48.dp), SitaraRadius.Cover)
        } else {
            ArtworkImage(artwork, Modifier.size(48.dp), cornerRadius = SitaraRadius.Cover)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A playlist's cover: its first four different song covers as a 2 x 2 collage when it has four, else its first one (a quiet gradient when empty). */
@Composable
fun PlaylistCover(covers: List<Artwork>, modifier: Modifier, cornerRadius: Dp) {
    if (covers.size < COLLAGE_SIZE) {
        ArtworkImage(covers.firstOrNull() ?: EmptyCollectionArtwork, modifier, cornerRadius = cornerRadius)
        return
    }
    Column(modifier.aspectRatio(1f).clip(RoundedCornerShape(cornerRadius)).testTag("collage")) {
        covers.take(COLLAGE_SIZE).chunked(2).forEach { pair ->
            Row(Modifier.weight(1f)) { pair.forEach { ArtworkImage(it, Modifier.weight(1f), cornerRadius = 0.dp) } }
        }
    }
}

private const val COLLAGE_SIZE = 4
