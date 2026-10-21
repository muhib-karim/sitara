package app.sitara.music.feature.home

import app.sitara.music.core.model.Chart
import app.sitara.music.core.model.HomePage
import app.sitara.music.core.model.HomeShelf
import app.sitara.music.core.model.MoodChip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The catalogue's Home feed, as far as it has loaded. */
sealed interface FeedState {
    data object Loading : FeedState

    data object Failed : FeedState

    data class Ready(val shelves: List<HomeShelf>, val continuation: String?, val paging: Paging = Paging.IDLE) : FeedState
}

/** Fetching the shelves after the ones shown. */
enum class Paging { IDLE, LOADING, FAILED }

/** The parts of Home that come from the network: the feed, its mood chips, and the trending chart that stands in when the feed fails. */
data class OnlineHome(
    val chips: List<MoodChip> = emptyList(),
    val feed: FeedState = FeedState.Loading,
    val trending: TrendingState = TrendingState.Loading,
) {
    val selectedChip: MoodChip? get() = chips.firstOrNull { it.selected }
}

interface HomeActions {
    /** Loads the feed again after it failed. */
    fun retryHome()

    /** Filters the feed to [chip], or back to all of Home when [chip] is the one selected. */
    fun selectMood(chip: MoodChip)

    /** Fetches the shelves after the ones shown, if there are more; also the retry after a page failed. */
    fun loadMoreHome()

    object None : HomeActions {
        override fun retryHome() = Unit
        override fun selectMood(chip: MoodChip) = Unit
        override fun loadMoreHome() = Unit
    }
}

/**
 * Loads [OnlineHome] in [scope] from the first page on. One request runs at a time: a chip tapped mid-load replaces it.
 * The chart is only fetched when the unfiltered feed fails, and kept once it has loaded.
 */
class OnlineHomeLoader(
    private val scope: CoroutineScope,
    private val firstPage: suspend (MoodChip?) -> HomePage,
    private val nextPage: suspend (continuation: String) -> HomePage,
    private val chart: suspend () -> Chart,
) : HomeActions {
    private val mutableState = MutableStateFlow(OnlineHome())
    val state: StateFlow<OnlineHome> = mutableState
    private var request: Job? = null

    init {
        load(null)
    }

    override fun retryHome() {
        if (state.value.feed == FeedState.Failed) load(state.value.selectedChip)
    }

    override fun selectMood(chip: MoodChip) {
        val selected = chip.takeUnless { it.selected }
        mutableState.update { home -> home.copy(chips = home.chips.map { it.copy(selected = it.params == selected?.params) }) }
        load(selected)
    }

    override fun loadMoreHome() {
        val feed = state.value.feed as? FeedState.Ready ?: return
        val continuation = feed.continuation ?: return
        if (feed.paging == Paging.LOADING) return
        mutableState.update { it.copy(feed = feed.copy(paging = Paging.LOADING)) }
        request = scope.launch {
            val next = try {
                val page = nextPage(continuation)
                FeedState.Ready(feed.shelves + page.shelves, page.continuation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                feed.copy(paging = Paging.FAILED)
            }
            mutableState.update { it.copy(feed = next) }
        }
    }

    private fun load(chip: MoodChip?) {
        request?.cancel()
        mutableState.update { it.copy(feed = FeedState.Loading) }
        request = scope.launch {
            try {
                val page = firstPage(chip)
                mutableState.update { it.copy(chips = page.chips.ifEmpty { it.chips }, feed = FeedState.Ready(page.shelves, page.continuation)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(feed = FeedState.Failed) }
                if (chip == null && state.value.trending !is TrendingState.Ready) loadChart()
            }
        }
    }

    private suspend fun loadChart() {
        mutableState.update { it.copy(trending = TrendingState.Loading) }
        val trending = try {
            TrendingState.Ready(chart())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TrendingState.Failed
        }
        mutableState.update { it.copy(trending = trending) }
    }
}
