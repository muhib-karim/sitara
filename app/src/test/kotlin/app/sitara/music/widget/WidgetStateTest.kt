package app.sitara.music.widget

import app.sitara.music.core.designsystem.ArtworkPalette
import app.sitara.music.core.designsystem.CoverColors
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import app.sitara.music.core.player.PlaybackSnapshot
import app.sitara.music.core.player.QueueState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetStateTest {
    private val songs = (1..2).map { Track("id$it", "Song $it", "Artist $it", "Album", 200_000, Artwork(listOf(0xFF336699.toInt()))) }
    private val queue = QueueState().setQueue(songs)

    private fun snapshot(playing: Boolean = true, positionMs: Long = 50_000, durationMs: Long = 200_000, failed: Boolean = false) =
        PlaybackSnapshot(queue, playing, positionMs, durationMs, failedUid = queue.currentEntry!!.uid.takeIf { failed })

    @Test
    fun `with nothing loaded the widget is idle and says whether there is a queue to resume`() {
        assertEquals(WidgetState.Idle(canResume = true), widgetState(null, liked = false, canResume = true))
        assertEquals(WidgetState.Idle(canResume = false), widgetState(PlaybackSnapshot(), liked = false, canResume = false))
    }

    @Test
    fun `a playing song shows as playing with how far it has got`() {
        assertEquals(
            WidgetState.NowPlaying(songs[0], playing = true, liked = true, failed = false, positionMs = 50_000, durationMs = 200_000),
            widgetState(snapshot(), liked = true, canResume = false),
        )
        assertEquals(0.25f, (widgetState(snapshot(), liked = true, canResume = false) as WidgetState.NowPlaying).progress)
    }

    @Test
    fun `a paused song shows as paused`() {
        val state = widgetState(snapshot(playing = false), liked = false, canResume = true) as WidgetState.NowPlaying
        assertEquals(false, state.playing)
        assertEquals(songs[0], state.track)
    }

    @Test
    fun `a song that could not be played shows as failed`() {
        val state = widgetState(snapshot(playing = false, failed = true), liked = false, canResume = false) as WidgetState.NowPlaying
        assertTrue(state.failed)
    }

    @Test
    fun `progress stays between the start and the end, and is zero while the length is unknown`() {
        assertEquals(0f, (widgetState(snapshot(durationMs = 0), false, false) as WidgetState.NowPlaying).progress)
        assertEquals(1f, (widgetState(snapshot(positionMs = 300_000), false, false) as WidgetState.NowPlaying).progress)
    }

    @Test
    fun `the widget redraws when what it shows changes, moves its progress every 15 s while playing, and never while paused`() = runTest {
        val states = MutableStateFlow(widgetState(snapshot(playing = false), liked = false, canResume = false))
        var refreshes = 0
        // The engine reads the player's position again when refreshed.
        val refresh = {
            refreshes++
            val now = states.value as WidgetState.NowPlaying
            states.value = now.copy(positionMs = now.positionMs + 15_000)
        }
        val shown = mutableListOf<WidgetState>()
        val job = launch { shownWidgetStates(states, refresh).collect { shown += it } }
        runCurrent()
        assertEquals(1, shown.size)

        advanceTimeBy(10 * 60_000)
        states.value = widgetState(snapshot(playing = false, positionMs = 55_000), liked = false, canResume = false)
        runCurrent()
        assertEquals("paused: no ticks, and a few seconds' move draws nothing", 1, shown.size)
        assertEquals(0, refreshes)

        states.value = widgetState(snapshot(playing = true), liked = false, canResume = false)
        runCurrent()
        assertEquals(2, shown.size)
        advanceTimeBy(15_001)
        assertEquals(1, refreshes)
        assertEquals("the progress line moves after 15 s of playing", 3, shown.size)
        assertEquals(65_000L, (shown.last() as WidgetState.NowPlaying).positionMs)

        states.value = (states.value as WidgetState.NowPlaying).copy(liked = true)
        runCurrent()
        assertEquals("liking the song redraws at once", 4, shown.size)

        states.value = (states.value as WidgetState.NowPlaying).copy(playing = false)
        runCurrent()
        val drawn = shown.size
        advanceTimeBy(10 * 60_000)
        assertEquals("pausing stops the ticks", drawn, shown.size)
        assertEquals(1, refreshes)
        job.cancel()
    }

    @Test
    fun `cover colours give text, buttons and the progress line contrast on the widget's background`() {
        val covers = listOf(0xFFF2A33A, 0xFF3B6FD8, 0xFFFFFFFF, 0xFF000000, 0xFF777777, 0xFFE53935, 0xFF2E7D32, 0xFFFFEB3B).map { it.toInt() }
        covers.forEach { main ->
            listOf(null, CoverColors(listOf(main, 0xFF00BCD4.toInt()), main)).forEach { cover ->
                val colors = widgetColors(Artwork(listOf(main)), cover)
                assertTrue("text on ${main.toString(16)}", ArtworkPalette.contrast(colors.content, colors.background) >= 4.5)
                assertTrue("play icon on ${main.toString(16)}", ArtworkPalette.contrast(colors.onAccent, colors.accent) >= 4.5)
                assertTrue("play button against the background on ${main.toString(16)}", ArtworkPalette.contrast(colors.accent, colors.background) >= 3.0)
            }
        }
    }
}
