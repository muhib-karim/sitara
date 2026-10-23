package app.sitara.music.core.designsystem

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.sitara.music.core.model.AccentColor
import app.sitara.music.core.model.AudioQuality
import app.sitara.music.core.model.EqualiserPreset
import app.sitara.music.core.model.LibrarySort
import app.sitara.music.core.model.Palette
import app.sitara.music.core.model.ScrobbleService
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.StatsRange

// Localized display names for the model's enums. The English resources match each enum's `label` word for word;
// the Resources overloads are for callers outside composition (services, notifications, view models).

@Composable
fun SearchFilter.displayName(): String = stringResource(labelRes())

fun SearchFilter.displayName(resources: Resources): String = resources.getString(labelRes())

@Composable
fun ScrobbleService.displayName(): String = stringResource(labelRes())

fun ScrobbleService.displayName(resources: Resources): String = resources.getString(labelRes())

@Composable
fun StatsRange.displayName(): String = stringResource(labelRes())

fun StatsRange.displayName(resources: Resources): String = resources.getString(labelRes())

@Composable
fun AccentColor.displayName(): String = stringResource(labelRes())

fun AccentColor.displayName(resources: Resources): String = resources.getString(labelRes())

@Composable
fun Palette.displayName(): String = stringResource(labelRes())

fun Palette.displayName(resources: Resources): String = resources.getString(labelRes())

@Composable
fun AudioQuality.displayName(): String = stringResource(labelRes())

fun AudioQuality.displayName(resources: Resources): String = resources.getString(labelRes())

@Composable
fun LibrarySort.displayName(): String = stringResource(labelRes())

fun LibrarySort.displayName(resources: Resources): String = resources.getString(labelRes())

@Composable
fun EqualiserPreset.displayName(): String = stringResource(labelRes())

fun EqualiserPreset.displayName(resources: Resources): String = resources.getString(labelRes())

@StringRes
private fun SearchFilter.labelRes(): Int = when (this) {
    SearchFilter.SONGS -> R.string.ds_label_search_filter_songs
    SearchFilter.ALBUMS -> R.string.ds_label_search_filter_albums
    SearchFilter.ARTISTS -> R.string.ds_label_search_filter_artists
    SearchFilter.PLAYLISTS -> R.string.ds_label_search_filter_playlists
}

@StringRes
private fun ScrobbleService.labelRes(): Int = when (this) {
    ScrobbleService.LISTENBRAINZ -> R.string.ds_label_scrobble_listenbrainz
    ScrobbleService.LASTFM -> R.string.ds_label_scrobble_lastfm
}

@StringRes
private fun StatsRange.labelRes(): Int = when (this) {
    StatsRange.WEEK -> R.string.ds_label_stats_week
    StatsRange.MONTH -> R.string.ds_label_stats_month
    StatsRange.YEAR -> R.string.ds_label_stats_year
    StatsRange.ALL_TIME -> R.string.ds_label_stats_all_time
}

@StringRes
private fun AccentColor.labelRes(): Int = when (this) {
    AccentColor.SAFFRON -> R.string.ds_label_accent_saffron
    AccentColor.ROSE -> R.string.ds_label_accent_rose
    AccentColor.VIOLET -> R.string.ds_label_accent_violet
    AccentColor.BLUE -> R.string.ds_label_accent_blue
    AccentColor.TEAL -> R.string.ds_label_accent_teal
    AccentColor.GREEN -> R.string.ds_label_accent_green
    AccentColor.ARTWORK -> R.string.ds_label_accent_artwork
}

@StringRes
private fun Palette.labelRes(): Int = when (this) {
    Palette.SITARA -> R.string.ds_label_palette_sitara
    Palette.SAFFRON_DUSK -> R.string.ds_label_palette_saffron_dusk
    Palette.LOTUS -> R.string.ds_label_palette_lotus
    Palette.INDIGO_INK -> R.string.ds_label_palette_indigo_ink
    Palette.HARBOUR -> R.string.ds_label_palette_harbour
    Palette.MONSOON -> R.string.ds_label_palette_monsoon
    Palette.TEA_GARDEN -> R.string.ds_label_palette_tea_garden
}

@StringRes
private fun AudioQuality.labelRes(): Int = when (this) {
    AudioQuality.AUTO -> R.string.ds_label_quality_auto
    AudioQuality.HIGH -> R.string.ds_label_quality_high
    AudioQuality.NORMAL -> R.string.ds_label_quality_normal
    AudioQuality.DATA_SAVER -> R.string.ds_label_quality_data_saver
}

@StringRes
private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.RECENTLY_ADDED -> R.string.ds_label_sort_recently_added
    LibrarySort.RECENTLY_PLAYED -> R.string.ds_label_sort_recently_played
    LibrarySort.A_TO_Z -> R.string.ds_label_sort_a_to_z
    LibrarySort.MOST_PLAYED -> R.string.ds_label_sort_most_played
}

@StringRes
private fun EqualiserPreset.labelRes(): Int = when (this) {
    EqualiserPreset.FLAT -> R.string.ds_label_eq_flat
    EqualiserPreset.BASS_BOOST -> R.string.ds_label_eq_bass_boost
    EqualiserPreset.TREBLE_BOOST -> R.string.ds_label_eq_treble_boost
    EqualiserPreset.VOCAL -> R.string.ds_label_eq_vocal
    EqualiserPreset.ROCK -> R.string.ds_label_eq_rock
    EqualiserPreset.POP -> R.string.ds_label_eq_pop
    EqualiserPreset.CLASSICAL -> R.string.ds_label_eq_classical
    EqualiserPreset.ELECTRONIC -> R.string.ds_label_eq_electronic
    EqualiserPreset.ACOUSTIC -> R.string.ds_label_eq_acoustic
    EqualiserPreset.LATE_NIGHT -> R.string.ds_label_eq_late_night
}
