package app.sitara.music.source.lyrics

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.LyricsLine
import app.sitara.music.core.model.LyricsSong
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.LyricsProvider
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every source is asked at once; their lyrics are ranked, a failed or silent source is left out, and the listener's pick wins. */
class LyricsSourcesTest {
    private val song = Track("dQw4w9WgXcQ", "Paper Lanterns", "Orchid Lane", "", 214_000, Artwork(listOf(0)))
    private val exact = LyricsSong("Paper Lanterns", "Orchid Lane", 214_000)
    private val choices = object : LyricsChoices {
        val picked = mutableMapOf<String, String>()

        override suspend fun get(trackId: String) = picked[trackId]

        override suspend fun put(trackId: String, providerId: String) {
            picked[trackId] = providerId
        }
    }

    private class Source(override val id: String, val answer: suspend () -> Lyrics?) : LyricsProvider {
        override val name: String = id.uppercase()

        override suspend fun lyrics(track: Track): Lyrics? = answer()
    }

    private fun lyrics(synced: Boolean, matched: LyricsSong? = exact) =
        Lyrics(listOf(LyricsLine(0, 4_000, "Paper lanterns")), synced, source = "test", matched = matched)

    private fun sources(vararg providers: LyricsProvider) = LyricsSources(providers.toList(), choices)

    @Test
    fun `synced lyrics come before plain ones whichever source answered`() = runTest {
        val found = sources(Source("plain") { lyrics(synced = false) }, Source("synced") { lyrics(synced = true) }).lookUp(song)

        assertEquals(listOf("synced", "plain"), found.map { it.providerId })
    }

    @Test
    fun `of lyrics alike, those as long as the song come first`() = runTest {
        val found = sources(
            Source("longer") { lyrics(synced = false, matched = exact.copy(durationMs = 260_000)) },
            Source("same") { lyrics(synced = false, matched = exact.copy(durationMs = 215_500)) },
        ).lookUp(song)

        assertEquals(listOf("same", "longer"), found.map { it.providerId })
    }

    @Test
    fun `of lyrics alike and as long, the exact title and artist come first, in any case`() = runTest {
        val found = sources(
            Source("other") { lyrics(synced = true, matched = exact.copy(title = "Paper Lanterns Reprise")) },
            Source("exact") { lyrics(synced = true, matched = exact.copy(title = "paper lanterns", artist = "ORCHID LANE")) },
        ).lookUp(song)

        assertEquals(listOf("exact", "other"), found.map { it.providerId })
    }

    @Test
    fun `with LRCLIB finding nothing, KuGou's synced lyrics lead YouTube Music's plain ones`() = runTest {
        val found = sources(
            Source("lrclib") { null },
            Source("kugou") { lyrics(synced = true, matched = exact.copy(durationMs = 215_000)) },
            Source("youtube-music") { lyrics(synced = false, matched = null) },
        ).lookUp(song)

        assertEquals(listOf("kugou", "youtube-music"), found.map { it.providerId })
    }

    @Test
    fun `a source that fails leaves the lyrics the others found`() = runTest {
        val found = sources(Source("down") { throw IOException("503") }, Source("up") { lyrics(synced = false) }).lookUp(song)

        assertEquals(listOf("up"), found.map { it.providerId })
    }

    @Test
    fun `a source that does not answer in time is left out after ten seconds`() = runTest {
        val found = sources(Source("silent") { awaitCancellation() }, Source("up") { lyrics(synced = true) }).lookUp(song)

        assertEquals(listOf("up"), found.map { it.providerId })
        assertEquals(10_000, currentTime)
    }

    @Test
    fun `nothing found anywhere is an empty answer`() = runTest {
        assertEquals(emptyList<LyricsMatch>(), sources(Source("a") { null }, Source("b") { null }).lookUp(song))
    }

    @Test
    fun `nothing found while a source failed or fell silent is a failure that can be tried again`() = runTest {
        val failed = runCatching { sources(Source("a") { null }, Source("down") { throw IOException("offline") }).lookUp(song) }.exceptionOrNull()
        val silent = runCatching { sources(Source("a") { null }, Source("silent") { awaitCancellation() }).lookUp(song) }.exceptionOrNull()

        assertTrue(failed is IOException, "got $failed")
        assertTrue(silent is IOException, "got $silent")
    }

    @Test
    fun `the source the listener picked comes first and stays picked for the song`() = runTest {
        val sources = sources(Source("synced") { lyrics(synced = true) }, Source("plain") { lyrics(synced = false) })

        sources.choose(song, "plain")

        assertEquals(listOf("plain", "synced"), sources.lookUp(song).map { it.providerId })
        assertEquals("plain", choices.picked[song.id])
        assertEquals(listOf("synced", "plain"), sources.lookUp(song.copy(id = "other")).map { it.providerId })
    }

    @Test
    fun `a picked source that no longer has the song gives way to the ranking`() = runTest {
        choices.picked[song.id] = "gone"

        val found = sources(Source("gone") { null }, Source("plain") { lyrics(synced = false) }, Source("synced") { lyrics(synced = true) }).lookUp(song)

        assertEquals(listOf("synced", "plain"), found.map { it.providerId })
    }

    @Test
    fun `the sources are named in the order they are asked`() {
        assertEquals(listOf("LRCLIB", "YOUTUBE"), sources(Source("lrclib") { null }, Source("youtube") { null }).names)
    }
}
