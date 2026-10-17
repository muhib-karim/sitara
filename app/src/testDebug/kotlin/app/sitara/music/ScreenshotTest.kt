package app.sitara.music

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.Library
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.ThemeMode
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
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

/** Records the README screenshots. Run `recordRoborazziDebug` to refresh them. */
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
        lyrics = runBlocking { source.lyrics(track) },
    )

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
    ) {
        val settings = SitaraSettings(themeMode = mode, darkMode = if (dark) DarkMode.DARK else DarkMode.LIGHT, reducedMotion = reduced)
        val searchResults = runBlocking { source.search(query) }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                SitaraShell(
                    settings = settings,
                    home = home,
                    library = library,
                    query = query,
                    results = searchResults,
                    player = playerState,
                    actions = PlayerActions.None,
                    onQueryChange = {},
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
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun miniPlayerDark() = shot("mini_player", dark = true)

    @Test fun miniPlayerLight() = shot("mini_player", dark = false)

    @Test fun homeWithoutQueue() = shot("home_empty_queue", dark = true, playerState = PlayerUiState())

    @Test fun playerAmoled() = shot("player_amoled", dark = true, expanded = true, mode = ThemeMode.AMOLED)

    @Test fun playerBlur() = shot("player_blur", dark = true, expanded = true, reduced = false)

    @Test fun lyricsBlur() = shot("lyrics_blur", dark = true, expanded = true, page = 1, reduced = false)

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

    @Test fun playerDark() = shot("player", dark = true, expanded = true)

    @Test fun playerLight() = shot("player", dark = false, expanded = true)

    @Test fun lyricsDark() = shot("lyrics", dark = true, expanded = true, page = 1)

    @Test fun lyricsLight() = shot("lyrics", dark = false, expanded = true, page = 1)

    @Test fun settingsDark() = shot("settings", dark = true, settingsOpen = true)

    @Test fun settingsLight() = shot("settings", dark = false, settingsOpen = true)
}
