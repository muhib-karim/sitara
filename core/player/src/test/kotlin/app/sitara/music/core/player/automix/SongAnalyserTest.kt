package app.sitara.music.core.player.automix

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.BeatGrid
import app.sitara.music.core.model.RegionBeats
import app.sitara.music.core.model.SongBeats
import app.sitara.music.core.model.Track
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongAnalyserTest {
    private val a = Track("a", "Out", "Artist", "", 240_000, Artwork(listOf(0)))
    private val b = Track("b", "In", "Artist", "", 200_000, Artwork(listOf(0)))

    private class Store : SongBeatsStore {
        val rows = mutableMapOf<String, SongBeats>()
        var reads = 0

        override suspend fun get(trackId: String): SongBeats? {
            reads++
            return rows[trackId]
        }

        override suspend fun put(beats: SongBeats) {
            val old = rows[beats.trackId]
            rows[beats.trackId] = SongBeats(beats.trackId, beats.intro ?: old?.intro, beats.outro ?: old?.outro)
        }
    }

    private class Decoder(private val fail: Boolean = false, var deferred: Boolean = false) : RegionDecoder {
        val calls = mutableListOf<Pair<String, SongSide>>()

        override suspend fun decode(track: Track, side: SongSide): Pcm? {
            calls += track.id to side
            if (fail) throw IOException("offline")
            if (deferred) throw AnalysisDeferredException()
            val fixture = BeatFixture(124.0, seconds = 12.0)
            return Pcm(fixture.samples, fixture.sampleRate, if (side == SongSide.OUTRO) track.durationMs - 12_000 else 0)
        }
    }

    private fun TestScope.analyser(store: SongBeatsStore?, decoder: RegionDecoder) =
        SongAnalyser(this, store, decoder, StandardTestDispatcher(testScheduler)).apply { enabled = true }

    @Test
    fun `the playing song's end and the next song's start are analysed, kept and remembered`() = runTest {
        val store = Store()
        val decoder = Decoder()
        val analyser = analyser(store, decoder)
        analyser.follow(a, b)
        advanceUntilIdle()

        assertEquals(listOf("a" to SongSide.OUTRO, "b" to SongSide.INTRO), decoder.calls)
        val outro = analyser.beatsOf("a")?.outro
        assertNotNull(outro)
        assertEquals(124.0, outro!!.grid!!.bpm, 2.5)
        assertTrue("song time of the end", outro.grid!!.downbeatMs >= 228_000)
        assertNotNull(analyser.beatsOf("b")?.intro)
        assertEquals(store.rows["a"]?.outro, outro)
    }

    @Test
    fun `each side of a song is analysed once`() = runTest {
        val decoder = Decoder()
        val analyser = analyser(Store(), decoder)
        analyser.follow(a, b)
        advanceUntilIdle()
        analyser.follow(a, b)
        analyser.follow(b, null)
        advanceUntilIdle()

        assertEquals(listOf("a" to SongSide.OUTRO, "b" to SongSide.INTRO, "b" to SongSide.OUTRO), decoder.calls)
    }

    @Test
    fun `a kept analysis is read instead of decoding again`() = runTest {
        val store = Store()
        val kept = RegionBeats(BeatGrid(100.0, 200_000, 0.9f), -9f, 210_000, 239_000)
        store.rows["a"] = SongBeats("a", outro = kept)
        val decoder = Decoder()
        val analyser = analyser(store, decoder)
        analyser.follow(a, null)
        advanceUntilIdle()

        assertTrue(decoder.calls.isEmpty())
        assertEquals(kept, analyser.beatsOf("a")?.outro)
    }

    @Test
    fun `nothing is analysed while automix is off`() = runTest {
        val decoder = Decoder()
        val analyser = analyser(Store(), decoder).apply { enabled = false }
        analyser.follow(a, b)
        advanceUntilIdle()

        assertTrue(decoder.calls.isEmpty())
        assertNull(analyser.beatsOf("a"))
    }

    @Test
    fun `a song that cannot be decoded leaves nothing kept and is not retried`() = runTest {
        val store = Store()
        val decoder = Decoder(fail = true)
        val analyser = analyser(store, decoder)
        analyser.follow(a, null)
        advanceUntilIdle()
        analyser.follow(a, null)
        advanceUntilIdle()

        assertTrue(store.rows.isEmpty())
        assertNull(analyser.beatsOf("a"))
        assertEquals(1, decoder.calls.size)
    }

    @Test
    fun `songs skipped past before their turn are not analysed`() = runTest {
        val c = Track("c", "Later", "Artist", "", 200_000, Artwork(listOf(0)))
        val d = Track("d", "Even later", "Artist", "", 200_000, Artwork(listOf(0)))
        val decoder = Decoder()
        val analyser = analyser(Store(), decoder)
        analyser.follow(a, b)
        analyser.follow(c, d) // skipped on before anything ran
        advanceUntilIdle()

        assertEquals(listOf("c" to SongSide.OUTRO, "d" to SongSide.INTRO), decoder.calls)
    }

    @Test
    fun `a deferred analysis is asked again at the next change`() = runTest {
        val decoder = Decoder(deferred = true)
        val analyser = analyser(Store(), decoder)
        analyser.follow(a, null)
        advanceUntilIdle()
        decoder.deferred = false
        analyser.follow(a, null)
        advanceUntilIdle()

        assertEquals(2, decoder.calls.size)
        assertNotNull(analyser.beatsOf("a")?.outro)
    }
}
