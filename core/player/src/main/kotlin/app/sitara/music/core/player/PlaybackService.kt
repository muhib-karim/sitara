package app.sitara.music.core.player

import android.content.Intent
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the ExoPlayer and the [PlaybackEngine], so the queue, auto-advance and the sleep timer
 * keep working after every screen is gone. Exposed through a MediaSession whose connections
 * are filtered by [ControllerPolicy]. ExoPlayer handles audio focus (ducking, pausing for a call and
 * resuming after it) and pauses when headphones or Bluetooth disconnect. The queue is saved as it
 * changes and restored, paused, when the service starts again after the process died.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var session: MediaSession? = null
    private var engine: PlaybackEngine? = null
    private lateinit var dependencies: PlaybackDependencies

    override fun onCreate() {
        super.onCreate()
        val dependencies = application as PlaybackDependencies
        this.dependencies = dependencies
        val resolver = dependencies.streamResolver
        lateinit var port: Media3PlayerPort
        val stream = ResolvingDataSource.Factory(DefaultDataSource.Factory(this), StreamUriResolver(resolver) { port.track(it) })
        // A downloaded song plays from the download cache with no network; others stream without being written to it.
        val dataSource = CacheDataSource.Factory()
            .setCache(dependencies.downloadCache)
            .setUpstreamDataSourceFactory(stream)
            .setCacheWriteDataSinkFactory(null)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // handleAudioFocus
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        port = Media3PlayerPort(player)
        val engine = PlaybackEngine(port, scope, ElapsedRealtimeTimeSource, dependencies::onTrackStarted)
        player.addListener(EngineListener(engine))
        this.engine = engine
        session = MediaSession.Builder(this, player).setCallback(ConnectionPolicyCallback()).build()
        PlaybackEngineRegistry.attach(engine)
        scope.launch {
            restoreSavedQueue(engine)
            keepSaving(engine, dependencies::saveQueue)
        }
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        engine?.savedQueue()?.let(dependencies::saveQueue)
        engine?.let(PlaybackEngineRegistry::detach)
        engine = null
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
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

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) engine.onPausedAtEndOfItem()
        }
    }

    private inner class ConnectionPolicyCallback : MediaSession.Callback {
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
                    .setAvailableSessionCommands(SessionCommands.EMPTY)
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

        /** The app queues through the engine, never through the session, so no controller may add items. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFailedFuture(UnsupportedOperationException("Sitara does not accept external media items"))
    }
}
