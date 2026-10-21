package app.sitara.music.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.assertHasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.assertHasSendBroadcastClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasText
import androidx.media3.session.MediaButtonReceiver
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.MainActivity
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Track
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the widget draws and what each control does, as TalkBack and a tap reach them. */
@RunWith(RobolectricTestRunner::class)
class WidgetContentTest {
    private val song = Track("id1", "Song 1", "Artist 1", "Album", 200_000, Artwork(listOf(0xFF336699.toInt())))
    private val colors = widgetColors(song.artwork, cover = null)

    private fun GlanceAppWidgetUnitTest.show(state: WidgetState, tall: Boolean) {
        setContext(ApplicationProvider.getApplicationContext())
        setAppWidgetSize(if (tall) DpSize(300.dp, 140.dp) else DpSize(300.dp, 64.dp))
        provideComposable { SitaraWidgetContent(state, cover = null, colors = colors) }
    }

    @Test
    fun `the tall widget names every control, and each one acts`() = runGlanceAppWidgetUnitTest {
        show(WidgetState.NowPlaying(song, playing = true, liked = false, failed = false, positionMs = 50_000, durationMs = 200_000), tall = true)
        onNode(hasText("Song 1")).assertExists()
        onNode(hasText("Artist 1")).assertExists()
        onNode(hasContentDescription("Previous song")).assertHasRunCallbackClickAction<SkipPrevious>()
        onNode(hasContentDescription("Pause")).assertHasRunCallbackClickAction<TogglePlayback>()
        onNode(hasContentDescription("Next song")).assertHasRunCallbackClickAction<SkipNext>()
        onNode(hasContentDescription("Like")).assertHasRunCallbackClickAction<ToggleLike>()
        onNode(hasStartActivityClickAction<MainActivity>(actionParametersOf(OpenPlayer to true))).assertExists()
    }

    @Test
    fun `a liked, paused song offers to play and to unlike`() = runGlanceAppWidgetUnitTest {
        show(WidgetState.NowPlaying(song, playing = false, liked = true, failed = false, positionMs = 0, durationMs = 200_000), tall = true)
        onNode(hasContentDescription("Play")).assertHasRunCallbackClickAction<TogglePlayback>()
        onNode(hasContentDescription("Remove from liked songs")).assertHasRunCallbackClickAction<ToggleLike>()
    }

    @Test
    fun `the one-row widget keeps the transport and leaves the heart to the tall one`() = runGlanceAppWidgetUnitTest {
        show(WidgetState.NowPlaying(song, playing = true, liked = false, failed = false, positionMs = 0, durationMs = 200_000), tall = false)
        onNode(hasContentDescription("Pause")).assertHasRunCallbackClickAction<TogglePlayback>()
        onNode(hasContentDescription("Next song")).assertExists()
        onNode(hasContentDescription("Like")).assertDoesNotExist()
    }

    @Test
    fun `a song that could not be played says so and offers to try again`() = runGlanceAppWidgetUnitTest {
        show(WidgetState.NowPlaying(song, playing = false, liked = false, failed = true, positionMs = 0, durationMs = 200_000), tall = true)
        onNode(hasText("Couldn’t play this song")).assertExists()
        onNode(hasContentDescription("Try again")).assertHasRunCallbackClickAction<TogglePlayback>()
    }

    @Test
    fun `idle with a saved queue, Play something resumes it`() = runGlanceAppWidgetUnitTest {
        show(WidgetState.Idle(canResume = true), tall = true)
        onNode(hasText("Play something")).assertHasSendBroadcastClickAction<MediaButtonReceiver>()
    }

    @Test
    fun `idle with nothing to resume, Play something opens Home`() = runGlanceAppWidgetUnitTest {
        show(WidgetState.Idle(canResume = false), tall = false)
        onNode(hasText("Play something")).assertExists()
        onNode(hasStartActivityClickAction<MainActivity>()).assertExists()
        onNode(hasContentDescription("Pause")).assertDoesNotExist()
    }
}
