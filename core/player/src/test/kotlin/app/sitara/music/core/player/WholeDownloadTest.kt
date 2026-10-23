package app.sitara.music.core.player

import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WholeDownloadTest {
    private val folder: File = Files.createTempDirectory("downloads").toFile()
    private val cache = SimpleCache(folder, NoOpCacheEvictor(), StandaloneDatabaseProvider(RuntimeEnvironment.getApplication()))
    private val song = ByteArray(300_000) { (it * 31 % 256).toByte() }

    @After
    fun tearDown() {
        cache.release()
        folder.deleteRecursively()
    }

    /** Caches [bytes] as a download does: unbounded, so the cache learns the length at the end; or only [length] bytes. */
    private fun store(id: String, bytes: ByteArray, length: Long = C.LENGTH_UNSET.toLong()) {
        val key = StreamUriResolver.uriFor(id).toString()
        val upstream = CacheDataSource(cache, ByteArrayDataSource(bytes))
        CacheWriter(upstream, DataSpec.Builder().setUri(key).setKey(key).setLength(length).build(), null, null).cache()
    }

    @Test
    fun `a whole download reads back byte for byte`() {
        store("song1", song)
        assertArrayEquals(song, readWholeDownload(cache, "song1"))
    }

    @Test
    fun `a download cut short gives nothing`() {
        store("song2", song)
        val key = StreamUriResolver.uriFor("song2").toString()
        // Drop the last span, as an eviction or a failed write would.
        cache.getCachedSpans(key).maxBy { it.position }.let(cache::removeSpan)
        assertNull(readWholeDownload(cache, "song2"))
    }

    @Test
    fun `a partly fetched download gives nothing`() {
        store("song3", song, length = 100_000)
        assertNull(readWholeDownload(cache, "song3"))
    }

    @Test
    fun `a whole download reads by ranges for the extractor, and a partial one gives no source`() {
        store("song4", song)
        val source = cachedDownloadSource(cache, "song4")!!
        assertEquals(song.size.toLong(), source.size)
        val part = ByteArray(1_000)
        assertEquals(1_000, source.readAt(150_000, part, 0, 1_000))
        assertArrayEquals(song.copyOfRange(150_000, 151_000), part)
        assertEquals(-1, source.readAt(song.size.toLong(), part, 0, 10))
        store("song5", song, length = 100_000)
        assertNull(cachedDownloadSource(cache, "song5"))
    }

    @Test
    fun `a song never downloaded gives nothing`() {
        assertNull(readWholeDownload(cache, "missing"))
    }
}
