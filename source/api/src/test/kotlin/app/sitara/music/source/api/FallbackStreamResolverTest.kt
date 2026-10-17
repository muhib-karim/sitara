package app.sitara.music.source.api

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FallbackStreamResolverTest {
    private val track = Track("t1", "Title", "Artist", "Album", 1000, Artwork(listOf(0)))

    private fun resolver(id: String, url: String?) = object : StreamResolver {
        override val id = id
        override suspend fun resolve(track: Track): ResolvedStream =
            url?.let { ResolvedStream(it, "audio/mpeg", 128) } ?: error("$id down")
    }

    @Test
    fun `falls back to the next resolver when the first fails`() = runBlocking {
        val chain = FallbackStreamResolver(listOf(resolver("a", null), resolver("b", "https://b")))
        assertEquals("https://b", chain.resolve(track).url)
    }

    @Test
    fun `reports every failure when all resolvers fail`() {
        val chain = FallbackStreamResolver(listOf(resolver("a", null), resolver("b", null)))
        val e = assertThrows<StreamUnavailableException> { runBlocking { chain.resolve(track) } }
        assertEquals(2, e.causes.size)
    }
}
