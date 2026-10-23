package app.sitara.music.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import java.io.ByteArrayOutputStream

/**
 * The downloaded song [trackId]'s file, read from [cache] only, or null unless every byte of it is there: a download
 * still running, cut short or partly evicted is never handed out as if it were the whole song.
 */
@OptIn(UnstableApi::class)
fun readWholeDownload(cache: Cache, trackId: String): ByteArray? {
    val key = StreamUriResolver.uriFor(trackId).toString()
    val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
    if (length <= 0 || length > MAX_EXPORT_BYTES || !cache.isCached(key, 0, length)) return null
    // No upstream and no write sink: a miss fails instead of reaching the network.
    val source = CacheDataSource(cache, null, CacheDataSource.FLAG_BLOCK_ON_CACHE)
    return try {
        source.open(DataSpec.Builder().setUri(key).setKey(key).build())
        val out = ByteArrayOutputStream(length.toInt())
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, read)
        }
        out.toByteArray().takeIf { it.size.toLong() == length }
    } finally {
        source.close()
    }
}

/** Larger than any song YouTube serves as audio; a bigger entry is not exported. */
private const val MAX_EXPORT_BYTES = 200L * 1024 * 1024
