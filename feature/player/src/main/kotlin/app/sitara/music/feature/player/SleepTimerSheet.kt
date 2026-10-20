package app.sitara.music.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.sitara.music.core.common.formatDuration
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing
import kotlin.math.roundToInt

private val MinutePresets = listOf(15, 30, 45, 60)
private const val MAX_CUSTOM_MINUTES = 180
private const val DEFAULT_CUSTOM_MINUTES = 20

/** Whether and when playback pauses by itself. */
sealed interface SleepTimerStatus {
    data object Off : SleepTimerStatus

    data class Counting(val remainingMs: Long) : SleepTimerStatus

    data object EndOfTrack : SleepTimerStatus
}

private fun SleepTimerStatus.describe(): String = when (this) {
    SleepTimerStatus.Off -> "Off"
    SleepTimerStatus.EndOfTrack -> "Pauses when this song ends"
    is SleepTimerStatus.Counting -> "Pauses in ${formatDuration(remainingMs)}"
}

/** "45 min", "1 h", "1 h 30 min". */
private fun minutesLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** Top-bar button; accent-coloured while a timer is set. */
@Composable
internal fun SleepTimerButton(status: SleepTimerStatus, onClick: () -> Unit) {
    GlassIconButton(
        SitaraIcons.Timer,
        "Sleep timer",
        onClick,
        Modifier.semantics { stateDescription = status.describe() },
        tint = if (status == SleepTimerStatus.Off) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun SleepTimerSheet(status: SleepTimerStatus, actions: PlayerActions, onDismiss: () -> Unit) {
    SitaraSheet(onDismiss) { close -> SleepTimerOptions(status, actions, close) }
}

/**
 * Set a timer from presets or any number of minutes, extend or turn off a running one, or pause after this song; the
 * content of [SleepTimerSheet]. [close] runs an action and closes the sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerOptions(status: SleepTimerStatus, actions: PlayerActions, close: (action: () -> Unit) -> Unit) {
    var custom by rememberSaveable { mutableIntStateOf(DEFAULT_CUSTOM_MINUTES) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = SitaraSpacing.Gutter).padding(bottom = 16.dp)) {
        Text("Sleep timer", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        if (status != SleepTimerStatus.Off) {
            Text(
                status.describe(),
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status is SleepTimerStatus.Counting) {
                    PillButton("+5 min", { actions.extendSleepTimer(5) }, primary = false)
                    PillButton("+15 min", { actions.extendSleepTimer(15) }, primary = false)
                }
                PillButton("Turn off", { close(actions::cancelSleepTimer) })
            }
        }
        Text(
            "Pause after",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MinutePresets.forEach { minutes -> PillButton(minutesLabel(minutes), { close { actions.startSleepTimer(minutes) } }, primary = false) }
            PillButton("End of this song", { close(actions::sleepAtEndOfTrack) }, primary = false)
        }
        SliderHeading("Custom", minutesLabel(custom), Modifier.padding(top = 16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { custom = (custom - 1).coerceAtLeast(1) }) {
                Icon(SitaraIcons.Minus, "One minute less", tint = MaterialTheme.colorScheme.onSurface)
            }
            Slider(
                value = custom.toFloat(),
                onValueChange = { custom = it.roundToInt() },
                // Continuous, so the track shows no row of 180 stops; the value still rounds to whole minutes.
                valueRange = 1f..MAX_CUSTOM_MINUTES.toFloat(),
                colors = accentSlider(),
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "Custom minutes"
                    stateDescription = minutesLabel(custom)
                },
            )
            IconButton(onClick = { custom = (custom + 1).coerceAtMost(MAX_CUSTOM_MINUTES) }) {
                Icon(SitaraIcons.Add, "One minute more", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = SitaraSpacing.TouchTarget), horizontalArrangement = Arrangement.End) {
            PillButton("Start ${minutesLabel(custom)} timer", { close { actions.startSleepTimer(custom) } })
        }
    }
}
