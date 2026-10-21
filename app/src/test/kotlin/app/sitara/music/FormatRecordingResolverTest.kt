package app.sitara.music

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.StreamFormat
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.ResolvedStream
import app.sitara.music.source.api.StreamResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatRecordingResolverTest {
    private fun track(n: Int) = Track("id$n", "Song $n", "Artist", "", 200_000, Artwork(listOf(0)))

    private val resolver = FormatRecordingResolver(
        object : StreamResolver {
            override val id = "fixed"
            override suspend fun resolve(track: Track) = ResolvedStream("https://a.example/${track.id}", "audio/webm", 160, codec = "Opus")
        },
    )

    @Test
    fun eachSongKeepsTheFormatItStreamedIn() = runBlocking {
        resolver.resolve(track(1))
        assertEquals(StreamFormat("Opus", 160), resolver.formats.value["id1"])
    }

    @Test
    fun onlyTheLatestFiftySongsAreKept() = runBlocking {
        for (n in 1..51) resolver.resolve(track(n))
        assertNull("the oldest song is dropped", resolver.formats.value["id1"])
        assertEquals("fifty songs are kept", 50, resolver.formats.value.size)
        resolver.resolve(track(2))
        resolver.resolve(track(52))
        assertEquals("a song looked up again counts as new", StreamFormat("Opus", 160), resolver.formats.value["id2"])
    }
}
