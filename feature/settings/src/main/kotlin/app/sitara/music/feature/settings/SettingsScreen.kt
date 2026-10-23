package app.sitara.music.feature.settings

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.sitara.music.core.common.formatBytes
import app.sitara.music.core.designsystem.ArtworkPalette
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SectionHeader
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.accentTone
import app.sitara.music.core.designsystem.displayName
import app.sitara.music.core.designsystem.exportSummary
import app.sitara.music.core.designsystem.sitaraColorScheme
import app.sitara.music.core.designsystem.surfaceTint
import app.sitara.music.core.model.AccentColor
import app.sitara.music.core.model.AudioQuality
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.DownloadExport
import app.sitara.music.core.model.DownloadState
import app.sitara.music.core.model.Palette
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
    equaliserSummary: String = stringResource(R.string.settings_off),
    onOpenEqualiser: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    onImportPlaylist: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    scrobblingSummary: String = stringResource(R.string.settings_off),
    onOpenScrobbling: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    export: DownloadExport = DownloadExport.Idle,
    onExportDownloads: () -> Unit = {},
) {
    LazyColumn(modifier.fillMaxSize().testTag("settings-content"), contentPadding = contentPadding) {
        item(key = "title") {
            GlassIconButton(SitaraIcons.Back, stringResource(R.string.settings_back), onBack, Modifier.padding(start = 12.dp, top = 8.dp))
            LargeTitle(stringResource(R.string.settings_title))
        }
        item(key = "appearance") {
            AppearanceSettings(settings, onChange)
        }
        item(key = "motion") {
            SettingsGroup(stringResource(R.string.settings_motion)) {
                SwitchRow(
                    stringResource(R.string.settings_reduced_motion), stringResource(R.string.settings_reduced_motion_description), settings.reducedMotion,
                ) { onChange(settings.copy(reducedMotion = it)) }
            }
        }
        item(key = "playback") {
            PlaybackSettings(settings, onChange)
        }
        item(key = "sound") {
            SettingsGroup(stringResource(R.string.settings_sound)) {
                LinkRow(stringResource(R.string.settings_equaliser), equaliserSummary, onOpenEqualiser)
                InsetDivider()
                SwitchRow(stringResource(R.string.settings_normalise_loudness), stringResource(R.string.settings_normalise_loudness_description), settings.normalisation) { onChange(settings.copy(normalisation = it)) }
                InsetDivider()
                SwitchRow(stringResource(R.string.settings_skip_silence), stringResource(R.string.settings_skip_silence_description), settings.skipSilence) { onChange(settings.copy(skipSilence = it)) }
                InsetDivider()
                CrossfadeRow(settings.crossfadeSeconds) { onChange(settings.copy(crossfadeSeconds = it)) }
                InsetDivider()
                SwitchRow(
                    stringResource(R.string.settings_automix),
                    stringResource(R.string.settings_automix_description),
                    settings.automix,
                ) { onChange(settings.copy(automix = it)) }
            }
        }
        item(key = "library") {
            SettingsGroup(stringResource(R.string.settings_library)) {
                LinkRow(stringResource(R.string.settings_import_playlist), stringResource(R.string.settings_import_playlist_value), onImportPlaylist)
                InsetDivider()
                LinkRow(stringResource(R.string.settings_backup_and_restore), stringResource(R.string.settings_backup_and_restore_value), onOpenBackup)
            }
        }
        item(key = "history") {
            HistorySettings(settings, onChange, onOpenStats, onClearHistory, scrobblingSummary, onOpenScrobbling)
        }
        item(key = "downloads") {
            DownloadSettings(settings, onChange, downloads, onRemoveAllDownloads, export, onExportDownloads)
        }
    }
}

