package app.sitara.music

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.ListeningStats
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.StatsRange
import app.sitara.music.core.model.Track
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.library.SavedLibrary
import app.sitara.music.feature.library.StatsState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class StatsInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val asked = mutableListOf<StatsRange>()

    /** The sample stats for every range, except [empty] ones, which have no plays. */
    private fun pages(vararg empty: StatsRange) = object : DetailPages by SampleDetailPages(FakeMusicSource()) {
        override fun stats(range: StatsRange): StateFlow<StatsState> {
            asked += range
            return MutableStateFlow(StatsState.Ready(if (range in empty) ListeningStats.of(emptyList(), emptyList(), ZoneOffset.UTC) else sampleListeningStats(range)))
        }
    }

    private fun render(
        tab: Tab,
        details: DetailPages = pages(),
        settings: SitaraSettings = SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true),
        onSettings: (SitaraSettings) -> Unit = {},
        onClearHistory: () -> Unit = {},
        onPlay: (List<Track>, Int) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            SitaraShell(
                settings = settings,
                home = HomeFeed(),
                library = LibraryState.Ready(SavedLibrary(SampleCatalog.tracks.take(2), recent = emptyList(), playlists = emptyList(), albums = emptyList(), artists = emptyList())),
                query = "",
                searchFilter = SearchFilter.SONGS,
                search = SearchState.Idle,
                player = PlayerUiState(),
                actions = PlayerActions.None,
                details = details,
                onQueryChange = {},
                onSearchFilterChange = {},
                onRetrySearch = {},
                onPlay = onPlay,
                onShuffle = {},
                onSettingsChange = onSettings,
                initialTab = tab,
                onClearHistory = onClearHistory,
            )
        }
    }

    private val stats get() = compose.onNodeWithTag("stats-content")

    private fun tap(text: String) = compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)

    private fun openFromLibrary() {
        compose.onNodeWithTag("library-content").performScrollToNode(hasText("Your stats"))
        tap("Your stats")
    }

    @Test fun libraryOpensStatsForThisWeekWithTotalsAndTopSongs() {
        render(Tab.LIBRARY)
        openFromLibrary()

        compose.onNode(hasText("This week") and isSelectable()).assertIsSelected()
        assertEquals(StatsRange.WEEK, asked.last())
        val week = sampleListeningStats(StatsRange.WEEK)
        stats.performScrollToNode(hasText("${week.plays} plays"))
        stats.performScrollToNode(hasText(week.topSongs.first().track.title))
    }

    @Test fun aRangeChipAsksForThatRange() {
        render(Tab.LIBRARY)
        openFromLibrary()
        compose.onNode(hasText("All time") and isSelectable()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(StatsRange.ALL_TIME, asked.last())
        stats.performScrollToNode(hasText("${sampleListeningStats(StatsRange.ALL_TIME).plays} plays"))
    }

    @Test fun tappingATopSongPlaysTheTopSongsFromIt() {
        var played: Pair<List<Track>, Int>? = null
        render(Tab.LIBRARY, onPlay = { tracks, index -> played = tracks to index })
        openFromLibrary()
        val top = sampleListeningStats(StatsRange.WEEK).topSongs
        stats.performScrollToNode(hasText(top[1].track.title))
        tap(top[1].track.title)
        assertEquals(top.map { it.track } to 1, played)
    }

    @Test fun tappingATopArtistOpensTheirPage() {
        render(Tab.LIBRARY)
        openFromLibrary()
        val artist = sampleListeningStats(StatsRange.WEEK).topArtists.first()
        stats.performScrollToNode(hasContentDescription("${artist.name},", substring = true))
        compose.onNodeWithContentDescription("${artist.name},", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("detail-content").performScrollToNode(hasText("Albums and singles"))
    }

    @Test fun theChartReadsAsASummary() {
        render(Tab.LIBRARY)
        openFromLibrary()
        stats.performScrollToNode(hasContentDescription("Listening by hour of the day", substring = true))
    }

    @Test fun settingsOpensStats() {
        render(Tab.SETTINGS)
        compose.onNodeWithTag("settings-content").performScrollToNode(hasText("Your stats"))
        tap("Your stats")
        stats.performScrollToNode(hasText("Top songs"))
    }

    @Test fun shareShowsTheCardForTheRangeBeforeSharing() {
        render(Tab.LIBRARY)
        openFromLibrary()
        compose.onNodeWithContentDescription("Share your stats").performSemanticsAction(SemanticsActions.OnClick)
        val week = sampleListeningStats(StatsRange.WEEK)
        compose.onNodeWithTag("stats-share-card").assertContentDescriptionContains("This week on Sitara", substring = true)
        compose.onNodeWithTag("stats-share-card").assertContentDescriptionContains(week.topSongs.first().track.title, substring = true)
        compose.onNodeWithText("Share image").assertExists()
    }

    @Test fun aRangeWithoutPlaysHasNothingToShare() {
        render(Tab.LIBRARY, details = pages(StatsRange.WEEK))
        openFromLibrary()
        compose.onAllNodesWithContentDescription("Share your stats").assertCountEquals(0)
    }

    @Test fun settingsPauseListeningHistory() {
        var changed: SitaraSettings? = null
        render(Tab.SETTINGS, onSettings = { changed = it })
        compose.onNodeWithTag("settings-content").performScrollToNode(hasText("Pause listening history"))
        tap("Pause listening history")
        assertEquals(true, changed?.pauseListeningHistory)
    }

    @Test fun clearingListeningHistoryAsksFirst() {
        var cleared = 0
        render(Tab.SETTINGS, onClearHistory = { cleared++ })
        val list = compose.onNodeWithTag("settings-content")
        list.performScrollToNode(hasText("Clear listening history"))
        tap("Clear listening history")
        list.performScrollToNode(hasText("Keep history"))
        tap("Keep history")
        assertEquals("keeping cancels", 0, cleared)

        tap("Clear listening history")
        list.performScrollToNode(hasText("Clear history"))
        tap("Clear history")
        assertEquals(1, cleared)
    }

    @Test fun statsSayWhenHistoryIsPaused() {
        render(Tab.LIBRARY, settings = SitaraSettings(pauseListeningHistory = true, reducedMotion = true))
        openFromLibrary()
        stats.performScrollToNode(hasText("Listening history is paused", substring = true))
    }

    @Test fun aRangeWithoutPlaysSaysSoHonestly() {
        render(Tab.LIBRARY, details = pages(StatsRange.WEEK))
        openFromLibrary()
        stats.performScrollToNode(hasText("A play counts after 30 seconds", substring = true))
    }
}
