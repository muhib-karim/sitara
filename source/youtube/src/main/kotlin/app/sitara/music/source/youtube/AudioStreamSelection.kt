package app.sitara.music.source.youtube

import app.sitara.music.core.model.AudioQuality
import app.sitara.music.source.api.ResolvedStream
import com.grack.nanojson.JsonObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod

private val PLAYABLE_FORMATS = listOf(MediaFormat.WEBMA_OPUS, MediaFormat.WEBMA, MediaFormat.M4A)

/** Normal quality streams at most this bitrate when it can. */
private const val NORMAL_MAX_KBPS = 96

/**
 * The audio-only stream to play: progressive https in a format ExoPlayer decodes, original audio before dubbed or
 * described tracks, then by [quality] (see [byQuality]), with opus ahead of m4a on a tie. Null when nothing qualifies.
 */
internal fun selectAudioStream(streams: List<AudioStream>, quality: AudioQuality = AudioQuality.HIGH): ResolvedStream? {
    val playable = streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl && it.content.startsWith("https://") && it.format in PLAYABLE_FORMATS }
    val original = playable.filterNot { it.audioTrackType == AudioTrackType.DUBBED || it.audioTrackType == AudioTrackType.DESCRIPTIVE }.ifEmpty { playable }
    return byQuality(original, quality, kbps = { it.averageBitrate }, formatRank = { PLAYABLE_FORMATS.indexOf(it.format) })
        ?.let { ResolvedStream(it.content, it.format!!.mimeType, it.averageBitrate.coerceAtLeast(0), expiresAtMs(it.content), codec = codecOf(it.format!!)) }
}

// Player-response mime types start with the container; the codec follows in parameters.
private val PLAYABLE_MIME_TYPES = listOf("audio/webm", "audio/mp4")

/**
 * The same choice as [selectAudioStream], over the `adaptiveFormats` of a raw player response: audio with a direct https
 * URL (formats that need a deciphered signature are skipped), the default audio track first, then by [quality], with
 * opus ahead of m4a on a tie.
 */
internal fun selectAudioFormat(formats: List<JsonObject>, quality: AudioQuality = AudioQuality.HIGH): ResolvedStream? {
    val playable = formats.filter { it.getString("url").orEmpty().startsWith("https://") && it.container() in PLAYABLE_MIME_TYPES }
    val original = playable.filter { it.getObject("audioTrack").getBoolean("audioIsDefault", true) }.ifEmpty { playable }
    return byQuality(original, quality, kbps = { it.bitrate() / 1_000 }, formatRank = { PLAYABLE_MIME_TYPES.indexOf(it.container()) })
        ?.let { ResolvedStream(it.getString("url"), it.container(), it.bitrate() / 1_000, expiresAtMs(it.getString("url")), codec = codecOf(it.getString("mimeType").orEmpty())) }
}

/**
 * High (and Auto) takes the highest bitrate, Normal the highest up to [NORMAL_MAX_KBPS] (else the lowest), Data saver
 * the lowest; a lower [formatRank] wins a tie. Streams that do not say their bitrate are used only when none does.
 */
private fun <T> byQuality(streams: List<T>, quality: AudioQuality, kbps: (T) -> Int, formatRank: (T) -> Int): T? {
    val candidates = streams.filter { kbps(it) > 0 }.ifEmpty { streams }
    val lowest = compareBy(kbps).thenBy(formatRank)
    val highest = compareBy(kbps).thenByDescending(formatRank)
    return when (quality) {
        AudioQuality.AUTO, AudioQuality.HIGH -> candidates.maxWithOrNull(highest)
        AudioQuality.NORMAL -> candidates.filter { kbps(it) <= NORMAL_MAX_KBPS }.maxWithOrNull(highest) ?: candidates.minWithOrNull(lowest)
        AudioQuality.DATA_SAVER -> candidates.minWithOrNull(lowest)
    }
}

private fun codecOf(format: MediaFormat): String = when (format) {
    MediaFormat.WEBMA_OPUS -> "Opus"
    MediaFormat.WEBMA -> "Vorbis"
    else -> "AAC"
}

/** `audio/webm; codecs="opus"` names Opus; `mp4a.*` is AAC. */
private fun codecOf(mimeType: String): String? {
    val codecs = mimeType.substringAfter("codecs=", "").trim('"', ' ').lowercase()
    return when {
        codecs.startsWith("opus") -> "Opus"
        codecs.startsWith("vorbis") -> "Vorbis"
        codecs.startsWith("mp4a") -> "AAC"
        else -> null
    }
}

private fun JsonObject.container(): String = getString("mimeType").orEmpty().substringBefore(';').trim()

private fun JsonObject.bitrate(): Int = getInt("averageBitrate").takeIf { it > 0 } ?: getInt("bitrate")

/** Signed googlevideo URLs carry their expiry as `expire=<epoch seconds>`. */
private fun expiresAtMs(url: String): Long? =
    url.substringAfter('?', "").split('&').firstOrNull { it.startsWith("expire=") }?.substringAfter('=')?.toLongOrNull()?.times(1_000)
