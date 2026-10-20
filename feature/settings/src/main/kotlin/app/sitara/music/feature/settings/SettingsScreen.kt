package app.sitara.music.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.sitara.music.core.common.formatBytes
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.SongDownload
import app.sitara.music.core.model.ThemeMode

@Composable
fun SettingsScreen(
    settings: SitaraSettings,
    onChange: (SitaraSettings) -> Unit,
    downloads: List<SongDownload>,
    onRemoveAllDownloads: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(modifier.fillMaxSize().testTag("settings-content"), contentPadding = contentPadding) {
        item(key = "title") {
            GlassIconButton(SitaraIcons.Back, "Back", onBack, Modifier.padding(start = 12.dp, top = 8.dp))
            LargeTitle("Settings")
        }
        item(key = "colour") {
            SettingsGroup("Colour", selection = true) {
                Choice("From artwork", settings.themeMode == ThemeMode.ARTWORK) { onChange(settings.copy(themeMode = ThemeMode.ARTWORK)) }
                InsetDivider()
                // Wallpaper colours require Android 12; hide the option where it cannot work.
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    Choice("Wallpaper accent", settings.themeMode == ThemeMode.DYNAMIC) { onChange(settings.copy(themeMode = ThemeMode.DYNAMIC)) }
                    InsetDivider()
                }
                Choice("AMOLED black", settings.themeMode == ThemeMode.AMOLED) { onChange(settings.copy(themeMode = ThemeMode.AMOLED)) }
            }
        }
        item(key = "appearance") {
            SettingsGroup("Appearance", selection = true) {
                listOf(DarkMode.SYSTEM to "Follow system", DarkMode.LIGHT to "Light", DarkMode.DARK to "Dark").forEachIndexed { index, (mode, label) ->
                    if (index > 0) InsetDivider()
                    Choice(label, settings.darkMode == mode) { onChange(settings.copy(darkMode = mode)) }
                }
            }
        }
        item(key = "motion") {
            SettingsGroup("Motion") {
                SwitchRow("Reduced motion", "Instant transitions, without blur", settings.reducedMotion) { onChange(settings.copy(reducedMotion = it)) }
            }
        }
        item(key = "downloads") {
            DownloadSettings(settings, onChange, downloads, onRemoveAllDownloads)
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
private fun SettingsGroup(title: String, selection: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(bottom = 20.dp)) {
        SectionHeader(title)
        Column(
            Modifier.padding(horizontal = SitaraSpacing.Gutter).fillMaxWidth()
                .clip(RoundedCornerShape(SitaraRadius.Tile)).background(MaterialTheme.colorScheme.surfaceContainer)
                .then(if (selection) Modifier.selectableGroup() else Modifier),
            content = content,
        )
    }
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
            .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        RadioButton(
            selected, onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
