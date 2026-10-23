package app.sitara.music.core.data.imports

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.data.library.RoomPlaylists
import app.sitara.music.core.data.library.SitaraDatabase
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.ImportProblem
import app.sitara.music.core.model.MatchState
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.SourceSong
import app.sitara.music.core.model.SourceSongList
import app.sitara.music.core.model.SpotifyLink
import app.sitara.music.core.model.Track
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Imports against an in-memory database and a catalogue that answers from fixed lists. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistImporterTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SitaraDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val playlists = RoomPlaylists(db.playlists())
    private val catalogue = FakeCatalogue()
    private var now = 1_000L
    private val spotifyLists = mutableMapOf<String, SourceSongList>()
    private var spotifyReads = 0
    private val importer = PlaylistImporter(db.imports(), catalogue, clock = { now++ }) { link ->
        spotifyReads++
        spotifyLists[link.id] ?: throw ImportFailedException(ImportProblem.PLAYLIST_UNAVAILABLE)
    }

    @After
    fun close() = db.close()

    private fun song(id: String, title: String, artist: String, durationMs: Long = 200_000) =
        Track(id, title, artist, "", durationMs, Artwork(listOf(0xFF9A7BFF.toInt()), "https://example.org/$id.jpg"))

    private val rhapsody = song("rhapsody", "Bohemian Rhapsody", "Queen", 354_000)
    private val buckley = song("buckley", "Hallelujah", "Jeff Buckley")
    private val cale = song("cale", "Hallelujah", "John Cale")
    private val elephant = song("elephant", "Elephant", "Tame Impala")

    private val csv = "title,artist\nBohemian Rhapsody,Queen\nHallelujah,Leonard Cohen\nNo Such Song,Nobody\n"

    private class FakeCatalogue : ImportCatalogue {
        val playlists = mutableMapOf<String, Playlist>()
        val songs = mutableMapOf<String, Track>()
        val results = mutableMapOf<String, List<Track>>()
        val searches = mutableListOf<String>()
        var playlistReads = 0
        var offlineAfter = Int.MAX_VALUE

        override suspend fun playlist(id: String): Playlist {
            playlistReads++
            return playlists[id] ?: throw ImportFailedException(ImportProblem.PLAYLIST_UNAVAILABLE)
        }

        override suspend fun song(link: String): Track? = songs[link]

        override suspend fun search(query: String): List<Track> {
            if (searches.size >= offlineAfter) throw IOException("offline")
            searches += query
            return results[query].orEmpty()
        }
    }

    private fun answer() {
        catalogue.results["Queen Bohemian Rhapsody"] = listOf(rhapsody)
        catalogue.results["Leonard Cohen Hallelujah"] = listOf(buckley, cale)
    }

    @Test
    fun `a Spotify list's songs are looked up like a file's, and an interrupted import goes on without reading Spotify again`() = runTest {
        answer()
        spotifyLists["sp1"] = SourceSongList("Monsoon Evenings", listOf(SourceSong("Bohemian Rhapsody", "Queen", durationMs = 354_000), SourceSong("Hallelujah", "Leonard Cohen")))
        val source = ImportSource.SpotifyList(SpotifyLink(SpotifyLink.Kind.PLAYLIST, "sp1"))
        catalogue.offlineAfter = 1
        val id = importer.open(source)
        runCatching { importer.match(id) }

        catalogue.offlineAfter = Int.MAX_VALUE
        assertEquals("picked up, not started again", id, importer.open(source))
        importer.match(id)

        assertEquals(1, spotifyReads)
        val job = importer.job(id).first()!!
        assertEquals("Monsoon Evenings", job.name)
        assertEquals(1, job.matched)
        assertEquals(1, job.uncertain)
        assertTrue(job.fromSpotify)
        assertFalse("two songs are not the limit", job.mayHaveMore)
        assertEquals(listOf("Queen Bohemian Rhapsody", "Leonard Cohen Hallelujah"), catalogue.searches)
    }

    @Test
    fun `a Spotify list of a hundred songs may have more`() = runTest {
        spotifyLists["sp2"] = SourceSongList("Long", (1..100).map { SourceSong("Song $it", "Artist") }, truncated = true)
        val id = importer.open(ImportSource.SpotifyList(SpotifyLink(SpotifyLink.Kind.PLAYLIST, "sp2")))
        assertTrue(importer.job(id).first()!!.mayHaveMore)
    }

    @Test
    fun `a Spotify list that cannot be read starts nothing`() = runTest {
        val failure = runCatching { importer.open(ImportSource.SpotifyList(SpotifyLink(SpotifyLink.Kind.ALBUM, "missing"))) }.exceptionOrNull()
        assertEquals(ImportProblem.PLAYLIST_UNAVAILABLE, (failure as ImportFailedException).problem)
        assertNull(importer.unfinished.first())
    }

    @Test
    fun `a catalogue playlist imports its songs as they are, once each, under its own name`() = runTest {
        catalogue.playlists["PL1"] = Playlist("PL1", "Road trip", "", Artwork(listOf(0)), listOf(rhapsody, elephant, rhapsody))
        val id = importer.open(ImportSource.CataloguePlaylist("PL1"))
        importer.match(id)

        val job = importer.job(id).first()!!
        assertEquals(3, job.matched)
        assertEquals(0, job.pending)
        assertTrue(job.review.isEmpty())
        assertTrue(catalogue.searches.isEmpty())

        val result = importer.finish(id)
        assertEquals(2, result.added)
        assertFalse(result.updated)
        val playlist = playlists.playlist(result.playlistId).first()!!
        assertEquals("Road trip", playlist.name)
        assertEquals(listOf(rhapsody, elephant), playlist.tracks)
    }

    @Test
    fun `file rows are searched, giving confident matches, uncertain ones to review with their candidates, and songs not found`() = runTest {
        answer()
        val id = importer.open(ImportSource.PlaylistFile("Favourites.csv", csv))
        importer.match(id)

        val job = importer.job(id).first()!!
        assertEquals("Favourites", job.name)
        assertEquals(3, job.total)
        assertEquals(1, job.matched)
        assertEquals(1, job.uncertain)
        assertEquals(1, job.notFound)
        val review = job.review.single()
        assertEquals("Hallelujah", review.source.title)
        assertEquals(MatchState.UNCERTAIN, review.state)
        assertEquals(buckley, review.match)
        assertEquals(listOf(buckley, cale), review.candidates)
    }

    @Test
    fun `a song link in a file is taken as it is, without a search`() = runTest {
        catalogue.songs["https://youtu.be/x7kGuhlXmFI"] = elephant
        val id = importer.open(ImportSource.PlaylistFile("links.m3u", "https://youtu.be/x7kGuhlXmFI\n"))
        importer.match(id)

        assertEquals(1, importer.job(id).first()!!.matched)
        assertTrue(catalogue.searches.isEmpty())
        assertEquals(listOf(elephant), playlists.playlist(importer.finish(id).playlistId).first()!!.tracks)
    }

    @Test
    fun `picking another candidate or skipping decides a row, and only matched songs are added`() = runTest {
        answer()
        val id = importer.open(ImportSource.PlaylistFile("Favourites.csv", csv))
        importer.match(id)

        importer.choose(id, position = 1, track = cale)
        var row = importer.job(id).first()!!.review.single()
        assertEquals(MatchState.MATCHED, row.state)
        assertEquals(cale, row.match)

        importer.skip(id, position = 1)
        row = importer.job(id).first()!!.review.single()
        assertEquals(MatchState.SKIPPED, row.state)

        importer.choose(id, position = 1, track = cale)
        assertEquals(listOf(rhapsody, cale), playlists.playlist(importer.finish(id).playlistId).first()!!.tracks)
    }

    @Test
    fun `an interrupted import resumes where it stopped, without reading the source or searching again`() = runTest {
        answer()
        catalogue.offlineAfter = 1
        val source = ImportSource.PlaylistFile("Favourites.csv", csv)
        val id = importer.open(source)
        assertTrue(runCatching { importer.match(id) }.exceptionOrNull() is IOException)
        assertEquals(2, importer.job(id).first()!!.pending)
        assertEquals(id, importer.unfinished.first()!!.id)

        catalogue.offlineAfter = Int.MAX_VALUE
        assertEquals(id, importer.open(source))
        importer.match(id)

        assertEquals(listOf("Queen Bohemian Rhapsody", "Leonard Cohen Hallelujah", "Nobody No Such Song"), catalogue.searches)
        assertEquals(3, importer.job(id).first()!!.total)
    }

    @Test
    fun `importing the same source again updates its playlist and keeps earlier decisions`() = runTest {
        answer()
        val source = ImportSource.PlaylistFile("Favourites.csv", csv)
        val first = importer.open(source)
        importer.match(first)
        importer.choose(first, position = 1, track = cale)
        val playlistId = importer.finish(first).playlistId
        assertNull(importer.unfinished.first())

        catalogue.searches.clear()
        catalogue.results["Tame Impala Elephant"] = listOf(elephant)
        val second = importer.open(ImportSource.PlaylistFile("Favourites.csv", csv + "Elephant,Tame Impala\n"))
        importer.match(second)

        // Only the song not found and the new row are looked up; the choice made in review stands.
        assertEquals(listOf("Nobody No Such Song", "Tame Impala Elephant"), catalogue.searches)
        assertTrue(importer.job(second).first()!!.review.isEmpty())
        val result = importer.finish(second)
        assertTrue(result.updated)
        assertEquals(playlistId, result.playlistId)
        assertEquals(listOf(rhapsody, cale, elephant), playlists.playlist(playlistId).first()!!.tracks)
        assertEquals(1, playlists.all.first().size)
    }

    @Test
    fun `a playlist deleted since its import is made again`() = runTest {
        catalogue.playlists["PL1"] = Playlist("PL1", "Road trip", "", Artwork(listOf(0)), listOf(elephant))
        val source = ImportSource.CataloguePlaylist("PL1")
        val first = importer.open(source).also { importer.match(it) }
        playlists.delete(importer.finish(first).playlistId)

        val again = importer.open(source).also { importer.match(it) }
        val result = importer.finish(again)
        assertFalse(result.updated)
        assertEquals(listOf(elephant), playlists.playlist(result.playlistId).first()!!.tracks)
    }

    @Test
    fun `discarding an import forgets it`() = runTest {
        val id = importer.open(ImportSource.PlaylistFile("Favourites.csv", csv))
        importer.discard(id)
        assertNull(importer.unfinished.first())
        assertNull(importer.job(id).first())
    }

    @Test
    fun `a source with no songs is worded as nothing to import`() = runTest {
        catalogue.playlists["PL0"] = Playlist("PL0", "Empty", "", Artwork(listOf(0)), emptyList())
        for (source in listOf(ImportSource.CataloguePlaylist("PL0"), ImportSource.PlaylistFile("blank.csv", "title,artist\n"))) {
            val failure = runCatching { importer.open(source) }.exceptionOrNull()
            assertEquals(ImportProblem.NOTHING_TO_IMPORT, (failure as ImportFailedException).problem)
        }
        assertNull(importer.unfinished.first())
    }
}
