package app.sitara.music.feature.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
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
                Row(
                    Modifier.fillMaxWidth().toggleable(
                        settings.reducedMotion,
                        role = Role.Switch,
                        onValueChange = { onChange(settings.copy(reducedMotion = it)) },
                    ).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Reduced motion", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Instant transitions, without blur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.reducedMotion,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
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
