package app.sitara.music.feature.home

import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.Chart
import app.sitara.music.core.model.HomeItem
import app.sitara.music.core.model.HomePage
import app.sitara.music.core.model.HomeShelf
import app.sitara.music.core.model.MoodChip
import app.sitara.music.core.model.ShelfLayout
import app.sitara.music.core.model.Track
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineHomeLoaderTest {
    private val art = Artwork(listOf(0xFF101010.toInt()))
    private val song = Track("aaaaaaaaaaa", "Song", "Artist", "", 0, art)
    private fun shelf(title: String) = HomeShelf(title, null, ShelfLayout.SONG_ROWS, listOf(HomeItem.Song(song)))
    private val relax = MoodChip("Relax", "relax-params", selected = false)
    private val focus = MoodChip("Focus", "focus-params", selected = false)
    private val chart = Chart("YouTube Charts · Iceland", listOf(song))

    private val firstPages = mutableListOf<MoodChip?>()
    private val nextPages = mutableListOf<String>()
    private var charts = 0

    private fun TestScope.loader(
        first: suspend (MoodChip?) -> HomePage = { HomePage(listOf(relax, focus), listOf(shelf("Quick picks")), "page-2") },
        next: suspend (String) -> HomePage = { HomePage(emptyList(), listOf(shelf("New releases")), null) },
    // The test's own scope: advanceUntilIdle() runs its tasks, and runTest waits for the loader's requests to finish.
    ) = OnlineHomeLoader(
        this,
        firstPage = { firstPages += it; first(it) },
        nextPage = { nextPages += it; next(it) },
        chart = { charts++; chart },
    )

    @Test fun startsLoadingThenShowsTheFeedWithItsChipsWithoutAskingForTheChart() = runTest {
        val home = loader()
        assertEquals(FeedState.Loading, home.state.value.feed)

        advanceUntilIdle()

        assertEquals(listOf(relax, focus), home.state.value.chips)
        assertEquals(listOf("Quick picks"), (home.state.value.feed as FeedState.Ready).shelves.map { it.title })
        assertEquals(listOf<MoodChip?>(null), firstPages)
        assertEquals(0, charts)
    }

    @Test fun aRefusedFeedFallsBackToTheTrendingChart() = runTest {
        val home = loader(first = { throw IOException("refused") })
        advanceUntilIdle()

        assertEquals(FeedState.Failed, home.state.value.feed)
        assertEquals(TrendingState.Ready(chart), home.state.value.trending)
    }

    @Test fun tryAgainLoadsTheFeedAgainAndKeepsAChartThatLoaded() = runTest {
        var refuse = true
        val home = loader(first = { if (refuse) throw IOException("refused") else HomePage(emptyList(), listOf(shelf("Quick picks")), null) })
        advanceUntilIdle()

        refuse = false
        home.retryHome()
        advanceUntilIdle()

        assertTrue(home.state.value.feed is FeedState.Ready)
        assertEquals(2, firstPages.size)
        assertEquals(1, charts)
    }

    @Test fun aChipIsSelectedAtOnceAndFiltersTheFeedThenTappingItAgainClearsTheFilter() = runTest {
        val gate = CompletableDeferred<Unit>()
        val home = loader(first = { chip ->
            if (chip != null) gate.await()
            HomePage(listOf(relax.copy(selected = chip == relax), focus), listOf(shelf(chip?.title ?: "Quick picks")), null)
        })
        advanceUntilIdle()

        home.selectMood(relax)
        advanceUntilIdle()
        assertEquals(listOf(true, false), home.state.value.chips.map { it.selected })
        assertEquals(FeedState.Loading, home.state.value.feed)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("Relax"), (home.state.value.feed as FeedState.Ready).shelves.map { it.title })

        home.selectMood(home.state.value.chips.first())
        advanceUntilIdle()
        assertEquals(listOf(null, relax, null), firstPages)
        assertTrue(home.state.value.chips.none { it.selected })
    }

    @Test fun loadingMoreAppendsTheNextShelvesAndStopsAtTheLastPage() = runTest {
        val home = loader()
        advanceUntilIdle()

        home.loadMoreHome()
        advanceUntilIdle()
        home.loadMoreHome()
        advanceUntilIdle()

        val feed = home.state.value.feed as FeedState.Ready
        assertEquals(listOf("Quick picks", "New releases"), feed.shelves.map { it.title })
        assertEquals(null, feed.continuation)
        assertEquals(listOf("page-2"), nextPages)
    }

    @Test fun aFailedPageKeepsTheShelvesAndLoadsAgainWhenAskedAgain() = runTest {
        var refuse = true
        val home = loader(next = { if (refuse) throw IOException("refused") else HomePage(emptyList(), listOf(shelf("New releases")), null) })
        advanceUntilIdle()

        home.loadMoreHome()
        advanceUntilIdle()
        val failed = home.state.value.feed as FeedState.Ready
        assertEquals(Paging.FAILED, failed.paging)
        assertEquals(listOf("Quick picks"), failed.shelves.map { it.title })

        refuse = false
        home.loadMoreHome()
        advanceUntilIdle()
        assertEquals(listOf("Quick picks", "New releases"), (home.state.value.feed as FeedState.Ready).shelves.map { it.title })
    }
}
