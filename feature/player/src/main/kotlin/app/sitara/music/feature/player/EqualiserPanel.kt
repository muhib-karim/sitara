package app.sitara.music.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.model.EqualiserPreset
import app.sitara.music.core.model.EqualiserSettings
import kotlin.math.roundToInt

/** "31 Hz", "1 kHz", "16 kHz". */
internal fun bandLabel(band: Int): String {
    val hz = EqualiserSettings.BAND_CENTRES_HZ[band]
    return if (hz >= 1_000) "${(hz / 1_000).roundToInt()} kHz" else "${hz.toInt()} Hz"
}

/** "+3 dB", "0 dB", "−4 dB". */
internal fun decibelLabel(db: Int): String = when {
    db > 0 -> "+$db dB"
    db < 0 -> "−${-db} dB"
    else -> "0 dB"
}

/** What the equaliser is doing, in a word or two: "Off", "Rock", "Custom", "Phone equaliser". */
fun equaliserSummary(equaliser: EqualiserSettings, systemEqualiser: Boolean): String = when {
    equaliser.systemActive(systemEqualiser) -> "Phone equaliser"
    !equaliser.enabled -> "Off"
    else -> equaliser.preset?.label ?: "Custom"
}

/** The equaliser as a page of its own, reached from Settings; pads for the dock like the other pages. */
@Composable
fun EqualiserScreen(
    equaliser: EqualiserSettings,
    systemEqualiser: Boolean,
    onChange: (EqualiserSettings) -> Unit,
    onOpenSystemEqualiser: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).testTag("equaliser-content")) {
        GlassIconButton(SitaraIcons.Back, "Back", onBack, Modifier.padding(start = 12.dp, top = 8.dp))
        LargeTitle("Equaliser")
        EqualiserPanel(equaliser, systemEqualiser, onChange, onOpenSystemEqualiser)
    }
}

/** The equaliser over Now Playing, in the cover's colours. */
@Composable
internal fun EqualiserSheet(equaliser: EqualiserSettings, systemEqualiser: Boolean, actions: PlayerActions, onDismiss: () -> Unit) {
    SitaraSheet(onDismiss) { _ ->
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text("Equaliser", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter))
            EqualiserPanel(equaliser, systemEqualiser, actions::setEqualiser, actions::openSystemEqualiser)
        }
    }
}

/**
 * Every control changes the sound: the switch, a preset, a band or the preamp (the last three switch it on), Reset,
 * and the phone's own equaliser where the phone has one, which hides the built-in bands while it has the sound.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualiserPanel(
    equaliser: EqualiserSettings,
    systemEqualiser: Boolean,
    onChange: (EqualiserSettings) -> Unit,
    onOpenSystemEqualiser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = SitaraSpacing.Gutter)) {
        if (systemEqualiser) {
            SwitchRow("Use phone equaliser", "Sitara's bands turn off while your phone's equaliser shapes the sound", equaliser.useSystem) {
                onChange(equaliser.copy(useSystem = it))
            }
        }
        if (equaliser.systemActive(systemEqualiser)) {
            PillButton("Open phone equaliser", onOpenSystemEqualiser, Modifier.padding(top = 8.dp))
        } else {
            SwitchRow("Equaliser", if (equaliser.enabled) equaliser.preset?.label ?: "Custom" else "Songs play as they were mastered", equaliser.enabled) { onChange(equaliser.copy(enabled = it)) }
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EqualiserPreset.entries.forEach { preset ->
                    ChoicePill(preset.label, selected = equaliser.enabled && equaliser.preset == preset) { onChange(equaliser.withPreset(preset)) }
                }
            }
            BandSliders(equaliser, onChange, Modifier.padding(top = 20.dp))
            SliderHeading("Preamp", decibelLabel(equaliser.preampDb), Modifier.padding(top = 16.dp))
            Slider(
                value = equaliser.preampDb.toFloat(),
                onValueChange = { it.roundToInt().let { db -> if (db != equaliser.preampDb) onChange(equaliser.withPreamp(db)) } },
                valueRange = -EqualiserSettings.MAX_GAIN_DB.toFloat()..EqualiserSettings.MAX_GAIN_DB.toFloat(),
                steps = 2 * EqualiserSettings.MAX_GAIN_DB - 1,
                colors = accentSlider(),
                modifier = Modifier.semantics {
                    contentDescription = "Preamp"
                    stateDescription = decibelLabel(equaliser.preampDb)
                },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lower the preamp if loud songs sound squashed: a limiter keeps boosts from clipping.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                if (equaliser.preampDb != 0 || equaliser.bandsDb.any { it != 0 }) PillButton("Reset", { onChange(equaliser.reset()) }, primary = false)
            }
        }
    }
}

/** Ten vertical faders: in one row where each gets a full touch target, otherwise lows above highs. */
@Composable
private fun BandSliders(equaliser: EqualiserSettings, onChange: (EqualiserSettings) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= MinBandWidth * EqualiserSettings.BAND_COUNT) EqualiserSettings.BAND_COUNT else EqualiserSettings.BAND_COUNT / 2
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            (0 until EqualiserSettings.BAND_COUNT).chunked(perRow).forEach { bands ->
                Row(Modifier.fillMaxWidth()) {
                    bands.forEach { band ->
                        BandSlider(band, equaliser.bandsDb[band], { onChange(equaliser.withBand(band, it)) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BandSlider(band: Int, db: Int, onChange: (Int) -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // The slider announces its band and gain; the labels around it are for the eye only.
        Text(
            decibelLabel(db), style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, softWrap = false, modifier = Modifier.clearAndSetSemantics {},
        )
        Slider(
            value = db.toFloat(),
            onValueChange = { it.roundToInt().let { picked -> if (picked != db) onChange(picked) } },
            valueRange = -EqualiserSettings.MAX_GAIN_DB.toFloat()..EqualiserSettings.MAX_GAIN_DB.toFloat(),
            steps = 2 * EqualiserSettings.MAX_GAIN_DB - 1,
            colors = accentSlider(),
            // Outside the turn the height is the fader's length; inside it, the minimum height is its 48 dp touch width.
            modifier = Modifier.height(BandHeight).vertical().heightIn(min = SitaraSpacing.TouchTarget).semantics {
                contentDescription = bandLabel(band)
                stateDescription = decibelLabel(db)
            },
        )
        Text(
            bandLabel(band), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

// A horizontal slider turned a quarter anticlockwise: lowest gain at the bottom, dragged up to boost.
private fun Modifier.vertical() = graphicsLayer {
    rotationZ = 270f
    transformOrigin = TransformOrigin(0f, 0f)
}.layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints(constraints.minHeight, constraints.maxHeight, constraints.minWidth, constraints.maxWidth))
    layout(placeable.height, placeable.width) { placeable.place(-placeable.width, 0) }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = SitaraSpacing.TouchTarget).toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = MaterialTheme.colorScheme.onPrimary),
        )
    }
}

private val MinBandWidth = SitaraSpacing.TouchTarget
private val BandHeight = 168.dp