@Composable
private fun AppearanceSettings(settings: SitaraSettings, onChange: (SitaraSettings) -> Unit) {
    // Wallpaper colours need Android 12; below it the option is hidden and the picked accent always applies.
    val wallpaperAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val wallpaper = wallpaperAvailable && settings.dynamicColor
    val pick: (AccentColor) -> Unit = { onChange(settings.copy(accent = it, dynamicColor = false)) }
    SettingsGroup(stringResource(R.string.settings_appearance)) {
        AppearancePreview()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                DarkMode.SYSTEM to stringResource(R.string.settings_theme_system),
                DarkMode.LIGHT to stringResource(R.string.settings_theme_light),
                DarkMode.DARK to stringResource(R.string.settings_theme_dark),
            ).forEach { (mode, label) ->
                Segment(label, settings.darkMode == mode, Modifier.weight(1f)) { onChange(settings.copy(darkMode = mode)) }
            }
        }
        InsetDivider()
        SwitchRow(stringResource(R.string.settings_pure_black), stringResource(R.string.settings_pure_black_description), settings.pureBlack) {
            onChange(settings.copy(pureBlack = it))
        }
        if (wallpaperAvailable) {
            InsetDivider()
            SwitchRow(stringResource(R.string.settings_wallpaper_colours), stringResource(R.string.settings_wallpaper_colours_description), settings.dynamicColor) { onChange(settings.copy(dynamicColor = it)) }
        }
        InsetDivider()
        Column(Modifier.selectableGroup()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Text(stringResource(R.string.settings_palette), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.settings_palette_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val current = Palette.of(settings.accent, settings.surfaceTone).takeIf { !wallpaper }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp)) {
                Palette.entries.forEach { palette ->
                    PaletteCard(palette, dark, selected = palette == current) {
                        onChange(settings.copy(accent = palette.accent, surfaceTone = palette.tone, dynamicColor = false))
                    }
                }
            }
        }
        InsetDivider()
        Column(Modifier.selectableGroup()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Text(stringResource(R.string.settings_accent), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(if (wallpaper) R.string.settings_accent_description_wallpaper else R.string.settings_accent_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                AccentColor.entries.forEach { accent ->
                    accentTone(accent, dark)?.let { tone -> Swatch(accent.displayName(), tone, selected = !wallpaper && settings.accent == accent) { pick(accent) } }
                }
            }
            Choice(
                AccentColor.ARTWORK.displayName(), !wallpaper && settings.accent == AccentColor.ARTWORK,
                description = stringResource(R.string.settings_accent_artwork_description),
            ) { pick(AccentColor.ARTWORK) }
        }
        InsetDivider()
        SwitchRow(stringResource(R.string.settings_artwork_colours), stringResource(R.string.settings_artwork_colours_description), settings.artworkColorsAcrossApp) {
            onChange(settings.copy(artworkColorsAcrossApp = it))
        }
        // Android 13 and later keep a language per app; before that Sitara follows the phone's language.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InsetDivider()
            LanguageRow()
        }
    }
}

