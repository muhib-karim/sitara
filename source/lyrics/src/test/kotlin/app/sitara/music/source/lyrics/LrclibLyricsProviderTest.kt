package app.sitara.music.source.lyrics

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.CachedLyrics
import app.sitara.music.core.model.Track
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** An interceptor plays LRCLIB, answering by path, so these tests never touch the network. */
class LrclibLyricsProviderTest {
    private val sent = mutableListOf<Request>()
    private val answers = mutableMapOf<String, Pair<Int, String>>()
    private val cache = object : LyricsCache {
        val entries = mutableMapOf<String, CachedLyrics>()

        override suspend fun get(trackId: String) = entries[trackId]

        override suspend fun put(trackId: String, lyrics: CachedLyrics) {
            entries[trackId] = lyrics
        }
    }
    private var now = 1_000_000L
    private val interceptor = Interceptor { chain ->
        sent += chain.request()
        val (code, body) = answers[chain.request().url.encodedPath] ?: (404 to """{"statusCode":404,"name":"TrackNotFound"}""")
        okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("status $code")
            .body(body.toResponseBody()).build()
    }
    private val provider = LrclibLyricsProvider(OkHttpClient.Builder().addInterceptor(interceptor).build(), cache, clock = { now })
    private val song = Track("dQw4w9WgXcQ", "Paper Lanterns", "Orchid Lane", "", 214_000, Artwork(listOf(0)))

    private fun json(synced: String?, plain: String?, duration: Double = 214.0) =
        """{"id":1,"trackName":"Paper Lanterns","artistName":"Orchid Lane","duration":$duration,"instrumental":false,""" +
            """"plainLyrics":${plain?.let { "\"$it\"" } ?: "null"},"syncedLyrics":${synced?.let { "\"$it\"" } ?: "null"}}"""

    @Test
    fun `asks LRCLIB for the song by title, artist and length, naming the app`() = runTest {
        answers["/api/get"] = 200 to json("[00:01.00]Paper lanterns\\n[00:05.00]Over the water", "Paper lanterns\\nOver the water")

        val lyrics = provider.lyrics(song)!!

        val request = sent.single()
        assertEquals("lrclib.net", request.url.host)
        assertEquals("Paper Lanterns", request.url.queryParameter("track_name"))
        assertEquals("Orchid Lane", request.url.queryParameter("artist_name"))
        assertEquals("214", request.url.queryParameter("duration"))
        assertEquals("Sitara (https://github.com/muhib-karim/sitara)", request.header("User-Agent"))
        assertTrue(lyrics.synced)
        assertEquals(listOf("Paper lanterns", "Over the water"), lyrics.lines.map { it.text })
        assertEquals(5_000, lyrics.lines[0].endMs)
        assertEquals(214_000, lyrics.lines[1].endMs)
        assertEquals("LRCLIB", lyrics.source)
    }

    @Test
    fun `falls back to plain lyrics when nothing is synced`() = runTest {
        answers["/api/get"] = 200 to json(null, "Paper lanterns\\nOver the water")

        val lyrics = provider.lyrics(song)!!

        assertFalse(lyrics.synced)
        assertEquals(listOf("Paper lanterns", "Over the water"), lyrics.lines.map { it.text })
    }

    @Test
    fun `searches when there is no exact match and prefers synced lyrics of the same length`() = runTest {
        answers["/api/search"] = 200 to "[" + listOf(
            json("[00:01.00]Far away", "Far away", duration = 260.0),
            json(null, "Close but plain", duration = 215.0),
            json("[00:01.00]Close and synced", "Close and synced", duration = 213.0),
        ).joinToString(",") + "]"

        val lyrics = provider.lyrics(song)!!

        assertEquals(listOf("/api/get", "/api/search"), sent.map { it.url.encodedPath })
        assertEquals("Paper Lanterns", sent[1].url.queryParameter("track_name"))
        assertEquals(listOf("Close and synced"), lyrics.lines.map { it.text })
    }

    @Test
    fun `asks with the cleaned title first, then with the title as uploaded`() = runTest {
        val video = song.copy(title = "Paper Lanterns (feat. Kite) (Official Video)", artist = "Orchid Lane - Topic")

        assertNull(provider.lyrics(video))

        assertEquals(
            listOf(
                "/api/get" to "Paper Lanterns", "/api/search" to "Paper Lanterns",
                "/api/get" to video.title, "/api/search" to video.title,
            ),
            sent.map { it.url.encodedPath to it.url.queryParameter("track_name") },
        )
        assertEquals(listOf("Orchid Lane", "Orchid Lane", video.artist, video.artist), sent.map { it.url.queryParameter("artist_name") })
    }

    @Test
    fun `lyrics found with the cleaned title need no second lookup`() = runTest {
        answers["/api/get"] = 200 to json(null, "Paper lanterns")

        provider.lyrics(song.copy(title = "Paper Lanterns (Official Video)"))

        assertEquals(listOf("Paper Lanterns"), sent.map { it.url.queryParameter("track_name") })
    }

    @Test
    fun `a search with nothing of the same length finds nothing`() = runTest {
        answers["/api/search"] = 200 to "[" + json("[00:01.00]Other song", null, duration = 320.0) + "]"

        assertNull(provider.lyrics(song))
    }

    @Test
    fun `a song with no lyrics is not looked up again for a week`() = runTest {
        assertNull(provider.lyrics(song))
        val lookups = sent.size

        now += 6 * DAY_MS
        assertNull(provider.lyrics(song))
        assertEquals(lookups, sent.size)

        now += 2 * DAY_MS
        answers["/api/get"] = 200 to json(null, "Found at last")
        assertEquals(listOf("Found at last"), provider.lyrics(song)!!.lines.map { it.text })
    }

    @Test
    fun `cached lyrics are used without asking LRCLIB`() = runTest {
        cache.entries[song.id] = CachedLyrics("[00:02.00]From the cache", null, fetchedAtMs = 0)

        assertEquals(listOf("From the cache"), provider.lyrics(song)!!.lines.map { it.text })
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a server error fails the lookup and caches nothing`() = runTest {
        answers["/api/get"] = 503 to """{"statusCode":503,"name":"ServerOverloaded"}"""

        assertTrue(runCatching { provider.lyrics(song) }.exceptionOrNull() is IOException)
        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun `lyrics still show when the cache cannot be written`() = runTest {
        answers["/api/get"] = 200 to json(null, "Paper lanterns")
        val full = object : LyricsCache {
            override suspend fun get(trackId: String): CachedLyrics? = null

            override suspend fun put(trackId: String, lyrics: CachedLyrics) = throw IOException("disk full")
        }

        val lyrics = LrclibLyricsProvider(OkHttpClient.Builder().addInterceptor(interceptor).build(), full).lyrics(song)

        assertEquals(listOf("Paper lanterns"), lyrics!!.lines.map { it.text })
    }

    @Test
    fun `an instrumental has no lyrics`() = runTest {
        answers["/api/get"] = 200 to """{"id":2,"trackName":"Paper Lanterns","duration":214.0,"instrumental":true,"plainLyrics":null,"syncedLyrics":null}"""

        assertNull(provider.lyrics(song))
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1_000L
    }
}
