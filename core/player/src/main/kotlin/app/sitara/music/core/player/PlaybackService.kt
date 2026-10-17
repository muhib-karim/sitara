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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Owns the ExoPlayer and the [PlaybackEngine], so the queue, auto-advance and the sleep timer
 * keep working after every screen is gone. Exposed through a MediaSession whose connections
 * are filtered by [ControllerPolicy].
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var session: MediaSession? = null
    private var engine: PlaybackEngine? = null

    override fun onCreate() {
        super.onCreate()
        val resolver = (application as PlaybackDependencies).streamResolver
        lateinit var port: Media3PlayerPort
        val dataSource = ResolvingDataSource.Factory(DefaultDataSource.Factory(this), StreamUriResolver(resolver) { port.track(it) })
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
        val engine = PlaybackEngine(port, scope, ElapsedRealtimeTimeSource)
        player.addListener(EngineListener(engine))
        this.engine = engine
        session = MediaSession.Builder(this, player).setCallback(ConnectionPolicyCallback()).build()
        PlaybackEngineRegistry.attach(engine)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
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

        /** The app queues through the engine, never through the session, so no controller may add items. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFailedFuture(UnsupportedOperationException("Sitara does not accept external media items"))
    }
}
