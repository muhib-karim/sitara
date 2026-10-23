package app.sitara.music

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings → Sound → Automix: a switch that turns the setting on and off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AutomixSettingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun automixIsOffUntilTurnedOn() {
        val settings = mutableStateOf(SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true))
        compose.setContent {
            SitaraShell(
                settings = settings.value,
                home = HomeFeed(),
                library = LibraryState.Loading,
                query = "",
                searchFilter = SearchFilter.SONGS,
                search = SearchState.Idle,
                player = PlayerUiState(),
                actions = PlayerActions.None,
                details = SampleDetailPages(FakeMusicSource()),
                onQueryChange = {},
                onSearchFilterChange = {},
                onRetrySearch = {},
                onPlay = { _, _ -> },
                onShuffle = {},
                onSettingsChange = { settings.value = it },
                initialSettingsOpen = true,
            )
        }
        compose.onNodeWithTag("settings-content").performScrollToNode(hasText("Automix"))
        val row = compose.onNodeWithText("Automix")
        compose.onNodeWithText("Automix", useUnmergedTree = false).assertIsOff()
        row.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(settings.value.automix)
        compose.onNodeWithText("Automix").assertIsOn()
    }
}
