package app.sitara.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.sitara.music.core.model.EqualiserSettings
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.PlaybackTuning
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.Track
import app.sitara.music.feature.player.LyricsState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.player.RadioStatus
import app.sitara.music.feature.player.SleepTimerStatus

/** Debug capture fixture: a frozen clock and local queue, with no service or network connection. */
internal class DemoPlayer(private var original: List<Track>, lyrics: Lyrics) : PlayerActions {
    private var queue = original
    private var index = 0
    private var removed: Pair<Int, Track>? = null
    var state by mutableStateOf(
        PlayerUiState(
            track = original.first(),
            durationMs = original.first().durationMs,
            positionMs = 15_300,
            isPlaying = true,
            upNext = original.drop(1),
            lyrics = LyricsState.Found(lyrics),
        ),
    )
        private set

    private fun select(target: Int) {
        index = target.coerceIn(queue.indices)
        state = state.copy(track = queue[index], durationMs = queue[index].durationMs, positionMs = 0, upNext = queue.drop(index + 1), failed = false)
    }

    override fun togglePlay() { state = state.copy(isPlaying = !state.isPlaying) }
    override fun retryPlayback() { state = state.copy(isPlaying = true, failed = false) }
    override fun next() = select(index + 1)
    override fun previous() = select(index - 1)
    override fun seekTo(positionMs: Long) { state = state.copy(positionMs = positionMs.coerceIn(0, state.durationMs)) }
    override fun toggleShuffle() {
        val current = queue[index]
        queue = if (state.shuffled) original else listOf(current) + queue.filter { it.id != current.id }.reversed()
        index = queue.indexOf(current)
        state = state.copy(shuffled = !state.shuffled, upNext = queue.drop(index + 1))
    }
    override fun cycleRepeat() { state = state.copy(repeatMode = RepeatMode.entries[(state.repeatMode.ordinal + 1) % RepeatMode.entries.size]) }
    override fun playQueueIndex(upNextIndex: Int) {
        if (upNextIndex in state.upNext.indices) select(index + 1 + upNextIndex)
    }
    // The demo clock is frozen, so a timer shows its full length and never runs out.
    override fun startSleepTimer(minutes: Int) { state = state.copy(sleepTimer = SleepTimerStatus.Counting(minutes * 60_000L)) }
    override fun extendSleepTimer(minutes: Int) {
        (state.sleepTimer as? SleepTimerStatus.Counting)?.let { state = state.copy(sleepTimer = SleepTimerStatus.Counting(it.remainingMs + minutes * 60_000L)) }
    }
    override fun sleepAtEndOfTrack() { state = state.copy(sleepTimer = SleepTimerStatus.EndOfTrack) }
    override fun cancelSleepTimer() { state = state.copy(sleepTimer = SleepTimerStatus.Off) }
    override fun setSpeed(speed: Float) { state = state.copy(tuning = state.tuning.withSpeed(speed)) }
    override fun setPitch(semitones: Int) { state = state.copy(tuning = state.tuning.withSemitones(semitones)) }
    override fun resetTuning() { state = state.copy(tuning = PlaybackTuning()) }
    // Every sample song is already in the demo queue, so there is nothing left to suggest.
    override fun startRadio() { state = state.copy(radio = RadioStatus.UNAVAILABLE) }
    override fun toggleLike() { state = state.copy(liked = !state.liked) }
    override fun playNext(track: Track) = editQueue { add(index + 1, track) }
    override fun addToQueue(track: Track) = editQueue { add(track) }
    override fun moveUpNext(from: Int, to: Int) {
        if (from in state.upNext.indices && to in state.upNext.indices) editQueue { add(index + 1 + to, removeAt(index + 1 + from)) }
    }
    override fun removeUpNext(index: Int) {
        if (index in state.upNext.indices) editQueue { removed = index to removeAt(this@DemoPlayer.index + 1 + index) }
    }
    override fun undoRemoveUpNext() {
        val (at, track) = removed ?: return
        removed = null
        editQueue { add((index + 1 + at).coerceAtMost(size), track) }
    }
    // The demo's lyrics are built in, so they never fail.
    override fun retryLyrics() = Unit
    override fun setEqualiser(equaliser: EqualiserSettings) { state = state.copy(equaliser = equaliser) }

    // The demo offers no phone equaliser (systemEqualiser stays false), so there is nothing to open.
    override fun openSystemEqualiser() = Unit
    override fun setTrimDb(db: Int) { state = state.copy(trimDb = db) }

    private fun editQueue(change: MutableList<Track>.() -> Unit) {
        queue = queue.toMutableList().apply(change)
        state = state.copy(upNext = queue.drop(index + 1))
    }
    fun play(tracks: List<Track>, start: Int) {
        if (tracks.isEmpty()) return
        original = tracks
        queue = tracks
        select(start)
        state = state.copy(isPlaying = true, shuffled = false)
    }
}
