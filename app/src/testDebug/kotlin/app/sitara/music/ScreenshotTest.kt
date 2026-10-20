package app.sitara.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.designsystem.SitaraTheme
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.DownloadState
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SearchResults
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.SongDownload
import app.sitara.music.core.model.ThemeMode
import app.sitara.music.core.model.UserPlaylist
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.home.TrendingState
import app.sitara.music.feature.library.AddToPlaylist
import app.sitara.music.feature.library.DetailState
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.library.SavedIds
import app.sitara.music.feature.library.SavedLibrary
import app.sitara.music.feature.library.SongActions
import app.sitara.music.feature.player.LyricsState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
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
    private val home = sampleHomeFeed(runBlocking { source.trending() }, sampleSavedLibrary(library))
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

    private fun shot(
        name: String,
        dark: Boolean,
        tab: Tab = Tab.HOME,
        query: String = "",
        settingsOpen: Boolean = false,
        expanded: Boolean = false,
        page: Int = 0,
        mode: ThemeMode = ThemeMode.ARTWORK,
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
        beforeCapture: () -> Unit = {},
    ) {
        val settings = SitaraSettings(themeMode = mode, darkMode = if (dark) DarkMode.DARK else DarkMode.LIGHT, reducedMotion = reduced)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SitaraShell(
                    settings = settings,
                    home = homeFeed,
                    library = saved,
                    query = query,
                    searchFilter = filter,
                    search = search,
                    player = playerState,
                    actions = PlayerActions.None,
                    details = detail?.let(::FixedPage) ?: SampleDetailPages(source),
                    onQueryChange = {},
                    onSearchFilterChange = {},
                    onRetrySearch = {},
                    onRetryTrending = {},
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
                )
            }
        }
        beforeCapture()
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun miniPlayerDark() = shot("mini_player", dark = true)

    @Test fun miniPlayerLight() = shot("mini_player", dark = false)

    @Test fun homeWithoutQueue() = shot("home_empty_queue", dark = true, playerState = PlayerUiState())

    @Test fun playerAmoled() = shot("player_amoled", dark = true, expanded = true, mode = ThemeMode.AMOLED)

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

    @Test fun homeQuickPicksSecondPage() = shot("home_quick_page2", dark = true) {
        compose.onNodeWithTag("quick-picks").performTouchInput { swipeLeft() }
    }

    @Test fun homeLoading() = shot("home_loading", dark = true, playerState = PlayerUiState(), homeFeed = HomeFeed())

    @Test fun homeChartFailed() = shot("home_chart_failed", dark = true, homeFeed = home.copy(trending = TrendingState.Failed))

    @Test fun homeFreshInstall() = shot("home_fresh", dark = true, playerState = PlayerUiState(), homeFeed = HomeFeed(home.trending, libraryLoaded = true))

    @Test fun homeBottomLargeText() = shot("home_bottom_large_text", dark = true, fontScale = 2f) {
        compose.onNodeWithTag("home-content").performScrollToIndex(8)
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
    @Test fun playerCoverFailed() = shot(
        "player_cover_failed", dark = true, expanded = true,
        playerState = playing.copy(track = track.copy(artwork = track.artwork.copy(imageUrl = "https://offline.invalid/cover.jpg"))),
    )

    @Test fun playerLiked() = shot("player_liked", dark = true, expanded = true, playerState = playing.copy(liked = true))

    @Test fun songActionsSheet() = sheet("song_actions") { SongActions(Fixtures.playlist.tracks.first(), liked = true, download = null, {}, {}, {}, {}, {}, {}) }
    @Test fun songActionsDownloaded() = sheet("song_actions_downloaded") {
        SongActions(Fixtures.playlist.tracks.first(), liked = false, download = DownloadState.DOWNLOADED, {}, {}, {}, {}, {}, {})
    }

    @Test fun libraryDownloads() = shot("library_downloads", dark = true, tab = Tab.LIBRARY, downloads = Fixtures.downloads) {
        compose.onNodeWithTag("library-content").performScrollToKey("download-${Fixtures.downloads.last().track.id}")
    }
    @Test fun libraryDownloadsLight() = shot("library_downloads", dark = false, tab = Tab.LIBRARY, downloads = Fixtures.downloads) {
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
            SitaraTheme(ThemeMode.ARTWORK, DarkMode.DARK, reducedMotion = true) {
                Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_dark.png")
    }

    @Test fun searchIdleDark() = shot("search_idle", dark = true, tab = Tab.SEARCH)
    @Test fun searchIdleLight() = shot("search_idle", dark = false, tab = Tab.SEARCH)
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
        compose.onNodeWithTag("settings-content").performScrollToIndex(3)
    }

    @Test fun settingsSound() = shot("settings_sound", dark = true, settingsOpen = true) {
        compose.onNodeWithTag("settings-content").performScrollToIndex(4)
    }

    @Test fun settingsDownloads() = shot("settings_downloads", dark = true, settingsOpen = true, downloads = Fixtures.downloads) {
        compose.onNodeWithTag("settings-content").performScrollToIndex(5)
    }

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

    override fun retry(route: DetailRoute) = Unit
}
