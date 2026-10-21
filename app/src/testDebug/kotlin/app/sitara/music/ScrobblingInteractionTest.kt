package app.sitara.music

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import app.sitara.music.core.designsystem.SitaraTheme
import app.sitara.music.core.model.ScrobbleAccount
import app.sitara.music.core.model.ScrobbleService
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.feature.settings.ScrobbleServiceState
import app.sitara.music.feature.settings.ScrobblingActions
import app.sitara.music.feature.settings.ScrobblingScreen
import app.sitara.music.feature.settings.ScrobblingState
import app.sitara.music.feature.settings.SignInProblem
import app.sitara.music.feature.settings.SignInStep
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScrobblingInteractionTest {
    @get:Rule val compose = createComposeRule()

    private val done = mutableListOf<String>()
    private val actions = object : ScrobblingActions by ScrobblingActions.None {
        override fun connectListenBrainz(token: String) {
            done += "connect $token"
        }

        override fun sendNow() {
            done += "send"
        }

        override fun disconnect(service: ScrobbleService) {
            done += "disconnect ${service.label}"
        }

        override fun setPaused(paused: Boolean) {
            done += "paused $paused"
        }
    }

    private fun render(listenBrainz: ScrobbleServiceState, lastFm: ScrobbleServiceState = ScrobbleServiceState(ScrobbleService.LASTFM, available = false)) {
        compose.setContent {
            SitaraTheme(SitaraSettings(dynamicColor = false, reducedMotion = true), dark = true) {
                ScrobblingScreen(ScrobblingState(services = listOf(listenBrainz, lastFm)), actions, onBack = {})
            }
        }
    }

    private val connected = ScrobbleServiceState(ScrobbleService.LISTENBRAINZ, ScrobbleAccount(ScrobbleService.LISTENBRAINZ, "orchid"), pending = 3)

    @Test fun aPastedTokenConnects() {
        render(ScrobbleServiceState(ScrobbleService.LISTENBRAINZ))
        compose.onNodeWithTag("listenbrainz-token").performTextInput("token-1")
        compose.onNodeWithText("Connect").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("listenbrainz-token").performImeAction()
        assertEquals(listOf("connect token-1", "connect token-1"), done)
    }

    @Test fun aRefusedTokenIsExplainedUnderTheField() {
        render(ScrobbleServiceState(ScrobbleService.LISTENBRAINZ, signIn = SignInStep.Failed(SignInProblem.REFUSED)))
        compose.onNodeWithText("ListenBrainz doesn’t know this token. Copy it again from listenbrainz.org/settings.").assertIsDisplayed()
    }

    @Test fun waitingPlaysAreWordedForTalkBackAndCanBeSentNow() {
        render(connected)
        compose.onNodeWithContentDescription("3 plays waiting to be sent").assertIsDisplayed()
        compose.onNodeWithContentDescription("ListenBrainz: Connected").assertIsDisplayed()
        compose.onNodeWithText("Send now").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("send"), done)
    }

    @Test fun disconnectingAsksFirstAndSaysWhatIsDropped() {
        render(connected)
        compose.onNodeWithText("Disconnect").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Disconnect ListenBrainz? 3 plays waiting to be sent are dropped. Scrobbles already sent stay on your profile.").assertIsDisplayed()
        assertEquals(emptyList<String>(), done)

        compose.onNodeWithText("Disconnect").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("disconnect ListenBrainz"), done)
    }

    @Test fun pausingIsOneSwitchForEveryService() {
        render(connected)
        compose.onNodeWithText("Pause scrobbling").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("paused true"), done)
    }

    @Test fun aBuildWithoutALastFmKeySaysSoAndOffersNoButton() {
        render(connected)
        compose.onNodeWithTag("scrobbling-content").performScrollToNode(hasText("This build of Sitara", substring = true))
        compose.onNodeWithContentDescription("Last.fm: Unavailable").assertIsDisplayed()
        compose.onNodeWithText("Sign in with Last.fm").assertDoesNotExist()
    }
}
