package app.sitara.music.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.sitara.music.core.common.formatBytes
import app.sitara.music.core.designsystem.ArtworkPalette
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.accentTone
import app.sitara.music.core.model.AccentColor
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.SongDownload
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: SitaraSettings,
    onChange: (SitaraSettings) -> Unit,
    downloads: List<SongDownload>,
    onRemoveAllDownloads: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    equaliserSummary: String = "Off",
    onOpenEqualiser: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(modifier.fillMaxSize().testTag("settings-content"), contentPadding = contentPadding) {
        item(key = "title") {
            GlassIconButton(SitaraIcons.Back, "Back", onBack, Modifier.padding(start = 12.dp, top = 8.dp))
            LargeTitle("Settings")
        }
        item(key = "appearance") {
            AppearanceSettings(settings, onChange)
        }
        item(key = "motion") {
            SettingsGroup("Motion") {
                SwitchRow("Reduced motion", "Instant transitions, without blur", settings.reducedMotion) { onChange(settings.copy(reducedMotion = it)) }
            }
        }
        item(key = "sound") {
            SettingsGroup("Sound") {
                LinkRow("Equaliser", equaliserSummary, onOpenEqualiser)
                InsetDivider()
                SwitchRow("Normalise loudness", "Songs play at a similar volume", settings.normalisation) { onChange(settings.copy(normalisation = it)) }
                InsetDivider()
                SwitchRow("Skip silence", "Skips quiet stretches inside and between songs", settings.skipSilence) { onChange(settings.copy(skipSilence = it)) }
                InsetDivider()
                CrossfadeRow(settings.crossfadeSeconds) { onChange(settings.copy(crossfadeSeconds = it)) }
            }
        }
        item(key = "downloads") {
            DownloadSettings(settings, onChange, downloads, onRemoveAllDownloads)
        }
    }
}

@Composable
private fun AppearanceSettings(settings: SitaraSettings, onChange: (SitaraSettings) -> Unit) {
    // Wallpaper colours need Android 12; below it the option is hidden and the picked accent always applies.
    val wallpaperAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val wallpaper = wallpaperAvailable && settings.dynamicColor
    val pick: (AccentColor) -> Unit = { onChange(settings.copy(accent = it, dynamicColor = false)) }
    SettingsGroup("Appearance") {
        AppearancePreview()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(DarkMode.SYSTEM to "System", DarkMode.LIGHT to "Light", DarkMode.DARK to "Dark").forEach { (mode, label) ->
                Segment(label, settings.darkMode == mode, Modifier.weight(1f)) { onChange(settings.copy(darkMode = mode)) }
            }
        }
        InsetDivider()
        SwitchRow("Pure black", "Black instead of dark grey in the dark theme, and Now Playing without the cover’s blur", settings.pureBlack) {
            onChange(settings.copy(pureBlack = it))
        }
        if (wallpaperAvailable) {
            InsetDivider()
            SwitchRow("Wallpaper colours", "The accent comes from your wallpaper", settings.dynamicColor) { onChange(settings.copy(dynamicColor = it)) }
        }
        InsetDivider()
        Column(Modifier.selectableGroup()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Text("Accent", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (wallpaper) "From your wallpaper now. Pick a colour to use it instead." else "Buttons, highlights and the selected tab",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                AccentColor.entries.forEach { accent ->
                    accentTone(accent, dark)?.let { tone -> Swatch(accent.label, tone, selected = !wallpaper && settings.accent == accent) { pick(accent) } }
                }
            }
            Choice(
                AccentColor.ARTWORK.label, !wallpaper && settings.accent == AccentColor.ARTWORK,
                description = "Follows the cover of the song that’s playing; saffron when nothing plays",
            ) { pick(AccentColor.ARTWORK) }
        }
        InsetDivider()
        SwitchRow("Use artwork colours across the app", "Backgrounds, cards and chips take a tint from the cover of the song that’s playing", settings.artworkColorsAcrossApp) {
            onChange(settings.copy(artworkColorsAcrossApp = it))
        }
    }
}

