package app.sitara.music.feature.search

import app.sitara.music.core.model.MoodSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

private const val MAX_RECENT_SEARCHES = 20
private const val MAX_SUGGESTIONS = 6
private val WHITESPACE = Regex("\\s+")

/** [query] first, on one line with single spaces, replacing an earlier search for the same words in any case; the newest twenty are kept. */
fun List<String>.withRecentSearch(query: String): List<String> {
    val tidy = query.trim().replace(WHITESPACE, " ")
    if (tidy.isEmpty()) return this
    return (listOf(tidy) + filterNot { it.equals(tidy, ignoreCase = true) }).take(MAX_RECENT_SEARCHES)
}

/**
 * Completions for the query being typed, asked for once it settles for [debounceMs]; none for an empty query. When the
 * catalogue does not answer, the [recent] searches that contain the query stand in. The query itself is never offered.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun searchSuggestions(queries: Flow<String>, debounceMs: Long, recent: () -> List<String>, suggest: suspend (String) -> List<String>): Flow<List<String>> =
    queries.map { it.trim() }.debounce { if (it.isEmpty()) 0L else debounceMs }.distinctUntilChanged().mapLatest { query ->
        if (query.isEmpty()) return@mapLatest emptyList()
        val offered = try {
            suggest(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recent().filter { it.contains(query, ignoreCase = true) }
        }
        offered.filterNot { it.equals(query, ignoreCase = true) }.distinct().take(MAX_SUGGESTIONS)
    }

/** The catalogue's moods and genres for the empty Search page. */
sealed interface MoodsState {
    data object Loading : MoodsState

    data object Failed : MoodsState

    data class Ready(val sections: List<MoodSection>) : MoodsState
}

/** What Search shows around the results: recent searches and Moods & genres when nothing is typed, suggestions while typing. */
data class SearchLanding(
    val recent: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val moods: MoodsState = MoodsState.Loading,
)

interface SearchActions {
    /** Keeps [query] as the newest recent search. */
    fun saveSearch(query: String)

    fun removeRecentSearch(query: String)

    fun clearRecentSearches()

    /** Loads Moods & genres again after they failed. */
    fun retryMoods()

    object None : SearchActions {
        override fun saveSearch(query: String) = Unit
        override fun removeRecentSearch(query: String) = Unit
        override fun clearRecentSearches() = Unit
        override fun retryMoods() = Unit
    }
}
