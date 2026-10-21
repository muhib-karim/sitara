package app.sitara.music.core.player

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Listen
import app.sitara.music.core.model.Track
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningTrackerTest {
    private val time = TestTimeSource()
    private var wallMs = 1_000_000L
    private val listens = mutableListOf<Listen>()
    private val starts = mutableListOf<Track>()
    private val tracker = ListeningTracker(time, { wallMs }, listens::add, starts::add)

    private fun entry(uid: Long, durationMs: Long = 200_000) = QueueEntry(uid, Track("t$uid", "Song $uid", "Artist", "Album", durationMs, Artwork(listOf(0))))

    private val song = entry(1)

    @Test
    fun `a play counts after 30 seconds heard`() {
        tracker.update(song, playing = true)
        time += 29.seconds
        tracker.checkpoint()
        assertEquals(emptyList<Listen>(), listens)

        time += 1.seconds
        tracker.checkpoint()
        assertEquals(listOf(Listen(song.track, 1_000_000, 30_000)), listens)
    }

    @Test
    fun `a short song counts after half of it`() {
        val short = entry(2, durationMs = 40_000)
        tracker.update(short, playing = true)
        time += 20.seconds
        tracker.checkpoint()
        assertEquals(listOf(Listen(short.track, 1_000_000, 20_000)), listens)
    }

    @Test
    fun `a song skipped before it counts is not a play`() {
        tracker.update(song, playing = true)
        time += 10.seconds
        tracker.update(entry(2), playing = true)
        assertEquals(emptyList<Listen>(), listens)
    }

    @Test
    fun `paused time is not listening`() {
        tracker.update(song, playing = true)
        time += 20.seconds
        tracker.update(song, playing = false)
        time += 5_000.seconds
        tracker.update(song, playing = true)
        time += 5.seconds
        tracker.checkpoint()
        assertEquals("25 s heard does not count yet", emptyList<Listen>(), listens)

        time += 5.seconds
        tracker.update(song, playing = false)
        assertEquals(listOf(Listen(song.track, 1_000_000, 30_000)), listens)
    }

    @Test
    fun `a seek adds only what is then played`() {
        // The player seeks without pausing; heard time is time spent playing, wherever in the song.
        tracker.update(song, playing = true)
        time += 10.seconds
        tracker.update(song, playing = true)
        time += 20.seconds
        tracker.checkpoint()
        assertEquals(listOf(Listen(song.track, 1_000_000, 30_000)), listens)
    }

    @Test
    fun `the play starts when the song first plays, not when it is queued`() {
        tracker.update(song, playing = false)
        wallMs = 2_000_000
        tracker.update(song, playing = true)
        time += 31.seconds
        tracker.update(entry(2), playing = true)
        assertEquals(listOf(Listen(song.track, 2_000_000, 31_000)), listens)
    }

    @Test
    fun `later checkpoints report the same play with more time heard`() {
        tracker.update(song, playing = true)
        time += 30.seconds
        tracker.checkpoint()
        time += 15.seconds
        tracker.update(null, playing = false)
        assertEquals(listOf(30_000L, 45_000L), listens.map { it.listenedMs })
        assertEquals("one play, reported twice", setOf(1_000_000L), listens.map { it.startedAtMs }.toSet())
    }

    @Test
    fun `a checkpoint with nothing more heard reports nothing new`() {
        tracker.update(song, playing = true)
        time += 30.seconds
        tracker.update(song, playing = false)
        tracker.checkpoint()
        tracker.update(null, playing = false)
        assertEquals(listOf(Listen(song.track, 1_000_000, 30_000)), listens)
    }

    @Test
    fun `starting the same entry over is a new play`() {
        tracker.update(song, playing = true)
        time += 40.seconds
        wallMs = 1_040_000
        tracker.startOver(song.uid)
        tracker.update(song, playing = true)
        time += 30.seconds
        tracker.checkpoint()
        assertEquals(listOf(Listen(song.track, 1_000_000, 40_000), Listen(song.track, 1_040_000, 30_000)), listens)
    }

    @Test
    fun `moving to another entry is not starting over`() {
        tracker.update(song, playing = true)
        time += 40.seconds
        tracker.startOver(uid = 2)
        time += 10.seconds
        tracker.update(song, playing = false)
        assertEquals("one play, heard for 50 s", listOf(Listen(song.track, 1_000_000, 50_000)), listens)
    }

    @Test
    fun `a play is announced when it first plays, not again after a pause`() {
        tracker.update(song, playing = false)
        assertEquals("queued is not playing", emptyList<Track>(), starts)

        tracker.update(song, playing = true)
        tracker.update(song, playing = false)
        tracker.update(song, playing = true)
        assertEquals(listOf(song.track), starts)
    }

    @Test
    fun `each new play is announced, including the same entry started over`() {
        tracker.update(song, playing = true)
        tracker.startOver(song.uid)
        tracker.update(song, playing = true)
        tracker.update(entry(2), playing = true)
        assertEquals(listOf(song.track, song.track, entry(2).track), starts)
    }
}
