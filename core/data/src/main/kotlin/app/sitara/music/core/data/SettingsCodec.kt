package app.sitara.music.core.data

import app.sitara.music.core.model.AccentColor
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.EqualiserSettings
import app.sitara.music.core.model.LibraryLayout
import app.sitara.music.core.model.LibrarySort
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings

/**
 * Settings <-> flat string map. Unknown or corrupt values fall back to defaults
 * instead of crashing, so a bad backup or an old version never bricks the app.
 */
object SettingsCodec {
    const val DARK = "dark_mode"
    const val PURE_BLACK = "pure_black"
    const val DYNAMIC_COLOR = "dynamic_color"
    const val ACCENT = "accent"
    const val ARTWORK_ACROSS_APP = "artwork_colors_across_app"

    /** Up to 0.9.3: DYNAMIC, ARTWORK or AMOLED; read only to carry AMOLED over to [PURE_BLACK]. */
    const val LEGACY_THEME = "theme_mode"
    const val REDUCED_MOTION = "reduced_motion"
    const val CROSSFADE = "crossfade_seconds"
    const val NORMALISE = "normalisation"
    const val TARGET_LUFS = "target_lufs"
    const val WIFI_ONLY = "download_wifi_only"
    const val SKIP_SILENCE = "skip_silence"
    const val EQ_ON = "equaliser_on"
    const val EQ_BANDS = "equaliser_bands"
    const val EQ_PREAMP = "equaliser_preamp"
    const val EQ_SYSTEM = "equaliser_system"
    const val SEARCH_FILTER = "search_filter"
    const val RECENT_SEARCHES = "recent_searches"
    const val LIBRARY_LAYOUT = "library_layout"
    const val LIBRARY_SORT = "library_sort"
    const val PAUSE_HISTORY = "pause_listening_history"

    fun encode(s: SitaraSettings): Map<String, String> = mapOf(
        DARK to s.darkMode.name,
        PURE_BLACK to s.pureBlack.toString(),
        DYNAMIC_COLOR to s.dynamicColor.toString(),
        ACCENT to s.accent.name,
        ARTWORK_ACROSS_APP to s.artworkColorsAcrossApp.toString(),
        REDUCED_MOTION to s.reducedMotion.toString(),
        CROSSFADE to s.crossfadeSeconds.toString(),
        NORMALISE to s.normalisation.toString(),
        TARGET_LUFS to s.targetLufs.toString(),
        WIFI_ONLY to s.downloadOnWifiOnly.toString(),
        SKIP_SILENCE to s.skipSilence.toString(),
        EQ_ON to s.equaliser.enabled.toString(),
        EQ_BANDS to s.equaliser.bandsDb.joinToString(","),
        EQ_PREAMP to s.equaliser.preampDb.toString(),
        EQ_SYSTEM to s.equaliser.useSystem.toString(),
        SEARCH_FILTER to s.searchFilter.name,
        // One search per line: a search is kept on a single line (withRecentSearch).
        RECENT_SEARCHES to s.recentSearches.joinToString("\n"),
        LIBRARY_LAYOUT to s.libraryLayout.name,
        LIBRARY_SORT to s.librarySort.name,
        PAUSE_HISTORY to s.pauseListeningHistory.toString(),
    )

    fun decode(map: Map<String, String>): SitaraSettings {
        val d = SitaraSettings()
        // AMOLED was always black, whatever the light or dark choice; until the new keys are written it stays so.
        val amoled = PURE_BLACK !in map && map[LEGACY_THEME] == "AMOLED"
        return SitaraSettings(
            darkMode = if (amoled) DarkMode.DARK else map[DARK]?.let { v -> DarkMode.entries.firstOrNull { it.name == v } } ?: d.darkMode,
            pureBlack = amoled || (map[PURE_BLACK]?.toBooleanStrictOrNull() ?: d.pureBlack),
            dynamicColor = map[DYNAMIC_COLOR]?.toBooleanStrictOrNull() ?: d.dynamicColor,
            accent = map[ACCENT]?.let { v -> AccentColor.entries.firstOrNull { it.name == v } } ?: d.accent,
            artworkColorsAcrossApp = map[ARTWORK_ACROSS_APP]?.toBooleanStrictOrNull() ?: d.artworkColorsAcrossApp,
            reducedMotion = map[REDUCED_MOTION]?.toBooleanStrictOrNull() ?: d.reducedMotion,
            crossfadeSeconds = map[CROSSFADE]?.toIntOrNull()?.coerceIn(0, SitaraSettings.MAX_CROSSFADE_SECONDS) ?: d.crossfadeSeconds,
            normalisation = map[NORMALISE]?.toBooleanStrictOrNull() ?: d.normalisation,
            targetLufs = map[TARGET_LUFS]?.toFloatOrNull()?.takeIf { it in -30f..-5f } ?: d.targetLufs,
            downloadOnWifiOnly = map[WIFI_ONLY]?.toBooleanStrictOrNull() ?: d.downloadOnWifiOnly,
            skipSilence = map[SKIP_SILENCE]?.toBooleanStrictOrNull() ?: d.skipSilence,
            equaliser = EqualiserSettings(
                enabled = map[EQ_ON]?.toBooleanStrictOrNull() ?: d.equaliser.enabled,
                bandsDb = decodeBands(map[EQ_BANDS]) ?: d.equaliser.bandsDb,
                preampDb = map[EQ_PREAMP]?.toIntOrNull()?.coerceIn(-GAIN, GAIN) ?: d.equaliser.preampDb,
                useSystem = map[EQ_SYSTEM]?.toBooleanStrictOrNull() ?: d.equaliser.useSystem,
            ),
            searchFilter = map[SEARCH_FILTER]?.let { v -> SearchFilter.entries.firstOrNull { it.name == v } } ?: d.searchFilter,
            recentSearches = map[RECENT_SEARCHES]?.lines()?.filter { it.isNotBlank() } ?: d.recentSearches,
            libraryLayout = map[LIBRARY_LAYOUT]?.let { v -> LibraryLayout.entries.firstOrNull { it.name == v } } ?: d.libraryLayout,
            librarySort = map[LIBRARY_SORT]?.let { v -> LibrarySort.entries.firstOrNull { it.name == v } } ?: d.librarySort,
            pauseListeningHistory = map[PAUSE_HISTORY]?.toBooleanStrictOrNull() ?: d.pauseListeningHistory,
        )
    }

    private const val GAIN = EqualiserSettings.MAX_GAIN_DB

    // Exactly one whole number of decibels per band; anything else is corrupt.
    private fun decodeBands(text: String?): List<Int>? =
        text?.split(',')?.map { it.toIntOrNull()?.coerceIn(-GAIN, GAIN) ?: return null }?.takeIf { it.size == EqualiserSettings.BAND_COUNT }
}
