package app.sitara.music

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.width
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Predictive back on Now Playing: the stage follows the gesture, cancelling restores it, letting go collapses it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PlayerBackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var back: OnBackPressedDispatcher

    private fun render(reduced: Boolean = false) {
        compose.setContent {
            back = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            SitaraShell(
                settings = SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = reduced),
                home = HomeFeed(),
                library = LibraryState.Loading,
                query = "",
                searchFilter = SearchFilter.SONGS,
                search = SearchState.Idle,
                player = PlayerUiState(track = SampleCatalog.tracks.first(), durationMs = 200_000),
                actions = PlayerActions.None,
                details = SampleDetailPages(FakeMusicSource()),
                onQueryChange = {},
                onSearchFilterChange = {},
                onRetrySearch = {},
                onPlay = { _, _ -> },
                onShuffle = {},
                onSettingsChange = {},
                playerExpanded = true,
            )
        }
        compose.waitForIdle()
    }

    private fun event(progress: Float) = BackEventCompat(0f, 400f, progress, BackEventCompat.EDGE_LEFT)

    private fun stageWidth() = compose.onNodeWithTag("full-player").getBoundsInRoot().width.value

    @Test fun theStageShrinksWithTheGestureAndCancellingRestoresIt() {
        render()
        val full = stageWidth()
        compose.runOnUiThread {
            back.dispatchOnBackStarted(event(0f))
            back.dispatchOnBackProgressed(event(0.5f))
        }
        compose.waitForIdle()
        assertEquals("half a gesture shrinks the stage by 5 %", full * 0.95f, stageWidth(), full * 0.01f)

        compose.runOnUiThread { back.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(full, stageWidth(), 0.5f)
        compose.onNodeWithTag("full-player").assertIsDisplayed()
    }

    @Test fun lettingGoCollapsesToTheMiniPlayer() {
        render()
        compose.runOnUiThread {
            back.dispatchOnBackStarted(event(0f))
            back.dispatchOnBackProgressed(event(0.8f))
            back.onBackPressed()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("full-player").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open player").assertIsDisplayed()
    }

    @Test fun theStageLeavesFromWhereTheGestureLeftIt() {
        render()
        val full = stageWidth()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            back.dispatchOnBackStarted(event(0f))
            back.dispatchOnBackProgressed(event(1f))
            back.onBackPressed()
        }
        compose.mainClock.advanceTimeByFrame()
        // Still on screen as it fades out, and still shrunk: no snap back to full size.
        assertTrue("width ${stageWidth()} of $full", stageWidth() < full * 0.95f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun withReducedMotionTheStageKeepsItsSize() {
        render(reduced = true)
        val full = stageWidth()
        compose.runOnUiThread {
            back.dispatchOnBackStarted(event(0f))
            back.dispatchOnBackProgressed(event(0.5f))
        }
        compose.waitForIdle()
        assertTrue(kotlin.math.abs(stageWidth() - full) < 0.5f)
    }
}
