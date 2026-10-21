package app.sitara.music

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.media3.datasource.cache.Cache
import app.sitara.music.core.data.SettingsRepository
import app.sitara.music.core.data.library.LibraryStore
import app.sitara.music.core.data.library.PlayHistoryRepository
import app.sitara.music.core.data.scrobble.ScrobbleAccounts
import app.sitara.music.core.designsystem.sitaraImageLoader
import app.sitara.music.core.model.AudioQuality
import app.sitara.music.core.model.CachedLyrics
import app.sitara.music.core.model.Listen
import app.sitara.music.core.model.SavedQueue
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.StreamFormat
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.streamQuality
import app.sitara.music.core.player.DownloadDependencies
import app.sitara.music.core.player.LibraryContent
import app.sitara.music.core.player.PlaybackDependencies
import app.sitara.music.core.player.PlaybackEngineRegistry
import app.sitara.music.core.player.PlayerController
import app.sitara.music.core.player.SongDownloads
import app.sitara.music.source.api.StreamResolver
import app.sitara.music.source.lyrics.LyricsCache
import app.sitara.music.source.lyrics.LyricsChoices
import app.sitara.music.source.lyrics.LyricsSources
import app.sitara.music.source.lyrics.lrclibLyrics
import app.sitara.music.source.scrobble.LastFmKeys
import app.sitara.music.source.youtube.YouTubeMusicSource
import app.sitara.music.source.youtube.installNewPipe
import app.sitara.music.source.youtube.lyricsProvider
import app.sitara.music.source.youtube.youTubeStreamResolver
import app.sitara.music.widget.SitaraWidget
import app.sitara.music.widget.SitaraWidgetReceiver
import app.sitara.music.widget.WidgetState
import app.sitara.music.widget.shownWidgetStates
import app.sitara.music.widget.widgetState
import coil3.SingletonImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Keeps [listen] unless the listener paused listening history; the setting is read at the moment of the play. */
internal suspend fun PlayHistoryRepository.recordUnlessPaused(listen: Listen, settings: Flow<SitaraSettings>) {
    if (!settings.first().pauseListeningHistory) record(listen)
}

