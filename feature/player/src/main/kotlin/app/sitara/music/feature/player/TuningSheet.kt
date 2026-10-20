package app.sitara.music.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.GlassSurface
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.model.PlaybackTuning
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SpeedPresets = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/** "1×", "1.25×", "0.5×". */
internal fun speedLabel(speed: Float): String = String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.') + "×"

/** "Original key", "+2 semitones", "−1 semitone". */
internal fun pitchLabel(semitones: Int): String = when (semitones) {
    0 -> "Original key"
    else -> "${if (semitones > 0) "+" else "−"}${abs(semitones)} ${if (abs(semitones) == 1) "semitone" else "semitones"}"
}

/** Top-bar button showing the speed; the accent colour says speed or pitch differ from normal. */
@Composable
internal fun TuningButton(tuning: PlaybackTuning, onClick: () -> Unit) {
    Box(
        Modifier.size(SitaraSpacing.TouchTarget).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics {
            contentDescription = "Speed and pitch"
            stateDescription = "${speedLabel(tuning.speed)}, ${pitchLabel(tuning.semitones)}"
        },
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(Modifier.defaultMinSize(36.dp, 36.dp), shape = CircleShape) {
            Text(
                speedLabel(tuning.speed),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = if (tuning.isDefault) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 6.dp).clearAndSetSemantics {},
            )
        }
    }
}

@Composable
internal fun TuningSheet(tuning: PlaybackTuning, actions: PlayerActions, onDismiss: () -> Unit) {
    SitaraSheet(onDismiss) { _ -> SpeedAndPitch(tuning, actions) }
}

/** Speed (0.5x to 2x) and pitch (±6 semitones) for this session, with presets and a reset; the content of [TuningSheet]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedAndPitch(tuning: PlaybackTuning, actions: PlayerActions) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = SitaraSpacing.Gutter).padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = SitaraSpacing.TouchTarget), verticalAlignment = Alignment.CenterVertically) {
            Text("Speed and pitch", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (!tuning.isDefault) PillButton("Reset", actions::resetTuning, primary = false)
        }
        SliderHeading("Speed", speedLabel(tuning.speed))
        Slider(
            value = tuning.speed,
            onValueChange = actions::setSpeed,
            valueRange = PlaybackTuning.MIN_SPEED..PlaybackTuning.MAX_SPEED,
            steps = ((PlaybackTuning.MAX_SPEED - PlaybackTuning.MIN_SPEED) * PlaybackTuning.SPEED_STEPS_PER_UNIT).roundToInt() - 1,
            colors = accentSlider(),
            modifier = Modifier.semantics {
                contentDescription = "Speed"
                stateDescription = speedLabel(tuning.speed)
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpeedPresets.forEach { preset -> ChoicePill(speedLabel(preset), selected = tuning.speed == preset) { actions.setSpeed(preset) } }
        }
        SliderHeading("Pitch", pitchLabel(tuning.semitones), Modifier.padding(top = 20.dp))
        Slider(
            value = tuning.semitones.toFloat(),
            onValueChange = { actions.setPitch(it.roundToInt()) },
            valueRange = -PlaybackTuning.MAX_SEMITONES.toFloat()..PlaybackTuning.MAX_SEMITONES.toFloat(),
            steps = 2 * PlaybackTuning.MAX_SEMITONES - 1,
            colors = accentSlider(),
            modifier = Modifier.semantics {
                contentDescription = "Pitch"
                stateDescription = pitchLabel(tuning.semitones)
            },
        )
        Text(
            "Pitch changes the key without changing the speed. Both last until playback stops; lyrics keep time at any speed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun SliderHeading(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun accentSlider() = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)

/** One of a set of choices: filled with the accent when selected. */
@Composable
internal fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val fill = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.defaultMinSize(minWidth = SitaraSpacing.TouchTarget, minHeight = SitaraSpacing.TouchTarget).clip(CircleShape)
            .background(fill)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink)
    }
}
