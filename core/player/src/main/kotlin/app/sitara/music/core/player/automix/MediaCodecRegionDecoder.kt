package app.sitara.music.core.player.automix

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.localUri
import app.sitara.music.core.player.readWholeDownload
import app.sitara.music.source.api.StreamResolver
import java.nio.ByteOrder
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes one side of a song with the platform's extractor and decoders: a song on the phone from its content URI, a
 * download from the cache, anything else from its stream (the extractor asks only for the byte ranges it needs).
 */
@OptIn(UnstableApi::class)
class MediaCodecRegionDecoder(
    private val context: Context,
    private val streams: StreamResolver,
    private val cache: Cache,
) : RegionDecoder {
    override suspend fun decode(track: Track, side: SongSide): Pcm? {
        val local = track.localUri
        val download = withContext(Dispatchers.IO) { if (local == null) readWholeDownload(cache, track.id) else null }
        val url = if (local == null && download == null) streams.resolve(track).url else null
        return withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                when {
                    local != null -> extractor.setDataSource(context, Uri.parse(local), null)
                    download != null -> extractor.setDataSource(BytesSource(download))
                    else -> extractor.setDataSource(url!!, emptyMap())
                }
                decodeSide(extractor, side, track.durationMs)
            } finally {
                extractor.release()
            }
        }
    }

    private class BytesSource(private val bytes: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(size.toLong(), bytes.size - position).toInt()
            System.arraycopy(bytes, position.toInt(), buffer, offset, count)
            return count
        }

        override fun getSize(): Long = bytes.size.toLong()

        override fun close() = Unit
    }
}

/** Decodes [REGION_MS] from the start or the end of the selected file to mono floats. */
fun decodeSide(extractor: MediaExtractor, side: SongSide, fallbackDurationMs: Long): Pcm? {
    val index = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        ?: return null
    extractor.selectTrack(index)
    val format = extractor.getTrackFormat(index)
    val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
    val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else fallbackDurationMs * 1_000
    val regionUs = REGION_MS * 1_000
    val startUs = if (side == SongSide.INTRO) 0L else max(0L, durationUs - regionUs)
    val endUs = startUs + regionUs
    if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    var float = false
    val codec = MediaCodec.createDecoderByType(mime)
    val mono = FloatList()
    var firstUs = -1L
    try {
        codec.configure(format, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var steps = 0
        while (steps++ < MAX_STEPS) {
            if (!inputDone) {
                val slot = codec.dequeueInputBuffer(TIMEOUT_US)
                if (slot >= 0) {
                    val buffer = codec.getInputBuffer(slot) ?: break
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0 || extractor.sampleTime > endUs) {
                        codec.queueInputBuffer(slot, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(slot, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> codec.outputFormat.let { f ->
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    float = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                out >= 0 -> {
                    val buffer = codec.getOutputBuffer(out)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.nativeOrder())
                        val frames = info.size / ((if (float) 4 else 2) * channels)
                        for (frame in 0 until frames) {
                            val us = info.presentationTimeUs + frame * 1_000_000L / rate
                            var sum = 0f
                            for (c in 0 until channels) sum += if (float) buffer.float else buffer.short / 32_768f
                            if (us < startUs || us >= endUs) continue
                            if (firstUs < 0) firstUs = us
                            mono.add(sum / channels)
                        }
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
    } finally {
        runCatching { codec.stop() }
        codec.release()
    }
    if (mono.size < rate) return null
    return Pcm(mono.toArray(), rate, firstUs / 1_000)
}

private class FloatList {
    private var data = FloatArray(1 shl 16)
    var size = 0
        private set

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun toArray(): FloatArray = data.copyOf(size)
}

private const val TIMEOUT_US = 10_000L

// Enough loop turns for any 30 s of audio; a codec that stops answering ends the decode instead of hanging.
private const val MAX_STEPS = 200_000
