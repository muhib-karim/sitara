package app.sitara.music.source.lyrics

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.CachedLyrics
import app.sitara.music.core.model.LyricsSong
import app.sitara.music.core.model.Track
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** An interceptor plays KuGou, answering by path, so these tests never touch the network. */
class KugouLyricsProviderTest {
    private val sent = mutableListOf<Request>()
    private val answers = mutableMapOf<String, Pair<Int, String>>()
    private val cache = object : LyricsCache {
        val entries = mutableMapOf<String, CachedLyrics>()

        override suspend fun get(trackId: String) = entries[trackId]

        override suspend fun put(trackId: String, lyrics: CachedLyrics) {
            entries[trackId] = lyrics
        }
    }
    private val interceptor = Interceptor { chain ->
        sent += chain.request()
        val (code, body) = answers[chain.request().url.encodedPath] ?: (404 to "")
        okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("status $code")
            .body(body.toResponseBody()).build()
    }
    private val provider = KugouLyricsProvider(OkHttpClient.Builder().addInterceptor(interceptor).build(), cache, clock = { 1_000L })
    private val song = Track("v1", "Paper Lanterns (Official Video)", "Orchid Lane", "", 214_000, Artwork(listOf(0)))

    private fun candidate(id: String, song: String, singer: String = "Orchid Lane", duration: Long = 214_000) =
        """{"id":"$id","accesskey":"key$id","song":"$song","singer":"$singer","duration":$duration,"score":60}"""

    private fun search(vararg candidates: String) = 200 to """{"status":200,"errcode":200,"candidates":[${candidates.joinToString(",")}]}"""

    private fun download(lrc: String) =
        200 to """{"status":200,"fmt":"lrc","charset":"utf8","content":"${Base64.getEncoder().encodeToString(lrc.toByteArray())}"}"""

    private val lrc = listOf(
        "[ti:Paper Lanterns]",
        "[ar:Orchid Lane]",
        "[00:00.00]Paper Lanterns - Orchid Lane (兰花巷)",
        "[00:04.10]Lyrics by：Someone",
        "[00:08.20]Composed by：Someone Else",
        "[00:10.00]词：某人",
        "[00:12.00]Paper lanterns",
        "[00:16.00]Over the water",
        "[00:20.00]Music by the river",
    ).joinToString("\n")

    @Test
    fun `searches by artist and title with the length, then downloads the matching candidate`() = runTest {
        answers["/search"] = search(candidate("1", "Paper Lanterns"))
        answers["/download"] = download(lrc)

        val lyrics = provider.lyrics(song)!!

        val searchCall = sent[0].url
        assertEquals("lyrics.kugou.com", searchCall.host)
        assertEquals("Orchid Lane - Paper Lanterns", searchCall.queryParameter("keyword"))
        assertEquals("214000", searchCall.queryParameter("duration"))
        assertEquals("1", sent[1].url.queryParameter("id"))
        assertEquals("key1", sent[1].url.queryParameter("accesskey"))
        assertEquals("lrc", sent[1].url.queryParameter("fmt"))
        assertTrue(lyrics.synced)
        assertEquals("KuGou", lyrics.source)
        assertEquals(LyricsSong("Paper Lanterns", "Orchid Lane", 214_000), lyrics.matched)
    }

    @Test
    fun `credit lines before the first sung line are dropped, later lines that look alike stay`() = runTest {
        answers["/search"] = search(candidate("1", "Paper Lanterns"))
        answers["/download"] = download(lrc)

        val lyrics = provider.lyrics(song)!!

        assertEquals(listOf("Paper lanterns", "Over the water", "Music by the river"), lyrics.lines.map { it.text })
        assertEquals(12_000, lyrics.lines.first().startMs)
    }

    @Test
    fun `the first candidate of the same length wins over an earlier one of another length`() = runTest {
        answers["/search"] = search(candidate("1", "Paper Lanterns", duration = 240_000), candidate("2", "Paper Lanterns", duration = 215_500))
        answers["/download"] = download("[00:01.00]Paper lanterns")

        provider.lyrics(song)

        assertEquals("2", sent[1].url.queryParameter("id"))
    }

    @Test
    fun `a candidate with another title is not taken`() = runTest {
        answers["/search"] = search(candidate("1", "Lantern Festival Medley"))

        assertNull(provider.lyrics(song))
        assertEquals(1, sent.size)
        assertEquals(CachedLyrics(null, null, 1_000L), cache.entries["v1"])
    }

    @Test
    fun `a shorter title inside the song's, or another artist's song of that name, is not taken`() = runTest {
        val loveStory = song.copy(id = "v2", title = "Love Story", artist = "Orchid Lane")
        answers["/search"] = search(candidate("1", "Love"), candidate("2", "Love Story", singer = "Someone Else"))

        assertNull(provider.lyrics(loveStory))
        assertEquals(1, sent.size)
    }

    @Test
    fun `bracketed notes and a featured credit do not stop a match`() = runTest {
        answers["/search"] = search(candidate("1", "Paper Lanterns (Live)", singer = "Orchid Lane、Lumen Drift"))
        answers["/download"] = download("[00:01.00]Paper lanterns")

        provider.lyrics(song)

        assertEquals("1", sent[1].url.queryParameter("id"))
    }

    @Test
    fun `no candidates is a miss remembered for a week`() = runTest {
        answers["/search"] = search()

        assertNull(provider.lyrics(song))
        sent.clear()
        assertNull(provider.lyrics(song))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `content that is not base64 is a miss, not a crash`() = runTest {
        answers["/search"] = search(candidate("1", "Paper Lanterns"))
        answers["/download"] = 200 to """{"status":200,"content":"%%% not base64 %%%"}"""

        assertNull(provider.lyrics(song))
    }

    @Test
    fun `lyrics without timestamps are kept as plain lyrics`() = runTest {
        answers["/search"] = search(candidate("1", "Paper Lanterns"))
        answers["/download"] = download("Paper lanterns\nOver the water")

        val lyrics = provider.lyrics(song)!!

        assertEquals(false, lyrics.synced)
        assertEquals(listOf("Paper lanterns", "Over the water"), lyrics.lines.map { it.text })
    }

    @Test
    fun `a failed call throws and is not cached`() = runTest {
        answers["/search"] = 503 to "busy"

        assertThrows<IOException> { provider.lyrics(song) }
        assertTrue(cache.entries.isEmpty())
    }
}
