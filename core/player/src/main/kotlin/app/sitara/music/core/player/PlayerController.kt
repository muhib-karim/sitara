package app.sitara.music.core.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.sitara.music.core.model.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UI-side facade. Connecting a MediaController binds [PlaybackService], which creates the
 * [PlaybackEngine]; commands go to that engine (buffered by [EngineGate] until it exists) and
 * state is read from it. Releasing only drops the binding: playback, the queue and the sleep
 * timer carry on in the service.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerController(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val gate = EngineGate()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var ticker: Job? = null

    val state: StateFlow<PlaybackSnapshot> = PlaybackEngineRegistry.engine
        .flatMapLatest { it?.state ?: flowOf(PlaybackSnapshot()) }
        .stateIn(scope, SharingStarted.Eagerly, PlaybackSnapshot())

    val sleepTimerState: StateFlow<SleepTimerState> = PlaybackEngineRegistry.engine
        .flatMapLatest { it?.sleepTimerState ?: flowOf(SleepTimerState.Off) }
        .stateIn(scope, SharingStarted.Eagerly, SleepTimerState.Off)

    init {
        scope.launch { PlaybackEngineRegistry.engine.collect(gate::attach) }
    }

    /** Safe to call repeatedly; a live connection is reused and never reloads the player. */
    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync().also { future ->
            future.addListener({ runCatching { future.get() } }, ContextCompat.getMainExecutor(context))
        }
        ticker = scope.launch {
            while (isActive) {
                if (state.value.isPlaying) gate.run { it.refresh() }
                delay(POSITION_TICK_MS)
            }
        }
    }

    fun release() {
        ticker?.cancel()
        ticker = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
    }

    fun play(tracks: List<Track>, index: Int) = gate.run { it.play(tracks, index) }

    fun shuffle(tracks: List<Track>) = gate.run { it.shuffle(tracks) }

    fun append(tracks: List<Track>) = gate.run { it.append(tracks) }

    fun addToQueue(tracks: List<Track>) = gate.run { it.addToQueue(tracks) }

    fun playNext(tracks: List<Track>) = gate.run { it.playNext(tracks) }

    fun moveUpNext(from: Int, to: Int) = gate.run { it.moveUpNext(from, to) }

    fun removeUpNext(index: Int) = gate.run { it.removeUpNext(index) }

    fun togglePlay() = gate.run { it.togglePlay() }

    fun next() = gate.run { it.next() }

    fun previous() = gate.run { it.previous() }

    fun seekTo(positionMs: Long) = gate.run { it.seekTo(positionMs) }

    fun toggleShuffle() = gate.run { it.toggleShuffle() }

    fun cycleRepeat() = gate.run { it.cycleRepeat() }

    fun playUpNext(upNextIndex: Int) = gate.run { it.playUpNext(upNextIndex) }

    fun setSpeed(speed: Float) = gate.run { it.setSpeed(speed) }

    fun setPitch(semitones: Int) = gate.run { it.setPitch(semitones) }

    fun resetTuning() = gate.run { it.resetTuning() }

    fun startSleepTimer(minutes: Int) = gate.run { it.startSleepTimer(minutes) }

    fun extendSleepTimer(minutes: Int) = gate.run { it.extendSleepTimer(minutes) }

    fun sleepAtEndOfTrack() = gate.run { it.sleepAtEndOfTrack() }

    fun cancelSleepTimer() = gate.run { it.cancelSleepTimer() }

    private companion object {
        const val POSITION_TICK_MS = 250L
    }
}
