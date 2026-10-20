package app.sitara.music.source.lyrics

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LyricsQueryTest {
    private fun query(title: String, artist: String) = lyricsQuery(Track("id", title, artist, "", 200_000, Artwork(listOf(0))))

    @Test
    fun `a clean song title is asked as it is`() {
        assertEquals(LyricsQuery("Paper Lanterns", "Orchid Lane"), query("Paper Lanterns", "Orchid Lane"))
    }

    @Test
    fun `video labels in brackets are dropped, other brackets kept`() {
        assertEquals("Paper Lanterns", query("Paper Lanterns (Official Music Video)", "Orchid Lane").title)
        assertEquals("Paper Lanterns", query("Paper Lanterns [Official Audio] (Lyrics)", "Orchid Lane").title)
        assertEquals("Paper Lanterns", query("Paper Lanterns (Visualizer) [4K]", "Orchid Lane").title)
        assertEquals("Paper Lanterns (Acoustic)", query("Paper Lanterns (Acoustic)", "Orchid Lane").title)
        assertEquals("Paper Lanterns (feat. Kite)", query("Paper Lanterns (feat. Kite) | Official Video", "Orchid Lane").title)
    }

    @Test
    fun `an artist prefix in the title moves to the artist`() {
        assertEquals(LyricsQuery("Paper Lanterns", "Orchid Lane"), query("Orchid Lane - Paper Lanterns (Official Video)", "Orchid Lane"))
        assertEquals(LyricsQuery("Paper Lanterns", "Orchid Lane"), query("Orchid Lane - Paper Lanterns", "OrchidLaneVEVO"))
        assertEquals(LyricsQuery("Paper Lanterns", "Orchid Lane"), query("Orchid Lane – Paper Lanterns", "Paper Records"))
    }

    @Test
    fun `a version after the dash stays in the title`() {
        assertEquals(LyricsQuery("Paper Lanterns - Remastered 2011", "Orchid Lane"), query("Paper Lanterns - Remastered 2011", "Orchid Lane"))
        assertEquals(LyricsQuery("Paper Lanterns - Live at the Harbour", "Orchid Lane"), query("Paper Lanterns - Live at the Harbour", "Orchid Lane"))
    }

    @Test
    fun `a VEVO channel name is the artist without VEVO`() {
        assertEquals("Orchid Lane", query("Paper Lanterns", "Orchid LaneVEVO").artist)
    }
}
