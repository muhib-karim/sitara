package app.sitara.music.feature.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.sitara.music.core.common.formatDuration
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.LocalReducedMotion
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraColors
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraMotion
import app.sitara.music.core.model.RepeatMode

@Composable
internal fun PlayerControls(state: PlayerUiState, actions: PlayerActions, modifier: Modifier = Modifier) {
    Column(modifier) {
        // The scrubber stays where it is on a failed song, still, so the screen keeps its shape and its times.
        Scrubber(state, actions)
        if (state.failed) PlaybackFailed(actions)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(
                SitaraIcons.Shuffle, "Shuffle", actions::toggleShuffle,
                Modifier.semantics { stateDescription = if (state.shuffled) "On" else "Off" },
                tint = if (state.shuffled) MaterialTheme.colorScheme.primary else SitaraColors.StageSecondary,
            )
            IconButton(onClick = actions::previous, modifier = Modifier.size(48.dp)) {
                Icon(SitaraIcons.Previous, "Previous track", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            // On a failed song the main button retries it (the engine treats play as retry there).
            val (mainIcon, mainLabel) = when {
                state.failed -> SitaraIcons.Retry to "Try again"
                state.isPlaying -> SitaraIcons.Pause to "Pause"
                else -> SitaraIcons.Play to "Play"
            }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable(role = Role.Button, onClick = actions::togglePlay)
                    .semantics { contentDescription = mainLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(mainIcon, null, tint = Color.Black, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = actions::next, modifier = Modifier.size(48.dp)) {
                Icon(SitaraIcons.Next, "Next track", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Box(contentAlignment = Alignment.Center) {
                GlassIconButton(
                    SitaraIcons.Repeat, "Repeat", actions::cycleRepeat,
                    Modifier.semantics { stateDescription = state.repeatMode.name.lowercase() },
                    tint = if (state.repeatMode == RepeatMode.OFF) SitaraColors.StageSecondary else MaterialTheme.colorScheme.primary,
                )
                // The button already says "Repeat, one"; the figure is for the eye.
                if (state.repeatMode == RepeatMode.ONE) Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}

/** One row under the still scrubber, as tall as its buttons; on the Playing page the cover gives up the room, so the transport row stays put. */
@Composable
private fun PlaybackFailed(actions: PlayerActions) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(PLAYBACK_FAILED, color = SitaraColors.StageContent, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        PillButton("Try again", actions::retryPlayback)
        PillButton("Skip", actions::next, primary = false)
    }
}

internal const val PLAYBACK_FAILED = "Couldn’t play this song"

@Composable
private fun Scrubber(state: PlayerUiState, actions: PlayerActions) {
    var dragging by remember(state.track?.id) { mutableStateOf(false) }
    var dragValue by remember(state.track?.id) { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(0)
    val value = when {
        dragging -> dragValue
        duration > 0 -> (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        else -> 0f
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val played = MaterialTheme.colorScheme.primary
    val seekable = duration > 0 && !state.failed
    // A thick bar that thickens again under the finger, so a drag shows it has hold of the song.
    val thickness by animateDpAsState(if (dragging) 10.dp else 6.dp, SitaraMotion.orReduced(LocalReducedMotion.current, SitaraMotion.smooth()), label = "scrubber thickness")
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            // Keep the visual handle inside the generous native seek target.
            val inset = 10.dp.toPx()
            val start = if (rtl) size.width - inset else inset
            val end = if (rtl) inset else size.width - inset
            val center = size.height / 2f
            val thumb = start + (end - start) * value
            drawLine(Color.White.copy(alpha = 0.24f), Offset(start, center), Offset(end, center), thickness.toPx(), StrokeCap.Round)
            if (value > 0f) drawLine(played, Offset(start, center), Offset(thumb, center), thickness.toPx(), StrokeCap.Round)
            if (seekable) drawCircle(Color.White, (thickness + 4.dp).toPx() / 2f + 2.dp.toPx(), Offset(thumb, center))
        }
        // Use the stable overload for touch, keyboard, RTL and accessibility behavior.
        // Transparent colours keep the semantics visible to TalkBack (layer alpha=0 would not).
        Slider(
            value = value,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                if (dragging && duration > 0) actions.seekTo((dragValue * duration).toLong())
                dragging = false
            },
            enabled = seekable,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(48.dp).semantics { contentDescription = "Seek" },
        )
    }
    Row(Modifier.fillMaxWidth()) {
        val timeStyle = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
        Text(formatDuration((value * duration).toLong()), color = SitaraColors.StageSecondary, style = timeStyle)
        // What the song streams in, between the times; absent while unknown (before its stream is looked up, or played from a download).
        val format = state.stream
        val label = format?.label
        if (format != null && label != null) {
            Text(
                label, color = SitaraColors.StageSecondary, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = "Streaming ${format.codec} at ${format.bitrateKbps} kilobits per second" },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(formatDuration(duration), color = SitaraColors.StageSecondary, style = timeStyle)
    }
}
