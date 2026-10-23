package app.sitara.music

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SearchFilter
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.feature.home.HomeFeed
import app.sitara.music.feature.library.DetailState
import app.sitara.music.feature.library.LibraryState
import app.sitara.music.feature.player.PlayerActions
import app.sitara.music.feature.player.PlayerUiState
import app.sitara.music.feature.search.SearchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Sharing a song from its menu and an album from its page: the sheet with the QR code, and the link sent as text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ShareInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun render() {
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
                details = FixedPage(DetailState.AlbumReady(Fixtures.album)),
                onQueryChange = {},
                onSearchFilterChange = {},
                onRetrySearch = {},
                onPlay = { _, _ -> },
                onShuffle = {},
                onSettingsChange = {},
                initialTab = Tab.SEARCH,
                initialDetail = DetailRoute.Album(Fixtures.album.id),
            )
        }
    }

    private fun sentText(): String {
        val chooser = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        return send.getStringExtra(Intent.EXTRA_TEXT)!!
    }

    @Test fun anAlbumPageSharesItsLinkWithAQrCode() {
        render()
        compose.onNodeWithContentDescription("Share Paper Lanterns").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("QR code for https://music.youtube.com/playlist?list=OLAK5uy_fixture").assertIsDisplayed()
        compose.onNodeWithText("Send link").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals("Paper Lanterns: https://music.youtube.com/playlist?list=OLAK5uy_fixture", sentText())
    }

    @Test fun aSongSharesFromItsMenu() {
        render()
        val song = Fixtures.album.tracks.first()
        compose.onNodeWithTag("detail-content").performScrollToNode(hasText(song.title))
        compose.onNodeWithContentDescription("More options for ${song.title}").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Share").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag("qr-code").assertIsDisplayed()
        compose.onNodeWithText("Send link").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(sentText().endsWith("https://music.youtube.com/watch?v=${song.id}"))
    }
}
