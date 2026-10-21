package app.sitara.music.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.model.Track

/** A section title with optional quiet supporting text and, at its end, an optional [action] such as a "More" button. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    if (action == null) {
        SectionTitle(title, subtitle, modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    } else {
        Row(modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(title, subtitle, Modifier.weight(1f))
            action()
        }
    }
}

// The title leads; an optional subtitle follows as quiet supporting text, never as a label above it.
@Composable
private fun SectionTitle(title: String, subtitle: String?, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = SectionStyle, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}

/**
 * A 64 dp-tall track row: cover, title, artist. The whole row is one 48 dp+ target. With [onMore], a long press and,
 * when [moreButton] is on, a trailing button open the song's actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    onMore: (() -> Unit)? = null,
    moreButton: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClickLabel = onMore?.let { "More options" }, onLongClick = onMore)
            .semantics(mergeDescendants = true) { contentDescription = "${track.title} by ${track.artist}" }
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        ArtworkImage(track.artwork, Modifier.size(48.dp), cornerRadius = 8.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
        if (onMore != null && moreButton) {
            IconButton(onClick = onMore) {
                Icon(SitaraIcons.More, "More options for ${track.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
