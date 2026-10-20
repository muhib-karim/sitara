package app.sitara.music

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.PlaybackTuning
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.player.LyricsState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PlayerInteractionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var backDispatcher: OnBackPressedDispatcher

    private fun render(
        expanded: Boolean = false,
        page: Int = 0,
        actions: PlayerActions = PlayerActions.None,
        durationMs: Long = 100_000,
        upNext: List<Track> = emptyList(),
        lyricsState: LyricsState? = null,
        tuning: PlaybackTuning = PlaybackTuning(),
    ) {
        val source = FakeMusicSource()
        val track = SampleCatalog.tracks.first()
        val lyrics = runBlocking { source.lyrics(track)!! }
        compose.setContent {
            backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            SitaraShell(
                settings = SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true),
                home = HomeFeed(),
                library = LibraryState.Loading,
                query = "",
                searchFilter = SearchFilter.SONGS,
                search = SearchState.Idle,
                player = PlayerUiState(track = track, durationMs = durationMs, lyrics = lyricsState ?: LyricsState.Found(lyrics), positionMs = 15_300, upNext = upNext, tuning = tuning),
                actions = actions,
                details = SampleDetailPages(source),
                onQueryChange = {},
                onSearchFilterChange = {},
                onRetrySearch = {},
                onRetryTrending = {},
                onPlay = { _, _ -> },
                onShuffle = {},
                onSettingsChange = {},
                playerExpanded = expanded,
                playerPage = page,
                greetingText = "Good evening",
            )
        }
    }

    @Test
    fun dockOpensPlayerAndCollapseRestoresNavigation() {
        render()
        compose.onNodeWithContentDescription("Open player").performClick()
        compose.onNodeWithContentDescription("Collapse player").assertIsDisplayed().performClick()
        compose.onNodeWithText("Home").assertIsSelected()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun dockSwipeSkipsAndOpensPlayer() {
        var next = 0
        render(actions = object : PlayerActions by PlayerActions.None {
            override fun next() { next++ }
        })
        compose.onNodeWithContentDescription("Open player").performTouchInput { swipeLeft() }
        assertEquals(1, next)
        compose.onNodeWithContentDescription("Open player").performTouchInput { swipeUp(startY = bottom, endY = -100f) }
        compose.onNodeWithContentDescription("Collapse player").assertIsDisplayed()
    }

    @Test
    fun backHandlingStaysDisabledDuringDragAndCollapsesTheExpandedPlayer() {
        render()
        compose.runOnIdle { assertFalse(backDispatcher.hasEnabledCallbacks()) }
        compose.onNodeWithContentDescription("Open player").performTouchInput {
            down(center)
            moveTo(Offset(centerX, top))
        }
        compose.runOnIdle { assertFalse(backDispatcher.hasEnabledCallbacks()) }
        compose.onNodeWithContentDescription("Open player").performTouchInput { cancel() }
        compose.runOnIdle { assertFalse(backDispatcher.hasEnabledCallbacks()) }

        compose.onNodeWithContentDescription("Open player").performTouchInput { swipeUp(startY = bottom, endY = -100f) }
        compose.onNodeWithContentDescription("Collapse player").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(backDispatcher.hasEnabledCallbacks())
            backDispatcher.onBackPressed()
        }

        compose.onNodeWithContentDescription("Open player").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsSelected()
        compose.runOnIdle { assertFalse(backDispatcher.hasEnabledCallbacks()) }
    }

    @Test
    fun artistNameInNowPlayingCollapsesThePlayerAndOpensTheArtist() {
        render(expanded = true)
        compose.onNode(SemanticsMatcher("opens the artist") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open artist" }).performClick()
        compose.onNodeWithContentDescription("Collapse player").assertDoesNotExist()
        compose.onNodeWithText("Top songs").assertIsDisplayed()
    }

    @Test
    fun upNextOffersToStartRadio() {
        var radios = 0
        render(expanded = true, page = 2, actions = object : PlayerActions by PlayerActions.None {
            override fun startRadio() { radios++ }
        })
        compose.onNodeWithText("Start radio").performClick()
        assertEquals(1, radios)
    }

    @Test
    fun seekHasAccessibleProgressAction() {
        var seek = -1L
        render(expanded = true, actions = object : PlayerActions by PlayerActions.None {
            override fun seekTo(positionMs: Long) { seek = positionMs }
        })
        compose.onNodeWithContentDescription("Seek").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(50_000L, seek)
    }

    @Test fun seekTapUsesTheFullTouchTarget() {
        var seek = -1L
        render(expanded = true, actions = object : PlayerActions by PlayerActions.None {
            override fun seekTo(positionMs: Long) { seek = positionMs }
        })
        compose.onNodeWithContentDescription("Seek").performTouchInput {
            click(Offset(width * 0.75f, centerY))
        }
        assertTrue("Tap must seek near three quarters", seek in 70_000L..80_000L)
    }

    @Test fun seekIsDisabledWhenDurationIsUnknown() {
        render(expanded = true, durationMs = 0)
        compose.onNodeWithContentDescription("Seek").assertIsNotEnabled()
    }

    @Test fun lyricsScrubberCommitsADrag() {
        var seek = -1L
        render(expanded = true, page = 1, actions = object : PlayerActions by PlayerActions.None {
            override fun seekTo(positionMs: Long) { seek = positionMs }
        })
        compose.onNodeWithContentDescription("Seek").performTouchInput {
            swipe(Offset(width * 0.2f, centerY), Offset(width * 0.8f, centerY))
        }
        assertTrue("Drag must commit near its final position", seek in 70_000L..90_000L)
    }

    @Test
    fun timedLyricSeeksAndTransportWorks() {
        var seek = -1L
        var plays = 0
        render(expanded = true, page = 1, actions = object : PlayerActions by PlayerActions.None {
            override fun seekTo(positionMs: Long) { seek = positionMs }
            override fun togglePlay() { plays++ }
        })
        val line = runBlocking { FakeMusicSource().lyrics(SampleCatalog.tracks.first())!! }.lines.last { it.startMs <= 15_300 }
        compose.onNodeWithText(line.text).performClick()
        assertEquals(line.startMs, seek)
        compose.onNodeWithContentDescription("Play").performClick()
        assertEquals(1, plays)
    }

    @Test
    fun failedLyricsOfferTryAgain() {
        var retries = 0
        render(expanded = true, page = 1, lyricsState = LyricsState.Failed, actions = object : PlayerActions by PlayerActions.None {
            override fun retryLyrics() { retries++ }
        })
        compose.onNodeWithText("Lyrics didn’t load. Check your connection, then try again.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun speedAndPitchSheetSetsBoth() {
        val calls = mutableListOf<String>()
        render(expanded = true, actions = object : PlayerActions by PlayerActions.None {
            override fun setSpeed(speed: Float) { calls += "speed $speed" }
            override fun setPitch(semitones: Int) { calls += "pitch $semitones" }
        })
        compose.onNodeWithContentDescription("Speed and pitch").performClick()
        compose.onNodeWithText("Reset").assertDoesNotExist()
        compose.onNodeWithText("1.5×").performClick()
        compose.onNodeWithContentDescription("Pitch").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        assertEquals(listOf("speed 1.5", "pitch 2"), calls)
    }

    @Test
    fun changedSpeedShowsInTheTopBarAndResets() {
        var resets = 0
        render(expanded = true, tuning = PlaybackTuning(speed = 1.25f, semitones = -1), actions = object : PlayerActions by PlayerActions.None {
            override fun resetTuning() { resets++ }
        })
        compose.onNode(hasContentDescription("Speed and pitch") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1.25×, −1 semitone")).performClick()
        compose.onNodeWithText("Reset").performClick()
        assertEquals(1, resets)
    }

    @Test
    fun nowPlayingLikesTheSongAndOpensThePlaylistPicker() {
        var likes = 0
        render(expanded = true, actions = object : PlayerActions by PlayerActions.None {
            override fun toggleLike() { likes++ }
        })
        compose.onNodeWithContentDescription("Like").performClick()
        assertEquals(1, likes)
        compose.onNodeWithContentDescription("Add to playlist").performClick()
        compose.onNodeWithText("No playlists yet. Name one above and this song starts it.").assertIsDisplayed()
    }

    @Test
    fun upNextRemovesAndReordersSongs() {
        val calls = mutableListOf<String>()
        val upNext = SampleCatalog.tracks.drop(1).take(3)
        render(expanded = true, page = 2, upNext = upNext, actions = object : PlayerActions by PlayerActions.None {
            override fun removeUpNext(index: Int) { calls += "remove $index" }
            override fun moveUpNext(from: Int, to: Int) { calls += "move $from $to" }
        })
        compose.onNodeWithContentDescription("Remove ${upNext[1].title} from the queue").performClick()
        // Home's quick picks stay composed behind the player, so look inside Up next only.
        compose.onNode(hasContentDescription("${upNext[0].title} by ${upNext[0].artist}") and hasAnyAncestor(hasTestTag("up-next"))).performCustomAction("Move down")
        assertEquals(listOf("remove 1", "move 0 1"), calls)
    }
}
