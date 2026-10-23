package app.sitara.music.core.player

import android.media.MediaDataSource
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata

/**
 * The downloaded song [trackId]'s file, read from [cache] only, or null unless every byte of it is there: a download
 * still running, cut short or partly evicted is never handed out as if it were the whole song.
 */
@OptIn(UnstableApi::class)
fun readWholeDownload(cache: Cache, trackId: String): ByteArray? {
    val key = StreamUriResolver.uriFor(trackId).toString()
    val length = wholeLength(cache, key) ?: return null
    if (length > MAX_EXPORT_BYTES) return null
    // One buffer of the file's size: an export holds the file once, not a growing copy and then another.
    val bytes = ByteArray(length.toInt())
    return bytes.takeIf { readCached(cache, key, 0, bytes, 0, bytes.size) == bytes.size }
}

/**
 * The downloaded song [trackId] as a [MediaDataSource] that reads only the ranges asked for from [cache] (never the
 * network), for the platform's extractor; null unless the whole download is there.
 */
@OptIn(UnstableApi::class)
fun cachedDownloadSource(cache: Cache, trackId: String): MediaDataSource? {
    val key = StreamUriResolver.uriFor(trackId).toString()
    val length = wholeLength(cache, key) ?: return null
    return object : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= length) return -1
            val wanted = minOf(size.toLong(), length - position).toInt()
            return readCached(cache, key, position, buffer, offset, wanted)
        }

        override fun getSize(): Long = length

        override fun close() = Unit
    }
}

@OptIn(UnstableApi::class)
private fun wholeLength(cache: Cache, key: String): Long? {
    val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
    return length.takeIf { it > 0 && cache.isCached(key, 0, it) }
}

/** Reads [size] bytes at [position] into [into] from the cache alone: no upstream and no write sink, so a miss fails. */
@OptIn(UnstableApi::class)
private fun readCached(cache: Cache, key: String, position: Long, into: ByteArray, offset: Int, size: Int): Int {
    val source = CacheDataSource(cache, null, CacheDataSource.FLAG_BLOCK_ON_CACHE)
    return try {
        source.open(DataSpec.Builder().setUri(key).setKey(key).setPosition(position).setLength(size.toLong()).build())
        var done = 0
        while (done < size) {
            val read = source.read(into, offset + done, size - done)
            if (read == C.RESULT_END_OF_INPUT) break
            done += read
        }
        done
    } finally {
        source.close()
    }
}

/** Larger than any song (a two-hour mix in Opus is about 140 MB); a bigger entry is not exported. */
private const val MAX_EXPORT_BYTES = 160L * 1024 * 1024
