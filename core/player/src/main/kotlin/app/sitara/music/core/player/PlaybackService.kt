package app.sitara.music.core.player

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import app.sitara.music.core.model.EqualiserSettings
import app.sitara.music.core.model.Track
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Owns the ExoPlayer and the [PlaybackEngine], so the queue, auto-advance and the sleep timer
 * keep working after every screen is gone. Exposed through a media library session whose connections
 * are filtered by [ControllerPolicy]; a car browses the library through [LibraryBrowser] and plays from it.
 * ExoPlayer handles audio focus (ducking, pausing for a call and resuming after it) and pauses when
 * headphones or Bluetooth disconnect. The queue is saved as it
 * changes and restored, paused, when the service starts again after the process died.
 */
@OptIn(UnstableApi::class, ExperimentalCoroutinesApi::class)
class PlaybackService : MediaLibraryService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    internal var session: MediaLibrarySession? = null
        private set
    internal val callback = SessionCallback()
    private lateinit var libraryBrowser: LibraryBrowser
    private var engine: PlaybackEngine? = null
    private var currentLiked = false
    private lateinit var dependencies: PlaybackDependencies

    // The tail player has its own leveller and equaliser, the leveller started from the main one's level at each fade.
    internal val leveller = LoudnessLeveller()
    private val tailLeveller = LoudnessLeveller()
    internal val equaliser = Equaliser()
    internal val tailEqualiser = Equaliser()
    private var equaliserChoice = EqualiserSettings()
    private var systemEqualiserAvailable = false
    private val systemEqualiser = SystemEqualiserSession { open, sessionId ->
        val action = if (open) AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION else AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        sendBroadcast(
            Intent(action)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC),
        )
    }
    internal lateinit var crossfader: Crossfader
    internal lateinit var player: ExoPlayer
    internal lateinit var tail: ExoPlayer

    override fun onCreate() {
        super.onCreate()
        val dependencies = application as PlaybackDependencies
        this.dependencies = dependencies
        libraryBrowser = LibraryBrowser(dependencies.libraryContent)
        val streams = PrefetchingStreamResolver(dependencies.streamResolver, scope, ElapsedRealtimeTimeSource)
        lateinit var port: Media3PlayerPort
        val stream = ResolvingDataSource.Factory(DefaultDataSource.Factory(this), StreamUriResolver(streams) { port.track(it) })
        // A downloaded song plays from the download cache with no network; others stream without being written to it.
        val dataSource = CacheDataSource.Factory()
            .setCache(dependencies.downloadCache)
            .setUpstreamDataSourceFactory(stream)
            .setCacheWriteDataSinkFactory(null)
        val mediaSources = DefaultMediaSourceFactory(dataSource)
        val music = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSources)
            .setRenderersFactory(SoundRenderersFactory(this, leveller, equaliser))
            .setAudioAttributes(
                music,
                // handleAudioFocus
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        // Plays only the end of a song during a crossfade; the main player holds audio focus for both.
        tail = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSources)
            .setRenderersFactory(SoundRenderersFactory(this, tailLeveller, tailEqualiser))
            .setAudioAttributes(
                music,
                // handleAudioFocus
                false,
            )
            .build()
        systemEqualiserAvailable = hasSystemEqualiser()
        // ExoPlayer creates its audio session in the background and reports it here.
        player.addListener(EqualiserListener())
        port = Media3PlayerPort(player)
        crossfader = Crossfader(player, tail, scope, trackOf = { item -> item.localConfiguration?.uri?.lastPathSegment?.let(port::track) }) {
            tailLeveller.startFrom(leveller)
            // The tail plays the end of the song the main player is leaving, at that song's trim.
            tailEqualiser.trimDb = equaliser.trimDb
        }
        val engine = PlaybackEngine(port, scope, ElapsedRealtimeTimeSource, dependencies::onListened) { track ->
            // A downloaded song plays from the cache and needs no lookup.
            if (!dependencies.downloadCache.holdsWhole(StreamUriResolver.uriFor(track.id).toString())) streams.prepare(track)
        }
        player.addListener(EngineListener(engine))
        this.engine = engine
        session = MediaLibrarySession.Builder(this, player, callback).build()
        PlaybackEngineRegistry.attach(engine)
        scope.launch {
            restoreSavedQueue(engine)
            keepSaving(engine, dependencies::saveQueue)
        }
        scope.launch {
            engine.state.map { it.queue.current?.id }.distinctUntilChanged()
                .flatMapLatest { id -> id?.let(dependencies::songGainDb) ?: flowOf(0) }
                // A store that cannot be read plays the song untrimmed rather than stopping the music.
                .catch { emit(0) }
                .collect { equaliser.trimDb = it }
        }
        scope.launch {
            val liked = engine.state.map { it.queue.current?.id }.distinctUntilChanged()
                .flatMapLatest { id -> id?.let(dependencies::isLiked) ?: flowOf(false) }
                // A store that cannot be read shows an empty heart rather than stopping the music.
                .catch { emit(false) }
            val modes = engine.state.map { it.queue.shuffled to it.queue.repeatMode }.distinctUntilChanged()
            combine(liked, modes) { isLiked, (shuffled, repeat) -> Triple(isLiked, shuffled, repeat) }.distinctUntilChanged().collect { (isLiked, shuffled, repeat) ->
                currentLiked = isLiked
                session?.setMediaButtonPreferences(SessionActions.buttons(isLiked, shuffled, repeat))
            }
        }
        scope.launch {
            dependencies.playbackSettings.collect { settings ->
                listOf(leveller, tailLeveller).forEach {
                    it.enabled = settings.normalisation
                    it.targetLufs = settings.targetLufs
                }
                equaliserChoice = settings.equaliser
                routeEqualiser()
                crossfader.windowMs = settings.crossfadeSeconds * 1_000L
                // Turns the silence-skipping processor in each player's audio chain on or off.
                player.skipSilenceEnabled = settings.skipSilence
                tail.skipSilenceEnabled = settings.skipSilence
            }
        }
    }

    /** Gives the sound to the built-in equaliser (each glides to new bands while playing) or to the device's, never both. */
    private fun routeEqualiser() {
        val builtIn = equaliserChoice.builtIn(systemEqualiserAvailable)
        equaliser.settings = builtIn
        tailEqualiser.settings = builtIn
        val playing = player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING
        systemEqualiser.update(equaliserChoice.systemActive(systemEqualiserAvailable) && playing, player.audioSessionId)
    }

    /** Runs a notification or lock screen button; false for anything else, or a like with nothing playing. */
    internal fun onSessionAction(action: String): Boolean {
        val engine = engine ?: return false
        when (action) {
            SessionActions.LIKE -> dependencies.setLiked(engine.state.value.queue.current ?: return false, !currentLiked)
            SessionActions.SHUFFLE -> engine.toggleShuffle()
            SessionActions.REPEAT -> engine.cycleRepeat()
            else -> return false
        }
        return true
    }

    /** Puts the last queue back unless something is already queued; a store that cannot be read only costs the old queue. */
    private suspend fun restoreSavedQueue(engine: PlaybackEngine) {
        val saved = try {
            dependencies.savedQueue()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        saved?.let(engine::restore)
    }

    private fun Cache.holdsWhole(key: String): Boolean {
        val length = ContentMetadata.getContentLength(getContentMetadata(key))
        return length > 0 && isCached(key, 0, length)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        engine?.checkpointListening()
        engine?.savedQueue()?.let(dependencies::saveQueue)
        engine?.let(PlaybackEngineRegistry::detach)
        engine = null
        scope.cancel()
        systemEqualiser.close()
        PlaybackEngineRegistry.audioSessionId = SystemEqualiserSession.NO_SESSION
        crossfader.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** ExoPlayer's renderers with [leveller] and [equaliser] in the audio pipeline. */
    private class SoundRenderersFactory(context: Context, private val leveller: LoudnessLeveller, private val equaliser: Equaliser) : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .setAudioProcessors(arrayOf(SoundAudioProcessor(leveller, equaliser)))
                .build()
    }

    /** The device's equaliser follows playback starting and stopping, and a new audio session. */
    private inner class EqualiserListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = routeEqualiser()

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            // One session for both players, so the device's equaliser also shapes the end of a song fading out.
            tail.audioSessionId = audioSessionId
            PlaybackEngineRegistry.audioSessionId = audioSessionId
            routeEqualiser()
        }
    }

    /** Forwards what the player does on its own to the engine. */
    private class EngineListener(private val engine: PlaybackEngine) : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.toLongOrNull()?.let(engine::onItemTransition)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            engine.onPlaybackStateChanged(ended = playbackState == Player.STATE_ENDED)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = engine.refresh()

        override fun onPlayerError(error: PlaybackException) = engine.onPlayerError()

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) engine.onPausedAtEndOfItem()
        }
    }

    /** Runs a library request on the main thread; a catalogue that cannot be reached becomes an error the car shows. */
    private fun <T> library(request: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> {
        val result = SettableFuture.create<LibraryResult<T>>()
        scope.launch {
            result.set(
                try {
                    request()
                } catch (e: CancellationException) {
                    result.cancel(false)
                    throw e
                } catch (e: Exception) {
                    LibraryResult.ofError(SessionError(SessionError.ERROR_IO, CATALOGUE_UNREACHABLE))
                },
            )
        }
        return result
    }

    /** What a car's pick plays: a song's list from that song, or by voice the songs a search finds. */
    private suspend fun queueFor(item: MediaItem): Pair<List<Track>, Int>? {
        val query = item.requestMetadata.searchQuery
        if (item.mediaId.isEmpty() && query != null) return libraryBrowser.voiceQueue(query).takeIf { it.isNotEmpty() }?.let { it to 0 }
        return (BrowseId.parse(item.mediaId) as? BrowseId.Song)?.let { libraryBrowser.queueFor(it) }
    }

    internal inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val identity = ControllerIdentity(
                packageName = controller.packageName,
                uid = controller.uid,
                isTrusted = controller.isTrusted,
                isMediaNotification = session.isMediaNotificationController(controller),
                isCar = session.isAutoCompanionController(controller) || session.isAutomotiveController(controller),
            )
            return when (ControllerPolicy.access(identity, Process.myUid())) {
                ControllerAccess.FULL -> MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
                ControllerAccess.PLAYBACK_ONLY -> MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailablePlayerCommands(
                        Player.Commands.Builder().addAll(*ControllerPolicy.PLAYBACK_COMMANDS.toIntArray()).build(),
                    )
                    .setAvailableSessionCommands(SessionActions.commands)
                    .build()
                ControllerAccess.BROWSE -> MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailablePlayerCommands(
                        Player.Commands.Builder().addAll(*ControllerPolicy.BROWSE_COMMANDS.toIntArray()).build(),
                    )
                    .setAvailableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().addSessionCommands(SessionActions.commands.commands).build(),
                    )
                    .build()
                ControllerAccess.REJECT -> MediaSession.ConnectionResult.reject()
            }
        }

        /**
         * A headset or Bluetooth play button, or the system's resume card, asked to play with nothing loaded (the
         * process had died): load the saved queue and resume it where it stopped.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaItemsWithStartPosition>()
            scope.launch {
                engine?.let { restoreSavedQueue(it) }
                val player = mediaSession.player
                if (player.mediaItemCount == 0) {
                    result.setException(IllegalStateException("No saved queue to resume"))
                } else {
                    result.set(MediaItemsWithStartPosition(List(player.mediaItemCount, player::getMediaItemAt), player.currentMediaItemIndex, player.currentPosition))
                }
            }
            return result
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> =
            Futures.immediateFuture(SessionResult(if (onSessionAction(customCommand.customAction)) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_NOT_SUPPORTED))

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(libraryBrowser.root.toMediaItem(), params))

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = library<MediaItem> {
            val item = BrowseId.parse(mediaId)?.let { libraryBrowser.item(it) }
            if (item == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) else LibraryResult.ofItem(item.toMediaItem(), null)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = library<ImmutableList<MediaItem>> {
            val children = BrowseId.parse(parentId)?.let { libraryBrowser.children(it) }
            if (children == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE) else LibraryResult.ofItemList(children.page(page, pageSize).map { it.toMediaItem() }, params)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = library<Void> {
            session.notifySearchResultChanged(browser, query, libraryBrowser.search(query).size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = library<ImmutableList<MediaItem>> {
            LibraryResult.ofItemList(libraryBrowser.search(query).page(page, pageSize).map { it.toMediaItem() }, params)
        }

        /**
         * A car picked a song (or asked by voice): the engine queues it, as it does every queue, and the session is handed
         * the engine's own playlist back, as for playback resumption.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaItemsWithStartPosition>()
            scope.launch {
                val picked = mediaItems.firstOrNull()
                val queue = try {
                    picked?.let { queueFor(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                val engine = engine
                if (queue == null || engine == null) {
                    result.setException(IllegalArgumentException("Nothing to play for ${picked?.mediaId}"))
                    return@launch
                }
                engine.play(queue.first, queue.second)
                val player = mediaSession.player
                result.set(MediaItemsWithStartPosition(List(player.mediaItemCount, player::getMediaItemAt), player.currentMediaItemIndex, C.TIME_UNSET))
            }
            return result
        }

        /** The app queues through the engine, never through the session, so no controller may add items. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFailedFuture(UnsupportedOperationException("Sitara does not accept external media items"))
    }

    private companion object {
        const val CATALOGUE_UNREACHABLE = "Can’t reach YouTube Music right now. Downloads play with no connection."
    }
}
