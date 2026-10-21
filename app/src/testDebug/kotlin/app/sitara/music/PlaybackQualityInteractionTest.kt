package app.sitara.music

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import app.sitara.music.core.model.AudioQuality
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.StreamFormat
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PlaybackQualityInteractionTest {
    @get:Rule val compose = createComposeRule()

    private fun render(player: PlayerUiState = PlayerUiState(), expanded: Boolean = false, onSettings: (SitaraSettings) -> Unit = {}) {
        compose.setContent {
            SitaraShell(
                settings = SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true),
                home = HomeFeed(),
                library = LibraryState.Loading,
                query = "",
                searchFilter = SearchFilter.SONGS,
                search = SearchState.Idle,
                player = player,
                actions = PlayerActions.None,
                details = SampleDetailPages(FakeMusicSource()),
                onQueryChange = {},
                onSearchFilterChange = {},
                onRetrySearch = {},
                onPlay = { _, _ -> },
                onShuffle = {},
                onSettingsChange = onSettings,
                initialSettingsOpen = !expanded,
                playerExpanded = expanded,
            )
        }
    }

    @Test fun eachNetworkGetsItsOwnQuality() {
        val changes = mutableListOf<SitaraSettings>()
        render(onSettings = { changes += it })
        compose.onNodeWithTag("settings-content").performScrollToNode(hasText("Quality on mobile data"))
        compose.onNodeWithText("Quality on mobile data").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Data saver").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(AudioQuality.DATA_SAVER, changes.single().mobileQuality)
        assertEquals(AudioQuality.AUTO, changes.single().wifiQuality)
    }

    @Test fun nowPlayingNamesTheStreamForTalkBack() {
        render(PlayerUiState(track = SampleCatalog.tracks.first(), durationMs = 200_000, stream = StreamFormat("Opus", 160)), expanded = true)
        compose.onNodeWithText("Opus · 160 kbps").assertContentDescriptionEquals("Streaming Opus at 160 kilobits per second")
    }

    @Test fun nowPlayingShowsNoReadoutWhenTheStreamIsUnknown() {
        render(PlayerUiState(track = SampleCatalog.tracks.first(), durationMs = 200_000, stream = StreamFormat(null, 160)), expanded = true)
        compose.onNodeWithContentDescription("Streaming", substring = true).assertDoesNotExist()
    }
}
