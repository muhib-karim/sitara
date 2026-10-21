package app.sitara.music

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.EqualiserSettings
import app.sitara.music.core.model.LibraryLayout
import app.sitara.music.core.model.LibrarySort
import app.sitara.music.core.model.ListeningStats
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.SongDownload
import app.sitara.music.core.model.StatsRange
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.toDownload
import app.sitara.music.core.player.QueueState
import app.sitara.music.core.player.SleepTimerState
import app.sitara.music.core.player.hasSystemEqualiser
import app.sitara.music.core.player.systemEqualiserPanel
import app.sitara.music.feature.home.FeedState
import app.sitara.music.feature.home.HomeActions
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.home.OnlineHomeLoader
import app.sitara.music.feature.home.homeFeed
import app.sitara.music.feature.library.DetailState
import app.sitara.music.feature.library.LibraryActions
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.library.SavedIds
import app.sitara.music.feature.library.SavedLibrary
import app.sitara.music.feature.library.StatsState
import app.sitara.music.feature.player.LyricsState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.player.RadioStatus
import app.sitara.music.feature.player.SleepTimerStatus
import app.sitara.music.feature.player.TranslationState
import app.sitara.music.feature.search.MoodsState
import app.sitara.music.feature.search.SearchActions
import app.sitara.music.feature.search.SearchLanding
import app.sitara.music.feature.search.SearchState
import app.sitara.music.feature.search.searchStates
import app.sitara.music.feature.search.searchSuggestions
import app.sitara.music.feature.search.withRecentSearch
import app.sitara.music.source.api.BrowseSource
import app.sitara.music.source.lyrics.TranslationException
import app.sitara.music.source.lyrics.TranslationProblem
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val app: SitaraApplication) :
    ViewModel(),
    PlayerActions,
    LibraryActions,
    SearchActions,
    DetailPages {
    private val player = app.player

    private val onlineHome = OnlineHomeLoader(viewModelScope, { app.youtube.home(it) }, app.youtube::moreHome, app.youtube::trending)

    /** Mood chips, more shelves and Try again on Home. */
    val homeActions: HomeActions = onlineHome
    val settings: StateFlow<SitaraSettings> = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, SitaraSettings())
    val query = MutableStateFlow("")

    /** The filter used last, kept in the settings so Search opens on it again. */
    val searchFilter: StateFlow<SearchFilter> = settings.map { it.searchFilter }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchFilter.SONGS)
    private val searchRetries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val tabRequests = Channel<Tab>(Channel.CONFLATED)
    private val playerRequests = Channel<Unit>(Channel.CONFLATED)
    private var pendingShare: SharedRequest? = null

    /** Tabs the shell should switch to, buffered until the UI collects them. */
    val requestedTabs: Flow<Tab> = tabRequests.receiveAsFlow()

    /** Requests to open Now Playing (from the home-screen widget), buffered until the UI collects them. */
    val requestedPlayer: Flow<Unit> = playerRequests.receiveAsFlow()

    /** Import playlist: runs here, so an import goes on while the listener browses. */
    val imports = PlaylistImports(viewModelScope, app.library.importer(YouTubeImportCatalogue(app.youtube))) { uri ->
        readPlaylistFile(app.contentResolver, Uri.parse(uri))
    }
    private val importRequests = Channel<Unit>(Channel.CONFLATED)

    /** Settings → Scrobbling; a sign-in in progress outlives the page. */
    val scrobbling = ScrobblingSettings(viewModelScope, app.scrobbling) { url ->
        try {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast("No browser to open Last.fm in")
        }
    }

    /** Requests to open Import playlist (a shared playlist link), buffered until the UI collects them. */
    val requestedImport: Flow<Unit> = importRequests.receiveAsFlow()
    val search: StateFlow<SearchState> = searchStates(query, searchRetries, debounceMs = 300, searchFilter) { q, filter -> app.youtube.search(q, filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Idle)

    private val moodRetries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Asked for once, when the shell first shows; again only on Try again.
    private val moods: Flow<MoodsState> = moodRetries.onStart { emit(Unit) }.transformLatest {
        emit(MoodsState.Loading)
        emit(
            try {
                MoodsState.Ready(app.youtube.moods())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MoodsState.Failed
            },
        )
    }

    /** Recent searches and Moods & genres for an empty Search, suggestions while typing. */
    val searchLanding: StateFlow<SearchLanding> = combine(
        settings.map { it.recentSearches }.distinctUntilChanged(),
        searchSuggestions(query, debounceMs = 200, recent = { settings.value.recentSearches }) { app.youtube.suggestions(it) },
        moods,
        ::SearchLanding,
    ).stateIn(viewModelScope, SharingStarted.Lazily, SearchLanding())

    private val radio = MutableStateFlow(RadioStatus.READY)

    private val lyricsRetries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val lyricsPicks = MutableSharedFlow<String>(extraBufferCapacity = 1)

    // Looked up when a song starts; a retry looks up the same song again, a pick shows another source's lyrics at once and is kept for the song.
    private val lyrics: StateFlow<LyricsState> = player.state.map { it.queue.current }.distinctUntilChangedBy { it?.id }
        .flatMapLatest<Track?, LyricsState> { track ->
            if (track == null) {
                flowOf(LyricsState.NotFound)
            } else {
                lyricsRetries.onStart { emit(Unit) }.transformLatest {
                    emit(LyricsState.Loading)
                    val found = lookUpLyrics(track)
                    emit(found)
                    if (found is LyricsState.Found) {
                        lyricsPicks.collect { id ->
                            val match = found.matches.firstOrNull { it.providerId == id } ?: return@collect
                            emit(found.copy(lyrics = match.lyrics))
                            app.lyrics.choose(track, id)
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LyricsState.NotFound)

    val downloads: StateFlow<List<SongDownload>> = app.songDownloads.songs

    val savedIds: StateFlow<SavedIds> = combine(
        app.library.likedSongs.ids, app.library.saved.albumIds, app.library.saved.artistIds, downloads.map { songs -> songs.associate { it.track.id to it.state } },
        ::SavedIds,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, SavedIds())

    val library: StateFlow<LibraryState> = combine(
        combine(
            app.library.likedSongs.tracks, app.library.history.recent(RECENT_SHELF), app.library.playlists.all, app.library.saved.albums, app.library.saved.artists,
            ::SavedLibrary,
        ),
        app.library.history.counts,
    ) { saved, counts -> LibraryState.Ready(saved.copy(counts = counts)) }
        .catch<LibraryState> { emit(LibraryState.Failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState.Loading)

    val home: StateFlow<HomeFeed> = combine(onlineHome.state, library) { online, saved ->
        if (saved is LibraryState.Ready) {
            saved.library.run { homeFeed(online, recent, liked, playlists, albums) }
        } else {
            HomeFeed(online)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeFeed())

    val playlists: StateFlow<List<PlaylistSummary>> = app.library.playlists.all.catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Opened pages, so going back or rotating does not load them again; a screen keeps its page's flow, so none is dropped.
    // shortcut: kept for the life of the view model; evict pages that are off the back stack if sessions browse hundreds.
    private val pages = mutableMapOf<DetailRoute, MutableStateFlow<DetailState>>()
    private val moodPages = mutableMapOf<DetailRoute.Mood, MutableStateFlow<FeedState>>()

    // The listener's own pages follow the store live; they hold no network state to retry.
    private val ownPages = mutableMapOf<DetailRoute, StateFlow<DetailState>>()

    /** Whether the phone has an equaliser of its own to hand the sound to; looked up once per screen session. */
    private val systemEqualiser: Boolean = app.hasSystemEqualiser()

    private val trimDb: Flow<Int> = player.state.map { it.queue.current?.id }.distinctUntilChanged()
        .flatMapLatest { id -> id?.let(app.library.songGain::trimDb) ?: flowOf(0) }
        .catch { emit(0) }

    private val nowPlaying = combine(player.state, lyrics, player.sleepTimerState, radio, savedIds) { s, l, timer, radioStatus, saved ->
        PlayerUiState(
            track = s.queue.current,
            isPlaying = s.isPlaying,
            positionMs = s.positionMs,
            durationMs = s.durationMs,
            upNext = s.queue.upNext,
            upNextFromRadio = s.queue.upNextFromRadio,
            shuffled = s.queue.shuffled,
            repeatMode = s.queue.repeatMode,
            lyrics = l,
            lyricsSources = app.lyrics.names,
            sleepTimer = when (timer) {
                SleepTimerState.Off -> SleepTimerStatus.Off
                SleepTimerState.EndOfTrack -> SleepTimerStatus.EndOfTrack
                is SleepTimerState.Counting -> SleepTimerStatus.Counting(timer.remainingMs)
            },
            radio = radioStatus,
            liked = s.queue.current?.id in saved.tracks,
            tuning = s.tuning,
            failed = s.failed,
        )
    }

    private val translating = MutableStateFlow(false)

    // The app's language until the listener picks another; kept for this session.
    private val translationLanguage = MutableStateFlow(Locale.getDefault().toLanguageTag().substringBefore('-').ifEmpty { "en" })
    private val translationRetries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // The shown lyrics translated while Translate is on: anew for another song, source or language, or when tried again.
    private val translation: StateFlow<TranslationState> = combine(lyrics, translating, translationLanguage, ::Triple)
        .flatMapLatest<Triple<LyricsState, Boolean, String>, TranslationState> { (state, on, language) ->
            if (!on || state !is LyricsState.Found) {
                flowOf(TranslationState.Off)
            } else {
                translationRetries.onStart { emit(Unit) }.transformLatest {
                    emit(TranslationState.Translating(language))
                    emit(translate(state, language))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TranslationState.Off)

    val playerState: StateFlow<PlayerUiState> = combine(nowPlaying, settings, trimDb, app.streamFormats, translation) { ui, s, trim, formats, translated ->
        ui.copy(equaliser = s.equaliser, systemEqualiser = systemEqualiser, trimDb = trim, stream = ui.track?.let { formats[it.id] }, translation = translated)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlayerUiState())

    init {
        player.connect()
        viewModelScope.launch { search.collect { playSharedSongIfResolved(it) } }
        val queue = player.state.map { it.queue }
        viewModelScope.launch { queue.distinctUntilChangedBy { it.currentEntry?.uid }.collect { radio.value = RadioStatus.READY } }
        // Like a radio station, playback carries on past the end of the queue with related songs.
        viewModelScope.launch { queue.filter { it.runningOut }.distinctUntilChangedBy { it.currentEntry?.uid }.collectLatest(::extendWithRadio) }
    }

    override fun state(route: DetailRoute): StateFlow<DetailState> = when (route) {
        DetailRoute.LikedSongs -> ownPages.getOrPut(route) { app.library.likedSongs.tracks.map { DetailState.LikedReady(it) }.ownPage() }
        is DetailRoute.UserPlaylist -> ownPages.getOrPut(route) {
            app.library.playlists.playlist(route.id).map { it?.let(DetailState::UserPlaylistReady) ?: DetailState.Gone }.ownPage()
        }
        else -> pages.getOrPut(route) { MutableStateFlow<DetailState>(DetailState.Loading).also { load(route, it) } }
    }

    private fun Flow<DetailState>.ownPage(): StateFlow<DetailState> =
        catch { emit(DetailState.Failed) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    // The range starts where it does when Stats opens, in the phone's zone and with its locale's first day of the week.
    override fun stats(range: StatsRange): StateFlow<StatsState> {
        val zone = ZoneId.systemDefault()
        val since = range.startMs(System.currentTimeMillis(), zone, WeekFields.of(Locale.getDefault()).firstDayOfWeek)
        return app.library.history.stats(since, zone).map<ListeningStats, StatsState> { StatsState.Ready(it) }.catch { emit(StatsState.Failed) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState.Loading)
    }

    override fun moodPage(route: DetailRoute.Mood): StateFlow<FeedState> =
        moodPages.getOrPut(route) { MutableStateFlow<FeedState>(FeedState.Loading).also { loadMood(route, it) } }

    override fun retry(route: DetailRoute) {
        if (route is DetailRoute.Mood) moodPages[route]?.let { loadMood(route, it) } else pages[route]?.let { load(route, it) }
    }

    private fun loadMood(route: DetailRoute.Mood, into: MutableStateFlow<FeedState>) {
        viewModelScope.launch {
            into.value = FeedState.Loading
            into.value = try {
                // shortcut: the first page only; page on scroll if mood pages ever come with continuations (none seen in October 2026).
                FeedState.Ready(app.youtube.moodPage(route.params).shelves, continuation = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FeedState.Failed
            }
        }
    }

    private fun load(route: DetailRoute, into: MutableStateFlow<DetailState>) {
        viewModelScope.launch {
            into.value = DetailState.Loading
            into.value = try {
                when (route) {
                    is DetailRoute.Artist -> DetailState.ArtistReady(browse { artist(route.id) })
                    is DetailRoute.Album -> DetailState.AlbumReady(browse { album(route.id) })
                    is DetailRoute.Playlist -> DetailState.PlaylistReady(browse { playlist(route.id) })
                    DetailRoute.LikedSongs, is DetailRoute.UserPlaylist -> error("Own pages are not loaded from a catalogue")
                    is DetailRoute.Mood -> error("A mood page is a feed: see moodPage")
                    DetailRoute.Stats -> error("Stats have their own state: see stats")
                    DetailRoute.Import -> error("Import has its own state: see imports")
                    DetailRoute.Scrobbling -> error("Scrobbling has its own state: see scrobbling")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DetailState.Failed
            }
        }
    }

    private suspend fun <T : Any> browse(page: suspend BrowseSource.() -> T?): T = app.youtube.page() ?: throw NoSuchElementException("No catalogue has this page")

    private suspend fun lookUpLyrics(track: Track): LyricsState = try {
        app.lyrics.lookUp(track).takeIf { it.isNotEmpty() }?.let { LyricsState.Found(it.first().lyrics, it) } ?: LyricsState.NotFound
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LyricsState.Failed
    }

    override fun retryLyrics() {
        val track = player.state.value.queue.current ?: return
        if (lyrics.value != LyricsState.Failed && lyrics.value != LyricsState.NotFound) return
        // A source that found nothing is not asked again for a week unless its answer is forgotten first.
        viewModelScope.launch {
            app.library.lyrics.forgetMisses(track.id)
            lyricsRetries.emit(Unit)
        }
    }

    override fun chooseLyrics(providerId: String) {
        lyricsPicks.tryEmit(providerId)
    }

    private suspend fun translate(found: LyricsState.Found, language: String): TranslationState = try {
        val track = player.state.value.queue.current ?: return TranslationState.Off
        val providerId = found.matches.firstOrNull { it.lyrics == found.lyrics }?.providerId ?: found.lyrics.source.orEmpty()
        TranslationState.Translated(app.translator.translate(track.id, providerId, found.lyrics.lines.map { it.text }, language))
    } catch (e: CancellationException) {
        throw e
    } catch (e: TranslationException) {
        TranslationState.Failed(e.problem, language)
    } catch (e: Exception) {
        TranslationState.Failed(TranslationProblem.SERVICE, language)
    }

    override fun toggleTranslation() {
        translating.value = !translating.value
    }

    override fun retryTranslation() {
        translationRetries.tryEmit(Unit)
    }

    override fun setTranslationLanguage(language: String) {
        translationLanguage.value = language
    }

    override fun startRadio() {
        if (radio.value == RadioStatus.LOADING) return
        viewModelScope.launch { extendWithRadio(player.state.value.queue) }
    }

    private suspend fun extendWithRadio(queue: QueueState) {
        val seed = queue.current ?: return
        radio.value = RadioStatus.LOADING
        val queued = queue.items.mapTo(HashSet()) { it.id }
        val picks = try {
            app.youtube.radio(seed, queued).orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        player.appendRadio(picks)
        radio.value = if (picks.isEmpty()) RadioStatus.UNAVAILABLE else RadioStatus.READY
    }

    fun openPlayer() {
        playerRequests.trySend(Unit)
    }

    /** Opens Import playlist with a shared playlist [link] in its field. */
    fun importPlaylist(link: String) {
        imports.prefill(link)
        importRequests.trySend(Unit)
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

    fun setSearchFilter(filter: SearchFilter) {
        viewModelScope.launch { app.settings.update { it.copy(searchFilter = filter) } }
    }

    override fun saveSearch(query: String) {
        viewModelScope.launch { app.settings.update { it.copy(recentSearches = it.recentSearches.withRecentSearch(query)) } }
    }

    override fun removeRecentSearch(query: String) {
        viewModelScope.launch { app.settings.update { it.copy(recentSearches = it.recentSearches - query) } }
    }

    override fun clearRecentSearches() {
        viewModelScope.launch { app.settings.update { it.copy(recentSearches = emptyList()) } }
    }

    override fun retryMoods() {
        moodRetries.tryEmit(Unit)
    }

    fun updateSettings(s: SitaraSettings) {
        viewModelScope.launch { app.settings.update { s } }
    }

    override fun setEqualiser(equaliser: EqualiserSettings) {
        viewModelScope.launch { app.settings.update { it.copy(equaliser = equaliser) } }
    }

    override fun openSystemEqualiser() {
        try {
            app.startActivity(systemEqualiserPanel(app, player.audioSessionId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast("Your phone's equaliser could not be opened")
        }
    }

    override fun setTrimDb(db: Int) {
        val id = player.state.value.queue.current?.id ?: return
        app.writeLibrary { app.library.songGain.setTrimDb(id, db) }
    }

    override fun togglePlay() = player.togglePlay()
    override fun retryPlayback() = player.retry()
    override fun next() = player.next()
    override fun previous() = player.previous()
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun toggleShuffle() = player.toggleShuffle()
    override fun cycleRepeat() = player.cycleRepeat()
    override fun playQueueIndex(upNextIndex: Int) = player.playUpNext(upNextIndex)
    override fun setSpeed(speed: Float) = player.setSpeed(speed)
    override fun setPitch(semitones: Int) = player.setPitch(semitones)
    override fun resetTuning() = player.resetTuning()
    override fun moveUpNext(from: Int, to: Int) = player.moveUpNext(from, to)
    override fun removeUpNext(index: Int) = player.removeUpNext(index)
    override fun undoRemoveUpNext() = player.undoRemoveUpNext()

    // With nothing queued both start playing at once, which needs no message.
    override fun playNext(track: Track) {
        if (player.state.value.queue.current != null) toast("Playing next")
        player.playNext(listOf(track))
    }

    override fun addToQueue(track: Track) {
        if (player.state.value.queue.current != null) toast("Added to queue")
        player.addToQueue(listOf(track))
    }

    override fun toggleLike() {
        val track = player.state.value.queue.current ?: return
        setLiked(track, track.id !in savedIds.value.tracks)
    }

    override fun setLiked(track: Track, liked: Boolean) = app.writeLibrary { app.library.likedSongs.setLiked(track, liked) }
    override fun setSaved(album: Album, saved: Boolean) = app.writeLibrary { app.library.saved.setSaved(album, saved) }
    override fun setSaved(artist: Artist, saved: Boolean) = app.writeLibrary { app.library.saved.setSaved(artist, saved) }
    override fun renamePlaylist(id: Long, name: String) = app.writeLibrary { app.library.playlists.rename(id, name) }
    override fun deletePlaylist(id: Long) = app.writeLibrary { app.library.playlists.delete(id) }
    override fun movePlaylistEntry(id: Long, from: Int, to: Int) = app.writeLibrary { app.library.playlists.move(id, from, to) }
    override fun removePlaylistEntry(id: Long, trackId: String) = app.writeLibrary { app.library.playlists.remove(id, trackId) }

    override fun download(tracks: List<Track>) {
        // Straight from the download manager: the saved ids wait for the library store before they say anything.
        val wanted = tracks.toDownload(downloads.value.associate { it.track.id to it.state })
        if (wanted.isEmpty()) return
        toast(if (wanted.size == 1) "Downloading" else "Downloading ${wanted.size} songs")
        app.songDownloads.download(wanted)
    }

    override fun removeDownload(trackId: String) = app.songDownloads.remove(trackId)

    override fun setLibraryLayout(layout: LibraryLayout) {
        viewModelScope.launch { app.settings.update { it.copy(libraryLayout = layout) } }
    }

    override fun setLibrarySort(sort: LibrarySort) {
        viewModelScope.launch { app.settings.update { it.copy(librarySort = sort) } }
    }

    fun removeAllDownloads() = app.songDownloads.removeAll()

    fun clearListeningHistory() = app.writeLibrary { app.library.history.clear() }

    override fun createPlaylist(name: String, tracks: List<Track>) = app.writeLibrary {
        val id = app.library.playlists.create(name)
        // A song queued twice goes in once: a playlist holds each song once.
        val added = tracks.count { app.library.playlists.add(id, it) }
        if (added > 0) toast(if (tracks.size == 1) "Added to $name" else "Saved $added songs to $name")
    }

    override fun addToPlaylist(playlist: PlaylistSummary, track: Track) = app.writeLibrary {
        toast(if (app.library.playlists.add(playlist.id, track)) "Added to ${playlist.name}" else "Already in ${playlist.name}")
    }

    private fun toast(text: String) = Toast.makeText(app, text, Toast.LENGTH_SHORT).show()

    override fun startSleepTimer(minutes: Int) = player.startSleepTimer(minutes)
    override fun extendSleepTimer(minutes: Int) = player.extendSleepTimer(minutes)
    override fun sleepAtEndOfTrack() = player.sleepAtEndOfTrack()
    override fun cancelSleepTimer() = player.cancelSleepTimer()

    /** Drops the UI's binding only; the service keeps playing, advancing and timing. */
    override fun onCleared() {
        player.release()
    }

    companion object {
        private const val RECENT_SHELF = 20

        fun factory(app: SitaraApplication): ViewModelProvider.Factory = viewModelFactory { initializer { AppViewModel(app) } }
    }
}
