package app.sitara.music

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.LibraryLayout
import app.sitara.music.core.model.LocalFolder
import app.sitara.music.core.model.LocalSong
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.localTrackId
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.LibraryActions
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.library.LocalActions
import app.sitara.music.feature.library.LocalLibraryState
import app.sitara.music.feature.library.SavedLibrary
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Library → On this phone and songs on the phone in Search, through the shell with a fixed local index. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LocalInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val tree = "content://com.android.externalstorage.documents/tree/primary%3AMusic"

    private fun song(n: Int, title: String, album: String, track: Int?, added: Long, folder: String? = null) = LocalSong(
        Track(localTrackId("content://media/external/audio/media/$n"), title, "Tara Lane", album, 61_000, Artwork(listOf(0xFF3A2A55.toInt(), 0xFFE0A030.toInt()))),
        folder, "Music/$album/$title.mp3", track, added,
    )

    private val songs = listOf(
        song(1, "Second Light", "Orbit", track = 2, added = 30),
        song(2, "Night Bus", "City", track = 1, added = 20, folder = tree),
        song(3, "Saffron Morning", "Orbit", track = 1, added = 10),
    )
    private val played = mutableListOf<List<String>>()
    private val calls = mutableListOf<String>()
    private val settings = mutableStateOf(SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true, libraryLayout = LibraryLayout.LIST))
    private val actions = object : LocalActions {
        override fun addFolder(uri: String) { calls += "add $uri" }
        override fun relinkFolder(folder: LocalFolder, uri: String) { calls += "relink ${folder.name} $uri" }
        override fun removeFolder(folder: LocalFolder) { calls += "remove ${folder.name}" }
        override fun rescan() { calls += "rescan" }
    }
    private val remembering = object : LibraryActions by LibraryActions.None {
        override fun setLibraryLayout(layout: LibraryLayout) { settings.value = settings.value.copy(libraryLayout = layout) }
    }

    private fun render(
        local: LocalLibraryState,
        route: DetailRoute? = DetailRoute.OnThisPhone,
        tab: Tab = Tab.LIBRARY,
        query: String = "",
        search: SearchState = SearchState.Idle,
    ) {
        compose.setContent {
            SitaraShell(
                settings = settings.value,
                home = HomeFeed(),
                library = LibraryState.Ready(SavedLibrary(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())),
                query = query,
                searchFilter = SearchFilter.SONGS,
                search = search,
                player = PlayerUiState(),
                actions = PlayerActions.None,
                details = SampleDetailPages(FakeMusicSource()),
                onQueryChange = {},
                onSearchFilterChange = {},
                onRetrySearch = {},
                onPlay = { tracks, index -> played += tracks.drop(index).map { it.title } },
                onShuffle = {},
                onSettingsChange = {},
                initialTab = tab,
                initialDetail = route,
                libraryActions = remembering,
                local = local,
                localActions = actions,
            )
        }
    }

    private val page get() = compose.onNodeWithTag("local-content")

    private fun tap(text: String, list: String = "local-content") {
        compose.onNodeWithTag(list).performScrollToNode(hasText(text))
        compose.onAllNodesWithText(text)[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test fun libraryCountsTheSongsOnThePhoneAndOpensThem() {
        render(LocalLibraryState(songs, musicAccess = true), route = null)
        compose.onNodeWithTag("library-content").performScrollToNode(hasText("3 songs"))
        tap("On this phone", list = "library-content")
        page.performScrollToNode(hasText("Saffron Morning"))
        assertEquals("opening the page reads the phone again", "rescan", calls.first())
    }

    @Test fun songsPlayNewestFirstFromTheOneTapped() {
        render(LocalLibraryState(songs, musicAccess = true))
        tap("Night Bus")
        assertEquals(listOf(listOf("Night Bus", "Saffron Morning")), played)
    }

    @Test fun anAlbumOpensWithItsSongsInTrackOrder() {
        render(LocalLibraryState(songs, musicAccess = true))
        compose.onNode(hasText("Albums") and isSelectable()).performSemanticsAction(SemanticsActions.OnClick)
        tap("Orbit")
        compose.onNodeWithTag("local-group").performScrollToNode(hasText("Second Light"))
        compose.onNodeWithText("Play").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(listOf("Saffron Morning", "Second Light")), played)
    }

    @Test fun withoutAccessAndWithoutSongsBothWaysInAreOffered() {
        render(LocalLibraryState(musicAccess = false))
        compose.onNodeWithText("Play the music on your phone").assertIsDisplayed()
        compose.onNodeWithText("Allow access").assertIsDisplayed()
        tap("Choose a folder")
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, shadowOf(compose.activity).nextStartedActivity?.action)
    }

    @Test fun withAccessAndNoSongsTheEmptyStateSaysNoneWereFound() {
        render(LocalLibraryState(musicAccess = true))
        compose.onNodeWithText("No music found on this phone").assertIsDisplayed()
        assertThrows(AssertionError::class.java) { compose.onNodeWithText("Allow access").assertIsDisplayed() }
        compose.onNodeWithText("Choose a folder").assertIsDisplayed()
    }

    @Test fun aRevokedFolderKeepsItsSongsAndOffersRelink() {
        render(LocalLibraryState(songs, listOf(LocalFolder(tree, "Music", songCount = 1, linked = false)), musicAccess = true))
        page.performScrollToNode(hasText("Night Bus"))
        page.performScrollToNode(hasText("Sitara can no longer read this folder · 1 song kept"))
        tap("Relink folder")
        val picker = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, picker?.action)
    }

    @Test fun aPickedFolderCanBeRemoved() {
        render(LocalLibraryState(songs, listOf(LocalFolder(tree, "Music", songCount = 1, linked = true)), musicAccess = true))
        page.performScrollToNode(hasText("Picked folders"))
        compose.onNodeWithContentDescription("Remove Music and its songs from Sitara").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("remove Music", calls.last())
    }

    @Test fun searchListsMatchingSongsOnThePhoneWithABadgeEvenOffline() {
        render(LocalLibraryState(songs, musicAccess = true), route = null, tab = Tab.SEARCH, query = "orbit", search = SearchState.Failed("orbit"))
        val results = compose.onNodeWithTag("search-content")
        results.performScrollToNode(hasText("Saffron Morning"))
        results.performScrollToNode(hasText("Search isn’t working right now. Check your connection, then try again."))
        assertEquals(2, compose.onAllNodesWithText("On this phone", useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onAllNodesWithText("Second Light")[0].performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(listOf("Second Light", "Saffron Morning")), played)
    }

    @Test fun aSongOnThePhoneHasNoDownloadOrShareInItsMenu() {
        render(LocalLibraryState(songs, musicAccess = true))
        page.performScrollToNode(hasText("Night Bus"))
        compose.onNodeWithContentDescription("More options for Night Bus").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Add to playlist").assertIsDisplayed()
        assertThrows(AssertionError::class.java) { compose.onNodeWithText("Download").assertIsDisplayed() }
        // It has no link either.
        assertThrows(AssertionError::class.java) { compose.onNodeWithText("Share").assertIsDisplayed() }
    }

    @Test fun searchWithoutMatchesOnThePhoneShowsCatalogueResultsOnly() {
        val results = SearchResults("bus", emptyList(), emptyList(), emptyList())
        render(LocalLibraryState(songs.take(1), musicAccess = true), route = null, tab = Tab.SEARCH, query = "bus", search = SearchState.Loaded("bus", results))
        compose.onNodeWithTag("search-content").performScrollToNode(hasText("No songs found for “bus”."))
        assertEquals(0, compose.onAllNodesWithText("On this phone", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
