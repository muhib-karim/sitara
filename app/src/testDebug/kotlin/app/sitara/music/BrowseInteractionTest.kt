package app.sitara.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Chart
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.DownloadState
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.SongDownload
import app.sitara.music.core.model.ThemeMode
import app.sitara.music.core.model.Track
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.home.TrendingState
import app.sitara.music.feature.library.DetailState
import app.sitara.music.feature.library.LibraryActions
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.library.SavedIds
import app.sitara.music.feature.library.SavedLibrary
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val opensArtist = SemanticsMatcher("opens the artist") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open artist" }
    private val source = FakeMusicSource()
    private val library = runBlocking { source.library() }
    private val chart = runBlocking { source.trending() }

    private fun render(
        tab: Tab = Tab.HOME,
        fontScale: Float = 1f,
        onPlay: (List<Track>, Int) -> Unit = { _, _ -> },
        onQuery: (String) -> Unit = {},
        onSettings: (SitaraSettings) -> Unit = {},
        saved: LibraryState = LibraryState.Ready(sampleSavedLibrary(library)),
        savedIds: SavedIds = SavedIds(),
        libraryActions: LibraryActions = LibraryActions.None,
        actions: PlayerActions = PlayerActions.None,
        initialQuery: String = "",
        search: SearchState = SearchState.Idle,
        onRetrySearch: () -> Unit = {},
        details: DetailPages = SampleDetailPages(source),
        onShuffle: (List<Track>) -> Unit = {},
        onFilter: (SearchFilter) -> Unit = {},
        initialDetail: DetailRoute? = null,
        home: HomeFeed = sampleHomeFeed(chart, (saved as? LibraryState.Ready)?.library ?: sampleSavedLibrary(library)),
        onRetryTrending: () -> Unit = {},
        downloads: List<SongDownload> = emptyList(),
        onRemoveAllDownloads: () -> Unit = {},
    ): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            var settings by remember { mutableStateOf(SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true)) }
            var query by remember { mutableStateOf(initialQuery) }
            var filter by remember { mutableStateOf(SearchFilter.SONGS) }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SitaraShell(
                    settings, home, saved, query, filter, search,
                    PlayerUiState(track = SampleCatalog.tracks.first()), actions, details,
                    onQueryChange = { query = it; onQuery(it) }, onSearchFilterChange = { filter = it; onFilter(it) },
                    onRetrySearch = onRetrySearch, onRetryTrending = onRetryTrending, onPlay = onPlay, onShuffle = onShuffle,
                    onSettingsChange = { settings = it; onSettings(it) }, initialTab = tab, initialDetail = initialDetail,
                    savedIds = savedIds, playlists = (saved as? LibraryState.Ready)?.library?.playlists.orEmpty(), libraryActions = libraryActions,
                    downloads = downloads, onRemoveAllDownloads = onRemoveAllDownloads,
                )
            }
        }
        return restoration
    }

    @Test fun libraryOpensLikedSongsPlaylistsAndAlbums() {
        var queue = emptyList<Track>()
        render(Tab.LIBRARY, onPlay = { tracks, _ -> queue = tracks })
        compose.onNodeWithText("Liked songs").performClick()
        val liked = library.likedTracks.first()
        compose.onNodeWithContentDescription("${liked.title} by ${liked.artist}").performClick()
        assertEquals(library.likedTracks, queue)
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onAllNodesWithText(library.playlists.first().title).onFirst().performClick()
        compose.onNodeWithText("Your playlist").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        val album = library.albums.first()
        compose.onNodeWithTag("library-content").performScrollToNode(hasText(album.title))
        // Scrolled just into view, the tile can sit under the floating dock, so tap it through its click action.
        compose.onNodeWithText(album.title).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Play all").performClick()
        assertEquals(album.tracks, queue)
    }

    @Test fun emptyLibraryExplainsHowToFillEachSection() {
        render(Tab.LIBRARY, saved = LibraryState.Ready(SavedLibrary(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())))
        compose.onNodeWithText("0 songs").assertIsDisplayed()
        listOf(
            "Make a playlist to keep songs in your own order.",
            "Songs you play show up here.",
            "Save an album from its page to keep it here.",
            "Save an artist from their page to keep them here.",
        ).forEach { compose.onNodeWithTag("library-content").performScrollToNode(hasText(it)) }
    }

    @Test fun newPlaylistIsNamedBeforeItIsCreated() {
        val created = mutableListOf<String>()
        render(Tab.LIBRARY, libraryActions = object : LibraryActions by LibraryActions.None {
            override fun createPlaylist(name: String, first: Track?) { created += name }
        })
        compose.onNodeWithText("New playlist").performClick()
        compose.onNodeWithText("Create").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).setTextUnfocused("  Road trip ")
        compose.onNodeWithText("Create").performClick()
        assertEquals(listOf("Road trip"), created)
    }

    @Test fun songMenuQueuesAndAddsToAPlaylist() {
        val queued = mutableListOf<Track>()
        val added = mutableListOf<Pair<PlaylistSummary, Track>>()
        val playlist = library.playlists.first()
        render(
            initialDetail = DetailRoute.Playlist(playlist.id),
            actions = object : PlayerActions by PlayerActions.None {
                override fun addToQueue(track: Track) { queued += track }
            },
            libraryActions = object : LibraryActions by LibraryActions.None {
                override fun addToPlaylist(playlist: PlaylistSummary, track: Track) { added += playlist to track }
            },
        )
        val track = playlist.tracks.first()
        compose.onNodeWithContentDescription("More options for ${track.title}").performClick()
        compose.onNodeWithText("Add to queue").performClick()
        assertEquals(listOf(track), queued)

        compose.onNodeWithContentDescription("More options for ${track.title}").performClick()
        compose.onNodeWithText("Add to playlist").performClick()
        val target = sampleSavedLibrary(library).playlists[1]
        compose.onNode(hasText(target.name) and hasAnyAncestor(hasTestTag("playlist-picker"))).performClick()
        assertEquals(listOf(target to track), added)
    }

    @Test fun songMenuDownloadsCancelsAndRemoves() {
        val calls = mutableListOf<String>()
        val (downloading, waiting, failed, downloaded) = Fixtures.downloads.map { it.track }
        val fresh = Fixtures.playlist.tracks.last()
        render(
            initialDetail = DetailRoute.Playlist(Fixtures.playlist.id),
            details = FixedPage(DetailState.PlaylistReady(Fixtures.playlist)),
            savedIds = SavedIds(downloads = Fixtures.downloadStates),
            libraryActions = object : LibraryActions by LibraryActions.None {
                override fun download(tracks: List<Track>) { calls += "download ${tracks.joinToString { it.id }}" }
                override fun removeDownload(trackId: String) { calls += "remove $trackId" }
            },
        )
        fun menu(track: Track, action: String) {
            compose.onNodeWithTag("detail-content").performScrollToNode(hasContentDescription("More options for ${track.title}"))
            compose.onNodeWithContentDescription("More options for ${track.title}").performSemanticsAction(SemanticsActions.OnClick)
            compose.onNodeWithText(action).performClick()
        }
        menu(fresh, "Download")
        menu(failed, "Download")
        menu(downloading, "Cancel download")
        menu(waiting, "Cancel download")
        menu(downloaded, "Remove download")
        assertEquals(
            listOf("download ${fresh.id}", "download ${failed.id}", "remove ${downloading.id}", "remove ${waiting.id}", "remove ${downloaded.id}"),
            calls,
        )
    }

    @Test fun downloadAllOffersOnlyWhatIsMissingAndBadgesDownloadedSongs() {
        val asked = mutableListOf<List<Track>>()
        val album = Fixtures.album
        val actions = object : LibraryActions by LibraryActions.None {
            override fun download(tracks: List<Track>) { asked += tracks }
        }
        var ids by mutableStateOf(SavedIds(downloads = Fixtures.downloadStates))
        compose.setContent {
            SitaraShell(
                SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true), HomeFeed(), LibraryState.Loading, "", SearchFilter.SONGS, SearchState.Idle,
                PlayerUiState(), PlayerActions.None, FixedPage(DetailState.AlbumReady(album)),
                onQueryChange = {}, onSearchFilterChange = {}, onRetrySearch = {}, onRetryTrending = {}, onPlay = { _, _ -> }, onShuffle = {},
                onSettingsChange = {}, initialDetail = DetailRoute.Album(album.id), savedIds = ids, libraryActions = actions,
            )
        }
        compose.onAllNodesWithContentDescription("Downloaded").assertCountEquals(1)
        compose.onNodeWithText("Download all").performClick()
        assertEquals(listOf(album.tracks), asked)

        ids = SavedIds(downloads = album.tracks.associate { it.id to DownloadState.DOWNLOADED })
        compose.onNodeWithText("Download all").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Downloaded").assertCountEquals(album.tracks.size)
    }

    @Test fun libraryDownloadsShowProgressAndCancelDeleteOrRetry() {
        val calls = mutableListOf<String>()
        val (downloading, waiting, failed, downloaded) = Fixtures.downloads.map { it.track }
        var queue = emptyList<Track>()
        render(
            Tab.LIBRARY,
            onPlay = { tracks, _ -> queue = tracks },
            downloads = Fixtures.downloads,
            libraryActions = object : LibraryActions by LibraryActions.None {
                override fun download(tracks: List<Track>) { calls += "download ${tracks.joinToString { it.id }}" }
                override fun removeDownload(trackId: String) { calls += "remove $trackId" }
            },
        )
        val list = compose.onNodeWithTag("library-content")
        list.performScrollToNode(hasText("4 songs · 8.5 MB"))
        // The end of the list, so the rows clear the floating dock.
        list.performScrollToKey("download-${downloaded.id}")
        compose.onNodeWithText("42%").assertIsDisplayed()
        compose.onNodeWithText("Waiting for Wi-Fi").assertIsDisplayed()
        compose.onNodeWithText("Failed").assertIsDisplayed()
        compose.onNodeWithText("6.2 MB").assertIsDisplayed()

        compose.onNodeWithContentDescription("Cancel the download of ${downloading.title}").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Cancel the download of ${waiting.title}").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Download ${failed.title} again").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Remove ${failed.title} from downloads").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Delete the download of ${downloaded.title}").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(
            listOf("remove ${downloading.id}", "remove ${waiting.id}", "download ${failed.id}", "remove ${failed.id}", "remove ${downloaded.id}"),
            calls,
        )

        compose.onNodeWithContentDescription("${downloaded.title} by ${downloaded.artist}").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(Fixtures.downloads.map { it.track }, queue)
    }

    @Test fun settingsShowStorageAndAskBeforeRemovingEveryDownload() {
        var removals = 0
        var changed: SitaraSettings? = null
        render(Tab.SETTINGS, downloads = Fixtures.downloads, onRemoveAllDownloads = { removals++ }, onSettings = { changed = it })
        val list = compose.onNodeWithTag("settings-content")
        list.performScrollToNode(hasText("Download on Wi-Fi only"))
        compose.onNodeWithText("Download on Wi-Fi only").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(false, changed?.downloadOnWifiOnly)

        list.performScrollToNode(hasText("4 songs · 8.5 MB"))
        compose.onNodeWithText("Remove all").performSemanticsAction(SemanticsActions.OnClick)
        list.performScrollToNode(hasText("Keep"))
        compose.onNodeWithText("Keep").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("keeping cancels", 0, removals)

        compose.onNodeWithText("Remove all").performSemanticsAction(SemanticsActions.OnClick)
        list.performScrollToNode(hasText("Remove all downloads"))
        compose.onNodeWithText("Remove all downloads").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, removals)
    }

    @Test fun albumSaveButtonShowsAndTogglesItsLibraryState() {
        val album = library.albums.first()
        val saves = mutableListOf<Boolean>()
        render(
            initialDetail = DetailRoute.Album(album.id),
            savedIds = SavedIds(albums = setOf(album.id)),
            libraryActions = object : LibraryActions by LibraryActions.None {
                override fun setSaved(album: Album, saved: Boolean) { saves += saved }
            },
        )
        compose.onNodeWithText("Saved").performClick()
        assertEquals(listOf(false), saves)
    }

    @Test fun ownPlaylistReordersRemovesRenamesAndDeletes() {
        val calls = mutableListOf<String>()
        val (first, second) = library.playlists.first().tracks
        render(
            initialDetail = DetailRoute.UserPlaylist(1),
            libraryActions = object : LibraryActions by LibraryActions.None {
                override fun movePlaylistEntry(id: Long, from: Int, to: Int) { calls += "move $id $from $to" }
                override fun removePlaylistEntry(id: Long, trackId: String) { calls += "remove $id $trackId" }
                override fun renamePlaylist(id: Long, name: String) { calls += "rename $id $name" }
                override fun deletePlaylist(id: Long) { calls += "delete $id" }
            },
        )
        compose.onNodeWithContentDescription("${first.title} by ${first.artist}").performCustomAction("Move down")
        compose.onNodeWithContentDescription("Remove ${second.title} from this playlist").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNode(hasSetTextAction()).setTextUnfocused("Evening")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete playlist").performClick()

        assertEquals(listOf("move 1 0 1", "remove 1 ${second.id}", "rename 1 Evening", "delete 1"), calls)
        compose.onNodeWithTag("home-content").assertExists()
    }

    @Test fun detailBackStackSurvivesRestorationAndUnwindsInOrder() {
        val album = library.albums.first()
        val restoration = render(Tab.LIBRARY, initialDetail = DetailRoute.Album(album.id))
        compose.onNode(hasText(album.artist) and opensArtist).performClick()
        compose.onNodeWithText("Top songs").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Top songs").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Play all").assertIsDisplayed()
        compose.onNodeWithText(album.title).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("library-content").assertExists()
    }

    @Test fun playlistPagePlaysFromATappedRowAndShuffles() {
        val playlist = library.playlists.first()
        var queue = emptyList<Track>()
        var index = -1
        var shuffled = emptyList<Track>()
        render(
            initialDetail = DetailRoute.Playlist(playlist.id),
            onPlay = { tracks, selected -> queue = tracks; index = selected },
            onShuffle = { shuffled = it },
        )
        val second = playlist.tracks[1]
        compose.onNodeWithContentDescription("${second.title} by ${second.artist}").performClick()
        assertEquals(playlist.tracks, queue)
        assertEquals(1, index)
        compose.onNodeWithText("Shuffle").performClick()
        assertEquals(playlist.tracks, shuffled)
    }

    @Test fun failedPageRetriesItsOwnRoute() {
        val route = DetailRoute.Artist("UCabcdefghijklmnopqrstuv")
        val retried = mutableListOf<DetailRoute>()
        val failing = object : DetailPages {
            override fun state(route: DetailRoute) = MutableStateFlow<DetailState>(DetailState.Failed)
            override fun retry(route: DetailRoute) { retried += route }
        }
        render(details = failing, initialDetail = route)
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf(route), retried)
    }

    @Test fun searchFiltersSwitchTheResultKindAndOpenPages() {
        val filters = mutableListOf<SearchFilter>()
        val playlist = library.playlists.first()
        render(
            Tab.SEARCH, initialQuery = "night", onFilter = { filters += it },
            search = SearchState.Loaded("night", SearchResults.empty("night").copy(playlists = listOf(playlist)), SearchFilter.PLAYLISTS),
        )
        compose.onNodeWithText("Searching…").assertIsDisplayed()
        compose.onNodeWithText("Playlists").performClick()
        assertEquals(listOf(SearchFilter.PLAYLISTS), filters)
        compose.onNode(hasText("Playlists") and isSelectable()).assertIsSelected()
        compose.onNodeWithText(playlist.title).performClick()
        compose.onNodeWithText("Play all").assertIsDisplayed()
    }

    @Test fun quickPickSecondPageUsesTheOriginalQueueIndex() {
        var index = -1
        var queue = emptyList<Track>()
        render(onPlay = { tracks, selected -> queue = tracks; index = selected })
        compose.onNodeWithTag("quick-picks").performTouchInput { swipeLeft() }
        val picks = chart.tracks
        compose.onNodeWithContentDescription("${picks[4].title} by ${picks[4].artist}").performClick()
        assertEquals(picks, queue)
        assertEquals(4, index)
    }

    @Test fun searchSuggestionsAreActions() {
        var query = ""
        render(Tab.SEARCH, onQuery = { query = it })
        compose.onNodeWithTag("search-content").performScrollToIndex(4)
        compose.onNodeWithText("Jazz piano").performClick()
        assertEquals("Jazz piano", query)
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals("", query)
    }

    @Test fun failedSearchOffersRetry() {
        var retries = 0
        render(Tab.SEARCH, initialQuery = "lofi", search = SearchState.Failed("lofi"), onRetrySearch = { retries++ })
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test fun staleResultsShowSearchingForTheNewQuery() {
        render(Tab.SEARCH, initialQuery = "jazz", search = SearchState.Loaded("lo", SearchResults.empty("lo")))
        compose.onNodeWithText("Searching…").assertIsDisplayed()
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
        // Title, the trending heading and chart, then a heading and a rail for each of the three library shelves.
        compose.onNodeWithTag("home-content").performScrollToIndex(8)
        val rail = compose.onNodeWithTag("rail-likes").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("playback-dock").fetchSemanticsNode().boundsInRoot
        assertTrue("The final rail must clear the entire playback dock", rail.bottom <= dock.top)
    }

    @Test fun failedChartOffersTryAgain() {
        var retries = 0
        render(home = HomeFeed(TrendingState.Failed), onRetryTrending = { retries++ })
        compose.onNodeWithText("The charts didn’t load. Check your connection, then try again.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test fun freshInstallHomeSaysHowItFillsUp() {
        render(home = HomeFeed(TrendingState.Ready(Chart("YouTube Charts · Iceland", emptyList())), libraryLoaded = true))
        compose.onNodeWithText("Nothing is charting here right now.").assertIsDisplayed()
        compose.onNodeWithText("Make Home yours").assertIsDisplayed()
        compose.onNodeWithText("Recently played").assertDoesNotExist()
    }

    @Test fun homeShelvesPlayAndOpenWhatTheyShow() {
        var queue = emptyList<Track>()
        var index = -1
        val saved = sampleSavedLibrary(library)
        render(onPlay = { tracks, selected -> queue = tracks; index = selected })
        compose.onNodeWithText(chart.label).assertIsDisplayed()

        compose.onNodeWithTag("home-content").performScrollToIndex(4)
        compose.onNode(hasText(saved.recent[1].title) and hasAnyAncestor(hasTestTag("rail-recent"))).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(saved.recent, queue)
        assertEquals(1, index)

        compose.onNodeWithTag("home-content").performScrollToIndex(6)
        compose.onNode(hasText(saved.playlists.first().name) and hasAnyAncestor(hasTestTag("rail-jump"))).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Your playlist").assertIsDisplayed()
    }
}
