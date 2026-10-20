package app.sitara.music.core.data

import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.EqualiserSettings
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.ThemeMode

/**
 * Settings <-> flat string map. Unknown or corrupt values fall back to defaults
 * instead of crashing, so a bad backup or an old version never bricks the app.
 */
object SettingsCodec {
    const val THEME = "theme_mode"
    const val DARK = "dark_mode"
    const val REDUCED_MOTION = "reduced_motion"
    const val CROSSFADE = "crossfade_seconds"
    const val NORMALISE = "normalisation"
    const val TARGET_LUFS = "target_lufs"
    const val WIFI_ONLY = "download_wifi_only"
    const val SKIP_SILENCE = "skip_silence"
    const val EQ_ON = "equaliser_on"
    const val EQ_BANDS = "equaliser_bands"
    const val EQ_PREAMP = "equaliser_preamp"

    fun encode(s: SitaraSettings): Map<String, String> = mapOf(
        THEME to s.themeMode.name,
        DARK to s.darkMode.name,
        REDUCED_MOTION to s.reducedMotion.toString(),
        CROSSFADE to s.crossfadeSeconds.toString(),
        NORMALISE to s.normalisation.toString(),
        TARGET_LUFS to s.targetLufs.toString(),
        WIFI_ONLY to s.downloadOnWifiOnly.toString(),
        SKIP_SILENCE to s.skipSilence.toString(),
        EQ_ON to s.equaliser.enabled.toString(),
        EQ_BANDS to s.equaliser.bandsDb.joinToString(","),
        EQ_PREAMP to s.equaliser.preampDb.toString(),
    )

    fun decode(map: Map<String, String>): SitaraSettings {
        val d = SitaraSettings()
        return SitaraSettings(
            themeMode = map[THEME]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: d.themeMode,
            darkMode = map[DARK]?.let { v -> DarkMode.entries.firstOrNull { it.name == v } } ?: d.darkMode,
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
            ),
        )
    }

    private const val GAIN = EqualiserSettings.MAX_GAIN_DB

    // Exactly one whole number of decibels per band; anything else is corrupt.
    private fun decodeBands(text: String?): List<Int>? =
        text?.split(',')?.map { it.toIntOrNull()?.coerceIn(-GAIN, GAIN) ?: return null }?.takeIf { it.size == EqualiserSettings.BAND_COUNT }
}
