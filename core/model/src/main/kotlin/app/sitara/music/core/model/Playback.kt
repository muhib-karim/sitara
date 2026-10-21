package app.sitara.music.core.model

import kotlin.math.pow
import kotlin.math.roundToInt

enum class RepeatMode { OFF, ALL, ONE }

enum class DarkMode { SYSTEM, LIGHT, DARK }

/** The app's accent: a fixed colour, or [ARTWORK], which follows the cover of the song that is playing. */
enum class AccentColor(val label: String) {
    SAFFRON("Saffron"),
    ROSE("Rose"),
    VIOLET("Violet"),
    BLUE("Blue"),
    TEAL("Teal"),
    GREEN("Green"),
    ARTWORK("From artwork"),
}

/** How much data a stream may use: Auto is High on Wi-Fi and Normal on mobile data; Normal is up to 96 kbps; Data saver the lowest offered. */
enum class AudioQuality(val label: String) {
    AUTO("Auto"),
    HIGH("High"),
    NORMAL("Normal"),
    DATA_SAVER("Data saver"),
}

/** The quality to stream at on the network in use: that network's setting, with Auto made concrete. */
fun streamQuality(settings: SitaraSettings, onWifi: Boolean): AudioQuality {
    val chosen = if (onWifi) settings.wifiQuality else settings.mobileQuality
    return if (chosen != AudioQuality.AUTO) chosen else if (onWifi) AudioQuality.HIGH else AudioQuality.NORMAL
}

/** What the playing stream is, for the readout in Now Playing; [label] is null unless both are known. */
data class StreamFormat(val codec: String?, val bitrateKbps: Int) {
    val label: String? get() = codec?.takeIf { bitrateKbps > 0 }?.let { "$it · $bitrateKbps kbps" }
}

/** User preferences. Defaults are the values a fresh install starts with. */
data class SitaraSettings(
    val darkMode: DarkMode = DarkMode.SYSTEM,
    /** Black instead of dark grey in the dark theme, and a Now Playing without the cover's blur. */
    val pureBlack: Boolean = false,
    /** The wallpaper's accent (Material You, Android 12 and later) instead of [accent]. */
    val dynamicColor: Boolean = true,
    val accent: AccentColor = AccentColor.SAFFRON,
    /** Backgrounds, cards and chips take a tint from the cover of the song that is playing. */
    val artworkColorsAcrossApp: Boolean = false,
    val reducedMotion: Boolean = false,
    val crossfadeSeconds: Int = 0,
    val normalisation: Boolean = true,
    val targetLufs: Float = DEFAULT_TARGET_LUFS,
    /** Downloads wait for an unmetered network (Wi-Fi) instead of using mobile data. */
    val downloadOnWifiOnly: Boolean = true,
    /** The player skips silent stretches (Media3 silence skipping in the audio chain). */
    val skipSilence: Boolean = false,
    val equaliser: EqualiserSettings = EqualiserSettings(),
    /** The search filter used last, so Search opens on it again. */
    val searchFilter: SearchFilter = SearchFilter.SONGS,
    /** Searches the listener made, newest first. */
    val recentSearches: List<String> = emptyList(),
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    val librarySort: LibrarySort = LibrarySort.RECENTLY_ADDED,
    /** New plays are neither kept nor counted; what is kept stays. */
    val pauseListeningHistory: Boolean = false,
    /** Stream quality on Wi-Fi (and Ethernet) and on mobile data. */
    val wifiQuality: AudioQuality = AudioQuality.AUTO,
    val mobileQuality: AudioQuality = AudioQuality.AUTO,
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

/** How Library lays out a collection: covers in a grid, or rows. */
enum class LibraryLayout { GRID, LIST }

/** How Library orders a collection. Not every kind offers every order: a playlist has no plays of its own. */
enum class LibrarySort(val label: String) {
    RECENTLY_ADDED("Recently added"),
    RECENTLY_PLAYED("Recently played"),
    A_TO_Z("A to Z"),
    MOST_PLAYED("Most played"),
}

/** One play of a song: when it started (wall clock) and how long it was heard, pauses left out. */
data class Listen(val track: Track, val startedAtMs: Long, val listenedMs: Long)

/** How often a song was played in the kept history and when it last played, for ordering Library by plays. */
data class PlayCount(val trackId: String, val artistId: String?, val lastPlayedAt: Long, val plays: Int)
