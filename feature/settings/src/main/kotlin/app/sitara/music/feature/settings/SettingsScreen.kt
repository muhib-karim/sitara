package app.sitara.music.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.ThemeMode

@Composable
fun SettingsScreen(
    settings: SitaraSettings,
    onChange: (SitaraSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                Icon(SitaraIcons.Back, "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            LargeTitle("Settings")
        }
        item { SectionHeader("Colour") }
        val themes = listOf(
            ThemeMode.ARTWORK to "From artwork",
            ThemeMode.DYNAMIC to "Material You (wallpaper)",
            ThemeMode.AMOLED to "AMOLED black",
        )
        themes.forEach { (mode, label) ->
            item { Choice(label, settings.themeMode == mode) { onChange(settings.copy(themeMode = mode)) } }
        }
        item { SectionHeader("Appearance", modifier = Modifier.padding(top = 12.dp)) }
        listOf(DarkMode.SYSTEM to "Follow system", DarkMode.LIGHT to "Light", DarkMode.DARK to "Dark").forEach { (mode, label) ->
            item { Choice(label, settings.darkMode == mode) { onChange(settings.copy(darkMode = mode)) } }
        }
        item { SectionHeader("Motion and sound", modifier = Modifier.padding(top = 12.dp)) }
        item {
            Toggle("Reduced motion", "Swap springs for instant changes", settings.reducedMotion) { onChange(settings.copy(reducedMotion = it)) }
        }
        item {
            Toggle("Loudness normalisation", "Match every song to ${settings.targetLufs.toInt()} LUFS", settings.normalisation) { onChange(settings.copy(normalisation = it)) }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Crossfade: ${if (settings.crossfadeSeconds == 0) "off" else "${settings.crossfadeSeconds} s"}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Slider(
                    value = settings.crossfadeSeconds.toFloat(),
                    onValueChange = { onChange(settings.copy(crossfadeSeconds = it.toInt())) },
                    valueRange = 0f..SitaraSettings.MAX_CROSSFADE_SECONDS.toFloat(),
                    steps = SitaraSettings.MAX_CROSSFADE_SECONDS - 1,
                )
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Toggle(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
