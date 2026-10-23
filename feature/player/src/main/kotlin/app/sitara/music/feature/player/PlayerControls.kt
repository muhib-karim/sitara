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
import androidx.compose.ui.res.stringResource
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
        val shuffleState = stringResource(if (state.shuffled) R.string.player_state_on else R.string.player_state_off)
        val repeatState = stringResource(
            when (state.repeatMode) {
                RepeatMode.OFF -> R.string.player_repeat_off
                RepeatMode.ALL -> R.string.player_repeat_all
                RepeatMode.ONE -> R.string.player_repeat_one
            },
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(
                SitaraIcons.Shuffle, stringResource(R.string.player_shuffle), actions::toggleShuffle,
                Modifier.semantics { stateDescription = shuffleState },
                tint = if (state.shuffled) MaterialTheme.colorScheme.primary else SitaraColors.StageSecondary,
            )
            IconButton(onClick = actions::previous, modifier = Modifier.size(48.dp)) {
                Icon(SitaraIcons.Previous, stringResource(R.string.player_previous_track), tint = Color.White, modifier = Modifier.size(28.dp))
            }
            // On a failed song the main button retries it (the engine treats play as retry there).
            val (mainIcon, mainLabelRes) = when {
                state.failed -> SitaraIcons.Retry to R.string.player_try_again
                state.isPlaying -> SitaraIcons.Pause to R.string.player_pause
                else -> SitaraIcons.Play to R.string.player_play
            }
            val mainLabel = stringResource(mainLabelRes)
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable(role = Role.Button, onClick = actions::togglePlay)
                    .semantics { contentDescription = mainLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(mainIcon, null, tint = Color.Black, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = actions::next, modifier = Modifier.size(48.dp)) {
                Icon(SitaraIcons.Next, stringResource(R.string.player_next_track), tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Box(contentAlignment = Alignment.Center) {
                GlassIconButton(
                    SitaraIcons.Repeat, stringResource(R.string.player_repeat), actions::cycleRepeat,
                    Modifier.semantics { stateDescription = repeatState },
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
        Text(stringResource(R.string.player_playback_failed), color = SitaraColors.StageContent, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        PillButton(stringResource(R.string.player_try_again), actions::retryPlayback)
        PillButton(stringResource(R.string.player_skip), actions::next, primary = false)
    }
}

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
    val seekLabel = stringResource(R.string.player_seek)
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(48.dp).semantics { contentDescription = seekLabel },
        )
    }
    Row(Modifier.fillMaxWidth()) {
        val timeStyle = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
        Text(formatDuration((value * duration).toLong()), color = SitaraColors.StageSecondary, style = timeStyle)
        // What the song streams in, between the times; absent while unknown (before its stream is looked up, or played from a download).
        val format = state.stream
        val codec = format?.codec
        val blend = state.blend
        if (blend != null) {
            // During an automix blend the readout says what is being matched.
            val blendDescription = stringResource(R.string.player_automix_description, blend.fromBpm, blend.toBpm, blend.bars)
            Text(
                stringResource(R.string.player_automix_readout, blend.fromBpm, blend.toBpm, blend.bars), color = SitaraColors.StageSecondary,
                style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics {
                    contentDescription = blendDescription
                    liveRegion = LiveRegionMode.Polite
                },
            )
        } else if (format != null && codec != null && format.bitrateKbps > 0) {
            // The same condition as StreamFormat.label: a codec and a known bitrate.
            val streamDescription = stringResource(R.string.player_streaming_description, codec, format.bitrateKbps)
            Text(
                stringResource(R.string.player_stream_format, codec, format.bitrateKbps), color = SitaraColors.StageSecondary, style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = streamDescription },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(formatDuration(duration), color = SitaraColors.StageSecondary, style = timeStyle)
    }
}
