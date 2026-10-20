package app.sitara.music.source.lyrics

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LyricsQueryTest {
    private fun query(title: String, artist: String) = lyricsQuery(Track("id", title, artist, "", 200_000, Artwork(listOf(0))))

    // The block ends on its last row: a Kotlin raw string keeps a trailing whitespace line, which would be one more (empty) row.
    @ParameterizedTest(name = "{0} by {1} -> {2} by {3}")
    @CsvSource(
        delimiter = '|',
        textBlock = """
        expectations (feat. Chappell Roan) (Official Music Video) | Orchid Lane         | expectations          | Orchid Lane
        Paper Lanterns (Official Video)                           | Orchid Lane         | Paper Lanterns        | Orchid Lane
        Paper Lanterns (Official Audio)                           | Orchid Lane - Topic | Paper Lanterns        | Orchid Lane
        Paper Lanterns (Lyric Video)                              | Orchid Lane         | Paper Lanterns        | Orchid Lane
        Paper Lanterns (Lyrics)                                   | Orchid Lane         | Paper Lanterns        | Orchid Lane
        Paper Lanterns [Lyrics]                                   | Orchid Lane         | Paper Lanterns        | Orchid Lane
        Paper Lanterns (Visualizer)                               | Orchid Lane         | Paper Lanterns        | Orchid Lane
        Paper Lanterns (Audio)                                    | Orchid Lane         | Paper Lanterns        | Orchid Lane
        Paper Lanterns [HD]                                       | Orchid Lane         | Paper Lanterns        | Orchid Lane
        Paper Lanterns (4K Remaster)                              | Orchid Lane         | Paper Lanterns        | Orchid Lane
        PAPER LANTERNS (OFFICIAL MUSIC VIDEO)                     | Orchid Lane         | PAPER LANTERNS        | Orchid Lane
        Paper Lanterns ft. Kite                                   | Orchid Lane         | Paper Lanterns        | Orchid Lane
        Paper Lanterns feat. Kite & Moth (Official Video)         | Orchid Lane         | Paper Lanterns        | Orchid Lane
        Paper Lanterns [feat. Kite]                               | Orchid Lane         | Paper Lanterns        | Orchid Lane
        Paper Lanterns (featuring Kite)                           | Orchid Lane         | Paper Lanterns        | Orchid Lane
        Orchid Lane ft. Kite - Paper Lanterns (Official Video)    | Paper Records       | Paper Lanterns        | Orchid Lane
        Orchid Lane - Paper Lanterns ft. Kite                     | OrchidLaneVEVO      | Paper Lanterns        | Orchid Lane
        Paper Lanterns (Acoustic) (Official Audio)                | Orchid Lane - Topic | Paper Lanterns (Acoustic) | Orchid Lane
        Left Behind                                               | Orchid Lane         | Left Behind           | Orchid Lane
        Soft Light                                                | Orchid Lane         | Soft Light            | Orchid Lane""",
    )
    fun `real-world video titles become the song and its artist`(title: String, artist: String, song: String, songArtist: String) {
        assertEquals(LyricsQuery(song, songArtist), query(title, artist))
    }

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
        assertEquals("Paper Lanterns", query("Paper Lanterns (feat. Kite) | Official Video", "Orchid Lane").title)
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

    @Test
    fun `a Topic channel name is the artist without Topic`() {
        assertEquals("Orchid Lane", query("Paper Lanterns", "Orchid Lane - Topic").artist)
    }
}
