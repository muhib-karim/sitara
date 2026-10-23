package app.sitara.music

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.DetailState
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A long press on an album in Search opens its preview: songs as they load, Play, Open, and a worded failure. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PreviewInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val played = mutableListOf<List<String>>()

    private fun render(page: DetailState) {
        val results = SearchResults("lanterns", emptyList(), listOf(Fixtures.album), emptyList())
        compose.setContent {
            SitaraShell(
                settings = SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true),
                home = HomeFeed(),
                library = LibraryState.Loading,
                query = "lanterns",
                searchFilter = SearchFilter.ALBUMS,
                search = SearchState.Loaded("lanterns", results, filter = SearchFilter.ALBUMS),
                player = PlayerUiState(),
                actions = PlayerActions.None,
                details = FixedPage(page),
                onQueryChange = {},
                onSearchFilterChange = {},
                onRetrySearch = {},
                onPlay = { tracks: List<Track>, _ -> played += tracks.map { it.title } },
                onShuffle = {},
                onSettingsChange = {},
                initialTab = Tab.SEARCH,
            )
        }
    }

    private fun longPressAlbum() {
        compose.onNodeWithTag("search-content").performScrollToNode(hasText(Fixtures.album.title))
        compose.onNodeWithText(Fixtures.album.title).performSemanticsAction(SemanticsActions.OnLongClick)
        compose.waitForIdle()
    }

    @Test fun aLongPressPreviewsTheAlbumAndPlaysItInOrder() {
        render(DetailState.AlbumReady(Fixtures.album))
        longPressAlbum()
        compose.onNodeWithTag("collection-preview").assertIsDisplayed()
        compose.onNode(hasText(Fixtures.album.tracks[1].title) and hasAnyAncestor(hasTestTag("collection-preview"))).assertIsDisplayed()
        compose.onNodeWithText("Play").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(listOf(Fixtures.album.tracks.map { it.title }), played)
    }

    @Test fun openGoesToTheAlbumPage() {
        render(DetailState.AlbumReady(Fixtures.album))
        longPressAlbum()
        compose.onNodeWithText("Open").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag("detail-content").assertIsDisplayed()
    }

    @Test fun aPreviewThatCannotLoadSaysSoAndOffersTryAgain() {
        render(DetailState.Failed)
        longPressAlbum()
        compose.onNodeWithText("Couldn’t load the songs. Check your connection, then try again.").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        // The header is there before any song: the preview opens at once.
        compose.onNodeWithText("Open").assertIsDisplayed()
    }
}
