package app.sitara.music

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import app.sitara.music.feature.settings.BackupFailure
import app.sitara.music.feature.settings.BackupStatus
import app.sitara.music.source.fake.FakeMusicSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Settings → Backup and restore through the shell: the file pickers it opens and what each outcome says. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class BackupInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render(status: BackupStatus = BackupStatus.Idle, route: DetailRoute? = DetailRoute.Backup) {
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
                initialDetail = route,
                backup = status,
            )
        }
    }

    private fun tap(text: String) {
        compose.onNodeWithTag("backup-content").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test fun settingsOpensBackupAndRestore() {
        render(route = null)
        compose.onNodeWithTag("settings-content").performScrollToNode(hasText("Backup and restore"))
        compose.onNodeWithText("Backup and restore").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("backup-content").performScrollToNode(hasText("Back up to a file"))
    }

    @Test fun backingUpAsksWhereToSaveAJsonFileNamedForToday() {
        render()
        tap("Back up to a file")
        val picker = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.action)
        assertEquals("application/json", picker.type)
        assertTrue(picker.getStringExtra(Intent.EXTRA_TITLE)!!.matches(Regex("""sitara-backup-\d{4}-\d{2}-\d{2}\.json""")))
    }

    @Test fun restoringSaysWhatItReplacesBeforeAnyFileIsPicked() {
        render()
        tap("Restore from a file")
        assertEquals(null, shadowOf(compose.activity).nextStartedActivity)
        compose.onNodeWithText("Replace your library with a backup?", substring = true).assertIsDisplayed()
        tap("Choose a backup")
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, shadowOf(compose.activity).nextStartedActivity.action)
    }

    @Test fun cancellingTheRestorePicksNothing() {
        render()
        tap("Restore from a file")
        tap("Cancel")
        assertEquals(null, shadowOf(compose.activity).nextStartedActivity)
        compose.onNodeWithText("Restore from a file").assertIsDisplayed()
    }

    @Test fun aDamagedBackupSaysNothingChanged() {
        render(BackupStatus.Failed(BackupFailure.DAMAGED))
        compose.onNodeWithTag("backup-content").performScrollToNode(hasText("That backup is damaged or incomplete, so nothing was changed. Try another copy."))
    }

    @Test fun aRestoreSaysWhatCameBack() {
        render(BackupStatus.Restored(likedSongs = 1_000, playlists = 3, plays = 1))
        compose.onNodeWithTag("backup-content").performScrollToNode(hasText("Restored 1,000 liked songs, 3 playlists and 1 play, and your settings."))
    }

    @Test fun whileWorkingNeitherButtonCanStartAnother() {
        render(BackupStatus.Working)
        compose.onNodeWithTag("backup-content").performScrollToNode(hasText("Working on it…"))
        assertThrows(AssertionError::class.java) { compose.onNodeWithText("Back up to a file").assertIsDisplayed() }
        assertThrows(AssertionError::class.java) { compose.onNodeWithText("Restore from a file").assertIsDisplayed() }
    }
}
