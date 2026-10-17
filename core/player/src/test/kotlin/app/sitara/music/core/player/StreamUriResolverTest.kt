package app.sitara.music.core.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.ResolvedStream
import app.sitara.music.source.api.StreamResolver
import java.io.InterruptedIOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreamUriResolverTest {
    private val tracks = (1..2).map {
        Track("t$it", "Song $it", "Artist", "Album", 1_000, Artwork(listOf(0)))
    }

    @Test
    fun `overlapping resolutions finishing out of order keep their own request`() {
        val started = CountDownLatch(1)
        val slowStream = CompletableDeferred<ResolvedStream>()
        val resolver = resolver { track ->
            if (track.id == "t1") {
                started.countDown()
                slowStream.await()
            } else {
                stream("t2")
            }
        }
        val loader = Executors.newSingleThreadExecutor()
        try {
            val slow = loader.submit<DataSpec> { resolver.resolveDataSpec(spec("t1")) }
            assertTrue(started.await(5, TimeUnit.SECONDS))

            val fast = resolver.resolveDataSpec(spec("t2"))
            assertEquals("https://example.com/t2", fast.uri.toString())
            assertEquals(128L, fast.position)
            assertEquals(256L, fast.length)
            assertFalse(slow.isDone)

            slowStream.complete(stream("t1"))
            val late = slow.get(5, TimeUnit.SECONDS)
            assertEquals("https://example.com/t1", late.uri.toString())
            assertEquals(128L, late.position)
            assertEquals(256L, late.length)
            assertEquals("https://example.com/t2", fast.uri.toString())
        } finally {
            slowStream.cancel()
            loader.shutdownNow()
        }
    }

    @Test
    fun `loader interruption is reported as cancellation and a later request still resolves`() {
        val started = CountDownLatch(1)
        val result = CompletableFuture<Pair<Throwable?, Boolean>>()
        val resolver = resolver { track ->
            if (track.id == "t1") {
                started.countDown()
                awaitCancellation()
            } else {
                stream("t2")
            }
        }
        val loader = Thread {
            val failure = runCatching { resolver.resolveDataSpec(spec("t1")) }.exceptionOrNull()
            result.complete(failure to Thread.currentThread().isInterrupted)
        }
        loader.start()
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            loader.interrupt()
            val (failure, interrupted) = result.get(5, TimeUnit.SECONDS)

            assertTrue("Loader cancellation must be an InterruptedIOException", failure is InterruptedIOException)
            assertTrue(failure?.cause is InterruptedException)
            assertTrue("Loader interrupt status must be preserved", interrupted)
            assertEquals("https://example.com/t2", resolver.resolveDataSpec(spec("t2")).uri.toString())
        } finally {
            loader.interrupt()
            loader.join(5_000)
        }
    }

    @Test
    fun `coroutine cancellation preserves its cause`() {
        val cancellation = CancellationException("Request cancelled")
        val resolver = resolver { throw cancellation }

        val failure = assertThrows(InterruptedIOException::class.java) { resolver.resolveDataSpec(spec("t1")) }

        assertEquals(cancellation, failure.cause)
    }

    private fun resolver(load: suspend (Track) -> ResolvedStream) = StreamUriResolver(
        object : StreamResolver {
            override val id = "controlled"

            override suspend fun resolve(track: Track): ResolvedStream = load(track)
        },
        trackFor = { id -> tracks.find { it.id == id } },
    )

    private fun spec(id: String): DataSpec = DataSpec.Builder()
        .setUri(StreamUriResolver.uriFor(id))
        .setPosition(128)
        .setLength(256)
        .build()

    private fun stream(id: String) = ResolvedStream("https://example.com/$id", "audio/mpeg", 128)
}
