package app.sitara.music.feature.player

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.GlassSurface
import app.sitara.music.core.designsystem.SitaraColors
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.model.Lyrics
import app.sitara.music.source.lyrics.LyricsMatch

/** Above found lyrics: which source they came from, as a chip that opens every source's match. */
@Composable
internal fun LyricsToolbar(state: PlayerUiState, actions: PlayerActions, modifier: Modifier = Modifier) {
    val found = state.lyrics as? LyricsState.Found ?: return
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SourceChip(found.lyrics, { picking = true }, Modifier.weight(1f, fill = false))
    }
    if (picking) LyricsSourceSheet(state, actions) { picking = false }
}

@Composable
private fun SourceChip(lyrics: Lyrics, onClick: () -> Unit, modifier: Modifier) {
    // The touch target is the full row height; the glass pill inside stays compact.
    Row(modifier.heightIn(min = SitaraSpacing.TouchTarget).clickable(role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        GlassSurface(shape = CircleShape) {
            Text(
                sourceLabel(lyrics),
                style = MaterialTheme.typography.labelLarge,
                color = SitaraColors.StageContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

private fun sourceLabel(lyrics: Lyrics) = "Source: ${lyrics.source ?: "unknown"}, ${if (lyrics.synced) "synced" else "plain"}"

@Composable
private fun LyricsSourceSheet(state: PlayerUiState, actions: PlayerActions, onDismiss: () -> Unit) {
    SitaraSheet(onDismiss) { close -> LyricsSourceList(state) { id -> close { actions.chooseLyrics(id) } } }
}

/** Every source's lyrics for the song, how they are timed, how many lines and how their length compares, with their first lines; the content of the source sheet. */
@Composable
fun LyricsSourceList(state: PlayerUiState, onChoose: (providerId: String) -> Unit) {
    val found = state.lyrics as? LyricsState.Found ?: return
    val songMs = state.track?.durationMs ?: 0
    val missing = state.lyricsSources.filter { name -> found.matches.none { it.lyrics.source == name } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(
            "Lyrics source", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
        )
        found.matches.forEach { match -> SourceRow(match, selected = match.lyrics == found.lyrics, songMs) { onChoose(match.providerId) } }
        missing.forEach { name ->
            Text(
                "$name has no lyrics for this song", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SourceRow(match: LyricsMatch, selected: Boolean, songMs: Long, onClick: () -> Unit) {
    val lyrics = match.lyrics
    val details = listOfNotNull(
        if (lyrics.synced) "Synced" else "Plain",
        if (lyrics.lines.size == 1) "1 line" else "${lyrics.lines.size} lines",
        lengthLabel(lyrics.matched?.durationMs, songMs),
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = SitaraSpacing.Gutter, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(lyrics.source, lyrics.licensor).joinToString(" · "), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                lyrics.lines.take(2).joinToString("\n") { it.text },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (selected) Icon(SitaraIcons.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) else Spacer(Modifier.width(24.dp))
    }
}

/** How much longer or shorter the song the lyrics were filed under is, to the second; null when either length is unknown. */
internal fun lengthLabel(matchedMs: Long?, songMs: Long): String? {
    if (matchedMs == null || matchedMs <= 0 || songMs <= 0) return null
    val seconds = (matchedMs - songMs) / 1_000
    return when {
        seconds == 0L -> "same length"
        seconds > 0 -> "$seconds s longer"
        else -> "${-seconds} s shorter"
    }
}
