package app.sitara.music

import app.sitara.music.core.model.ScrobbleService
import app.sitara.music.feature.settings.ScrobbleServiceState
import app.sitara.music.feature.settings.ScrobblingActions
import app.sitara.music.feature.settings.ScrobblingState
import app.sitara.music.feature.settings.SignInProblem
import app.sitara.music.feature.settings.SignInStep
import app.sitara.music.source.scrobble.SignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Scrobbling page's state, from the accounts, the outbox and sign-ins in progress, and its actions. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrobblingSettings(private val scope: CoroutineScope, private val scrobbling: Scrobbling) : ScrobblingActions {
    private val steps = MutableStateFlow(mapOf<ScrobbleService, SignInStep>())

    val state: StateFlow<ScrobblingState> = scrobbling.accounts.setup.flatMapLatest { setup ->
        val perService = ScrobbleService.entries.map { service ->
            combine(scrobbling.outbox.pendingCount(service), scrobbling.outbox.lastSentAt(service), steps) { pending, lastSent, step ->
                ScrobbleServiceState(
                    service,
                    account = setup.accounts.firstOrNull { it.service == service },
                    pending = pending,
                    lastSentAt = lastSent,
                    available = scrobbling.available(service),
                    signIn = step[service] ?: SignInStep.Idle,
                )
            }
        }
        combine(perService) { ScrobblingState(setup.paused, it.toList()) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), ScrobblingState())

    override fun connectListenBrainz(token: String) = signIn(ScrobbleService.LISTENBRAINZ) { scrobbling.connectListenBrainz(token) }

    override fun signInToLastFm() = Unit

    override fun finishLastFmSignIn() = Unit

    override fun cancelSignIn(service: ScrobbleService) = step(service, SignInStep.Idle)

    override fun sendNow() = scrobbling.send(now = true)

    override fun disconnect(service: ScrobbleService) {
        scope.launch { scrobbling.disconnect(service) }
    }

    override fun setLibraryOnly(service: ScrobbleService, libraryOnly: Boolean) {
        scope.launch { scrobbling.accounts.setLibraryOnly(service, libraryOnly) }
    }

    override fun setPaused(paused: Boolean) {
        scope.launch { scrobbling.accounts.setPaused(paused) }
    }

    private fun signIn(service: ScrobbleService, attempt: suspend () -> SignIn) {
        step(service, SignInStep.Checking)
        scope.launch { step(service, attempt().toStep()) }
    }

    private fun step(service: ScrobbleService, step: SignInStep) = steps.update { it + (service to step) }

    private fun SignIn.toStep(): SignInStep = when (this) {
        is SignIn.Done -> SignInStep.Idle
        SignIn.Refused -> SignInStep.Failed(SignInProblem.REFUSED)
        SignIn.Unreachable -> SignInStep.Failed(SignInProblem.OFFLINE)
        SignIn.Waiting -> SignInStep.InBrowser
    }
}
