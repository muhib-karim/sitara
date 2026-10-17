package app.sitara.music

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PlayerInteractionTest {
    @get:Rule val compose = createComposeRule()

    private fun render(expanded: Boolean = false, page: Int = 0, actions: PlayerActions = PlayerActions.None) {
        val source = FakeMusicSource()
        val track = SampleCatalog.tracks.first()
        val home = runBlocking { source.home() }
        val library = runBlocking { source.library() }
        val lyrics = runBlocking { source.lyrics(track) }
        compose.setContent {
            SitaraShell(
                settings = SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true),
                home = home,
                library = library,
                query = "",
                results = SearchResults.empty(),
                player = PlayerUiState(track = track, durationMs = 100_000, lyrics = lyrics, positionMs = 15_300),
                actions = actions,
                onQueryChange = {},
                onPlay = { _, _ -> },
                onSettingsChange = {},
                playerExpanded = expanded,
                playerPage = page,
                greetingText = "Good evening",
            )
        }
    }

    @Test
    fun dockOpensPlayerAndCollapseRestoresNavigation() {
        render()
        compose.onNodeWithContentDescription("Open player").performClick()
        compose.onNodeWithContentDescription("Collapse player").assertIsDisplayed().performClick()
        compose.onNodeWithText("Home").assertIsSelected()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun dockSwipeSkipsAndOpensPlayer() {
        var next = 0
        render(actions = object : PlayerActions by PlayerActions.None {
            override fun next() { next++ }
        })
        compose.onNodeWithContentDescription("Open player").performTouchInput { swipeLeft() }
        assertEquals(1, next)
        compose.onNodeWithContentDescription("Open player").performTouchInput { swipeUp(startY = bottom, endY = -100f) }
        compose.onNodeWithContentDescription("Collapse player").assertIsDisplayed()
    }

    @Test
    fun seekHasAccessibleProgressAction() {
        var seek = -1L
        render(expanded = true, actions = object : PlayerActions by PlayerActions.None {
            override fun seekTo(positionMs: Long) { seek = positionMs }
        })
        compose.onNodeWithContentDescription("Seek").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(50_000L, seek)
    }

    @Test
    fun timedLyricSeeksAndTransportWorks() {
        var seek = -1L
        var plays = 0
        render(expanded = true, page = 1, actions = object : PlayerActions by PlayerActions.None {
            override fun seekTo(positionMs: Long) { seek = positionMs }
            override fun togglePlay() { plays++ }
        })
        val line = runBlocking { FakeMusicSource().lyrics(SampleCatalog.tracks.first()) }.lines.last { it.startMs <= 15_300 }
        compose.onNodeWithText(line.text).performClick()
        assertEquals(line.startMs, seek)
        compose.onNodeWithContentDescription("Play").performClick()
        assertEquals(1, plays)
    }
}
