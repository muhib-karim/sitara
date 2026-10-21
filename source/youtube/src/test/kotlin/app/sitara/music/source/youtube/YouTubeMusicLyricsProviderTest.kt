package app.sitara.music.source.youtube

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.CachedLyrics
import app.sitara.music.core.model.LyricsSong
import app.sitara.music.core.model.Track
import app.sitara.music.source.lyrics.LyricsCache
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * YouTube Music's lyrics tab, answered from responses recorded from music.youtube.com (trimmed, the lyric text replaced
 * with original lines), so these tests never touch the network.
 */
class YouTubeMusicLyricsProviderTest {
    private val song = Track("fHI8X4OXluQ", "Paper Lanterns", "Orchid Lane", "", 214_000, Artwork(listOf(0)))
    private val sent = mutableListOf<Pair<String, Map<String, String>>>()
    private val answers = mutableMapOf<String, String>()
    private var failure: IOException? = null
    private var now = 1_000_000L
    private val cache = object : LyricsCache {
        val entries = mutableMapOf<String, CachedLyrics>()

        override suspend fun get(trackId: String) = entries[trackId]

        override suspend fun put(trackId: String, lyrics: CachedLyrics) {
            entries[trackId] = lyrics
        }
    }
    private val provider = YouTubeMusicLyricsProvider(cache, clock = { now }) { url, fields ->
        sent += url to fields
        failure?.let { throw it }
        fixture(answers.getValue(url.substringAfterLast('/').substringBefore('?')))
    }

    private fun fixture(name: String): JsonObject = JsonParser.`object`().from(javaClass.getResourceAsStream("/lyrics/$name.json"))

    @Test
    fun `reads the lyrics tab of the song's watch page, crediting the licensor`() = runTest {
        answers += mapOf("next" to "next_with_lyrics", "browse" to "browse_lyrics")

        val lyrics = provider.lyrics(song)!!

        assertEquals(mapOf("videoId" to "fHI8X4OXluQ"), sent[0].second)
        assertEquals(mapOf("browseId" to "MPLYt_bhuEtLF1qfp-3"), sent[1].second)
        assertFalse(lyrics.synced)
        assertEquals(listOf("Paper lanterns over the river", "We carried the night in our hands", "And the morning came anyway"), lyrics.lines.map { it.text })
        assertEquals("YouTube Music", lyrics.source)
        assertEquals("Musixmatch", lyrics.licensor)
        assertEquals(LyricsSong("Paper Lanterns", "Orchid Lane", 214_000), lyrics.matched)
    }

    @Test
    fun `found lyrics are kept, so the song is not asked for again`() = runTest {
        answers += mapOf("next" to "next_with_lyrics", "browse" to "browse_lyrics")
        val first = provider.lyrics(song)
        sent.clear()
        now += 365L * 24 * 60 * 60 * 1_000

        assertEquals(first, provider.lyrics(song))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a song whose lyrics tab cannot be opened has none, and is asked for again after a week`() = runTest {
        answers["next"] = "next_without_lyrics"

        assertNull(provider.lyrics(song))
        assertEquals(1, sent.size, "no lyrics page to ask for")
        assertNull(provider.lyrics(song))
        assertEquals(1, sent.size, "a day later the answer is still known")
        now += 8L * 24 * 60 * 60 * 1_000
        assertNull(provider.lyrics(song))
        assertEquals(2, sent.size)
    }

    @Test
    fun `a lyrics page saying they are not available has none`() = runTest {
        answers += mapOf("next" to "next_with_lyrics", "browse" to "browse_not_available")

        assertNull(provider.lyrics(song))
        assertEquals(CachedLyrics(null, null, now), cache.entries[song.id])
    }

    @Test
    fun `a watch page without its tabs is an error, not a song without lyrics`() = runTest {
        answers["next"] = "browse_not_available"

        val error = runCatching { provider.lyrics(song) }.exceptionOrNull()

        assertTrue(error is IOException, "got $error")
        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun `a failed call fails the lookup and keeps nothing`() = runTest {
        failure = IOException("offline")

        assertTrue(runCatching { provider.lyrics(song) }.exceptionOrNull() is IOException)
        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun `a song that is not a YouTube video is not asked for`() = runTest {
        assertNull(provider.lyrics(song.copy(id = "sample:1")))
        assertTrue(sent.isEmpty())
    }
}
