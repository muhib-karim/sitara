package app.sitara.music

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.ImportProblem
import app.sitara.music.core.model.ImportResult
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.Track
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.ImportActions
import app.sitara.music.feature.library.ImportState
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.library.SavedLibrary
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.fake.SampleCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ImportInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : ImportActions {
        override fun setLink(text: String) { calls += "link $text" }
        override fun importLink() { calls += "import link" }
        override fun importFile(uri: String) { calls += "file $uri" }
        override fun resume() { calls += "resume" }
        override fun cancel() { calls += "cancel" }
        override fun choose(position: Int, track: Track) { calls += "choose $position ${track.id}" }
        override fun skip(position: Int) { calls += "skip $position" }
        override fun finish() { calls += "finish" }
        override fun close() { calls += "close" }
    }

    private fun render(state: ImportState, initialDetail: DetailRoute? = DetailRoute.Import) {
        compose.setContent {
            SitaraShell(
                settings = SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true),
                home = HomeFeed(),
                library = LibraryState.Ready(SavedLibrary(SampleCatalog.tracks.take(2), recent = emptyList(), playlists = emptyList(), albums = emptyList(), artists = emptyList())),
                query = "",
                searchFilter = SearchFilter.SONGS,
                search = SearchState.Idle,
                player = PlayerUiState(),
                actions = PlayerActions.None,
                details = SampleDetailPages(FakeMusicSource()),
                onQueryChange = {},
                onSearchFilterChange = {},
                onRetrySearch = {},
                onPlay = { _, _ -> },
                onShuffle = {},
                onSettingsChange = {},
                initialTab = Tab.LIBRARY,
                initialDetail = initialDetail,
                imports = state,
                importActions = actions,
            )
        }
    }

    private val content get() = compose.onNodeWithTag("import-content")

    private fun tap(text: String) {
        content.performScrollToNode(hasText(text))
        compose.onAllNodesWithText(text)[0].performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test fun libraryOpensImportPlaylist() {
        render(ImportState.Start(), initialDetail = null)
        compose.onNodeWithTag("library-content").performScrollToNode(hasText("Import playlist"))
        compose.onNodeWithText("Import playlist").performSemanticsAction(SemanticsActions.OnClick)
        content.performScrollToNode(hasText("Choose a file"))
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun aPastedLinkIsImported() {
        render(ImportState.Start())
        compose.onNodeWithTag("import-link").performTextInput("https://music.youtube.com/playlist?list=PL123abc")
        tap("Import link")
        assertEquals(listOf("link https://music.youtube.com/playlist?list=PL123abc", "import link"), calls)
    }

    @Test fun aProblemIsWordedAndOfflineOffersTryAgain() {
        render(ImportState.Start("https://music.youtube.com/playlist?list=PL123abc", problem = ImportProblem.OFFLINE))
        content.performScrollToNode(hasText("Couldn’t reach YouTube Music. Check your connection and try again."))
        tap("Try again")
        assertEquals(listOf("import link"), calls)
    }

    @Test fun anUnfinishedImportCanBeResumedOrDiscarded() {
        render(ImportState.Start(unfinished = Fixtures.importMatching))
        content.performScrollToNode(hasText("37 of 120 songs checked"))
        tap("Resume")
        tap("Discard")
        assertEquals(listOf("resume", "cancel"), calls)
    }

    @Test fun lookingSongsUpShowsTheCountsAndCancels() {
        render(ImportState.Running(Fixtures.importMatching))
        content.performScrollToNode(hasText("Not found"))
        tap("Cancel import")
        assertEquals(listOf("cancel"), calls)
    }

    @Test fun reviewTakesTheMatchAnotherCandidateOrSkips() {
        render(ImportState.Running(Fixtures.importReview))
        val row = Fixtures.importReview.review.first()
        tap("Use this")
        tap("Other matches")
        compose.onNodeWithText(row.candidates[1].title).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(listOf("choose 4 ${row.match!!.id}", "choose 4 ${row.candidates[1].id}"), calls)
    }

    @Test fun finishingAddsTheFoundSongs() {
        render(ImportState.Running(Fixtures.importReview))
        content.performScrollToNode(hasText("109 songs ready", substring = true))
        tap("Finish")
        assertEquals(listOf("finish"), calls)
    }

    @Test fun aFinishedImportOpensItsPlaylist() {
        render(ImportState.Done(ImportResult(playlistId = 1, name = "Road trip", added = 109, updated = false), leftOut = 11))
        content.performScrollToNode(hasText("109 songs · 11 left out"))
        tap("Open playlist")
        assertEquals(listOf("close"), calls)
    }
}
