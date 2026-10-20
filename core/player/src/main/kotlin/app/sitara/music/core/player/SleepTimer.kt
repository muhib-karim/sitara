package app.sitara.music.core.player

import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SleepTimerState {
    data object Off : SleepTimerState

    data class Counting(val remainingMs: Long) : SleepTimerState

    data object EndOfTrack : SleepTimerState
}

/**
 * Pauses playback after a number of minutes, which can be extended while counting, or when the current track ends.
 * Ticks once a second against a monotonic [timeSource], so wall-clock corrections
 * can neither stretch nor cut short the countdown. Production passes [ElapsedRealtimeTimeSource].
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource,
    private val onExpire: () -> Unit,
) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()
    private var job: Job? = null

    // Set while counting; the countdown reads it on every tick, so extending needs no restart.
    private var deadline: TimeMark? = null

    private fun remainingMs(): Long = -(deadline ?: return 0).elapsedNow().inWholeMilliseconds

    fun start(minutes: Int) {
        require(minutes > 0) { "minutes must be positive" }
        job?.cancel()
        deadline = timeSource.markNow() + minutes.minutes
        _state.value = SleepTimerState.Counting(remainingMs())
        job = scope.launch {
            while (true) {
                val remaining = remainingMs()
                if (remaining <= 0) break
                _state.value = SleepTimerState.Counting(remaining)
                delay(minOf(TICK_MS, remaining))
            }
            deadline = null
            _state.value = SleepTimerState.Off
            onExpire()
        }
    }

    /** Adds [minutes] to a running countdown; without one there is nothing to extend. */
    fun extend(minutes: Int) {
        require(minutes > 0) { "minutes must be positive" }
        val current = deadline ?: return
        deadline = current + minutes.minutes
        _state.value = SleepTimerState.Counting(remainingMs())
    }

    fun startEndOfTrack() {
        job?.cancel()
        deadline = null
        _state.value = SleepTimerState.EndOfTrack
    }

    /** Call when a track finishes; returns true if playback should stop. */
    fun onTrackEnded(): Boolean {
        if (_state.value != SleepTimerState.EndOfTrack) return false
        _state.value = SleepTimerState.Off
        onExpire()
        return true
    }

    fun cancel() {
        job?.cancel()
        job = null
        deadline = null
        _state.value = SleepTimerState.Off
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
