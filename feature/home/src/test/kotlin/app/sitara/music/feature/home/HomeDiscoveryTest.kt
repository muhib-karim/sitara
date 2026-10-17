package app.sitara.music.feature.home

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.ListeningMood
import app.sitara.music.core.model.Playlist
import app.sitara.music.core.model.Shelf
import app.sitara.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDiscoveryTest {
    private val art = Artwork(listOf(0xFF101010.toInt()))
    private val tracks = (0..5).map { Track("$it", "Song $it", "Artist", "Album", 1_000, art) }

    @Test fun pagesKeepOrderAndIncludeThePartialLastPage() {
        assertEquals(listOf(tracks.take(4), tracks.drop(4)), quickPickPages(tracks))
        assertTrue(quickPickPages(emptyList()).isEmpty())
    }

    @Test fun moodUsesCuratedMembershipAndDropsUnrelatedShelves() {
        val focus = Playlist("focus", "Focus", "", art, tracks.take(2), setOf(ListeningMood.FOCUS))
        val shelves = listOf(Shelf("quick", "Quick picks", tracks = tracks), Shelf("mixes", "Your mixes", playlists = listOf(focus)))
        assertEquals(shelves, shelvesForMood(shelves, null))
        val filtered = shelvesForMood(shelves, ListeningMood.FOCUS)
        assertEquals(tracks.take(2), filtered.first().tracks)
        assertEquals(listOf(focus), filtered.last().playlists)
        assertTrue(shelvesForMood(shelves, ListeningMood.UPLIFT).isEmpty())
    }
}
