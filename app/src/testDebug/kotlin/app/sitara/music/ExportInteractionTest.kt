package app.sitara.music

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.DownloadExport
import app.sitara.music.core.model.ExportProblem
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.SongDownload
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.source.fake.FakeMusicSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Settings → Downloads → Export to a folder through the shell: the folder picker it opens and what each outcome says. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ExportInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(export: DownloadExport = DownloadExport.Idle, downloads: List<SongDownload> = Fixtures.downloads) {
        compose.setContent {
            SitaraShell(
                settings = SitaraSettings(darkMode = DarkMode.DARK, reducedMotion = true),
                home = HomeFeed(),
                library = LibraryState.Loading,
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
                initialSettingsOpen = true,
                downloads = downloads,
                export = export,
            )
        }
    }

    private fun shows(text: String) = compose.onNodeWithTag("settings-content").performScrollToNode(hasText(text))

    @Test fun exportAsksForAFolder() {
        render()
        shows("Export to a folder")
        compose.onNodeWithText("Export").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, shadowOf(compose.activity).nextStartedActivity.action)
    }

    @Test fun beforeAnExportTheRowSaysWhatItWrites() {
        render()
        shows("Tagged files with their covers, and .lrc lyrics where there are synced lyrics, for any music app")
    }

    @Test fun aFolderThatCannotBeWrittenIsWorded() {
        render(DownloadExport.Failed(ExportProblem.FOLDER_UNWRITABLE))
        shows("Couldn't write to that folder. Pick another and try again.")
    }

    @Test fun withNothingDownloadedThereIsNoExportButton() {
        render(downloads = emptyList())
        shows("Export to a folder")
        compose.onNodeWithText("Export").assertDoesNotExist()
    }
}
