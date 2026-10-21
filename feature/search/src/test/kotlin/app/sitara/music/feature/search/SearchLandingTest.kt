package app.sitara.music.feature.search

import app.cash.turbine.test
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchLandingTest {
    @Test
    fun aSearchGoesFirstTidiedAndReplacesTheSameSearchInAnyCase() {
        val recent = listOf("jazz piano", "Lo-fi", "ambient")

        assertEquals(listOf("lo-fi beats", "jazz piano", "Lo-fi", "ambient"), recent.withRecentSearch("  lo-fi \n beats "))
        assertEquals(listOf("LO-FI", "jazz piano", "ambient"), recent.withRecentSearch("LO-FI"))
    }

    @Test
    fun recentSearchesKeepTheNewestTwenty() {
        val full = (1..20).map { "song $it" }

        val after = full.withRecentSearch("new")

        assertEquals(20, after.size)
        assertEquals("new", after.first())
        assertEquals("song 19", after.last())
    }

    @Test
    fun aBlankSearchIsNotKept() {
        assertEquals(listOf("jazz"), listOf("jazz").withRecentSearch("   "))
    }

    @Test
    fun typingAsksForSuggestionsOnceTheQuerySettlesAndLeavesOutWhatIsTyped() = runTest {
        val queries = MutableStateFlow("")
        val asked = mutableListOf<String>()
        searchSuggestions(queries, debounceMs = 200, recent = { emptyList() }) { q -> asked += q; listOf("lofi", "lofi girl", "Lofi hip hop") }.test {
            assertEquals(emptyList<String>(), awaitItem())
            queries.value = "lo"
            advanceTimeBy(100)
            runCurrent()
            queries.value = "lofi "

            assertEquals(listOf("lofi girl", "Lofi hip hop"), awaitItem())
            assertEquals(listOf("lofi"), asked)
        }
    }

    @Test
    fun whenSuggestionsFailTheMatchingRecentSearchesStandIn() = runTest {
        val queries = MutableStateFlow("")
        searchSuggestions(queries, debounceMs = 200, recent = { listOf("Lofi beats", "jazz", "lofi") }) { throw IOException("refused") }.test {
            assertEquals(emptyList<String>(), awaitItem())
            queries.value = "lof"

            assertEquals(listOf("Lofi beats", "lofi"), awaitItem())
        }
    }

    @Test
    fun clearingTheQueryClearsTheSuggestionsAtOnce() = runTest {
        val queries = MutableStateFlow("lofi")
        searchSuggestions(queries, debounceMs = 200, recent = { emptyList() }) { listOf("lofi girl") }.test {
            assertEquals(listOf("lofi girl"), awaitItem())
            queries.value = ""

            assertEquals(emptyList<String>(), awaitItem())
        }
    }
}
