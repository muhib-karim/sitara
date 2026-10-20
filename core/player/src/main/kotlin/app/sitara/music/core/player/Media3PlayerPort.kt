package app.sitara.music.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.Track

/**
 * [PlayerPort] over ExoPlayer. Items carry a `sitara://track/<id>` URI that [StreamUriResolver]
 * resolves when the player loads them, so stream URLs are fetched lazily and never go stale in the
 * playlist. The media id is the queue entry uid, which is how transitions map back to the queue.
 */
@OptIn(UnstableApi::class)
class Media3PlayerPort(private val player: ExoPlayer) : PlayerPort {
    @Volatile
    private var tracks: Map<String, Track> = emptyMap()

    /** Called from the loader thread by [StreamUriResolver]. */
    fun track(id: String): Track? = tracks[id]

    override val isPlaying: Boolean get() = player.isPlaying
    override val positionMs: Long get() = player.currentPosition
    override val durationMs: Long get() = player.duration.takeIf { it != C.TIME_UNSET } ?: 0

    override fun setQueue(entries: List<QueueEntry>, index: Int, play: Boolean) {
        remember(entries)
        player.setMediaItems(entries.map(::toMediaItem), index, 0)
        player.prepare()
        player.playWhenReady = play
    }

    override fun reorder(entries: List<QueueEntry>, index: Int) {
        // Membership is unchanged; keep the same snapshot visible to in-flight loaders.
        val current = player.currentMediaItemIndex
        val count = player.mediaItemCount
        if (current + 1 < count) player.removeMediaItems(current + 1, count)
        if (current > 0) player.removeMediaItems(0, current)
        player.addMediaItems(0, entries.take(index).map(::toMediaItem))
        player.addMediaItems(entries.drop(index + 1).map(::toMediaItem))
    }

    override fun append(entries: List<QueueEntry>) {
        // Publish the grown membership before the player can ask for the new items.
        tracks = tracks + entries.associate { it.track.id to it.track }
        player.addMediaItems(entries.map(::toMediaItem))
    }

    override fun seekToItem(index: Int, positionMs: Long) = player.seekTo(index, positionMs)

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun play() {
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    override fun pause() = player.pause()

    override fun setRepeatMode(mode: RepeatMode) {
        player.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    override fun setPauseAtEndOfItem(enabled: Boolean) {
        player.pauseAtEndOfMediaItems = enabled
    }

    private fun remember(entries: List<QueueEntry>) {
        // Build privately, then publish once. Published maps are never mutated.
        tracks = entries.associate { it.track.id to it.track }
    }

    private fun toMediaItem(entry: QueueEntry): MediaItem = MediaItem.Builder()
        .setMediaId(entry.uid.toString())
        .setUri(StreamUriResolver.uriFor(entry.track.id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(entry.track.title)
                .setArtist(entry.track.artist)
                .setAlbumTitle(entry.track.album)
                .build(),
        )
        .build()
}
