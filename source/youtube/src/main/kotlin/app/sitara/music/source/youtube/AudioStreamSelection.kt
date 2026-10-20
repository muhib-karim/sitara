package app.sitara.music.source.youtube

import app.sitara.music.source.api.ResolvedStream
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod

private val PLAYABLE_FORMATS = listOf(MediaFormat.WEBMA_OPUS, MediaFormat.WEBMA, MediaFormat.M4A)

/**
 * The audio-only stream to play: progressive https in a format ExoPlayer decodes, original audio before dubbed or
 * described tracks, then the highest bitrate, with opus ahead of m4a on a tie. Null when nothing qualifies.
 */
internal fun selectAudioStream(streams: List<AudioStream>): ResolvedStream? = streams
    .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl && it.content.startsWith("https://") && it.format in PLAYABLE_FORMATS }
    .sortedWith(
        compareBy<AudioStream> { it.audioTrackType == AudioTrackType.DUBBED || it.audioTrackType == AudioTrackType.DESCRIPTIVE }
            .thenByDescending { it.averageBitrate }
            .thenBy { PLAYABLE_FORMATS.indexOf(it.format) },
    )
    .firstOrNull()
    ?.let { ResolvedStream(it.content, it.format!!.mimeType, it.averageBitrate.coerceAtLeast(0), expiresAtMs(it.content)) }

/** Signed googlevideo URLs carry their expiry as `expire=<epoch seconds>`. */
private fun expiresAtMs(url: String): Long? =
    url.substringAfter('?', "").split('&').firstOrNull { it.startsWith("expire=") }?.substringAfter('=')?.toLongOrNull()?.times(1_000)
