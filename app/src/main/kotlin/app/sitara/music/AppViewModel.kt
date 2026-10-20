package app.sitara.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.sitara.music.core.common.formatDuration
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.Shelf
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.core.player.SleepTimerState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.feature.search.searchStates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val app: SitaraApplication) : ViewModel(), PlayerActions {
    private val player = app.player

    val home = MutableStateFlow<List<Shelf>>(emptyList())
    val library = MutableStateFlow(Library(emptyList(), emptyList(), emptyList(), emptyList()))
    val query = MutableStateFlow("")
    private val searchRetries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val tabRequests = Channel<Tab>(Channel.CONFLATED)
    private var pendingShare: SharedRequest? = null

    /** Tabs the shell should switch to, buffered until the UI collects them. */
    val requestedTabs: Flow<Tab> = tabRequests.receiveAsFlow()
    val search: StateFlow<SearchState> = searchStates(query, searchRetries, debounceMs = 300, search = { app.search.search(it) })
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Idle)
    val settings: StateFlow<SitaraSettings> = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, SitaraSettings())

    private val lyrics: StateFlow<Lyrics> = player.state.map { it.queue.current }.distinctUntilChanged()
        .mapLatest { t -> t?.let { app.sampleSource.lyrics(it) } ?: Lyrics.Empty }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Lyrics.Empty)

    val playerState: StateFlow<PlayerUiState> = combine(player.state, lyrics, player.sleepTimerState) { s, l, timer ->
        PlayerUiState(
            track = s.queue.current,
            isPlaying = s.isPlaying,
            positionMs = s.positionMs,
            durationMs = s.durationMs,
            upNext = s.queue.upNext,
            shuffled = s.queue.shuffled,
            repeatMode = s.queue.repeatMode,
            lyrics = l,
            sleepTimerLabel = when (timer) {
                SleepTimerState.Off -> null
                SleepTimerState.EndOfTrack -> "end of track"
                is SleepTimerState.Counting -> formatDuration(timer.remainingMs)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUiState())

    init {
        player.connect()
        viewModelScope.launch {
            home.value = app.sampleSource.home()
            library.value = app.sampleSource.library()
        }
        viewModelScope.launch { search.collect { playSharedSongIfResolved(it) } }
    }

    /** Shows a shared link or text in Search; a linked song starts playing as soon as it resolves. */
    fun open(request: SharedRequest) {
        pendingShare = request.takeIf { it.playVideoId != null }
        query.value = request.query
        tabRequests.trySend(Tab.SEARCH)
        // Sharing the link already on screen changes no state, so check the current one too.
        playSharedSongIfResolved(search.value)
    }

    private fun playSharedSongIfResolved(state: SearchState) {
        val track = pendingShare?.let { autoplayTrack(state, it) } ?: return
        pendingShare = null
        player.play(listOf(track), 0)
    }

    fun play(tracks: List<Track>, index: Int) = player.play(tracks, index)

    fun retrySearch() {
        searchRetries.tryEmit(Unit)
    }

    fun updateSettings(s: SitaraSettings) {
        viewModelScope.launch { app.settings.update { s } }
    }

    override fun togglePlay() = player.togglePlay()
    override fun next() = player.next()
    override fun previous() = player.previous()
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun toggleShuffle() = player.toggleShuffle()
    override fun cycleRepeat() = player.cycleRepeat()
    override fun playQueueIndex(upNextIndex: Int) = player.playUpNext(upNextIndex)

    /** Off -> 15 min -> 30 min -> end of track -> off. */
    override fun cycleSleepTimer() {
        when (val s = player.sleepTimerState.value) {
            SleepTimerState.Off -> player.startSleepTimer(15)
            is SleepTimerState.Counting -> if (s.remainingMs > 15 * 60_000) player.sleepAtEndOfTrack() else player.startSleepTimer(30)
            SleepTimerState.EndOfTrack -> player.cancelSleepTimer()
        }
    }

    /** Drops the UI's binding only; the service keeps playing, advancing and timing. */
    override fun onCleared() {
        player.release()
    }

    companion object {
        fun factory(app: SitaraApplication): ViewModelProvider.Factory = viewModelFactory { initializer { AppViewModel(app) } }
    }
}
