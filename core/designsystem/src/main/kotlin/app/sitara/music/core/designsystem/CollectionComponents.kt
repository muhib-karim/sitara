package app.sitara.music.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artwork

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

@Composable
fun ArtworkTile(
    title: String,
    subtitle: String,
    artwork: Artwork,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    enabled: Boolean = true,
) {
    Column(
        modifier.clip(RoundedCornerShape(SitaraRadius.Tile)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ArtworkImage(artwork, Modifier.fillMaxWidth(), cornerRadius = if (round) 500.dp else SitaraRadius.Tile)
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
@Composable
fun CollectionRow(
    title: String,
    subtitle: String,
    artwork: Artwork,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(SitaraRadius.Cover))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) {}
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(artwork, Modifier.size(48.dp), cornerRadius = SitaraRadius.Cover)
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
