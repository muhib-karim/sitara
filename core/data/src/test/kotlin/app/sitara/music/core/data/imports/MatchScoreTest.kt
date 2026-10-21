package app.sitara.music.core.data.imports

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.SourceSong
import app.sitara.music.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchScoreTest {
    private fun track(title: String, artist: String, durationMs: Long = 0) = Track("id", title, artist, "", durationMs, Artwork(listOf(0)))

    private fun score(title: String, artist: String, durationMs: Long? = null, candidate: Track) =
        matchScore(SourceSong(title = title, artist = artist, durationMs = durationMs), candidate)

    @Test
    fun `the same song is a confident match`() {
        assertEquals(1f, score("Bohemian Rhapsody", "Queen", 354_000, track("Bohemian Rhapsody", "Queen", 355_000)), 0.001f)
    }

    @Test
    fun `case, accents, punctuation, brackets, featured artists and remaster notes do not count`() {
        assertEquals(1f, score("Beyoncé - Halo", "", candidate = track("beyonce halo", "")), 0.001f)
        assertEquals(1f, score("Get Lucky (feat. Pharrell Williams)", "Daft Punk", candidate = track("Get Lucky [Official Audio]", "Daft Punk")), 0.001f)
        assertEquals(1f, score("Let It Be - Remastered 2009", "The Beatles", candidate = track("Let It Be", "Beatles")), 0.001f)
        assertEquals(1f, score("One More Time", "Daft Punk", candidate = track("One More Time", "Daft Punk - Topic")), 0.001f)
    }

    @Test
    fun `a video title that leads with the artist still matches`() {
        assertTrue(score("Elephant", "Tame Impala", candidate = track("Tame Impala - Elephant (Official Video)", "TameImpalaVEVO")) >= CONFIDENT)
    }

    @Test
    fun `one shared artist among several is enough`() {
        assertTrue(score("Under Pressure", "Queen & David Bowie", candidate = track("Under Pressure", "Queen")) >= CONFIDENT)
        assertTrue(score("Under Pressure", "Queen", candidate = track("Under Pressure", "David Bowie, Queen")) >= CONFIDENT)
    }

    @Test
    fun `the same title by someone else needs a look`() {
        val cover = score("Hallelujah", "Leonard Cohen", candidate = track("Hallelujah", "Jeff Buckley"))
        assertTrue(cover >= PLAUSIBLE && cover < CONFIDENT)
    }

    @Test
    fun `another song by the same artist is not a match`() {
        assertTrue(score("Bohemian Rhapsody", "Queen", candidate = track("Radio Ga Ga", "Queen")) < PLAUSIBLE)
    }

    @Test
    fun `a very different length lowers the score`() {
        val near = score("Song", "Band", 200_000, track("Song", "Band", 201_000))
        val far = score("Song", "Band", 200_000, track("Song", "Band", 600_000))
        assertTrue(far < near)
    }

    @Test
    fun `scripts other than Latin are compared as written`() {
        assertEquals(1f, score("নিয়ে যাবে কি", "Xefer", candidate = track("নিয়ে যাবে কি", "Xefer")), 0.001f)
        assertTrue(score("নিয়ে যাবে কি", "Xefer", candidate = track("আমার সোনার বাংলা", "Xefer")) < PLAUSIBLE)
    }
}
