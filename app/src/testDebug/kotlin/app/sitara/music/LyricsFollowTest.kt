package app.sitara.music

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.SitaraTheme
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.feature.player.LyricsPage
import app.sitara.music.feature.player.LyricsState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LyricsFollowTest {
    @get:Rule val compose = createComposeRule()

    private val lyrics = runBlocking { FakeMusicSource().lyrics(SampleCatalog.tracks.first())!! }

    // Starts on the last line, so the list has followed it down and a hand scroll goes back up to the first lines.
    private var position by mutableLongStateOf(lyrics.lines.last().startMs)
    private val seeks = mutableListOf<Long>()

    private fun render() = compose.setContent {
        SitaraTheme(SitaraSettings(reducedMotion = true)) {
            LyricsPage(LyricsState.Found(lyrics), emptyList(), position, Color.White, { seeks += it }, {}, Modifier.height(320.dp).testTag("lyrics"))
        }
    }

    private fun scrollOffset() = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun scrollBackToTheStartByHand() {
        compose.onNodeWithTag("lyrics").performTouchInput { swipeDown() }
        compose.waitForIdle()
    }

    @Test
    fun followingLyricsShowNoReturnControl() {
        render()
        assertTrue(scrollOffset() > 0f)
        compose.onNodeWithText("Back to current line").assertDoesNotExist()
    }

    @Test
    fun aHandScrollStopsTheListFollowingTheSong() {
        render()
        scrollBackToTheStartByHand()
        compose.onNodeWithText("Back to current line").assertIsDisplayed()
        val browsed = scrollOffset()

        position = lyrics.lines[6].startMs
        compose.waitForIdle()

        assertEquals(browsed, scrollOffset(), 0.5f)
    }

    @Test
    fun backToCurrentLineFollowsTheSongAgain() {
        render()
        scrollBackToTheStartByHand()
        val browsed = scrollOffset()

        compose.onNodeWithText("Back to current line").performClick()

        compose.onNodeWithText("Back to current line").assertDoesNotExist()
        compose.onNodeWithText(lyrics.lines.last().text).assertIsDisplayed()
        assertTrue(scrollOffset() > browsed)
    }

    @Test
    fun tappingALineWhileBrowsingSeeksAndKeepsTheListWhereItIs() {
        render()
        scrollBackToTheStartByHand()
        val browsed = scrollOffset()

        compose.onNodeWithText(lyrics.lines[1].text).performClick()

        assertEquals(listOf(lyrics.lines[1].startMs), seeks)
        assertEquals(browsed, scrollOffset(), 0.5f)
        compose.onNodeWithText("Back to current line").assertIsDisplayed()
    }
}