/**
 * Dependencies are wired by hand (see ADR 0001). Home's chart, search, detail pages, radio and streams come from
 * YouTube, lyrics from LRCLIB and YouTube Music, covers through Coil; the library, the play queue, fetched lyrics and downloaded songs are stored on the device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SitaraApplication :
    Application(),
    PlaybackDependencies,
    DownloadDependencies,
    SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val youtube by lazy { YouTubeMusicSource() }
    val settings by lazy { SettingsRepository(this) }
    val library by lazy { LibraryStore(this) }
    val lyrics: LyricsSources by lazy {
        LyricsSources(
            listOf(lrclibLyrics(lyricsCache("lrclib")), youtube.lyricsProvider(lyricsCache("youtube-music"))),
            object : LyricsChoices {
                override suspend fun get(trackId: String) = library.lyrics.choice(trackId)

                override suspend fun put(trackId: String, providerId: String) = library.lyrics.choose(trackId, providerId)
            },
        )
    }

    private fun lyricsCache(provider: String) = object : LyricsCache {
        override suspend fun get(trackId: String) = library.lyrics.get(trackId, provider)

        override suspend fun put(trackId: String, lyrics: CachedLyrics) = library.lyrics.put(trackId, provider, lyrics)
    }

    // Read on the loader thread at each lookup, so a changed setting or network applies from the next song.
    private val currentSettings by lazy { settings.settings.stateIn(appScope, SharingStarted.Eagerly, SitaraSettings()) }
    private val recordingResolver by lazy {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        FormatRecordingResolver(youTubeStreamResolver { streamQuality(currentSettings.value, connectivity.onWifi()) })
    }
    override val streamResolver: StreamResolver get() = recordingResolver

    /** The codec and bitrate songs streamed in, by track id. */
    val streamFormats: StateFlow<Map<String, StreamFormat>> get() = recordingResolver.formats
    // A download is kept, so it takes the best stream whatever the network's streaming quality.
    override val songDownloads: SongDownloads by lazy { SongDownloads(this, youTubeStreamResolver { AudioQuality.HIGH }, appScope, settings.settings.map { it.downloadOnWifiOnly }.distinctUntilChanged()) }
    override val downloadCache: Cache get() = songDownloads.cache
    override val playbackSettings get() = settings.settings
    val player by lazy { PlayerController(this, appScope) }

    /** Last.fm (when the build has its API key) and ListenBrainz; a song is in the library when it is liked or in a playlist. */
    val scrobbling by lazy {
        val lastFmKeys = LastFmKeys(BuildConfig.LASTFM_API_KEY, BuildConfig.LASTFM_SHARED_SECRET).takeIf { it.apiKey.isNotBlank() && it.secret.isNotBlank() }
        Scrobbling(this, ScrobbleAccounts(this), library.scrobbles, lastFmKeys) { track ->
            track.id in library.likedSongs.ids.first() || library.playlists.inAnyPlaylist(track.id)
        }
    }

    /** What the home-screen widget shows, changing only when that is worth drawing (see shownWidgetStates). */
    val widgetStates: StateFlow<WidgetState> by lazy {
        val snapshots = PlaybackEngineRegistry.engine.flatMapLatest { it?.state ?: flowOf(null) }
        val liked = snapshots.map { it?.queue?.current?.id }.distinctUntilChanged()
            .flatMapLatest { id -> id?.let(::isLiked) ?: flowOf(false) }
            .catch { emit(false) }
        val canResume = snapshots.map { it?.queue?.current == null }.distinctUntilChanged()
            .flatMapLatest { idle -> flow { emit(idle && savedQueue() != null) } }
            .catch { emit(false) }
        val states = combine(snapshots, liked, canResume) { snapshot, heart, resumable -> widgetState(snapshot, heart, resumable) }
            .stateIn(appScope, SharingStarted.Eagerly, WidgetState.Idle(canResume = false))
        shownWidgetStates(states) { PlaybackEngineRegistry.engine.value?.refresh() }.stateIn(appScope, SharingStarted.Eagerly, states.value)
    }

    private val widgetUpdates by lazy {
        appScope.launch {
            widgetStates.collect { if (hasWidgets()) SitaraWidget().updateAll(this@SitaraApplication) }
        }
    }

    private fun hasWidgets() = AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, SitaraWidgetReceiver::class.java)).isNotEmpty()

    /** Redraws the home-screen widgets as the player changes; called at start when one is placed and whenever one is drawn. */
    fun followPlayerOnWidgets() {
        widgetUpdates.start()
    }

    override val libraryContent: LibraryContent by lazy {
        object : LibraryContent {
            override suspend fun recentlyPlayed() = library.history.recent(RECENT_IN_CAR).first()

            override suspend fun likedSongs() = library.likedSongs.tracks.first()

            override suspend fun downloadedSongs() = songDownloads.downloaded()

            override suspend fun playlists() = library.playlists.all.first()

            override suspend fun playlistSongs(id: Long) = library.playlists.playlist(id).first()?.tracks.orEmpty()

            override suspend fun savedAlbums() = library.saved.albums.first()

            override suspend fun savedArtists() = library.saved.artists.first()

            override suspend fun albumSongs(id: String) = youtube.album(id)?.tracks.orEmpty()

            override suspend fun artistSongs(id: String) = youtube.artist(id)?.topSongs.orEmpty()

            override suspend fun searchSongs(query: String) = youtube.search(query, SearchFilter.SONGS).tracks
        }
    }

    override fun newImageLoader(context: Context) = sitaraImageLoader(context)

    override fun onListened(listen: Listen) {
        writeLibrary { library.history.recordUnlessPaused(listen, settings.settings) }
        writeLibrary { scrobbling.onListened(listen) }
    }

    override fun onPlayStarted(track: Track) = writeLibrary { scrobbling.onPlayStarted(track) }

    override fun songGainDb(trackId: String) = library.songGain.trimDb(trackId)

    override fun isLiked(trackId: String) = library.likedSongs.ids.map { trackId in it }.distinctUntilChanged()

    override fun setLiked(track: Track, liked: Boolean) = writeLibrary { library.likedSongs.setLiked(track, liked) }

    override suspend fun savedQueue(): SavedQueue? = library.queue.load()

    override fun saveQueue(queue: SavedQueue) = writeLibrary { library.queue.save(queue) }

    /** Runs a library write past the screen that asked for it; a failed write (disk full, a playlist deleted meanwhile) is logged, never fatal. */
    fun writeLibrary(write: suspend () -> Unit) {
        appScope.launch {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Sitara", "Library write failed", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        installNewPipe()
        // With no widget on the home screen, nothing follows the player for one.
        if (hasWidgets()) followPlayerOnWidgets()
    }

    private companion object {
        const val RECENT_IN_CAR = 100
    }
}
