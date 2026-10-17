package app.sitara.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.ThemeMode
import app.sitara.music.core.model.Track
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class BrowseInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val source = FakeMusicSource()
    private val home = runBlocking { source.home() }
    private val library = runBlocking { source.library() }

    private fun render(
        tab: Tab = Tab.HOME,
        fontScale: Float = 1f,
        onPlay: (List<Track>, Int) -> Unit = { _, _ -> },
        onQuery: (String) -> Unit = {},
        onSettings: (SitaraSettings) -> Unit = {},
        libraryState: Library = library,
    ): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            var settings by remember { mutableStateOf(SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true)) }
            var query by remember { mutableStateOf("") }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SitaraShell(
                    settings, home, libraryState, query, SearchResults.empty(query),
                    PlayerUiState(track = SampleCatalog.tracks.first()), PlayerActions.None,
                    onQueryChange = { query = it; onQuery(it) }, onPlay = onPlay,
                    onSettingsChange = { settings = it; onSettings(it) }, initialTab = tab,
                )
            }
        }
        return restoration
    }

    @Test fun libraryLayoutSurvivesFilterChangesAndRestoration() {
        val restoration = render(Tab.LIBRARY)
        compose.onNodeWithTag("library-grid").assertExists()
        compose.onNodeWithContentDescription("Show list").performClick()
        compose.onNodeWithTag("library-list").assertExists()
        compose.onNodeWithText("Albums").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("library-list").assertExists()
        compose.onNodeWithText("Albums").assertIsSelected()
        compose.onNode(hasText("Home") and hasAnyAncestor(hasTestTag("playback-dock"))).performClick()
        compose.onNode(hasText("Library") and hasAnyAncestor(hasTestTag("playback-dock"))).performClick()
        compose.onNodeWithTag("library-list").assertExists()
        compose.onNodeWithText("Albums").assertIsSelected()
        compose.onNodeWithContentDescription("Show grid").performClick()
        compose.onNodeWithTag("library-grid").assertExists()
    }

    @Test fun likedTileOpensSongsAndPlaysTheChosenTrack() {
        var queue = emptyList<Track>()
        var index = -1
        render(Tab.LIBRARY, onPlay = { tracks, selected -> queue = tracks; index = selected })
        compose.onNodeWithContentDescription("Open liked songs").performClick()
        compose.onNodeWithContentDescription("Show list").performClick()
        val track = library.likedTracks.first()
        compose.onNodeWithContentDescription("${track.title} by ${track.artist}").performClick()
        assertEquals(library.likedTracks, queue)
        assertEquals(0, index)
    }

    @Test fun emptyPlaylistHasNoPlaybackActionInEitherLayout() {
        val empty = library.playlists.first().copy(tracks = emptyList())
        render(Tab.LIBRARY, libraryState = library.copy(playlists = listOf(empty)))
        compose.onNodeWithText(empty.title).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Show list").performClick()
        compose.onNodeWithText(empty.title).assertIsNotEnabled()
    }

    @Test fun albumAndArtistEntriesOpenHeadersAndPlayTheirOwnTracks() {
        var queue = emptyList<Track>()
        render(Tab.LIBRARY, onPlay = { tracks, _ -> queue = tracks })
        compose.onNodeWithText("Albums").performClick()
        compose.onNodeWithText(library.albums.first().title).performClick()
        compose.onNodeWithText("Play album").performClick()
        assertEquals(library.albums.first().tracks, queue)
        compose.onNodeWithContentDescription("Back to browse").performClick()
        compose.onNodeWithText("Artists").performClick()
        compose.onNode(hasText(library.artists.first().name) and hasAnyAncestor(hasTestTag("library-grid"))).performClick()
        compose.onNodeWithText("Play artist").performClick()
        assertTrue(queue.isNotEmpty())
        assertTrue(queue.all { it.artist == library.artists.first().name })
    }

    @Test fun quickPickSecondPageUsesTheOriginalQueueIndex() {
        var index = -1
        var queue = emptyList<Track>()
        render(onPlay = { tracks, selected -> queue = tracks; index = selected })
        compose.onNodeWithTag("quick-picks").performTouchInput { swipeLeft() }
        val picks = home.first { it.id == "quick" }.tracks
        compose.onNodeWithContentDescription("${picks[4].title} by ${picks[4].artist}").performClick()
        assertEquals(picks, queue)
        assertEquals(4, index)
    }

    @Test fun searchCategoriesAndSuggestionsAreActions() {
        var query = ""
        render(Tab.SEARCH, onQuery = { query = it })
        compose.onNodeWithText("Albums").performClick()
        compose.onNodeWithText(library.albums.first().title).performClick()
        compose.onNodeWithText("Play album").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to browse").performClick()
        compose.onNodeWithContentDescription("All browse categories").performClick()
        compose.onNodeWithTag("search-content").performScrollToIndex(4)
        compose.onNodeWithText("Night Bus Static").performClick()
        assertEquals("Night Bus Static", query)
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals("", query)
    }

    @Test fun settingsOnlyExposeImplementedControls() {
        var updated: SitaraSettings? = null
        render(Tab.SETTINGS, onSettings = { updated = it })
        compose.onNodeWithTag("settings-content").performScrollToIndex(3)
        compose.onNodeWithText("Reduced motion").performClick()
        assertEquals(false, updated?.reducedMotion)
        listOf("Loudness normalisation", "Crossfade", "Coming in a future update").forEach { label ->
            assertThrows(AssertionError::class.java) {
                compose.onNodeWithTag("settings-content").performScrollToNode(hasText(label))
            }
        }
    }

    @Test fun wallpaperAccentUpdatesTheThemeOnSupportedAndroid() {
        var updated: SitaraSettings? = null
        render(Tab.SETTINGS, onSettings = { updated = it })
        compose.onNodeWithText("Wallpaper accent").performClick()
        assertEquals(ThemeMode.DYNAMIC, updated?.themeMode)
        compose.onNodeWithText("Wallpaper accent").assertIsSelected()
    }

    @Test
    @Config(sdk = [30])
    fun wallpaperAccentIsHiddenOnOlderAndroid() {
        render(Tab.SETTINGS)
        compose.onNodeWithText("From artwork").assertIsDisplayed()
        assertThrows(AssertionError::class.java) {
            compose.onNodeWithTag("settings-content").performScrollToNode(hasText("Wallpaper accent"))
        }
    }

    @Test fun lastHomeRailScrollsAboveTheDockAtLargeText() {
        render(fontScale = 2f)
        // Header, moods, and two items per populated shelf (heading + content).
        compose.onNodeWithTag("home-content").performScrollToIndex(2 + home.size * 2 - 1)
        val rail = compose.onNodeWithTag("rail-${home.last().id}").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("playback-dock").fetchSemanticsNode().boundsInRoot
        assertTrue("The final rail must clear the entire playback dock", rail.bottom <= dock.top)
    }
}
