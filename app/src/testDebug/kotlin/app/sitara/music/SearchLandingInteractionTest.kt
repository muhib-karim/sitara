package app.sitara.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.MoodsState
import app.sitara.music.feature.search.SearchActions
import app.sitara.music.feature.search.SearchLanding
import app.sitara.music.feature.search.SearchState
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
class SearchLandingInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val source = FakeMusicSource()
    private val moods = MoodsState.Ready(runBlocking { source.moods() })

    private class RecordedActions : SearchActions {
        val calls = mutableListOf<String>()
        override fun saveSearch(query: String) { calls += "save $query" }
        override fun removeRecentSearch(query: String) { calls += "remove $query" }
        override fun clearRecentSearches() { calls += "clear" }
        override fun retryMoods() { calls += "retry moods" }
    }

    private fun render(
        landing: SearchLanding,
        actions: SearchActions,
        initialQuery: String = "",
        onQuery: (String) -> Unit = {},
        search: SearchState = SearchState.Idle,
    ) {
        compose.setContent {
            var query by remember { mutableStateOf(initialQuery) }
            SitaraShell(
                settings = SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true),
                home = HomeFeed(),
                library = LibraryState.Loading,
                query = query,
                searchFilter = SearchFilter.SONGS,
                search = search,
                player = PlayerUiState(track = SampleCatalog.tracks.first()),
                actions = PlayerActions.None,
                details = SampleDetailPages(source),
                onQueryChange = { query = it; onQuery(it) },
                onSearchFilterChange = {},
                onRetrySearch = {},
                onPlay = { _, _ -> },
                onShuffle = {},
                onSettingsChange = {},
                initialTab = Tab.SEARCH,
                searchLanding = landing,
                searchActions = actions,
            )
        }
    }

    @Test fun aRecentSearchSearchesAgainAndCanBeRemovedOnItsOwn() {
        val actions = RecordedActions()
        var query = ""
        render(SearchLanding(recent = listOf("lofi girl", "jazz piano"), moods = moods), actions, onQuery = { query = it })

        compose.onNodeWithText("Recent searches").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove lofi girl from recent searches").performClick()
        compose.onNodeWithText("jazz piano").performClick()

        assertEquals("jazz piano", query)
        assertEquals(listOf("remove lofi girl", "save jazz piano"), actions.calls)
    }

    @Test fun clearAllAsksFirstAndOnlyClearsWhenConfirmed() {
        val actions = RecordedActions()
        render(SearchLanding(recent = listOf("lofi girl"), moods = moods), actions)

        compose.onNodeWithText("Clear all").performClick()
        compose.onNodeWithText("Keep them").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<String>(), actions.calls)

        compose.onNodeWithText("Clear all").performClick()
        compose.onNodeWithText("Clear recent searches?").assertIsDisplayed()
        compose.onNodeWithText("Clear").performClick()
        compose.waitForIdle()
        assertEquals(listOf("clear"), actions.calls)
    }

    @Test fun withNoRecentSearchesOneActionStartsASearch() {
        render(SearchLanding(moods = moods), RecordedActions())

        compose.onNodeWithText("Your searches show up here").assertIsDisplayed()
        compose.onNodeWithText("Start a search").performClick()
        compose.onNodeWithContentDescription("Search").assertIsFocused()
    }

    @Test fun searchingFromTheKeyboardKeepsTheSearch() {
        val actions = RecordedActions()
        render(SearchLanding(moods = moods), actions, initialQuery = "  night drive ")

        compose.onNodeWithContentDescription("Search").performImeAction()

        assertEquals(listOf("save night drive"), actions.calls)
    }

    @Test fun aSuggestionSearchesForItAndIsKept() {
        val actions = RecordedActions()
        var query = ""
        render(SearchLanding(suggestions = listOf("Lumen Drift", "Lumen Drift live"), moods = moods), actions, initialQuery = "lum", onQuery = { query = it })

        compose.onNodeWithContentDescription("Search for Lumen Drift live").performClick()

        assertEquals("Lumen Drift live", query)
        assertEquals(listOf("save Lumen Drift live"), actions.calls)
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals("", query)
    }

    @Test fun openingAResultKeepsTheSearch() {
        val actions = RecordedActions()
        val playlist = SampleCatalog.playlists.first()
        render(
            SearchLanding(moods = moods), actions, initialQuery = "night ",
            search = SearchState.Loaded("night", SearchResults.empty("night").copy(playlists = listOf(playlist)), SearchFilter.SONGS),
        )

        compose.onNodeWithText(playlist.title).performClick()

        assertEquals(listOf("save night"), actions.calls)
    }

    @Test fun aMoodTileOpensItsPageAndBackReturnsToSearch() {
        render(SearchLanding(moods = moods), RecordedActions())

        compose.onNodeWithTag("search-content").performScrollToNode(hasText("Genres"))
        compose.onNodeWithText("Jazz").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Jazz mixes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Genres").assertIsDisplayed()
    }

    @Test fun moodsThatFailedOfferTryAgain() {
        val actions = RecordedActions()
        render(SearchLanding(recent = listOf("lofi girl"), moods = MoodsState.Failed), actions)

        compose.onNodeWithTag("search-content").performScrollToNode(hasText("Try again"))
        compose.onNodeWithText("Try again").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf("retry moods"), actions.calls)
    }

    @Test fun moodsWhileLoadingAreOneAnnouncementInTheRealLayout() {
        render(SearchLanding(moods = MoodsState.Loading), RecordedActions())

        compose.onNodeWithContentDescription("Loading moods and genres").assertExists()
    }
}
