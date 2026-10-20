package app.sitara.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.sitara.music.core.common.formatDuration
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.Shelf
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.core.player.QueueState
import app.sitara.music.core.player.SleepTimerState
import app.sitara.music.feature.library.DetailState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.player.RadioStatus
import app.sitara.music.feature.search.SearchState
import app.sitara.music.feature.search.searchStates
import app.sitara.music.source.api.BrowseSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val app: SitaraApplication) :
    ViewModel(),
    PlayerActions,
    DetailPages {
    private val player = app.player

    val home = MutableStateFlow<List<Shelf>>(emptyList())
    val library = MutableStateFlow(Library(emptyList(), emptyList(), emptyList(), emptyList()))
    val query = MutableStateFlow("")
    val searchFilter = MutableStateFlow(SearchFilter.SONGS)
    private val searchRetries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val tabRequests = Channel<Tab>(Channel.CONFLATED)
    private var pendingShare: SharedRequest? = null

    /** Tabs the shell should switch to, buffered until the UI collects them. */
    val requestedTabs: Flow<Tab> = tabRequests.receiveAsFlow()
    val search: StateFlow<SearchState> = searchStates(query, searchRetries, debounceMs = 300, searchFilter) { q, filter -> app.youtube.search(q, filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Idle)
    val settings: StateFlow<SitaraSettings> = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, SitaraSettings())

    private val lyrics: StateFlow<Lyrics> = player.state.map { it.queue.current }.distinctUntilChanged()
        .mapLatest { t -> t?.let { app.sampleSource.lyrics(it) } ?: Lyrics.Empty }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Lyrics.Empty)

    private val radio = MutableStateFlow(RadioStatus.READY)

    // Opened pages, so going back or rotating does not load them again; a screen keeps its page's flow, so none is dropped.
    // shortcut: kept for the life of the view model; evict pages that are off the back stack if sessions browse hundreds.
    private val pages = mutableMapOf<DetailRoute, MutableStateFlow<DetailState>>()

    val playerState: StateFlow<PlayerUiState> = combine(player.state, lyrics, player.sleepTimerState, radio) { s, l, timer, radioStatus ->
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
            radio = radioStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUiState())

    init {
        player.connect()
        viewModelScope.launch {
            home.value = app.sampleSource.home()
            library.value = app.sampleSource.library()
        }
        viewModelScope.launch { search.collect { playSharedSongIfResolved(it) } }
        val queue = player.state.map { it.queue }
        viewModelScope.launch { queue.distinctUntilChangedBy { it.currentEntry?.uid }.collect { radio.value = RadioStatus.READY } }
        // Like a radio station, playback carries on past the end of the queue with related songs.
        viewModelScope.launch { queue.filter { it.runningOut }.distinctUntilChangedBy { it.currentEntry?.uid }.collectLatest(::extendWithRadio) }
    }

    override fun state(route: DetailRoute): StateFlow<DetailState> = pages.getOrPut(route) { MutableStateFlow<DetailState>(DetailState.Loading).also { load(route, it) } }

    override fun retry(route: DetailRoute) {
        pages[route]?.let { load(route, it) }
    }

    private fun load(route: DetailRoute, into: MutableStateFlow<DetailState>) {
        viewModelScope.launch {
            into.value = DetailState.Loading
            into.value = try {
                when (route) {
                    is DetailRoute.Artist -> DetailState.ArtistReady(browse { artist(route.id) })
                    is DetailRoute.Album -> DetailState.AlbumReady(browse { album(route.id) })
                    is DetailRoute.Playlist -> DetailState.PlaylistReady(browse { playlist(route.id) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DetailState.Failed
            }
        }
    }

    // Samples first: that lookup is local, and sample ids are never YouTube ids.
    private suspend fun <T : Any> browse(page: suspend BrowseSource.() -> T?): T =
        app.sampleSource.page() ?: app.youtube.page() ?: throw NoSuchElementException("No catalogue has this page")

    override fun startRadio() {
        if (radio.value == RadioStatus.LOADING) return
        viewModelScope.launch { extendWithRadio(player.state.value.queue) }
    }

    private suspend fun extendWithRadio(queue: QueueState) {
        val seed = queue.current ?: return
        radio.value = RadioStatus.LOADING
        val queued = queue.items.mapTo(HashSet()) { it.id }
        val picks = try {
            app.sampleSource.radio(seed, queued) ?: app.youtube.radio(seed, queued).orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        player.append(picks)
        radio.value = if (picks.isEmpty()) RadioStatus.UNAVAILABLE else RadioStatus.READY
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

    fun shuffle(tracks: List<Track>) = player.shuffle(tracks)

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
