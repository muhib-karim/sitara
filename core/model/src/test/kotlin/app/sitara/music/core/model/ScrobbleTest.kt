package app.sitara.music.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScrobbleTest {
    @Test
    fun `a song scrobbles once half of it is heard`() {
        assertFalse(scrobbleCounts(durationMs = 200_000, heardMs = 99_999))
        assertTrue(scrobbleCounts(durationMs = 200_000, heardMs = 100_000))
    }

    @Test
    fun `a long song scrobbles after four minutes, however long it is`() {
        assertFalse(scrobbleCounts(durationMs = 20 * 60_000, heardMs = 239_999))
        assertTrue(scrobbleCounts(durationMs = 20 * 60_000, heardMs = 240_000))
    }

    @Test
    fun `a song of 30 seconds or less never scrobbles`() {
        assertFalse(scrobbleCounts(durationMs = 30_000, heardMs = 30_000))
        assertTrue(scrobbleCounts(durationMs = 30_001, heardMs = 15_001))
    }

    @Test
    fun `a song of unknown length never scrobbles`() {
        assertFalse(scrobbleCounts(durationMs = 0, heardMs = 600_000))
    }

    @Test
    fun `a scrobble keeps the play's song and start`() {
        val track = Track("x7kGuhlXmFI", "Paper Lanterns", "Orchid Lane", "Harbour Lights", 214_000, Artwork(listOf(0)))
        assertEquals(
            Scrobble("x7kGuhlXmFI", "Paper Lanterns", "Orchid Lane", "Harbour Lights", 214_000, startedAtMs = 1_700_000_000_000),
            Listen(track, 1_700_000_000_000, 120_000).toScrobble(),
        )
    }

    private val song = Track("t1", "Paper Lanterns", "Orchid Lane", "", 200_000, Artwork(listOf(0)))
    private val heard = Listen(song, 1_000, 100_000)
    private val listenBrainz = ScrobbleAccount(ScrobbleService.LISTENBRAINZ, "orchid")
    private val lastFm = ScrobbleAccount(ScrobbleService.LASTFM, "orchid", libraryOnly = true)

    @Test
    fun `a play that counts goes to every connected service`() {
        assertEquals(
            setOf(ScrobbleService.LISTENBRAINZ, ScrobbleService.LASTFM),
            scrobbleTargets(heard, listOf(listenBrainz, lastFm), paused = false, inLibrary = true),
        )
    }

    @Test
    fun `a service set to library songs only skips songs outside the library`() {
        assertEquals(setOf(ScrobbleService.LISTENBRAINZ), scrobbleTargets(heard, listOf(listenBrainz, lastFm), paused = false, inLibrary = false))
    }

    @Test
    fun `a service waiting for a new sign-in still collects plays`() {
        val ended = listenBrainz.copy(signInNeeded = true)
        assertEquals(setOf(ScrobbleService.LISTENBRAINZ), scrobbleTargets(heard, listOf(ended), paused = false, inLibrary = false))
    }

    @Test
    fun `nothing is scrobbled while paused, or before the play counts`() {
        assertEquals(emptySet<ScrobbleService>(), scrobbleTargets(heard, listOf(listenBrainz), paused = true, inLibrary = true))
        assertEquals(emptySet<ScrobbleService>(), scrobbleTargets(heard.copy(listenedMs = 99_000), listOf(listenBrainz), paused = false, inLibrary = true))
    }
}
