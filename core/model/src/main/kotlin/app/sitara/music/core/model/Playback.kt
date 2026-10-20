package app.sitara.music.core.model

enum class RepeatMode { OFF, ALL, ONE }

enum class ThemeMode { DYNAMIC, ARTWORK, AMOLED }

enum class DarkMode { SYSTEM, LIGHT, DARK }

/** User preferences. Defaults are the values a fresh install starts with. */
data class SitaraSettings(
    val themeMode: ThemeMode = ThemeMode.ARTWORK,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val reducedMotion: Boolean = false,
    val crossfadeSeconds: Int = 0,
    val normalisation: Boolean = true,
    val targetLufs: Float = DEFAULT_TARGET_LUFS,
    /** Downloads wait for an unmetered network (Wi-Fi) instead of using mobile data. */
    val downloadOnWifiOnly: Boolean = true,
) {
    init {
        require(crossfadeSeconds in 0..MAX_CROSSFADE_SECONDS) { "crossfade out of range: $crossfadeSeconds" }
    }

    companion object {
        const val MAX_CROSSFADE_SECONDS = 12
        const val DEFAULT_TARGET_LUFS = -14f
    }
}

/**
 * The play queue as kept across a restart: songs in play order, the current one and how far into it playback was.
 * When shuffled, [unshuffledOrder] lists the play-order indices in the order the songs were queued.
 */
data class SavedQueue(
    val tracks: List<Track>,
    val currentIndex: Int,
    val positionMs: Long,
    val repeatMode: RepeatMode,
    val unshuffledOrder: List<Int>? = null,
)
