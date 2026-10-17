package app.sitara.music.feature.player

import app.sitara.music.core.model.Lyrics
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
    val lyrics: Lyrics = Lyrics.Empty,
    val sleepTimerLabel: String? = null,
)

interface PlayerActions {
    fun togglePlay()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun playQueueIndex(upNextIndex: Int)
    fun cycleSleepTimer()

    object None : PlayerActions {
        override fun togglePlay() = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun toggleShuffle() = Unit
        override fun cycleRepeat() = Unit
        override fun playQueueIndex(upNextIndex: Int) = Unit
        override fun cycleSleepTimer() = Unit
    }
}
