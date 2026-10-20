package app.sitara.music.core.player

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.ResolvedStream
import app.sitara.music.source.api.StreamResolver
import java.io.IOException
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchingStreamResolverTest {
    private fun t(n: Int) = Track("t$n", "Song $n", "Artist", "Album", 1_000, Artwork(listOf(0)))
    private fun stream(id: String) = ResolvedStream("https://example.com/$id", "audio/webm", 160)
    private val asked = mutableListOf<String>()

    private fun TestScope.prefetching(resolve: suspend (Track) -> ResolvedStream = { stream(it.id) }) = PrefetchingStreamResolver(
        upstream = object : StreamResolver {
            override val id = "test"

            override suspend fun resolve(track: Track): ResolvedStream {
                asked += track.id
                return resolve(track)
            }
        },
        scope = backgroundScope,
        timeSource = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
            override fun read(): Long = testScheduler.currentTime
        },
    )

    @Test
    fun `a prepared song is handed over without asking the source again`() = runTest {
        val streams = prefetching()
        streams.prepare(t(1))
        streams.prepare(t(1))
        runCurrent()

        assertEquals(stream("t1"), streams.resolve(t(1)))
        assertEquals(listOf("t1"), asked)
    }

    @Test
    fun `a load that arrives while the song is still being prepared waits for it`() = runTest {
        val slow = CompletableDeferred<ResolvedStream>()
        val streams = prefetching { slow.await() }
        streams.prepare(t(1))
        runCurrent()

        val load = async { streams.resolve(t(1)) }
        runCurrent()
        assertFalse(load.isCompleted)
        slow.complete(stream("t1"))

        assertEquals(stream("t1"), load.await())
        assertEquals(listOf("t1"), asked)
    }

    @Test
    fun `preparing another song cancels the first lookup`() = runTest {
        var cancelled = false
        val streams = prefetching { track ->
            if (track.id == "t1" && asked.size == 1) {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            stream(track.id)
        }
        streams.prepare(t(1))
        runCurrent()
        streams.prepare(t(2))
        runCurrent()

        assertTrue(cancelled)
        assertEquals(stream("t1"), streams.resolve(t(1)))
        assertEquals(stream("t2"), streams.resolve(t(2)))
        assertEquals(listOf("t1", "t2", "t1"), asked)
    }

    @Test
    fun `a failed preparation is looked up again when the song loads`() = runTest {
        val streams = prefetching { track ->
            if (asked.size == 1) throw IOException("offline for a moment")
            stream(track.id)
        }
        streams.prepare(t(1))
        runCurrent()

        assertEquals(stream("t1"), streams.resolve(t(1)))
        assertEquals(listOf("t1", "t1"), asked)
    }

    @Test
    fun `a stream prepared long ago is looked up again, since its address may have expired`() = runTest {
        val streams = prefetching()
        streams.prepare(t(1))
        runCurrent()
        advanceTimeBy(PrefetchingStreamResolver.MAX_AGE.inWholeMilliseconds + 1)

        streams.resolve(t(1))
        assertEquals(listOf("t1", "t1"), asked)

        streams.prepare(t(1))
        runCurrent()
        assertEquals("an expired preparation is redone", listOf("t1", "t1", "t1"), asked)
    }
}
