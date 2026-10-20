package app.sitara.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.ThemeMode
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
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

    private val source = FakeMusicSource()
    private val home = runBlocking { source.home() }
    private val library: Library = runBlocking { source.library() }
    private val track = SampleCatalog.tracks.first()
    private val playing = PlayerUiState(
        track = track,
        isPlaying = true,
        positionMs = 15_300,
        durationMs = track.durationMs,
        upNext = SampleCatalog.tracks.drop(1),
        lyrics = runBlocking { source.lyrics(track)!! },
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
        beforeCapture: () -> Unit = {},
    ) {
        val settings = SitaraSettings(themeMode = mode, darkMode = if (dark) DarkMode.DARK else DarkMode.LIGHT, reducedMotion = reduced)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SitaraShell(
                    settings = settings,
                    home = home,
                    library = library,
                    query = query,
                    search = search,
                    player = playerState,
                    actions = PlayerActions.None,
                    onQueryChange = {},
                    onRetrySearch = {},
                    onPlay = { _, _ -> },
                    onSettingsChange = {},
                    initialTab = tab,
                    initialSettingsOpen = settingsOpen,
                    playerExpanded = expanded,
                    playerPage = page,
                    greetingText = "Good evening",
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

    @Test fun lyricsPlain() = shot("lyrics_plain", dark = true, expanded = true, page = 1, playerState = playing.copy(lyrics = playing.lyrics.copy(synced = false)))

    @Test fun lyricsEmpty() = shot("lyrics_empty", dark = true, expanded = true, page = 1, playerState = playing.copy(lyrics = Lyrics.Empty))

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

    @Test fun homeFocus() = shot("home_focus", dark = true) {
        compose.onNodeWithText("Focus").performClick()
    }

    @Test fun homeBottomLargeText() = shot("home_bottom_large_text", dark = true, fontScale = 2f) {
        compose.onNodeWithTag("home-content").performScrollToIndex(3 + home.size * 2 - 1)
    }

    @Test fun libraryListDark() = shot("library_list", dark = true, tab = Tab.LIBRARY) {
        compose.onNodeWithContentDescription("Show list").performClick()
    }

    @Test fun libraryListLight() = shot("library_list", dark = false, tab = Tab.LIBRARY) {
        compose.onNodeWithContentDescription("Show list").performClick()
    }

    @Test fun likedSongsGrid() = shot("library_liked", dark = true, tab = Tab.LIBRARY) {
        compose.onNodeWithContentDescription("Open liked songs").performClick()
    }

    @Test fun albumDark() = albumShot(true)
    @Test fun albumLight() = albumShot(false)
    private fun albumShot(dark: Boolean) = shot("album", dark, tab = Tab.LIBRARY) {
        compose.onNodeWithText("Albums").performClick()
        compose.onNodeWithText(library.albums.first().title).performClick()
    }

    @Test fun artistDark() = artistShot(true)
    @Test fun artistLight() = artistShot(false)
    private fun artistShot(dark: Boolean) = shot("artist", dark, tab = Tab.LIBRARY) {
        compose.onNodeWithText("Artists").performClick()
        compose.onNode(hasText(library.artists.first().name) and hasAnyAncestor(hasTestTag("library-grid"))).performClick()
    }

    @Test fun searchBrowseDark() = shot("search_browse", dark = true, tab = Tab.SEARCH)
    @Test fun searchBrowseLight() = shot("search_browse", dark = false, tab = Tab.SEARCH)
    @Test fun searchFailed() = shot("search_failed", dark = true, tab = Tab.SEARCH, query = "lofi", search = SearchState.Failed("lofi"))
    @Test fun searchGrouped() = shot("search_grouped", dark = true, tab = Tab.SEARCH, query = "Lumen Drift")

    @Test fun settingsMotion() = shot("settings_motion", dark = true, settingsOpen = true) {
        compose.onNodeWithTag("settings-content").performScrollToIndex(3)
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
