package app.sitara.music.feature.player

import app.sitara.music.core.model.EqualiserSettings
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.LyricsTranslation
import app.sitara.music.core.model.PlaybackTuning
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.StreamFormat
import app.sitara.music.core.model.Track
import app.sitara.music.source.lyrics.LyricsMatch
import app.sitara.music.source.lyrics.TranslationProblem

/** Everything the player surfaces render; produced by the app from the media controller. */
data class PlayerUiState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val upNext: List<Track> = emptyList(),
    /** How many of the last [upNext] songs radio added; they show in a section of their own. */
    val upNextFromRadio: Int = 0,
    val shuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val lyrics: LyricsState = LyricsState.NotFound,
    /** Every lyrics source the app asks, by name, in the order they are asked. */
    val lyricsSources: List<String> = emptyList(),
    /** The shown lyrics' translation, while Translate is on. */
    val translation: TranslationState = TranslationState.Off,
    val sleepTimer: SleepTimerStatus = SleepTimerStatus.Off,
    val radio: RadioStatus = RadioStatus.READY,
    /** Whether the current track is in Liked songs. */
    val liked: Boolean = false,
    /** Speed and pitch for this session. */
    val tuning: PlaybackTuning = PlaybackTuning(),
    /** The current song could not be played; the player surfaces offer Try again and Skip instead of Play. */
    val failed: Boolean = false,
    /** The equaliser as the listener set it. */
    val equaliser: EqualiserSettings = EqualiserSettings(),
    /** The device has an equaliser of its own that Sitara can hand its sound to. */
    val systemEqualiser: Boolean = false,
    /** The current song's volume trim in dB. */
    val trimDb: Int = 0,
    /** The codec and bitrate the current song streamed in, when its stream was looked up while the app ran. */
    val stream: StreamFormat? = null,
)

/** The current song's lyrics, looked up when it starts. */
sealed interface LyricsState {
    data object Loading : LyricsState

    /** [lyrics] are shown; [matches] are every source's lyrics for the song, best first. */
    data class Found(val lyrics: Lyrics, val matches: List<LyricsMatch> = listOf(LyricsMatch(lyrics.source.orEmpty(), lyrics))) : LyricsState

    data object NotFound : LyricsState

    /** The lookup failed (no connection, the lyrics service busy); it can be tried again. */
    data object Failed : LyricsState
}

/** The shown lyrics' translation line by line, once there is one. */
internal val PlayerUiState.translatedLines: List<String>? get() = (translation as? TranslationState.Translated)?.translation?.lines

/** Translate, off until the listener turns it on; [language]s are ISO 639-1 codes. */
sealed interface TranslationState {
    data object Off : TranslationState

    data class Translating(val language: String) : TranslationState

    data class Translated(val translation: LyricsTranslation) : TranslationState

    data class Failed(val problem: TranslationProblem, val language: String) : TranslationState
}

/** Whether "Start radio" can extend the queue from the current song right now. */
enum class RadioStatus { READY, LOADING, UNAVAILABLE }

interface PlayerActions {
    fun togglePlay()

    /** Loads the current song again after it failed to play. */
    fun retryPlayback()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun playQueueIndex(upNextIndex: Int)
    fun startSleepTimer(minutes: Int)

    /** Adds minutes to a running timer. */
    fun extendSleepTimer(minutes: Int)

    fun sleepAtEndOfTrack()

    fun cancelSleepTimer()

    /** 0.5 to 2; snapped to steps of 0.05. */
    fun setSpeed(speed: Float)

    /** Semitones from the original key, within ±6. */
    fun setPitch(semitones: Int)

    /** Back to normal speed and the original key. */
    fun resetTuning()

    /** Queues songs related to the current one after the last entry. */
    fun startRadio()

    /** Likes the current track, or unlikes it. */
    fun toggleLike()

    fun playNext(track: Track)

    fun addToQueue(track: Track)

    /** Positions count from the first song in Up next. */
    fun moveUpNext(from: Int, to: Int)

    fun removeUpNext(index: Int)

    /** Puts the Up next song removed last back where it was. */
    fun undoRemoveUpNext()

    /** Looks up the current song's lyrics again after a failure, or asks the sources that found none again. */
    fun retryLyrics()

    /** Shows the current song's lyrics from the source [providerId] from now on. */
    fun chooseLyrics(providerId: String)

    /** Turns lyric translation on or off. */
    fun toggleTranslation()

    /** Translates the lyrics again after a failure. */
    fun retryTranslation()

    /** Translates into [language] (an ISO 639-1 code) from now on. */
    fun setTranslationLanguage(language: String)

    fun setEqualiser(equaliser: EqualiserSettings)

    /** Opens the device's own equaliser on Sitara's audio session. */
    fun openSystemEqualiser()

    /** The current song's volume trim, within ±6 dB; kept with the song. */
    fun setTrimDb(db: Int)

    object None : PlayerActions {
        override fun togglePlay() = Unit
        override fun retryPlayback() = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun toggleShuffle() = Unit
        override fun cycleRepeat() = Unit
        override fun playQueueIndex(upNextIndex: Int) = Unit
        override fun startSleepTimer(minutes: Int) = Unit
        override fun extendSleepTimer(minutes: Int) = Unit
        override fun sleepAtEndOfTrack() = Unit
        override fun cancelSleepTimer() = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun setPitch(semitones: Int) = Unit
        override fun resetTuning() = Unit
        override fun startRadio() = Unit
        override fun toggleLike() = Unit
        override fun playNext(track: Track) = Unit
        override fun addToQueue(track: Track) = Unit
        override fun moveUpNext(from: Int, to: Int) = Unit
        override fun removeUpNext(index: Int) = Unit
        override fun undoRemoveUpNext() = Unit
        override fun retryLyrics() = Unit
        override fun chooseLyrics(providerId: String) = Unit
        override fun toggleTranslation() = Unit
        override fun retryTranslation() = Unit
        override fun setTranslationLanguage(language: String) = Unit
        override fun setEqualiser(equaliser: EqualiserSettings) = Unit
        override fun openSystemEqualiser() = Unit
        override fun setTrimDb(db: Int) = Unit
    }
}
