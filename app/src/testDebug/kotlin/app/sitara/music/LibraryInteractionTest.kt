package app.sitara.music

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Artist
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.LibraryLayout
import app.sitara.music.core.model.LibrarySort
import app.sitara.music.core.model.PlayCount
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.SongDownload
import app.sitara.music.core.model.Track
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.LibraryActions
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.library.SavedLibrary
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
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
class LibraryInteractionTest {
    @get:Rule val compose = createComposeRule()

    private fun art(color: Long) = Artwork(listOf(color.toInt()))
    private fun song(id: String, title: String) = Track(id, title, "Orchid Lane", "Paper Lanterns", 200_000, art(0xFF5B6CFF), artistId = "UC1")

    // Liked newest first: Cold Light, Amber Road, Blue Hour. Each order puts them differently.
    private val songs = listOf(song("s3", "Cold Light"), song("s1", "Amber Road"), song("s2", "Blue Hour"))
    private val saved = SavedLibrary(
        liked = songs,
        recent = emptyList(),
        playlists = listOf(
            PlaylistSummary(1, "Road trip", 5, listOf(0xFFE8A33D, 0xFF6FBF8E, 0xFFD9668B, 0xFF4BB8C7).map(::art)),
            PlaylistSummary(2, "Weekend", 1, listOf(art(0xFF8C7FD9))),
        ),
        albums = listOf(Album("al1", "Paper Lanterns", "Orchid Lane", 2024, art(0xFFC7894B), emptyList())),
        artists = listOf(Artist("UC1", "Orchid Lane", art(0xFF7FA8D9))),
        counts = listOf(PlayCount("s1", "UC1", lastPlayedAt = 500, plays = 1), PlayCount("s3", "UC1", 400, 3), PlayCount("s2", "UC1", 300, 9)),
    )
    private val settings = mutableStateOf(SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true))
    private val remembering = object : LibraryActions by LibraryActions.None {
        override fun setLibraryLayout(layout: LibraryLayout) { settings.value = settings.value.copy(libraryLayout = layout) }
        override fun setLibrarySort(sort: LibrarySort) { settings.value = settings.value.copy(librarySort = sort) }
    }

    private fun render(library: LibraryState = LibraryState.Ready(saved), downloads: List<SongDownload> = emptyList(), onPlay: (List<Track>, Int) -> Unit = { _, _ -> }) {
        compose.setContent {
            SitaraShell(
                settings = settings.value,
                home = HomeFeed(),
                library = library,
                query = "",
                searchFilter = SearchFilter.SONGS,
                search = SearchState.Idle,
                player = PlayerUiState(),
                actions = PlayerActions.None,
                details = SampleDetailPages(FakeMusicSource()),
                onQueryChange = {},
                onSearchFilterChange = {},
                onRetrySearch = {},
                onPlay = onPlay,
                onShuffle = {},
                onSettingsChange = {},
                initialTab = Tab.LIBRARY,
                libraryActions = remembering,
                downloads = downloads,
            )
        }
    }

    private val list get() = compose.onNodeWithTag("library-content")

    private fun kind(label: String) = compose.onNode(hasText(label) and isSelectable()).performSemanticsAction(SemanticsActions.OnClick)

    private fun shows(text: String) = list.performScrollToNode(hasText(text))

    private fun hides(text: String) = assertThrows(AssertionError::class.java) { list.performScrollToNode(hasText(text)) }

    private fun order(sort: String) {
        compose.onNodeWithContentDescription("Order: ", substring = true).performClick()
        compose.onNode(hasText(sort) and isSelectable()).performClick()
        compose.waitForIdle()
    }

    private fun top(title: String) = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y

    private fun assertOrder(vararg titles: String) {
        val tops = titles.map(::top)
        assertEquals("rows in the order ${titles.toList()}", tops.sorted(), tops)
    }

    @Test fun eachKindShowsItsOwnCollection() {
        render(downloads = Fixtures.downloads)
        shows("Road trip")
        hides("Paper Lanterns")

        kind("Albums")
        shows("Paper Lanterns")
        hides("Road trip")

        kind("Artists")
        shows("Orchid Lane")
        hides("Road trip")

        kind("Songs")
        shows("Amber Road")
        hides("Road trip")

        kind("Downloads")
        shows(Fixtures.downloads.first().track.title)
        hides("Amber Road")
    }

    @Test fun everyOrderOfferedForSongsChangesTheirOrderAndIsRemembered() {
        settings.value = settings.value.copy(libraryLayout = LibraryLayout.LIST)
        render()
        kind("Songs")
        assertOrder("Cold Light", "Amber Road", "Blue Hour")

        order("A to Z")
        assertEquals(LibrarySort.A_TO_Z, settings.value.librarySort)
        assertOrder("Amber Road", "Blue Hour", "Cold Light")

        order("Most played")
        assertOrder("Blue Hour", "Cold Light", "Amber Road")

        order("Recently played")
        assertOrder("Amber Road", "Cold Light", "Blue Hour")

        order("Recently added")
        assertOrder("Cold Light", "Amber Road", "Blue Hour")
    }

    @Test fun playlistsOrderByNameOrNewestAndOfferNoPlays() {
        settings.value = settings.value.copy(libraryLayout = LibraryLayout.LIST)
        render()
        assertOrder("Weekend", "Road trip")

        compose.onNodeWithContentDescription("Order: Recently added").performClick()
        compose.onNodeWithText("Recently played").assertDoesNotExist()
        compose.onNodeWithText("Most played").assertDoesNotExist()
        compose.onNode(hasText("A to Z") and isSelectable()).performClick()
        compose.waitForIdle()
        assertOrder("Road trip", "Weekend")
    }

    @Test fun downloadsAreRowsWithNoOrderOrLayoutToChoose() {
        render(downloads = Fixtures.downloads)
        kind("Downloads")

        compose.onNodeWithContentDescription("Order: ", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Show as a list").assertDoesNotExist()
    }

    @Test fun gridAndListSwitchAndTheChoiceIsKept() {
        render()
        compose.onAllNodesWithTag("library-row").assertCountEquals(0)
        assertTrue(compose.onAllNodesWithTag("library-tile").fetchSemanticsNodes().isNotEmpty())

        compose.onNodeWithContentDescription("Show as a list").performClick()

        assertEquals(LibraryLayout.LIST, settings.value.libraryLayout)
        compose.onAllNodesWithTag("library-tile").assertCountEquals(0)
        compose.onAllNodesWithTag("library-row").assertCountEquals(2)
        compose.onNodeWithContentDescription("Show as a grid").assertIsDisplayed()
    }

    @Test fun onlyAPlaylistWithFourDifferentCoversShowsACollage() {
        render()
        shows("Weekend")

        compose.onAllNodesWithTag("collage", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun pinnedTilesCountTheirSongsAndDownloadsOpensItsList() {
        render(downloads = Fixtures.downloads)

        compose.onNodeWithText("3 songs").assertIsDisplayed()
        compose.onNodeWithText("${Fixtures.downloads.size} songs").performClick()

        compose.onNode(hasText("Downloads") and isSelectable()).assertIsSelected()
    }

    @Test fun recentlyPlayedPlaysFromTheSongTapped() {
        var played: Pair<List<Track>, Int>? = null
        render(LibraryState.Ready(saved.copy(recent = songs)), onPlay = { tracks, index -> played = tracks to index })

        compose.onNodeWithText("Recently played").assertIsDisplayed()
        compose.onNodeWithTag("rail-recent").performScrollToNode(hasText("Blue Hour"))
        compose.onNodeWithText("Blue Hour").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(songs to 2, played)
    }

    @Test fun whileLoadingLibraryIsOneAnnouncementInItsOwnLayout() {
        render(LibraryState.Loading)

        compose.onNodeWithContentDescription("Loading your library").assertExists()
    }
}
