package app.sitara.music.feature.search

import app.sitara.music.core.model.SearchResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

/** What the results area shows; [query] is the trimmed query it belongs to. */
sealed interface SearchState {
    val query: String

    data object Idle : SearchState {
        override val query = ""
    }

    data class Loading(override val query: String) : SearchState

    data class Loaded(override val query: String, val results: SearchResults) : SearchState

    data class Failed(override val query: String) : SearchState
}

/**
 * Searches as the user types: waits [debounceMs] after the last change, cancels a search that a newer query
 * replaces, goes idle at once when the query is cleared, and runs the current query again on every [retries] event.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun searchStates(queries: Flow<String>, retries: Flow<Unit>, debounceMs: Long, search: suspend (String) -> SearchResults): Flow<SearchState> =
    queries.map { it.trim() }
        .debounce { if (it.isEmpty()) 0L else debounceMs }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                flowOf(SearchState.Idle)
            } else {
                retries.onStart { emit(Unit) }.transformLatest {
                    emit(SearchState.Loading(query))
                    emit(
                        try {
                            SearchState.Loaded(query, search(query))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            SearchState.Failed(query)
                        },
                    )
                }
            }
        }
