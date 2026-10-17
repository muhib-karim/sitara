package app.sitara.music.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.sitara.music.core.common.formatDuration
import app.sitara.music.core.designsystem.SitaraColors
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.model.RepeatMode

@Composable
internal fun PlayerControls(state: PlayerUiState, actions: PlayerActions, modifier: Modifier = Modifier) {
    Column(modifier) {
        Scrubber(state, actions)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions::toggleShuffle, modifier = Modifier.semantics { stateDescription = if (state.shuffled) "On" else "Off" }) {
                Icon(SitaraIcons.Shuffle, "Shuffle", tint = if (state.shuffled) SitaraColors.Saffron else SitaraColors.StageSecondary)
            }
            IconButton(onClick = actions::previous, modifier = Modifier.size(48.dp)) {
                Icon(SitaraIcons.Previous, "Previous track", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable(role = Role.Button, onClick = actions::togglePlay)
                    .semantics { contentDescription = if (state.isPlaying) "Pause" else "Play" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (state.isPlaying) SitaraIcons.Pause else SitaraIcons.Play, null, tint = Color.Black, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = actions::next, modifier = Modifier.size(48.dp)) {
                Icon(SitaraIcons.Next, "Next track", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = actions::cycleRepeat, modifier = Modifier.semantics { stateDescription = state.repeatMode.name.lowercase() }) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(SitaraIcons.Repeat, "Repeat", tint = if (state.repeatMode == RepeatMode.OFF) SitaraColors.StageSecondary else SitaraColors.Saffron)
                    if (state.repeatMode == RepeatMode.ONE) Text("1", style = MaterialTheme.typography.labelSmall, color = SitaraColors.Saffron)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Scrubber(state: PlayerUiState, actions: PlayerActions) {
    var dragging by remember(state.track?.id) { mutableStateOf(false) }
    var dragValue by remember(state.track?.id) { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(0)
    val value = if (dragging) dragValue else if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Slider(
        value = value,
        onValueChange = { dragging = true; dragValue = it },
        onValueChangeFinished = {
            if (dragging && duration > 0) actions.seekTo((dragValue * duration).toLong())
            dragging = false
        },
        enabled = duration > 0,
        thumb = { Box(Modifier.size(if (dragging) 16.dp else 10.dp).background(Color.White, CircleShape)) },
        track = {
            Box(Modifier.fillMaxWidth().height(if (dragging) 10.dp else 6.dp).clip(CircleShape).background(Color(0xFF777777))) {
                Box(Modifier.fillMaxWidth(value).height(if (dragging) 10.dp else 6.dp).background(SitaraColors.Saffron))
            }
        },
        modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "Seek" },
    )
    Row(Modifier.fillMaxWidth()) {
        val timeStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
        Text(formatDuration((value * duration).toLong()), color = SitaraColors.StageSecondary, style = timeStyle)
        Spacer(Modifier.weight(1f))
        Text(formatDuration(duration), color = SitaraColors.StageSecondary, style = timeStyle)
    }
}
