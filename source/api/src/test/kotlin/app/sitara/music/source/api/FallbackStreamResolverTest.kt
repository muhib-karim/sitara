package app.sitara.music.source.api

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FallbackStreamResolverTest {
    private val track = Track("t1", "Title", "Artist", "Album", 1000, Artwork(listOf(0)))
    private val other = track.copy(id = "t2")

    // Every resolve call, as "<resolver>:<track>", in the order the chain made them.
    private val calls = mutableListOf<String>()

    private fun resolver(id: String, url: String?, servesOnly: Set<String>? = null) = object : StreamResolver {
        override val id = id
        override suspend fun resolve(track: Track): ResolvedStream {
            calls += "$id:${track.id}"
            if (url == null || (servesOnly != null && track.id !in servesOnly)) error("$id down")
            return ResolvedStream(url, "audio/webm", 128)
        }
    }

    @Test
    fun `falls back to the next resolver when the first fails`() = runBlocking {
        val chain = FallbackStreamResolver(listOf(resolver("a", null), resolver("b", "https://b")))
        assertEquals("https://b", chain.resolve(track).url)
    }

    @Test
    fun `tries resolvers in the given order and stops at the first success`() = runBlocking {
        val chain = FallbackStreamResolver(listOf(resolver("a", null), resolver("b", "https://b"), resolver("c", "https://c")))

        chain.resolve(track)

        assertEquals(listOf("a:t1", "b:t1"), calls)
    }

    @Test
    fun `asks the resolver that last worked for a song first`() = runBlocking {
        val chain = FallbackStreamResolver(listOf(resolver("a", null), resolver("b", "https://b")))
        chain.resolve(track)
        calls.clear()

        assertEquals("https://b", chain.resolve(track).url)
        assertEquals(listOf("b:t1"), calls)
    }

    @Test
    fun `remembers the winner per song, not for every song`() = runBlocking {
        val chain = FallbackStreamResolver(listOf(resolver("a", "https://a", servesOnly = setOf("t2")), resolver("b", "https://b")))
        chain.resolve(track) // a fails for t1, b wins
        calls.clear()

        assertEquals("https://a", chain.resolve(other).url)
        assertEquals(listOf("a:t2"), calls)
    }

    @Test
    fun `falls back through the rest in order when the remembered resolver stops working`() = runBlocking {
        var bWorks = true
        val b = object : StreamResolver {
            override val id = "b"
            override suspend fun resolve(track: Track): ResolvedStream {
                calls += "b:${track.id}"
                return if (bWorks) ResolvedStream("https://b", "audio/webm", 128) else error("b down")
            }
        }
        val chain = FallbackStreamResolver(listOf(resolver("a", null), b, resolver("c", "https://c")))
        chain.resolve(track)
        bWorks = false
        calls.clear()

        assertEquals("https://c", chain.resolve(track).url)
        assertEquals(listOf("b:t1", "a:t1", "c:t1"), calls)
    }

    @Test
    fun `reports every failure when all resolvers fail`() {
        val chain = FallbackStreamResolver(listOf(resolver("a", null), resolver("b", null)))
        val e = assertThrows<StreamUnavailableException> { runBlocking { chain.resolve(track) } }
        assertEquals(2, e.causes.size)
    }
}
