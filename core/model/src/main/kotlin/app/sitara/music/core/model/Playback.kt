package app.sitara.music.core.model

import kotlin.math.pow
import kotlin.math.roundToInt

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
    /** The player skips silent stretches (Media3 silence skipping in the audio chain). */
    val skipSilence: Boolean = false,
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

/**
 * Playback speed and pitch for the listening session (they reset when playback stops). Pitch moves in semitones,
 * independently of speed, so a song can be slowed without dropping in key, or played in another key at its own tempo.
 */
data class PlaybackTuning(val speed: Float = 1f, val semitones: Int = 0) {
    init {
        require(speed in MIN_SPEED..MAX_SPEED) { "speed out of range: $speed" }
        require(semitones in -MAX_SEMITONES..MAX_SEMITONES) { "pitch out of range: $semitones" }
    }

    /** The frequency ratio a player takes: 2^(semitones / 12). */
    val pitch: Float get() = 2.0.pow(semitones / 12.0).toFloat()

    val isDefault: Boolean get() = this == PlaybackTuning()

    // Dividing a whole number of steps gives the same Float as the literal (1.3f), so presets compare equal.
    fun withSpeed(speed: Float): PlaybackTuning = copy(speed = ((speed * SPEED_STEPS_PER_UNIT).roundToInt().toFloat() / SPEED_STEPS_PER_UNIT).coerceIn(MIN_SPEED, MAX_SPEED))

    fun withSemitones(semitones: Int): PlaybackTuning = copy(semitones = semitones.coerceIn(-MAX_SEMITONES, MAX_SEMITONES))

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2f
        /** Speed moves in steps of 0.05. */
        const val SPEED_STEPS_PER_UNIT = 20
        const val MAX_SEMITONES = 6
    }
}