/** A still picture of the chosen colours: the page, a card, chips and a playing song. One node for TalkBack, with nothing to press. */
@Composable
private fun AppearancePreview() {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(SitaraRadius.Tile)).background(colors.background).padding(12.dp)
            .testTag("appearance-preview").clearAndSetSemantics { contentDescription = "Preview of the chosen colours" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Chill" to true, "Focus" to false, "Party" to false).forEach { (label, selected) ->
                Text(
                    label, style = MaterialTheme.typography.labelLarge, color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                    modifier = Modifier.clip(CircleShape).background(if (selected) colors.primary else colors.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(SitaraRadius.Tile)).background(colors.surfaceContainerHigh).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(SitaraRadius.Cover)).background(Brush.linearGradient(listOf(colors.primary, colors.surfaceContainerHighest))))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Song title", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text("Artist", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                    Icon(SitaraIcons.Play, null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(colors.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth(0.4f).fillMaxHeight().background(colors.primary))
            }
        }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.heightIn(min = SitaraSpacing.TouchTarget).clip(CircleShape).background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) colors.onPrimary else colors.onSurface)
    }
}

/** A 48 dp accent swatch; the chosen one is ringed and ticked, so the choice does not rest on colour alone. */
@Composable
private fun Swatch(label: String, tone: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(SitaraSpacing.TouchTarget).clip(CircleShape).selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(46.dp).border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
        Box(Modifier.size(36.dp).clip(CircleShape).background(tone), contentAlignment = Alignment.Center) {
            if (selected) Icon(SitaraIcons.Check, null, tint = Color(ArtworkPalette.inkOn(tone.toArgb())), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DownloadSettings(settings: SitaraSettings, onChange: (SitaraSettings) -> Unit, downloads: List<SongDownload>, onRemoveAll: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val songs = if (downloads.size == 1) "1 song" else "${downloads.size} songs"
    SettingsGroup("Downloads") {
        SwitchRow("Download on Wi-Fi only", "Downloads wait for Wi-Fi instead of using mobile data", settings.downloadOnWifiOnly) {
            onChange(settings.copy(downloadOnWifiOnly = it))
        }
        InsetDivider()
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Storage used", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (downloads.isEmpty()) "Nothing downloaded" else "$songs · ${formatBytes(downloads.sumOf { it.bytes })}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (downloads.isNotEmpty() && !confirming) PillButton("Remove all", { confirming = true }, primary = false)
        }
        if (downloads.isNotEmpty() && confirming) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Remove all downloads? $songs will stream again and need a connection.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Remove all downloads", { confirming = false; onRemoveAll() })
                    PillButton("Keep", { confirming = false }, primary = false)
                }
            }
        }
    }
}

@Composable
private fun CrossfadeRow(seconds: Int, onChange: (Int) -> Unit) {
    val label = if (seconds == 0) "Off" else "$seconds s"
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Crossfade", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "Overlaps the end of a song with the start of the next. Songs from one album still play without a gap.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = seconds.toFloat(),
            onValueChange = { it.roundToInt().let { picked -> if (picked != seconds) onChange(picked) } },
            valueRange = 0f..SitaraSettings.MAX_CROSSFADE_SECONDS.toFloat(),
            steps = SitaraSettings.MAX_CROSSFADE_SECONDS - 1,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.semantics {
                contentDescription = "Crossfade"
                stateDescription = if (seconds == 0) "Off" else "$seconds seconds"
            },
        )
    }
}

/** Opens a page of its own; [value] says what is set there. */
@Composable
private fun LinkRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(SitaraIcons.Back, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).size(20.dp).graphicsLayer { rotationZ = 180f })
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 20.dp)) {
        SectionHeader(title)
        Column(
            Modifier.padding(horizontal = SitaraSpacing.Gutter).fillMaxWidth()
                .clip(RoundedCornerShape(SitaraRadius.Tile)).background(MaterialTheme.colorScheme.surfaceContainer),
            content = content,
        )
    }
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Choice(label: String, selected: Boolean, description: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        RadioButton(
            selected, onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
