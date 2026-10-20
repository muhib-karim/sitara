package app.sitara.music.feature.player

import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.PlaybackTuning
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.Track

/** Everything the player surfaces render; produced by the app from the media controller. */
data class PlayerUiState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val upNext: List<Track> = emptyList(),
    val shuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val lyrics: LyricsState = LyricsState.NotFound,
    val sleepTimer: SleepTimerStatus = SleepTimerStatus.Off,
    val radio: RadioStatus = RadioStatus.READY,
    /** Whether the current track is in Liked songs. */
    val liked: Boolean = false,
    /** Speed and pitch for this session. */
    val tuning: PlaybackTuning = PlaybackTuning(),
)

/** The current song's lyrics, looked up when it starts. */
sealed interface LyricsState {
    data object Loading : LyricsState

    data class Found(val lyrics: Lyrics) : LyricsState

    data object NotFound : LyricsState

    /** The lookup failed (no connection, the lyrics service busy); it can be tried again. */
    data object Failed : LyricsState
}

/** Whether "Start radio" can extend the queue from the current song right now. */
enum class RadioStatus { READY, LOADING, UNAVAILABLE }

interface PlayerActions {
    fun togglePlay()
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

    /** Looks up the current song's lyrics again after a failure. */
    fun retryLyrics()

    object None : PlayerActions {
        override fun togglePlay() = Unit
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
        override fun retryLyrics() = Unit
    }
}
