package app.sitara.music.feature.search

import app.cash.turbine.test
import app.sitara.music.core.model.SearchResults
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchStatesTest {
    @Test
    fun typingSearchesOnceForTheSettledTrimmedQuery() = runTest {
        val queries = MutableStateFlow("")
        val searched = mutableListOf<String>()
        searchStates(queries, emptyFlow(), debounceMs = 300) { searched += it; SearchResults.empty(it) }.test {
            assertEquals(SearchState.Idle, awaitItem())
            queries.value = "n"
            advanceTimeBy(100)
            runCurrent()
            queries.value = "night "

            assertEquals(SearchState.Loading("night"), awaitItem())
            assertEquals(SearchState.Loaded("night", SearchResults.empty("night")), awaitItem())
            assertEquals(listOf("night"), searched)
        }
    }

    @Test
    fun failureOffersRetryWhichRunsTheSameQueryAgain() = runTest {
        val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var attempts = 0
        searchStates(MutableStateFlow("lofi"), retries, debounceMs = 300) {
            attempts++
            if (attempts == 1) throw IOException("offline")
            SearchResults.empty(it)
        }.test {
            assertEquals(SearchState.Loading("lofi"), awaitItem())
            assertEquals(SearchState.Failed("lofi"), awaitItem())

            retries.emit(Unit)

            assertEquals(SearchState.Loading("lofi"), awaitItem())
            assertEquals(SearchState.Loaded("lofi", SearchResults.empty("lofi")), awaitItem())
            assertEquals(2, attempts)
        }
    }

    @Test
    fun aNewQueryCancelsTheSearchItReplaces() = runTest {
        val queries = MutableStateFlow("a")
        searchStates(queries, emptyFlow(), debounceMs = 300) { delay(1_000); SearchResults.empty(it) }.test {
            assertEquals(SearchState.Loading("a"), awaitItem())

            queries.value = "ab"

            assertEquals(SearchState.Loading("ab"), awaitItem())
            assertEquals(SearchState.Loaded("ab", SearchResults.empty("ab")), awaitItem())
        }
    }

    @Test
    fun clearingTheQueryGoesIdleWithoutWaiting() = runTest {
        val queries = MutableStateFlow("jazz")
        searchStates(queries, emptyFlow(), debounceMs = 300) { SearchResults.empty(it) }.test {
            assertEquals(SearchState.Loading("jazz"), awaitItem())
            assertEquals(SearchState.Loaded("jazz", SearchResults.empty("jazz")), awaitItem())
            val clearedAt = currentTime

            queries.value = ""

            assertEquals(SearchState.Idle, awaitItem())
            assertEquals(clearedAt, currentTime)
        }
    }
}