/** The app's language: the phone's, English or Bangla, kept by the system (LocaleManager) like its own per-app setting. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(LocaleManager::class.java) }
    var current by remember { mutableStateOf(manager?.applicationLocales?.toLanguageTags().orEmpty()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val choices = listOf("" to stringResource(R.string.settings_language_system), "en" to "English", "bn" to "বাংলা")
    LinkRow(stringResource(R.string.settings_language), choices.firstOrNull { current.startsWith(it.first) && it.first.isNotEmpty() }?.second ?: choices[0].second) { picking = true }
    if (picking) {
        SitaraSheet(onDismiss = { picking = false }) { close ->
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp).selectableGroup()) {
                choices.forEach { (tag, label) ->
                    Choice(label, selected = if (tag.isEmpty()) current.isEmpty() else current.startsWith(tag)) {
                        close {
                            current = tag
                            // The system recreates the screens in the new language.
                            manager?.applicationLocales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                        }
                    }
                }
            }
        }
    }
}

/** A still picture of the chosen colours: the page, a card, chips and a playing song. One node for TalkBack, with nothing to press. */
@Composable
private fun AppearancePreview() {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.settings_preview_description)
    Column(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(SitaraRadius.Tile)).background(colors.background).padding(12.dp)
            .testTag("appearance-preview").clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                stringResource(R.string.settings_preview_chill) to true,
                stringResource(R.string.settings_preview_focus) to false,
                stringResource(R.string.settings_preview_party) to false,
            ).forEach { (label, selected) ->
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
                    Text(stringResource(R.string.settings_preview_song_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(stringResource(R.string.settings_preview_artist), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
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

/** A palette to pick: its surface and accent side by side in one circle, ringed when chosen, named under it. */
@Composable
private fun PaletteCard(palette: Palette, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val scheme = sitaraColorScheme(dark, accent = accentTone(palette.accent, dark), tint = surfaceTint(palette.tone))
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(SitaraRadius.Cover)).selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(52.dp).border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape))
            Row(Modifier.size(42.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)) {
                Box(Modifier.weight(1f).fillMaxHeight().background(scheme.surfaceContainerHigh))
                Box(Modifier.weight(1f).fillMaxHeight().background(scheme.primary))
            }
            if (selected) Icon(SitaraIcons.Check, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp).offset(x = 16.dp, y = 16.dp))
        }
        Text(palette.displayName(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 2)
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

/** Stream quality for Wi-Fi and for mobile data, each picked in a sheet. */
@Composable
private fun PlaybackSettings(settings: SitaraSettings, onChange: (SitaraSettings) -> Unit) {
    var picking by rememberSaveable { mutableStateOf<Boolean?>(null) } // true: Wi-Fi, false: mobile data
    SettingsGroup(stringResource(R.string.settings_playback)) {
        LinkRow(stringResource(R.string.settings_quality_wifi), settings.wifiQuality.displayName()) { picking = true }
        InsetDivider()
        LinkRow(stringResource(R.string.settings_quality_mobile), settings.mobileQuality.displayName()) { picking = false }
    }
    picking?.let { wifi ->
        val current = if (wifi) settings.wifiQuality else settings.mobileQuality
        SitaraSheet(onDismiss = { picking = null }) { close ->
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp).selectableGroup()) {
                Text(
                    stringResource(if (wifi) R.string.settings_quality_wifi else R.string.settings_quality_mobile), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                AudioQuality.entries.forEach { quality ->
                    Choice(quality.displayName(), quality == current, qualityDescription(quality, wifi)) {
                        close { onChange(if (wifi) settings.copy(wifiQuality = quality) else settings.copy(mobileQuality = quality)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun qualityDescription(quality: AudioQuality, wifi: Boolean): String = stringResource(
    when (quality) {
        AudioQuality.AUTO -> if (wifi) R.string.settings_quality_auto_wifi else R.string.settings_quality_auto_mobile
        AudioQuality.HIGH -> R.string.settings_quality_high_description
        AudioQuality.NORMAL -> R.string.settings_quality_normal_description
        AudioQuality.DATA_SAVER -> R.string.settings_quality_data_saver_description
    },
)

/** Stats and the listener's say over the plays kept for them on the phone, and scrobbling, which sends plays out. */
@Composable
private fun HistorySettings(
    settings: SitaraSettings,
    onChange: (SitaraSettings) -> Unit,
    onOpenStats: () -> Unit,
    onClear: () -> Unit,
    scrobblingSummary: String,
    onOpenScrobbling: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SettingsGroup(stringResource(R.string.settings_listening_history)) {
        LinkRow(stringResource(R.string.settings_your_stats), stringResource(R.string.settings_your_stats_value), onOpenStats)
        InsetDivider()
        LinkRow(stringResource(R.string.settings_scrobbling), scrobblingSummary, onOpenScrobbling)
        InsetDivider()
        SwitchRow(stringResource(R.string.settings_pause_history), stringResource(R.string.settings_pause_history_description), settings.pauseListeningHistory) {
            onChange(settings.copy(pauseListeningHistory = it))
        }
        InsetDivider()
        if (confirming) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.settings_clear_history_confirm),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(stringResource(R.string.settings_clear_history_button), { confirming = false; onClear() })
                    PillButton(stringResource(R.string.settings_keep_history), { confirming = false }, primary = false)
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { confirming = true }.padding(16.dp)) {
                Text(stringResource(R.string.settings_clear_history), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.settings_clear_history_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DownloadSettings(
    settings: SitaraSettings,
    onChange: (SitaraSettings) -> Unit,
    downloads: List<SongDownload>,
    onRemoveAll: () -> Unit,
    export: DownloadExport,
    onExport: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val songs = pluralStringResource(R.plurals.settings_songs, downloads.size, downloads.size)
    SettingsGroup(stringResource(R.string.settings_downloads)) {
        SwitchRow(stringResource(R.string.settings_wifi_only), stringResource(R.string.settings_wifi_only_description), settings.downloadOnWifiOnly) {
            onChange(settings.copy(downloadOnWifiOnly = it))
        }
        InsetDivider()
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.settings_storage_used), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (downloads.isEmpty()) {
                        stringResource(R.string.settings_nothing_downloaded)
                    } else {
                        stringResource(R.string.settings_storage_summary, songs, formatBytes(downloads.sumOf { it.bytes }))
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (downloads.isNotEmpty() && !confirming) PillButton(stringResource(R.string.settings_remove_all), { confirming = true }, primary = false)
        }
        InsetDivider()
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.settings_export_folder), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    exportSummary(export) ?: stringResource(R.string.settings_export_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (export is DownloadExport.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (downloads.any { it.state == DownloadState.DOWNLOADED } && export !is DownloadExport.Working) PillButton(stringResource(R.string.settings_export), onExport, primary = false)
        }
        if (downloads.isNotEmpty() && confirming) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    pluralStringResource(R.plurals.settings_remove_all_confirm, downloads.size, downloads.size),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(stringResource(R.string.settings_remove_all_downloads), { confirming = false; onRemoveAll() })
                    PillButton(stringResource(R.string.settings_keep), { confirming = false }, primary = false)
                }
            }
        }
    }
}

@Composable
private fun CrossfadeRow(seconds: Int, onChange: (Int) -> Unit) {
    val off = stringResource(R.string.settings_off)
    val label = if (seconds == 0) off else stringResource(R.string.settings_crossfade_value, seconds)
    val title = stringResource(R.string.settings_crossfade)
    val spoken = if (seconds == 0) off else pluralStringResource(R.plurals.settings_crossfade_seconds, seconds, seconds)
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.settings_crossfade_description),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = seconds.toFloat(),
            onValueChange = { it.roundToInt().let { picked -> if (picked != seconds) onChange(picked) } },
            valueRange = 0f..SitaraSettings.MAX_CROSSFADE_SECONDS.toFloat(),
            steps = SitaraSettings.MAX_CROSSFADE_SECONDS - 1,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.semantics {
                contentDescription = title
                stateDescription = spoken
            },
        )
    }
}

/** Opens a page of its own; [value] says what is set there. */
@Composable
internal fun LinkRow(title: String, value: String, onClick: () -> Unit) {
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
internal fun SwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
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
internal fun InsetDivider() {
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
