package app.sitara.music.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.model.SongGain
import kotlin.math.roundToInt

@Composable
internal fun PlayerMoreSheet(state: PlayerUiState, onAddToPlaylist: () -> Unit, onEqualiser: () -> Unit, onSongVolume: () -> Unit, onDismiss: () -> Unit) {
    SitaraSheet(onDismiss) { close -> PlayerMoreOptions(state, { close(onAddToPlaylist) }, { close(onEqualiser) }, { close(onSongVolume) }) }
}

/** Now Playing's overflow: the song's playlist, the equaliser and the song's own volume; the content of [PlayerMoreSheet]. */
@Composable
fun PlayerMoreOptions(state: PlayerUiState, onAddToPlaylist: () -> Unit, onEqualiser: () -> Unit, onSongVolume: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
        MoreAction(SitaraIcons.Add, stringResource(R.string.player_add_to_playlist), null, onAddToPlaylist)
        MoreAction(SitaraIcons.Equaliser, stringResource(R.string.player_equaliser), equaliserSummary(state.equaliser, state.systemEqualiser), onEqualiser)
        MoreAction(SitaraIcons.Volume, stringResource(R.string.player_song_volume), decibelLabel(state.trimDb), onSongVolume)
    }
}

@Composable
private fun MoreAction(icon: ImageVector, label: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = SitaraSpacing.Gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SongVolumeSheet(state: PlayerUiState, actions: PlayerActions, onDismiss: () -> Unit) {
    SitaraSheet(onDismiss) { _ -> SongVolume(state, actions) }
}

/** The current song's volume trim, −6 to +6 dB, kept with the song and applied before the equaliser; the content of [SongVolumeSheet]. */
@Composable
fun SongVolume(state: PlayerUiState, actions: PlayerActions) {
    val track = state.track ?: return
    val trim = decibelLabel(state.trimDb)
    val volumeFor = stringResource(R.string.player_volume_for, track.title)
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = SitaraSpacing.Gutter).padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = SitaraSpacing.TouchTarget), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.player_song_volume), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (state.trimDb != 0) PillButton(stringResource(R.string.player_reset), { actions.setTrimDb(0) }, primary = false)
        }
        Text(
            "${track.title} · ${track.artist}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        SliderHeading(stringResource(R.string.player_trim), trim, Modifier.padding(top = 12.dp))
        Slider(
            value = state.trimDb.toFloat(),
            onValueChange = { it.roundToInt().let { db -> if (db != state.trimDb) actions.setTrimDb(db) } },
            valueRange = -SongGain.MAX_TRIM_DB.toFloat()..SongGain.MAX_TRIM_DB.toFloat(),
            steps = 2 * SongGain.MAX_TRIM_DB - 1,
            colors = accentSlider(),
            modifier = Modifier.semantics {
                contentDescription = volumeFor
                stateDescription = trim
            },
        )
        Text(
            stringResource(R.string.player_song_volume_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
