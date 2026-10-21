package app.sitara.music.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
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
            // Off, the bands and preamp dim: they still show the curve, and moving one switches the equaliser on.
            Column(Modifier.alpha(if (equaliser.enabled) 1f else 0.38f)) {
                BandSliders(equaliser, onChange, Modifier.padding(top = 20.dp))
                SliderHeading("Preamp", decibelLabel(equaliser.preampDb), Modifier.padding(top = 16.dp))
                Fader(equaliser.preampDb, { onChange(equaliser.withPreamp(it)) }, "Preamp", vertical = false, Modifier.fillMaxWidth())
            }
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
        Fader(db, onChange, bandLabel(band), vertical = true, Modifier.height(BandHeight))
        Text(
            bandLabel(band), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, softWrap = false, textAlign = TextAlign.Center, modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * A gain fader in the app's slim style: a thin track with a 0 dB mark at its centre, filled from that mark towards
 * [db] (up or right for a boost, down or left for a cut, nothing at 0 dB) under a round thumb. A transparent Slider
 * on top keeps the touch target, keys and the TalkBack name ([label]) and value.
 */
@Composable
private fun Fader(db: Int, onChange: (Int) -> Unit, label: String, vertical: Boolean, modifier: Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val zero = MaterialTheme.colorScheme.onSurfaceVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val max = EqualiserSettings.MAX_GAIN_DB
    Box(modifier, contentAlignment = Alignment.Center) {
        Slider(
            value = db.toFloat(),
            onValueChange = { it.roundToInt().let { picked -> if (picked != db) onChange(picked) } },
            valueRange = -max.toFloat()..max.toFloat(),
            steps = 2 * max - 1,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            // Vertical, the height is the fader's length, so it goes outside the turn.
            modifier = (if (vertical) Modifier.fillMaxHeight().vertical() else Modifier.fillMaxWidth()).semantics {
                contentDescription = label
                stateDescription = decibelLabel(db)
            },
        )
        Canvas(Modifier.matchParentSize()) {
            val inset = 12.dp.toPx()
            val low = when {
                vertical -> Offset(size.width / 2f, size.height - inset)
                rtl -> Offset(size.width - inset, size.height / 2f)
                else -> Offset(inset, size.height / 2f)
            }
            val high = when {
                vertical -> Offset(size.width / 2f, inset)
                rtl -> Offset(inset, size.height / 2f)
                else -> Offset(size.width - inset, size.height / 2f)
            }
            val centre = lerp(low, high, 0.5f)
            val thumb = lerp(low, high, (db + max) / (2f * max))
            val across = if (vertical) Offset(7.dp.toPx(), 0f) else Offset(0f, 7.dp.toPx())
            drawLine(track, low, high, 4.dp.toPx(), StrokeCap.Round)
            drawLine(zero, centre - across, centre + across, 2.dp.toPx(), StrokeCap.Round)
            if (db != 0) drawLine(fill, centre, thumb, 4.dp.toPx(), StrokeCap.Round)
            drawCircle(fill, 8.dp.toPx(), thumb)
        }
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
