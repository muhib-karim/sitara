package app.sitara.music

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.Track
import app.sitara.music.feature.search.SearchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedRequestTest {
    private val view = "android.intent.action.VIEW"
    private val send = "android.intent.action.SEND"
    private val acceptance = SharedRequest("https://music.youtube.com/watch?v=x7kGuhlXmFI", playVideoId = "x7kGuhlXmFI")

    @Test
    fun openedLinksInEveryShapePlayTheAcceptanceTrack() {
        assertEquals(acceptance, sharedRequest(view, "https://music.youtube.com/watch?v=x7kGuhlXmFI&si=AbC123xyz", null))
        assertEquals(acceptance, sharedRequest(view, "https://www.youtube.com/watch?v=x7kGuhlXmFI", null))
        assertEquals(acceptance, sharedRequest(view, "https://youtu.be/x7kGuhlXmFI?si=AbC123xyz", null))
    }

    @Test
    fun sharedTextContainingALinkPlaysIt() {
        assertEquals(acceptance, sharedRequest(send, null, "Niye Jabe Ki by Xefer https://youtu.be/x7kGuhlXmFI"))
    }

    @Test
    fun sharedPlainTextBecomesASearch() {
        assertEquals(SharedRequest("Niye Jabe Ki Xefer"), sharedRequest(send, null, "  Niye Jabe Ki Xefer \n"))
    }

    @Test
    fun overlongSharedTextIsCutToASearchLength() {
        assertEquals(200, sharedRequest(send, null, "a".repeat(5_000))!!.query.length)
    }

    @Test
    fun anythingElseIsIgnored() {
        assertNull(sharedRequest(view, "https://example.com/watch?v=x7kGuhlXmFI", null))
        assertNull(sharedRequest(view, null, null))
        assertNull(sharedRequest(send, null, "   "))
        assertNull(sharedRequest(send, null, null))
        assertNull(sharedRequest("android.intent.action.MAIN", "https://youtu.be/x7kGuhlXmFI", null))
    }

    private fun song(id: String) = Track(id, "Niye Jabe Ki", "Xefer", "", 201_000, Artwork(listOf(0)))

    @Test
    fun autoplayWaitsForTheResolvedLinkedSong() {
        val loaded = SearchState.Loaded(acceptance.query, SearchResults(acceptance.query, listOf(song("x7kGuhlXmFI")), emptyList(), emptyList()))

        assertEquals(song("x7kGuhlXmFI"), autoplayTrack(loaded, acceptance))
        assertNull(autoplayTrack(SearchState.Loading(acceptance.query), acceptance))
        assertNull(autoplayTrack(SearchState.Failed(acceptance.query), acceptance))
    }

    @Test
    fun autoplayIgnoresResultsForAnotherQueryOrSong() {
        val other = SearchState.Loaded("jazz", SearchResults("jazz", listOf(song("x7kGuhlXmFI")), emptyList(), emptyList()))
        val wrongSong = SearchState.Loaded(acceptance.query, SearchResults(acceptance.query, listOf(song("dQw4w9WgXcQ")), emptyList(), emptyList()))

        assertNull(autoplayTrack(other, acceptance))
        assertNull(autoplayTrack(wrongSong, acceptance))
        assertNull(autoplayTrack(other, SharedRequest("jazz")))
    }

    @Test
    fun openedOrSharedPlaylistLinksGoToImportAsACleanLink() {
        val clean = "https://music.youtube.com/playlist?list=PL123abc"
        assertEquals(clean, sharedPlaylistLink(view, "https://www.youtube.com/playlist?list=PL123abc&si=AbC", null))
        assertEquals(clean, sharedPlaylistLink(send, null, "My road trip mix https://music.youtube.com/playlist?list=PL123abc&si=AbC"))
    }

    @Test
    fun sharedSpotifyPlaylistsAndAlbumsGoToImportAsACleanLink() {
        assertEquals(
            "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
            sharedPlaylistLink(send, null, "Listen to this on Spotify https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"),
        )
        assertEquals("https://open.spotify.com/album/4aawyAB9vmqN3uQ7FjRGTy", sharedPlaylistLink(send, null, "https://open.spotify.com/album/4aawyAB9vmqN3uQ7FjRGTy"))
        assertNull(sharedPlaylistLink(send, null, "https://open.spotify.com/track/6OmhkSOpvYBokMKQxpIGx2"))
    }

    @Test
    fun aSongPlayedFromAPlaylistStillPlaysTheSong() {
        assertNull(sharedPlaylistLink(view, "https://www.youtube.com/watch?v=x7kGuhlXmFI&list=PL123abc", null))
        assertNull(sharedPlaylistLink(send, null, "Niye Jabe Ki Xefer"))
        assertNull(sharedPlaylistLink("android.intent.action.EDIT", "https://music.youtube.com/playlist?list=PL123abc", null))
    }
}
