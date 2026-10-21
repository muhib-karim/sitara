package app.sitara.music

import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.data.imports.ImportCatalogue
import app.sitara.music.core.data.imports.ImportFailedException
import app.sitara.music.core.data.imports.ImportSource
import app.sitara.music.core.data.library.LibraryStore
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.ImportProblem
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.Track
import app.sitara.music.feature.library.ImportState
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The import screen's controller over the real on-device store and a catalogue that answers from fixed lists. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistImportsTest {
    private val store = LibraryStore(ApplicationProvider.getApplicationContext())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val song = Track("x7kGuhlXmFI", "Niye Jabe Ki", "Xefer", "", 200_000, Artwork(listOf(0)))
    private val other = Track("other", "Niye Jabe Ki", "Someone Else", "", 200_000, Artwork(listOf(0)))

    private inner class Catalogue : ImportCatalogue {
        var calls = 0
        var offline = false
        var gate: CompletableDeferred<Unit>? = null
        var searchCancelled = false

        override suspend fun playlist(id: String): Playlist {
            calls++
            if (offline) throw IOException("offline")
            if (id != "PL123abc") throw ImportFailedException(ImportProblem.PLAYLIST_UNAVAILABLE)
            return Playlist(id, "Road trip", "", Artwork(listOf(0)), listOf(song))
        }

        override suspend fun song(link: String): Track? = null

        override suspend fun search(query: String): List<Track> {
            calls++
            try {
                gate?.await()
            } catch (e: CancellationException) {
                searchCancelled = true
                throw e
            }
            return listOf(other)
        }
    }

    private val catalogue = Catalogue()
    private val imports = PlaylistImports(scope, store.importer(catalogue)) { uri ->
        ImportSource.PlaylistFile("${uri.substringAfterLast('/')}.csv", "title,artist\nNiye Jabe Ki,Xefer\n")
    }

    @After
    fun stop() = scope.cancel()

    private fun <T> await(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

    private inline fun <reified S : ImportState> next(): S = await { imports.state.first { it is S } as S }

    @Test
    fun `opening the screen reads nothing from the network`() {
        val start = next<ImportState.Start>()
        assertNull(start.problem)
        assertEquals(0, catalogue.calls)
    }

    @Test
    fun `a link that is not a playlist link is worded without asking the network`() {
        imports.setLink("https://example.com/my-playlist")
        imports.importLink()
        assertEquals(ImportProblem.LINK_NOT_RECOGNISED, await { imports.state.first { (it as? ImportState.Start)?.problem != null } as ImportState.Start }.problem)
        assertEquals(0, catalogue.calls)
    }

    @Test
    fun `a playlist link is imported straight into a playlist`() {
        imports.setLink("Listen to this https://music.youtube.com/playlist?list=PL123abc&si=x")
        imports.importLink()
        val done = next<ImportState.Done>()
        assertEquals("Road trip", done.result.name)
        assertEquals(1, done.result.added)
        assertEquals(listOf(song), await { store.playlists.playlist(done.result.playlistId).first()!!.tracks })
    }

    @Test
    fun `offline and private playlists are worded, and the link stays for another try`() {
        catalogue.offline = true
        imports.setLink("https://www.youtube.com/playlist?list=PL123abc")
        imports.importLink()
        val offline = await { imports.state.first { (it as? ImportState.Start)?.problem != null } as ImportState.Start }
        assertEquals(ImportProblem.OFFLINE, offline.problem)
        assertEquals("https://www.youtube.com/playlist?list=PL123abc", offline.link)

        catalogue.offline = false
        imports.setLink("https://www.youtube.com/playlist?list=PLprivate")
        imports.importLink()
        await { imports.state.first { (it as? ImportState.Start)?.problem == ImportProblem.PLAYLIST_UNAVAILABLE } }
    }

    @Test
    fun `an uncertain match waits for review, then finishing adds the chosen song`() {
        imports.importFile("content://picked/favourites")
        val running = await { imports.state.first { (it as? ImportState.Running)?.job?.review?.isNotEmpty() == true } as ImportState.Running }
        val row = running.job.review.single()
        assertEquals(other, row.match)

        imports.choose(row.position, other)
        await { imports.state.first { (it as? ImportState.Running)?.job?.matched == 1 } }
        imports.finish()
        val done = next<ImportState.Done>()
        assertEquals("favourites", done.result.name)
        assertEquals(listOf(other), await { store.playlists.playlist(done.result.playlistId).first()!!.tracks })
    }

    @Test
    fun `cancel stops the search under way and forgets the import`() {
        catalogue.gate = CompletableDeferred()
        imports.importFile("content://picked/favourites")
        await { imports.state.first { it is ImportState.Running } }
        await { while (catalogue.calls == 0) delay(10) }

        imports.cancel()
        val start = await { imports.state.first { it is ImportState.Start && it.unfinished == null } }
        assertTrue(start is ImportState.Start)
        assertTrue(catalogue.searchCancelled)
        assertNull(await { store.importer(catalogue).unfinished.first() })
    }
}
