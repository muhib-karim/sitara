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
import androidx.compose.ui.test.assertIsNotSelected
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
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import app.sitara.music.core.model.AccentColor
import app.sitara.music.core.model.Album
import app.sitara.music.core.model.Chart
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.DownloadState
import app.sitara.music.core.model.MoodChip
import app.sitara.music.core.model.PlaylistSummary
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.SongDownload
import app.sitara.music.core.model.Track
import app.sitara.music.feature.home.FeedState
import app.sitara.music.feature.home.HomeActions
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.home.OnlineHome
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
    private val homePage = runBlocking { source.home() }

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
        home: HomeFeed = sampleHomeFeed(homePage, (saved as? LibraryState.Ready)?.library ?: sampleSavedLibrary(library)),
        homeActions: HomeActions = HomeActions.None,
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
                    onRetrySearch = onRetrySearch, onPlay = onPlay, onShuffle = onShuffle,
                    onSettingsChange = { settings = it; onSettings(it) }, initialTab = tab, initialDetail = initialDetail,
                    savedIds = savedIds, playlists = (saved as? LibraryState.Ready)?.library?.playlists.orEmpty(), libraryActions = libraryActions,
                    downloads = downloads, onRemoveAllDownloads = onRemoveAllDownloads, homeActions = homeActions,
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

        // The first row of covers can sit under the floating dock, so tap the tile through its click action.
        compose.onAllNodesWithText(library.playlists.first().title).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Your playlist").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        val album = library.albums.first()
        compose.onNode(hasText("Albums") and isSelectable()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("library-content").performScrollToNode(hasText(album.title))
        // Scrolled just into view, the tile can sit under the floating dock, so tap it through its click action.
        compose.onNodeWithText(album.title).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Play all").performClick()
        assertEquals(album.tracks, queue)
    }

    @Test fun emptyLibraryOffersOneWayToStart() {
        render(Tab.LIBRARY, saved = LibraryState.Ready(SavedLibrary(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())))
        compose.onNodeWithText("Your library starts here").assertIsDisplayed()
        compose.onNodeWithText("Find music").performClick()
        compose.onNodeWithContentDescription("Search").assertIsDisplayed()
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
                onQueryChange = {}, onSearchFilterChange = {}, onRetrySearch = {}, onPlay = { _, _ -> }, onShuffle = {},
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
        compose.onNode(hasText("Downloads") and isSelectable()).performSemanticsAction(SemanticsActions.OnClick)
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

    @Test fun soundSettingsSetNormalisationSkipSilenceAndCrossfade() {
        var changed = SitaraSettings()
        render(Tab.SETTINGS, onSettings = { changed = it })
        val list = compose.onNodeWithTag("settings-content")
        list.performScrollToNode(hasText("Normalise loudness"))
        compose.onNodeWithText("Normalise loudness").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(false, changed.normalisation)

        list.performScrollToNode(hasText("Skip silence"))
        compose.onNodeWithText("Skip silence").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("off by default, so a tap turns it on", true, changed.skipSilence)

        list.performScrollToNode(hasContentDescription("Crossfade"))
        compose.onNodeWithContentDescription("Crossfade").performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        assertEquals(6, changed.crossfadeSeconds)
        compose.onNodeWithText("6 s").assertIsDisplayed()
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
            override fun moodPage(route: DetailRoute.Mood) = MutableStateFlow<FeedState>(FeedState.Failed)
            override fun retry(route: DetailRoute) { retried += route }
        }
        render(details = failing, initialDetail = route)
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf(route), retried)
    }

    @Test fun failedMoodPageSaysWhichAndRetriesItself() {
        val route = DetailRoute.Mood("sample:mood:Jazz", "Jazz")
        val retried = mutableListOf<DetailRoute>()
        val failing = object : DetailPages {
            override fun state(route: DetailRoute) = MutableStateFlow<DetailState>(DetailState.Failed)
            override fun moodPage(route: DetailRoute.Mood) = MutableStateFlow<FeedState>(FeedState.Failed)
            override fun retry(route: DetailRoute) { retried += route }
        }
        render(details = failing, initialDetail = route)
        compose.onNodeWithText("Jazz didn’t load. Check your connection, then try again.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf<DetailRoute>(route), retried)
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
        // A page is narrower than the screen (the next one peeks in), so a swipe across it all would pass page two.
        compose.onNodeWithTag("quick-picks").performTouchInput { swipeLeft(startX = right * 0.75f, endX = right * 0.15f) }
        val picks = SampleCatalog.tracks
        compose.onNodeWithContentDescription("${picks[4].title} by ${picks[4].artist}").performClick()
        assertEquals(picks, queue)
        assertEquals(4, index)
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
        compose.onNodeWithTag("settings-content").performScrollToNode(hasText("Reduced motion"))
        compose.onNodeWithText("Reduced motion").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(false, updated?.reducedMotion)
        // Normalisation and crossfade work now (soundSettingsSetNormalisationAndCrossfade); placeholders stay out.
        assertThrows(AssertionError::class.java) {
            compose.onNodeWithTag("settings-content").performScrollToNode(hasText("Coming in a future update", substring = true))
        }
    }

    @Test fun appearanceSetsThemePureBlackAndArtworkColoursLive() {
        var updated = SitaraSettings()
        render(Tab.SETTINGS, onSettings = { updated = it })
        compose.onNodeWithTag("appearance-preview").assertIsDisplayed()
        compose.onNodeWithText("Light").performClick()
        assertEquals(DarkMode.LIGHT, updated.darkMode)
        compose.onNodeWithText("Light").assertIsSelected()

        compose.onNodeWithText("Pure black").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(true, updated.pureBlack)
        val list = compose.onNodeWithTag("settings-content")
        list.performScrollToNode(hasText("Use artwork colours across the app"))
        compose.onNodeWithText("Use artwork colours across the app").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(true, updated.artworkColorsAcrossApp)
    }

    @Test fun pickingAnAccentTurnsWallpaperColoursOff() {
        var updated = SitaraSettings()
        render(Tab.SETTINGS, onSettings = { updated = it })
        val list = compose.onNodeWithTag("settings-content")
        list.performScrollToNode(hasContentDescription("Rose"))
        compose.onNodeWithContentDescription("Rose").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(AccentColor.ROSE, updated.accent)
        assertEquals(false, updated.dynamicColor)
        compose.onNodeWithContentDescription("Rose").assertIsSelected()

        list.performScrollToNode(hasText("Wallpaper colours"))
        compose.onNodeWithText("Wallpaper colours").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(true, updated.dynamicColor)
        // The wallpaper decides the accent now, so no swatch claims to be it.
        compose.onNodeWithContentDescription("Rose").assertIsNotSelected()

        list.performScrollToNode(hasText("From artwork"))
        compose.onNodeWithText("From artwork").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(AccentColor.ARTWORK, updated.accent)
        assertEquals(false, updated.dynamicColor)
    }

    @Test
    @Config(sdk = [30])
    fun wallpaperColoursAreHiddenOnOlderAndroid() {
        render(Tab.SETTINGS)
        compose.onNodeWithText("Pure black").assertIsDisplayed()
        assertThrows(AssertionError::class.java) {
            compose.onNodeWithTag("settings-content").performScrollToNode(hasText("Wallpaper colours"))
        }
        // Without wallpaper colours the stored default cannot hide the chosen swatch.
        compose.onNodeWithTag("settings-content").performScrollToNode(hasContentDescription("Saffron"))
        compose.onNodeWithContentDescription("Saffron").assertIsSelected()
    }

    @Test fun lastHomeRailScrollsAboveTheDockAtLargeText() {
        render(fontScale = 2f)
        // Title, chips, Quick picks, the three library shelves, then three more feed shelves: a heading and a rail each.
        compose.onNodeWithTag("home-content").performScrollToIndex(15)
        val rail = compose.onNodeWithTag("rail-feed-3").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("playback-dock").fetchSemanticsNode().boundsInRoot
        assertTrue("The final rail must clear the entire playback dock", rail.bottom <= dock.top)
    }

    @Test fun readingOnFoldsTheDockIntoOneRowAndItsTabOrScrollingBackUnfoldsIt() {
        render()
        val inDock = hasAnyAncestor(hasTestTag("playback-dock"))
        val libraryTab = hasText("Library") and inDock
        compose.onNode(libraryTab).assertIsDisplayed()

        // Each swipe goes the other way, so the list always has room to move: a full swipe flings it to an end.
        compose.onNodeWithTag("home-content").performTouchInput { swipeUp() }
        compose.onNode(libraryTab).assertDoesNotExist()
        compose.onNodeWithContentDescription("Open player").assertIsDisplayed()
        compose.onNodeWithTag("home-content").performTouchInput { swipeDown() }
        compose.onNode(libraryTab).assertIsDisplayed()

        compose.onNodeWithTag("home-content").performTouchInput { swipeUp() }
        compose.onNode(libraryTab).assertDoesNotExist()
        compose.onNodeWithContentDescription("Home, show all tabs").performClick()
        compose.onNode(libraryTab).assertIsDisplayed()
    }

    @Test fun aFailedFeedOffersTryAgainAndKeepsTheChartAndTheListenersShelves() {
        var retries = 0
        val saved = sampleSavedLibrary(library)
        val offline = sampleHomeFeed(homePage, saved).copy(online = OnlineHome(feed = FeedState.Failed, trending = TrendingState.Ready(chart)))
        render(home = offline, homeActions = object : HomeActions by HomeActions.None {
            override fun retryHome() { retries++ }
        })
        compose.onNodeWithText("Home didn’t load").assertIsDisplayed()
        compose.onNodeWithText("Trending now").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
        compose.onNodeWithTag("home-content").performScrollToNode(hasTestTag("rail-recent"))
        compose.onNodeWithText("Listen again").assertIsDisplayed()
    }

    @Test fun freshInstallOfflineHomeSaysHowItFillsUp() {
        render(home = HomeFeed(OnlineHome(feed = FeedState.Failed, trending = TrendingState.Ready(Chart("YouTube Charts · Iceland", emptyList()))), libraryLoaded = true))
        compose.onNodeWithText("Nothing is charting here right now.").assertIsDisplayed()
        compose.onNodeWithText("Make Home yours").assertIsDisplayed()
        compose.onNodeWithText("Listen again").assertDoesNotExist()
    }

    @Test fun homeWhileLoadingIsOneLoadingAnnouncementInTheRealLayout() {
        render(home = HomeFeed())
        compose.onNodeWithContentDescription("Loading Home").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Loading Home").assertCountEquals(1)
    }

    @Test fun moodChipsAskForTheirFilter() {
        val asked = mutableListOf<MoodChip>()
        render(homeActions = object : HomeActions by HomeActions.None {
            override fun selectMood(chip: MoodChip) { asked += chip }
        })
        compose.onNode(hasText("Focus") and hasAnyAncestor(hasTestTag("mood-chips"))).performClick()
        assertEquals(listOf("Focus"), asked.map { it.title })
    }

    @Test fun feedCardsAndMoreOpenTheirPages() {
        val playlist = SampleCatalog.playlists[1]
        render()
        compose.onNodeWithTag("home-content").performScrollToNode(hasTestTag("rail-feed-2"))
        compose.onNode(hasText(playlist.title) and hasAnyAncestor(hasTestTag("rail-feed-2"))).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(playlist.description).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()

        compose.onNodeWithTag("home-content").performScrollToNode(hasContentDescription("More from Mixed for you"))
        compose.onNodeWithContentDescription("More from Mixed for you").performClick()
        compose.onNodeWithText(SampleCatalog.playlists.first().description).assertIsDisplayed()
    }

    @Test fun homeShelvesPlayAndOpenWhatTheyShow() {
        var queue = emptyList<Track>()
        var index = -1
        val saved = sampleSavedLibrary(library)
        render(onPlay = { tracks, selected -> queue = tracks; index = selected })
        compose.onNodeWithText("Quick picks").assertIsDisplayed()

        compose.onNodeWithTag("home-content").performScrollToIndex(5)
        compose.onNode(hasText(saved.recent[1].title) and hasAnyAncestor(hasTestTag("rail-recent"))).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(saved.recent, queue)
        assertEquals(1, index)

        compose.onNodeWithTag("home-content").performScrollToIndex(9)
        compose.onNode(hasText(saved.playlists.first().name) and hasAnyAncestor(hasTestTag("rail-jump"))).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Your playlist").assertIsDisplayed()
    }
}
