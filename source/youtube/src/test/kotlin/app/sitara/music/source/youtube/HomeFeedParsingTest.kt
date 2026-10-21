package app.sitara.music.source.youtube

import app.sitara.music.core.model.HomeItem
import app.sitara.music.core.model.HomePage
import app.sitara.music.core.model.ShelfLayout
import app.sitara.music.core.model.ShelfLink
import com.grack.nanojson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * YouTube Music Home responses recorded signed out (GB, October 2026) and trimmed to a few items per shelf. Signed out,
 * YouTube sends no artist, video, chart or moods shelves, so home_constructed.json builds those from recorded items.
 */
class HomeFeedParsingTest {
    private fun page(name: String): HomePage = parseHomePage(JsonParser.`object`().from(javaClass.getResourceAsStream("/home/$name.json")))

    @Test
    fun `the first page has the mood chips, Quick picks as song rows and playlists as a carousel`() {
        val home = page("home_page")

        assertEquals(
            listOf("Podcasts", "Sleep", "Relax", "Energize", "Workout", "Romance", "Sad", "Commute", "Feel good", "Party", "Focus"),
            home.chips.map { it.title },
        )
        assertTrue(home.chips.none { it.selected })
        assertTrue(home.chips.all { it.params.isNotEmpty() })
        // The taste-builder shelf asks a signed-in listener to pick artists; it has nothing to play, so it is left out.
        assertEquals(listOf("Quick picks", "Trending community playlists"), home.shelves.map { it.title })
        assertTrue(home.continuation!!.startsWith("4qmFsgKhAhIMRkVtdXNpY19ob21l"))
    }

    @Test
    fun `a Quick picks row is a song with its artist, album and a full-size cover`() {
        val quickPicks = page("home_page").shelves.first()

        assertEquals(ShelfLayout.SONG_ROWS, quickPicks.layout)
        assertEquals(8, quickPicks.items.size)
        val song = (quickPicks.items.first() as HomeItem.Song).track
        assertEquals("xukbqwRuN5w", song.id)
        assertEquals("Choosin' Texas", song.title)
        assertEquals("Ella Langley", song.artist)
        assertEquals("UCecnyZYofHiBVDJpx1XNYOQ", song.artistId)
        assertEquals("Dandelion", song.album)
        assertTrue(song.artwork.imageUrl!!.endsWith("=w544-h544-l90-rj"))
        // Its header button plays the shelf as a radio, which is not a page; the rows already play.
        assertNull(quickPicks.more)
    }

    @Test
    fun `a playlist card opens the playlist without its VL browse prefix and shows the line under it`() {
        val shelf = page("home_page").shelves[1]

        assertEquals(ShelfLayout.CAROUSEL, shelf.layout)
        val playlist = (shelf.items.first() as HomeItem.PlaylistCard).playlist
        assertEquals("PLkpaQgbH2nStd0401Ul7-ffbGc-equxdB", playlist.id)
        assertEquals("Throwback 2000s Hits", playlist.title)
        assertEquals("Brando • 18M views", playlist.curator)
        assertTrue(playlist.tracks.isEmpty())
    }

    @Test
    fun `a continuation page brings more shelves and its own continuation, without chips`() {
        val more = page("home_continuation")

        assertTrue(more.chips.isEmpty())
        assertEquals(listOf("Throwbacks", "New & trending songs", "New releases"), more.shelves.map { it.title })
        assertEquals("RDCLAK5uy_lMzHW51iFg1Kx0d_2EHpzbOgCrwtu8cgI", more.shelves.first().items.first().id)
        assertTrue(more.continuation!!.startsWith("4qmFsgKhAhIMRkVtdXNp"))
    }

    @Test
    fun `an album card is the album's OLAK playlist, credited to its artists without the release type`() {
        val releases = page("home_continuation").shelves.last()

        val albums = releases.items.map { (it as HomeItem.AlbumCard).album }
        assertEquals("OLAK5uy_kioY0jae--XsahLl0CB9u4cxDr8k2_n1o", albums[0].id)
        assertEquals("Unloveu", albums[0].title)
        assertEquals("KATSEYE", albums[0].artist)
        assertEquals("UCX9kfYB9t0tnd6DYUC2iuKg", albums[0].artistId)
        assertEquals("Los Dareyes De La Sierra & Raúl Castro", albums[2].artist)
        // "More" leads to the new-releases page, which the app has no screen for.
        assertNull(releases.more)
    }

    @Test
    fun `the last page has no continuation and drops shelves of podcasts`() {
        val last = page("home_last_page")

        assertEquals(listOf("Trending songs for you"), last.shelves.map { it.title })
        assertNull(last.continuation)
        val song = (last.shelves.single().items.first() as HomeItem.Song).track
        assertEquals("Ai6LZlMCtNE", song.id)
        assertEquals("Horror Skunx", song.artist)
        assertEquals("The Aura Farmer", song.album)
    }

    @Test
    fun `a page filtered by a chip marks that chip selected`() {
        val relax = page("home_relax")

        assertEquals(listOf("Relax"), relax.chips.filter { it.selected }.map { it.title })
        assertEquals(listOf("Peaceful bedtime", "Mindfulness & meditation", "Chilled"), relax.shelves.map { it.title })
    }

    @Test
    fun `artist cards, video cards and More links to artists and playlists are kept, mood buttons are dropped`() {
        val shelves = page("home_constructed").shelves

        assertEquals(listOf("Recommended artists", "Music videos for you"), shelves.map { it.title })
        val artists = shelves[0].items.map { (it as HomeItem.ArtistCard).artist }
        assertEquals(listOf("UCecnyZYofHiBVDJpx1XNYOQ", "UCoIOOL7QKuBhQHVKL8y7BEQ"), artists.map { it.id })
        assertEquals(listOf("Ella Langley", "Michael Jackson"), artists.map { it.name })
        assertEquals(ShelfLink.ToArtist("UCecnyZYofHiBVDJpx1XNYOQ"), shelves[0].more)

        val videos = shelves[1]
        assertEquals(ShelfLayout.CAROUSEL, videos.layout)
        val first = (videos.items.first() as HomeItem.Song).track
        assertEquals(listOf("ZMGNAvNs_20", "CLSuAhT71yU"), videos.items.map { it.id })
        assertEquals("Ayra Starr", first.artist)
        assertEquals("UCO4TFKYiSDo7vOQC04mr7Fg", first.artistId)
        assertEquals(ShelfLink.ToPlaylist("PLkpaQgbH2nStd0401Ul7-ffbGc-equxdB"), videos.more)
    }
}
