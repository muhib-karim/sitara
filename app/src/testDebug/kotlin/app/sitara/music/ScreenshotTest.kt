package app.sitara.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.designsystem.SitaraTheme
import app.sitara.music.core.model.AccentColor
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.DownloadState
import app.sitara.music.core.model.EqualiserPreset
import app.sitara.music.core.model.EqualiserSettings
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.LibraryLayout
import app.sitara.music.core.model.PlaybackTuning
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.SongDownload
import app.sitara.music.core.model.StatsRange
import app.sitara.music.core.model.UserPlaylist
import app.sitara.music.feature.home.FeedState
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.home.OnlineHome
import app.sitara.music.feature.home.TrendingState
import app.sitara.music.feature.library.AddToPlaylist
import app.sitara.music.feature.library.DetailState
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.library.SavedIds
import app.sitara.music.feature.library.SavedLibrary
import app.sitara.music.feature.library.StatsState
import app.sitara.music.feature.library.SongActions
import app.sitara.music.feature.player.EqualiserPanel
import app.sitara.music.feature.player.LyricsState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerMoreOptions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.player.SleepTimerOptions
import app.sitara.music.feature.player.SleepTimerStatus
import app.sitara.music.feature.player.SongVolume
import app.sitara.music.feature.player.SpeedAndPitch
import app.sitara.music.feature.search.MoodsState
import app.sitara.music.feature.search.SearchLanding
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Records/verifies baselines through the existing CI workflows only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @OptIn(DelicateCoilApi::class)
    @Before
    fun drawSampleCovers() = SingletonImageLoader.setUnsafe(sampleImageLoader(ApplicationProvider.getApplicationContext()))

    @OptIn(DelicateCoilApi::class)
    @After
    fun releaseImageLoader() = SingletonImageLoader.reset()

    private val source = FakeMusicSource()
    private val library: Library = runBlocking { source.library() }
    private val home = sampleHomeFeed(runBlocking { source.home() }, sampleSavedLibrary(library))
    private val offline = home.copy(online = OnlineHome(feed = FeedState.Failed, trending = TrendingState.Ready(runBlocking { source.trending() })))
    private val track = SampleCatalog.tracks.first()
    private val sampleLyrics = runBlocking { source.lyrics(track)!! }
    private val playing = PlayerUiState(
        track = track,
        isPlaying = true,
        positionMs = 15_300,
        durationMs = track.durationMs,
        upNext = SampleCatalog.tracks.drop(1),
        lyrics = LyricsState.Found(sampleLyrics.copy(source = "LRCLIB")),
    )

    // The sample catalogue answers each filter on its own; merged, every result group shows at once.
    private fun allKinds(query: String) = runBlocking {
        SearchFilter.entries.map { source.search(query, it) }
            .reduce { all, more -> all.copy(tracks = all.tracks + more.tracks, albums = all.albums + more.albums, artists = all.artists + more.artists) }
    }

    private val searchLanding = SearchLanding(recent = listOf("Lumen Drift", "night drive", "jazz piano"), moods = MoodsState.Ready(runBlocking { source.moods() }))

    private fun shot(
        name: String,
        dark: Boolean,
        tab: Tab = Tab.HOME,
        query: String = "",
        settingsOpen: Boolean = false,
        expanded: Boolean = false,
        page: Int = 0,
        pureBlack: Boolean = false,
        accent: AccentColor = AccentColor.SAFFRON,
        artworkAcrossApp: Boolean = false,
        reduced: Boolean = true,
        fontScale: Float = 1f,
        playerState: PlayerUiState = playing,
        search: SearchState = SearchState.Loaded(query.trim(), allKinds(query)),
        filter: SearchFilter = SearchFilter.SONGS,
        detail: DetailState? = null,
        route: DetailRoute? = null,
        saved: LibraryState = LibraryState.Ready(sampleSavedLibrary(library)),
        homeFeed: HomeFeed = home,
        savedIds: SavedIds = SavedIds(),
        downloads: List<SongDownload> = emptyList(),
        equaliser: EqualiserSettings = EqualiserSettings(),
        landing: SearchLanding = searchLanding,
        libraryLayout: LibraryLayout = LibraryLayout.GRID,
        beforeCapture: () -> Unit = {},
    ) {
        val settings = SitaraSettings(
            darkMode = if (dark) DarkMode.DARK else DarkMode.LIGHT, pureBlack = pureBlack, accent = accent, artworkColorsAcrossApp = artworkAcrossApp,
            // Robolectric has no wallpaper to take colours from; the shots show Sitara's own accents.
            dynamicColor = false, reducedMotion = reduced, libraryLayout = libraryLayout,
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SitaraShell(
                    settings = settings,
                    home = homeFeed,
                    library = saved,
                    query = query,
                    searchFilter = filter,
                    search = search,
                    player = playerState.copy(equaliser = equaliser),
                    actions = PlayerActions.None,
                    details = detail?.let(::FixedPage) ?: SampleDetailPages(source),
                    onQueryChange = {},
                    onSearchFilterChange = {},
                    onRetrySearch = {},
                    onPlay = { _, _ -> },
                    onShuffle = {},
                    onSettingsChange = {},
                    initialTab = tab,
                    initialDetail = route ?: detail?.let { DetailRoute.Album("fixture") },
                    initialSettingsOpen = settingsOpen,
                    playerExpanded = expanded,
                    playerPage = page,
                    greetingText = "Good evening",
                    savedIds = savedIds,
                    downloads = downloads,
                    searchLanding = landing,
                )
            }
        }
        beforeCapture()
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun miniPlayerDark() = shot("mini_player", dark = true)

    @Test fun miniPlayerLight() = shot("mini_player", dark = false)

    @Test fun homeWithoutQueue() = shot("home_empty_queue", dark = true, playerState = PlayerUiState())

    @Test fun playerAmoled() = shot("player_amoled", dark = true, expanded = true, pureBlack = true)

    @Test fun playerBlur() = shot("player_blur", dark = true, expanded = true, reduced = false)

    @Test fun lyricsBlur() = shot("lyrics_blur", dark = true, expanded = true, page = 1, reduced = false)

    @Test fun lyricsLastLine() = shot("lyrics_last_line", dark = true, expanded = true, page = 1, playerState = playing.copy(positionMs = 40_000))

    @Test fun lyricsPlain() = shot("lyrics_plain", dark = true, expanded = true, page = 1, playerState = playing.copy(lyrics = LyricsState.Found(sampleLyrics.copy(synced = false, source = "LRCLIB"))))

    @Test fun lyricsEmpty() = shot("lyrics_empty", dark = true, expanded = true, page = 1, playerState = playing.copy(lyrics = LyricsState.NotFound))

    @Test fun lyricsLoading() = shot("lyrics_loading", dark = true, expanded = true, page = 1, playerState = playing.copy(lyrics = LyricsState.Loading))

    @Test fun lyricsFailed() = shot("lyrics_failed", dark = true, expanded = true, page = 1, playerState = playing.copy(lyrics = LyricsState.Failed))

    @Test fun miniPlayerLargeText() = shot("mini_large_text", dark = true, fontScale = 2f)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun lyricsLandscape() = shot("lyrics_landscape", dark = true, expanded = true, page = 1)

    @Test fun playerLargeText() = shot("player_large_text", dark = true, expanded = true, fontScale = 2f)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun playerLandscape() = shot("player_landscape", dark = true, expanded = true)

    @Test
    @Config(sdk = [30])
    fun playerLegacyBlurFallback() = shot("player_api30", dark = true, expanded = true, reduced = false)

    @Test fun homeDark() = shot("home", dark = true)

    @Test fun homeLight() = shot("home", dark = false)

    @Test fun searchDark() = shot("search", dark = true, tab = Tab.SEARCH, query = "night")

    @Test fun searchLight() = shot("search", dark = false, tab = Tab.SEARCH, query = "night")

    @Test fun libraryDark() = shot("library", dark = true, tab = Tab.LIBRARY)

    @Test fun libraryLight() = shot("library", dark = false, tab = Tab.LIBRARY)

    @Test fun playerDark() = shot("player", dark = true) {
        compose.onNodeWithContentDescription("Open player").performTouchInput { swipeUp(startY = bottom, endY = -100f) }
    }

    @Test fun playerLight() = shot("player", dark = false, expanded = true)

    @Test fun lyricsDark() = shot("lyrics", dark = true, expanded = true, page = 1)

    @Test fun lyricsLight() = shot("lyrics", dark = false, expanded = true, page = 1)

    @Test fun settingsDark() = shot("settings", dark = true, settingsOpen = true)

    @Test fun settingsLight() = shot("settings", dark = false, settingsOpen = true)

    @Test fun appearanceAccentDark() = shot("appearance_violet", dark = true, settingsOpen = true, accent = AccentColor.VIOLET)

    @Test fun appearanceAccentLight() = shot("appearance_violet", dark = false, settingsOpen = true, accent = AccentColor.VIOLET)

    @Test fun homeArtworkColoursDark() = shot("home_artwork_colours", dark = true, accent = AccentColor.ARTWORK, artworkAcrossApp = true)

    @Test fun homeArtworkColoursLight() = shot("home_artwork_colours", dark = false, accent = AccentColor.ARTWORK, artworkAcrossApp = true)

    @Test fun homeQuickPicksSecondPage() = shot("home_quick_page2", dark = true) {
        compose.onNodeWithTag("quick-picks").performTouchInput { swipeLeft(startX = right * 0.75f, endX = right * 0.15f) }
    }

    @Test fun homeLoadingDark() = shot("home_loading", dark = true, playerState = PlayerUiState(), homeFeed = HomeFeed())

    @Test fun homeLoadingLight() = shot("home_loading", dark = false, playerState = PlayerUiState(), homeFeed = HomeFeed())

    @Test fun homeOfflineDark() = shot("home_offline", dark = true, homeFeed = offline)

    @Test fun homeOfflineLight() = shot("home_offline", dark = false, homeFeed = offline)

    @Test fun homeOfflineWithoutChart() = shot("home_offline_no_chart", dark = true, homeFeed = offline.copy(online = offline.online.copy(trending = TrendingState.Failed)))

    @Test fun homeMoodSelected() = shot(
        "home_mood", dark = true,
        homeFeed = sampleHomeFeed(runBlocking { source.home(home.online.chips.first()) }, sampleSavedLibrary(library)),
    )

    @Test fun homeFreshInstall() = shot("home_fresh", dark = true, playerState = PlayerUiState(), homeFeed = HomeFeed(home.online, libraryLoaded = true))

    @Test fun homeFeedShelves() = shot("home_shelves", dark = true) {
        compose.onNodeWithTag("home-content").performScrollToIndex(10)
    }

    @Test fun homeBottomLargeText() = shot("home_bottom_large_text", dark = true, fontScale = 2f) {
        compose.onNodeWithTag("home-content").performScrollToIndex(15)
    }

    @Test fun libraryList() = shot("library_list", dark = true, tab = Tab.LIBRARY, libraryLayout = LibraryLayout.LIST)

    @Test fun librarySongs() = shot("library_songs", dark = true, tab = Tab.LIBRARY) {
        compose.onNode(hasText("Songs") and isSelectable()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("library-content").performScrollToIndex(5)
    }

    @Test fun libraryEmpty() = shot("library_empty", dark = true, tab = Tab.LIBRARY, saved = LibraryState.Ready(SavedLibrary(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())))

    @Test fun libraryLoading() = shot("library_loading", dark = true, tab = Tab.LIBRARY, saved = LibraryState.Loading)

    @Test fun albumDark() = shot("album", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Album(library.albums.first().id))
    @Test fun albumLight() = shot("album", dark = false, tab = Tab.LIBRARY, route = DetailRoute.Album(library.albums.first().id))
    @Test fun artistDark() = shot("artist", dark = true, tab = Tab.LIBRARY, route = DetailRoute.Artist(library.artists.first().id))
    @Test fun artistLight() = shot("artist", dark = false, tab = Tab.LIBRARY, route = DetailRoute.Artist(library.artists.first().id))

    @Test fun likedPage() = shot("liked_page", dark = true, tab = Tab.LIBRARY, detail = DetailState.LikedReady(Fixtures.playlist.tracks))
    @Test fun likedEmpty() = shot("liked_empty", dark = true, tab = Tab.LIBRARY, detail = DetailState.LikedReady(emptyList()))
    @Test fun userPlaylistDark() = shot("user_playlist", dark = true, tab = Tab.LIBRARY, detail = DetailState.UserPlaylistReady(UserPlaylist(1, "Late night", Fixtures.playlist.tracks)))
    @Test fun userPlaylistLight() = shot("user_playlist", dark = false, tab = Tab.LIBRARY, detail = DetailState.UserPlaylistReady(UserPlaylist(1, "Late night", Fixtures.playlist.tracks)))
    @Test fun upNext() = shot("up_next", dark = true, expanded = true, page = 2)

    // Two radio songs under their own heading, and the Undo offer after a removal; the frozen clock holds the snackbar up.
    @Test fun upNextUndo() = shot("up_next_undo", dark = true, expanded = true, page = 2, playerState = playing.copy(upNext = playing.upNext.take(5), upNextFromRadio = 2)) {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Remove ${playing.upNext[1].title} from the queue").performClick()
        compose.mainClock.advanceTimeBy(1_000)
    }
    @Test fun playerCoverFailed() = shot(
        "player_cover_failed", dark = true, expanded = true,
        playerState = playing.copy(track = track.copy(artwork = track.artwork.copy(imageUrl = "https://offline.invalid/cover.jpg"))),
    )

    @Test fun miniPlayerFailed() = shot("mini_player_failed", dark = true, playerState = playing.copy(isPlaying = false, positionMs = 0, failed = true))

    @Test fun playerFailed() = shot("player_failed", dark = true, expanded = true, playerState = playing.copy(isPlaying = false, positionMs = 0, failed = true))

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun playerFailedSmallPhone() = shot("player_failed_small", dark = true, expanded = true, playerState = playing.copy(isPlaying = false, positionMs = 0, failed = true))

    @Test fun playerLiked() = shot("player_liked", dark = true, expanded = true, playerState = playing.copy(liked = true))

    @Test fun songActionsSheet() = sheet("song_actions") { SongActions(Fixtures.playlist.tracks.first(), liked = true, download = null, {}, {}, {}, {}, {}, {}) }
    @Test fun speedAndPitchSheet() = sheet("speed_pitch") { SpeedAndPitch(PlaybackTuning(speed = 1.25f, semitones = -2), PlayerActions.None) }
    @Test fun sleepTimerSheet() = sheet("sleep_timer") { SleepTimerOptions(SleepTimerStatus.Counting(754_000), PlayerActions.None) { it() } }
    @Test fun playerTimerAndSpeedSet() = shot(
        "player_timer_speed", dark = true, expanded = true,
        playerState = playing.copy(sleepTimer = SleepTimerStatus.Counting(754_000), tuning = PlaybackTuning(speed = 1.5f)),
    )
    @Test fun songActionsDownloaded() = sheet("song_actions_downloaded") {
        SongActions(Fixtures.playlist.tracks.first(), liked = false, download = DownloadState.DOWNLOADED, {}, {}, {}, {}, {}, {})
    }

    @Test fun libraryDownloads() = shot("library_downloads", dark = true, tab = Tab.LIBRARY, downloads = Fixtures.downloads) {
        compose.onNode(hasText("Downloads") and isSelectable()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("library-content").performScrollToKey("download-${Fixtures.downloads.last().track.id}")
    }
    @Test fun libraryDownloadsLight() = shot("library_downloads", dark = false, tab = Tab.LIBRARY, downloads = Fixtures.downloads) {
        compose.onNode(hasText("Downloads") and isSelectable()).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("library-content").performScrollToKey("download-${Fixtures.downloads.last().track.id}")
    }
    @Test fun albumPageDownloads() = shot(
        "album_page_downloads", dark = true, tab = Tab.SEARCH, detail = DetailState.AlbumReady(Fixtures.album),
        // Downloaded songs at the top, above the dock, so their badges show; one still missing keeps Download all.
        savedIds = SavedIds(
            downloads = Fixtures.album.tracks.take(3).zip(listOf(DownloadState.DOWNLOADED, DownloadState.DOWNLOADED, DownloadState.DOWNLOADING))
                .associate { (track, state) -> track.id to state },
        ),
    )
    @Test fun addToPlaylistSheet() = sheet("add_to_playlist") { AddToPlaylist(Fixtures.playlist.tracks.first(), sampleSavedLibrary(library).playlists, {}, {}) }

    // Sheets open in a dialog window; their content is captured on the sheet's own surface colour.
    private fun sheet(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SitaraTheme(SitaraSettings(dynamicColor = false, reducedMotion = true), dark = true) {
                Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_dark.png")
    }

    @Test fun searchIdleDark() = shot("search_idle", dark = true, tab = Tab.SEARCH)
    @Test fun searchIdleLight() = shot("search_idle", dark = false, tab = Tab.SEARCH)
    @Test fun searchNoRecentSearches() = shot("search_no_recent", dark = true, tab = Tab.SEARCH, landing = searchLanding.copy(recent = emptyList()))
    @Test fun searchMoodsLoading() = shot("search_moods_loading", dark = true, tab = Tab.SEARCH, landing = searchLanding.copy(moods = MoodsState.Loading))
    @Test fun searchSuggestions() = shot("search_suggestions", dark = true, tab = Tab.SEARCH, query = "Lu", landing = searchLanding.copy(suggestions = runBlocking { source.suggestions("Lu") }))
    @Test fun moodPage() = shot("mood_page", dark = true, tab = Tab.SEARCH, route = DetailRoute.Mood("sample:mood:Jazz", "Jazz"))
    @Test fun searchFailed() = shot("search_failed", dark = true, tab = Tab.SEARCH, query = "lofi", search = SearchState.Failed("lofi"))
    @Test fun searchGrouped() = shot("search_grouped", dark = true, tab = Tab.SEARCH, query = "Lumen Drift")

    @Test fun artistPageDark() = shot("artist_page", dark = true, tab = Tab.SEARCH, detail = DetailState.ArtistReady(Fixtures.artistPage))
    @Test fun artistPageLight() = shot("artist_page", dark = false, tab = Tab.SEARCH, detail = DetailState.ArtistReady(Fixtures.artistPage))
    @Test fun albumPageDark() = shot("album_page", dark = true, tab = Tab.SEARCH, detail = DetailState.AlbumReady(Fixtures.album))
    @Test fun playlistPageDark() = shot("playlist_page", dark = true, tab = Tab.SEARCH, detail = DetailState.PlaylistReady(Fixtures.playlist))
    @Test fun detailLoading() = shot("detail_loading", dark = true, tab = Tab.SEARCH, detail = DetailState.Loading)
    @Test fun detailFailed() = shot("detail_failed", dark = true, tab = Tab.SEARCH, detail = DetailState.Failed)
    @Test fun albumEmpty() = shot("album_empty", dark = true, tab = Tab.SEARCH, detail = DetailState.AlbumReady(Fixtures.album.copy(tracks = emptyList())))
    @Test fun searchAlbumsFilter() = shot(
        "search_albums", dark = true, tab = Tab.SEARCH, query = "orchid", filter = SearchFilter.ALBUMS,
        search = SearchState.Loaded("orchid", SearchResults.empty("orchid").copy(albums = Fixtures.artistPage.releases), SearchFilter.ALBUMS),
    )

    @Test fun settingsMotion() = shot("settings_motion", dark = true, settingsOpen = true) {
        compose.onNodeWithTag("settings-content").performScrollToKey("motion")
    }

    @Test fun settingsSound() = shot("settings_sound", dark = true, settingsOpen = true) {
        compose.onNodeWithTag("settings-content").performScrollToKey("sound")
    }

    @Test fun settingsDownloads() = shot("settings_downloads", dark = true, settingsOpen = true, downloads = Fixtures.downloads) {
        compose.onNodeWithTag("settings-content").performScrollToKey("downloads")
    }

    private val rock = EqualiserSettings().withPreset(EqualiserPreset.ROCK)

    // The Sound group at the top, so its first row is clear of the dock; then make sure the page opened before capturing.
    private fun openEqualiser() {
        compose.onNodeWithTag("settings-content").performScrollToKey("sound")
        compose.onNodeWithText("Equaliser").performClick()
        compose.onNodeWithContentDescription("Preamp").assertExists()
    }

    @Test fun equaliserDark() = shot("equaliser", dark = true, settingsOpen = true, equaliser = rock) { openEqualiser() }
    @Test fun equaliserLight() = shot("equaliser", dark = false, settingsOpen = true, equaliser = rock) { openEqualiser() }
    @Test fun equaliserEnd() = shot("equaliser_end", dark = true, settingsOpen = true, equaliser = rock) {
        openEqualiser()
        // Scrolled right to the end, where the page's bottom padding lifts Reset clear of the dock.
        compose.onNodeWithTag("equaliser-content").performTouchInput { repeat(3) { swipeUp() } }
    }
    @Test fun equaliserCustomWithPhoneOption() = sheet("equaliser_custom") { EqualiserPanel(rock.withBand(0, 9), systemEqualiser = true, onChange = {}, onOpenSystemEqualiser = {}) }
    @Test fun equaliserPhone() = sheet("equaliser_phone") { EqualiserPanel(rock.copy(useSystem = true), systemEqualiser = true, onChange = {}, onOpenSystemEqualiser = {}) }
    @Test fun playerMoreOptions() = sheet("player_more") { PlayerMoreOptions(playing.copy(equaliser = rock, trimDb = -2), {}, {}, {}) }
    @Test fun songVolume() = sheet("song_volume") { SongVolume(playing.copy(trimDb = 3), PlayerActions.None) }

    @Test fun scrubberMidpoint() = shot(
        "player_scrubber_midpoint", dark = true, expanded = true,
        playerState = playing.copy(positionMs = track.durationMs / 2),
    )

    @Test
    @Config(qualifiers = "w320dp-h720dp-xxhdpi")
    fun homeNarrowLargeText() = shot("home_narrow_large_text", dark = true, fontScale = 2f)

    @Test
    @Config(qualifiers = "w320dp-h720dp-xxhdpi")
    fun libraryNarrowLargeText() = shot("library_narrow_large_text", dark = true, tab = Tab.LIBRARY, fontScale = 2f)

    @Test
    @Config(qualifiers = "w320dp-h720dp-xxhdpi")
    fun searchNarrowLargeText() = shot("search_narrow_large_text", dark = true, tab = Tab.SEARCH, fontScale = 2f)
}

/** Shows one page whatever route is open, so a screenshot needs no catalogue. */
internal class FixedPage(state: DetailState) : DetailPages {
    private val flow = MutableStateFlow(state)

    override fun state(route: DetailRoute): StateFlow<DetailState> = flow

    override fun moodPage(route: DetailRoute.Mood): StateFlow<FeedState> = error("A fixed page is a catalogue or own page, not a mood")

    override fun stats(range: StatsRange): StateFlow<StatsState> = error("A fixed page is a catalogue or own page, not stats")

    override fun retry(route: DetailRoute) = Unit
}
