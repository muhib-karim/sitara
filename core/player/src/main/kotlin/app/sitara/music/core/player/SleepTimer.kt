package app.sitara.music.core.player

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
 * Pauses playback after a number of minutes, or when the current track ends.
 * Ticks once a second against [clock] so the remaining time never drifts.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onExpire: () -> Unit,
) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(minutes: Int) {
        require(minutes > 0) { "minutes must be positive" }
        job?.cancel()
        val deadline = clock() + minutes * 60_000L
        _state.value = SleepTimerState.Counting(deadline - clock())
        job = scope.launch {
            while (true) {
                val remaining = deadline - clock()
                if (remaining <= 0) break
                _state.value = SleepTimerState.Counting(remaining)
                delay(minOf(TICK_MS, remaining))
            }
            _state.value = SleepTimerState.Off
            onExpire()
        }
    }

    fun startEndOfTrack() {
        job?.cancel()
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
        _state.value = SleepTimerState.Off
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
