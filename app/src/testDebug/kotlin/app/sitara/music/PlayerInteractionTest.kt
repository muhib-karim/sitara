package app.sitara.music

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.EqualiserPreset
import app.sitara.music.core.model.EqualiserSettings
import app.sitara.music.core.model.PlaybackTuning
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.LibraryActions
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.player.LyricsState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.player.SleepTimerStatus
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
        upNextFromRadio: Int = 0,
        libraryActions: LibraryActions = LibraryActions.None,
        lyricsState: LyricsState? = null,
        tuning: PlaybackTuning = PlaybackTuning(),
        sleepTimer: SleepTimerStatus = SleepTimerStatus.Off,
        failed: Boolean = false,
        equaliser: EqualiserSettings = EqualiserSettings(),
        systemEqualiser: Boolean = false,
        trimDb: Int = 0,
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
                player = PlayerUiState(
                    track = track, durationMs = durationMs, lyrics = lyricsState ?: LyricsState.Found(lyrics), positionMs = 15_300, upNext = upNext, upNextFromRadio = upNextFromRadio, tuning = tuning, sleepTimer = sleepTimer, failed = failed,
                    equaliser = equaliser, systemEqualiser = systemEqualiser, trimDb = trimDb,
                ),
                actions = actions,
                details = SampleDetailPages(source),
                onQueryChange = {},
                onSearchFilterChange = {},
                onRetrySearch = {},
                onPlay = { _, _ -> },
                onShuffle = {},
                onSettingsChange = {},
                playerExpanded = expanded,
                playerPage = page,
                greetingText = "Good evening",
                libraryActions = libraryActions,
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

    private class RecoveryActions : PlayerActions by PlayerActions.None {
        var retries = 0
        var skips = 0
        override fun retryPlayback() { retries++ }
        override fun next() { skips++ }
    }

    @Test
    fun failedSongInTheDockSaysSoAndOffersTryAgainAndSkipInsteadOfPlay() {
        val actions = RecoveryActions()
        render(actions = actions, failed = true)

        val inDock = hasAnyAncestor(hasTestTag("playback-dock"))
        compose.onNode(hasText("Couldn’t play this song") and inDock).assertIsDisplayed()
        compose.onNode(hasContentDescription("Play") and inDock).assertDoesNotExist()
        compose.onNode(hasContentDescription("Try again") and inDock).performClick()
        compose.onNode(hasContentDescription("Skip to next song") and inDock).performClick()
        assertEquals(1, actions.retries)
        assertEquals(1, actions.skips)
    }

    @Test
    fun failedSongInNowPlayingKeepsAStillScrubberAndOffersTryAgainAndSkip() {
        val actions = RecoveryActions()
        render(expanded = true, actions = actions, failed = true)

        compose.onNodeWithText("Couldn’t play this song").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seek").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Skip").performClick()
        assertEquals(1, actions.retries)
        assertEquals(1, actions.skips)
    }

    @Test
    @Config(qualifiers = "w360dp-h568dp-xxhdpi") // a 360 x 640 dp phone less a 24 dp status bar and a 48 dp navigation bar
    fun failedSongKeepsTheSeekBarAndTransportOnScreenOnASmallPhone() {
        render(expanded = true, failed = true)

        compose.onNodeWithText("Couldn’t play this song").assertIsDisplayed()
        val screenBottom = compose.onRoot().fetchSemanticsNode().size.height
        val notInDock = !hasAnyAncestor(hasTestTag("playback-dock"))
        listOf("Seek", "Shuffle", "Previous track", "Try again", "Next track", "Repeat").forEach { label ->
            // Unclipped: bounds in root are cut to the scrolling page, so a control below the fold would still look on screen.
            val node = compose.onNode(hasContentDescription(label) and notInDock).fetchSemanticsNode()
            val bottom = node.positionInRoot.y + node.size.height
            assertTrue("$label ends at $bottom px, below the screen's $screenBottom px", bottom <= screenBottom)
        }
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
    fun sleepTimerSheetStartsPresetsCustomMinutesAndEndOfSong() {
        val calls = mutableListOf<String>()
        val actions = object : PlayerActions by PlayerActions.None {
            override fun startSleepTimer(minutes: Int) { calls += "start $minutes" }
            override fun sleepAtEndOfTrack() { calls += "end of song" }
        }
        render(expanded = true, actions = actions)
        compose.onNode(hasContentDescription("Sleep timer") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off")).performClick()
        compose.onNodeWithText("Turn off").assertDoesNotExist()
        compose.onNodeWithText("30 min").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Sleep timer").performClick()
        compose.onNodeWithContentDescription("One minute more").performClick()
        compose.onNodeWithContentDescription("One minute more").performClick()
        compose.onNodeWithText("Start 22 min timer").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Sleep timer").performClick()
        compose.onNodeWithText("End of this song").performClick()
        assertEquals(listOf("start 30", "start 22", "end of song"), calls)
    }

    @Test
    fun runningSleepTimerExtendsAndTurnsOff() {
        val calls = mutableListOf<String>()
        render(expanded = true, sleepTimer = SleepTimerStatus.Counting(10 * 60_000L), actions = object : PlayerActions by PlayerActions.None {
            override fun extendSleepTimer(minutes: Int) { calls += "extend $minutes" }
            override fun cancelSleepTimer() { calls += "off" }
        })
        compose.onNodeWithContentDescription("Sleep timer").performClick()
        compose.onNodeWithText("Pauses in 10:00").assertIsDisplayed()
        compose.onNodeWithText("+5 min").performClick()
        compose.onNodeWithText("+15 min").performClick()
        compose.onNodeWithText("Turn off").performClick()
        assertEquals(listOf("extend 5", "extend 15", "off"), calls)
    }

    @Test
    fun nowPlayingLikesTheSongAndOpensThePlaylistPicker() {
        var likes = 0
        render(expanded = true, actions = object : PlayerActions by PlayerActions.None {
            override fun toggleLike() { likes++ }
        })
        compose.onNodeWithContentDescription("Like").performClick()
        assertEquals(1, likes)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Add to playlist").performClick()
        compose.onNodeWithText("No playlists yet. Name one above and this song starts it.").assertIsDisplayed()
    }

    @Test
    fun lyricsHeaderLikesTheSongAndOpensMoreOptions() {
        var likes = 0
        render(expanded = true, page = 1, actions = object : PlayerActions by PlayerActions.None {
            override fun toggleLike() { likes++ }
        })
        compose.onNodeWithContentDescription("Like").performClick()
        assertEquals(1, likes)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Add to playlist").assertIsDisplayed()
    }

    @Test
    fun nowPlayingEqualiserPicksPresetsAndMovesBandsWithAnnouncedGains() {
        val picked = mutableListOf<EqualiserSettings>()
        render(expanded = true, actions = object : PlayerActions by PlayerActions.None {
            override fun setEqualiser(equaliser: EqualiserSettings) { picked += equaliser }
        })
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Equaliser").performClick()
        compose.onNodeWithText("Rock").performScrollTo().performClick()
        val band = compose.onNodeWithContentDescription("1 kHz").performScrollTo()
        band.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 dB"))
        band.performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        compose.onNodeWithContentDescription("Preamp").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(-4f) }
        assertEquals(
            listOf(EqualiserSettings().withPreset(EqualiserPreset.ROCK), EqualiserSettings().withBand(5, 3), EqualiserSettings().withPreamp(-4)),
            picked,
        )
    }

    @Test
    fun everyBandIsNamedForScreenReaders() {
        render(expanded = true, equaliser = EqualiserSettings().withPreset(EqualiserPreset.BASS_BOOST))
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Bass boost").assertIsDisplayed()
        compose.onNodeWithText("Equaliser").performClick()
        listOf("31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz").zip(EqualiserPreset.BASS_BOOST.bandsDb).forEach { (name, db) ->
            compose.onNodeWithContentDescription(name).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, if (db > 0) "+$db dB" else "$db dB"))
                // Layout bounds are measured before the fader's quarter turn, so its length runs across.
                .assertWidthIsAtLeast(160.dp)
        }
    }

    @Test
    fun withThePhoneEqualiserChosenTheBandsGiveWayToIt() {
        var opened = 0
        val picked = mutableListOf<EqualiserSettings>()
        val system = EqualiserSettings(useSystem = true).withPreset(EqualiserPreset.ROCK)
        render(expanded = true, systemEqualiser = true, equaliser = system, actions = object : PlayerActions by PlayerActions.None {
            override fun openSystemEqualiser() { opened++ }
            override fun setEqualiser(equaliser: EqualiserSettings) { picked += equaliser }
        })
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Phone equaliser").assertIsDisplayed()
        compose.onNodeWithText("Equaliser").performClick()
        compose.onNodeWithContentDescription("1 kHz").assertDoesNotExist()
        compose.onNodeWithText("Open phone equaliser").performClick()
        compose.onNodeWithText("Use phone equaliser").performClick()
        assertEquals(1, opened)
        assertEquals(listOf(system.copy(useSystem = false)), picked)
    }

    @Test
    fun withoutAPhoneEqualiserItIsNotOffered() {
        render(expanded = true)
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Equaliser").performClick()
        compose.onNodeWithText("Use phone equaliser").assertDoesNotExist()
    }

    @Test
    fun nowPlayingSetsAndResetsTheSongsVolume() {
        val trims = mutableListOf<Int>()
        render(expanded = true, trimDb = 2, actions = object : PlayerActions by PlayerActions.None {
            override fun setTrimDb(db: Int) { trims += db }
        })
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("+2 dB").assertIsDisplayed()
        compose.onNodeWithText("Volume for this song").performClick()
        val track = SampleCatalog.tracks.first()
        compose.onNodeWithContentDescription("Volume for ${track.title}")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "+2 dB"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(-5f) }
        compose.onNodeWithText("Reset").performClick()
        assertEquals(listOf(-5, 0), trims)
    }

    @Test
    fun settingsOpensTheEqualiserPageAndBackReturns() {
        val picked = mutableListOf<EqualiserSettings>()
        render(actions = object : PlayerActions by PlayerActions.None {
            override fun setEqualiser(equaliser: EqualiserSettings) { picked += equaliser }
        })
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("settings-content").performScrollToNode(hasText("Equaliser"))
        compose.onNodeWithText("Equaliser").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Late night").performScrollTo().performClick()
        assertEquals(listOf(EqualiserSettings().withPreset(EqualiserPreset.LATE_NIGHT)), picked)
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithText("Normalise loudness").assertExists()
    }

    @Test
    fun upNextRemovesAndReordersSongs() {
        val calls = mutableListOf<String>()
        val upNext = SampleCatalog.tracks.drop(1).take(3)
        render(expanded = true, page = 2, upNext = upNext, actions = object : PlayerActions by PlayerActions.None {
            override fun removeUpNext(index: Int) { calls += "remove $index" }
            override fun moveUpNext(from: Int, to: Int) { calls += "move $from $to" }
            override fun undoRemoveUpNext() { calls += "undo" }
        })
        // A clock moved by hand shows the snackbar and keeps it up until Undo is tapped.
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Remove ${upNext[1].title} from the queue").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Removed ${upNext[1].title}").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.mainClock.autoAdvance = true
        // Home's quick picks stay composed behind the player, so look inside Up next only.
        val first = compose.onNode(hasContentDescription("${upNext[0].title} by ${upNext[0].artist}") and hasAnyAncestor(hasTestTag("up-next")))
        first.performCustomAction("Move down")
        // The moved song shows in its new place at once, so it is removed from there.
        first.performCustomAction("Remove from the queue")
        first.performTouchInput { swipeLeft() }
        assertEquals(listOf("remove 1", "undo", "move 0 1", "remove 1", "remove 1"), calls)
    }

    @Test
    fun radioSongsHaveTheirOwnSectionAndMoveOnlyAmongThemselves() {
        val calls = mutableListOf<String>()
        val upNext = SampleCatalog.tracks.drop(1).take(4)
        render(expanded = true, page = 2, upNext = upNext, upNextFromRadio = 2, actions = object : PlayerActions by PlayerActions.None {
            override fun moveUpNext(from: Int, to: Int) { calls += "move $from $to" }
            override fun removeUpNext(index: Int) { calls += "remove $index" }
        })
        compose.onNodeWithText("Up next from radio").assertIsDisplayed()
        fun row(track: Track) = compose.onNode(hasContentDescription("${track.title} by ${track.artist}") and hasAnyAncestor(hasTestTag("up-next")))
        assertTrue("the last own song cannot move into radio's", row(upNext[1]).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().none { it.label == "Move down" })
        row(upNext[2]).performCustomAction("Move down")
        row(upNext[2]).performCustomAction("Remove from the queue")
        assertEquals("the moved radio song is removed from its new place", listOf("move 2 3", "remove 3"), calls)
    }

    @Test
    fun savingTheQueueCreatesAPlaylistInPlayOrder() {
        var saved: Pair<String, List<Track>>? = null
        val upNext = SampleCatalog.tracks.drop(1).take(3)
        render(expanded = true, page = 2, upNext = upNext, libraryActions = object : LibraryActions by LibraryActions.None {
            override fun createPlaylist(name: String, tracks: List<Track>) { saved = name to tracks }
        })
        compose.onNodeWithText("Save as playlist").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Road trip")
        // The keyboard's Done key saves, as the Save button does, and closes the keyboard.
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals("Road trip" to listOf(SampleCatalog.tracks.first()) + upNext, saved)
    }
}
