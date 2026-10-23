package app.sitara.music.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.LargeTitle
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.displayName
import app.sitara.music.core.model.ScrobbleAccount
import app.sitara.music.core.model.ScrobbleService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Where signing in to a service stands. */
sealed interface SignInStep {
    data object Idle : SignInStep

    /** The token or the browser's answer is being checked. */
    data object Checking : SignInStep

    /** Last.fm is open in the browser; the listener allows access there and comes back. */
    data object InBrowser : SignInStep

    data class Failed(val problem: SignInProblem) : SignInStep
}

enum class SignInProblem {
    /** The service said no: a wrong token, or access not allowed. */
    REFUSED,

    /** The service could not be reached. */
    OFFLINE,
}

/** One service on the Scrobbling page. */
data class ScrobbleServiceState(
    val service: ScrobbleService,
    /** Null until the listener connects it. */
    val account: ScrobbleAccount? = null,
    /** Plays waiting to be sent. */
    val pending: Int = 0,
    val lastSentAt: Long? = null,
    /** False for Last.fm in a build made without a Last.fm API key. */
    val available: Boolean = true,
    val signIn: SignInStep = SignInStep.Idle,
)

data class ScrobblingState(
    val paused: Boolean = false,
    val services: List<ScrobbleServiceState> = ScrobbleService.entries.map(::ScrobbleServiceState),
) {
    /** What the Settings row says: the connected services, paused, or off. */
    val summary: String
        @Composable get() {
            val connected = services.filter { it.account != null }
            return when {
                connected.isEmpty() -> stringResource(R.string.settings_off)
                paused -> stringResource(R.string.settings_status_paused)
                else -> connected.map { it.service.displayName() }.joinToString(" · ")
            }
        }
}

interface ScrobblingActions {
    /** Checks a ListenBrainz user token and connects as its user. */
    fun connectListenBrainz(token: String)

    /** Opens Last.fm in the browser to allow access; the sign-in finishes when the listener comes back. */
    fun signInToLastFm()

    /** Checks whether access was allowed in the browser. */
    fun finishLastFmSignIn()

    /** Clears a failed or waiting sign-in. */
    fun cancelSignIn(service: ScrobbleService)

    fun sendNow()

    /** Signs out and drops the plays waiting for [service]. */
    fun disconnect(service: ScrobbleService)

    fun setLibraryOnly(service: ScrobbleService, libraryOnly: Boolean)

    fun setPaused(paused: Boolean)

    object None : ScrobblingActions {
        override fun connectListenBrainz(token: String) = Unit

        override fun signInToLastFm() = Unit

        override fun finishLastFmSignIn() = Unit

        override fun cancelSignIn(service: ScrobbleService) = Unit

        override fun sendNow() = Unit

        override fun disconnect(service: ScrobbleService) = Unit

        override fun setLibraryOnly(service: ScrobbleService, libraryOnly: Boolean) = Unit

        override fun setPaused(paused: Boolean) = Unit
    }
}

/** Settings → Scrobbling: each service's state and sign-in, waiting plays, and a pause for all of them. */
@Composable
fun ScrobblingScreen(
    state: ScrobblingState,
    actions: ScrobblingActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMs: Long = System.currentTimeMillis(),
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(modifier.fillMaxSize().testTag("scrobbling-content"), contentPadding = contentPadding) {
        item(key = "title") {
            GlassIconButton(SitaraIcons.Back, stringResource(R.string.settings_back), onBack, Modifier.padding(start = 12.dp, top = 8.dp))
            LargeTitle(stringResource(R.string.settings_scrobbling))
            Text(
                stringResource(R.string.settings_scrobbling_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SitaraSpacing.Gutter, end = SitaraSpacing.Gutter, bottom = 20.dp),
            )
        }
        if (state.services.any { it.account != null }) {
            item(key = "pause") {
                SettingsGroup(stringResource(R.string.settings_all_services)) {
                    SwitchRow(stringResource(R.string.settings_pause_scrobbling), stringResource(R.string.settings_pause_scrobbling_description), state.paused, actions::setPaused)
                }
            }
        }
        state.services.forEach { service ->
            item(key = service.service.name) { ServiceGroup(service, state.paused, actions, zone, nowMs) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceGroup(state: ScrobbleServiceState, paused: Boolean, actions: ScrobblingActions, zone: ZoneId, nowMs: Long) {
    val account = state.account
    SettingsGroup(state.service.displayName()) {
        // The chip moves under the title when both do not fit (large text, a long user name).
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    account != null -> stringResource(R.string.settings_signed_in_as, account.userName)
                    !state.available -> stringResource(R.string.settings_needs_api_key)
                    state.service == ScrobbleService.LISTENBRAINZ -> stringResource(R.string.settings_connect_token)
                    else -> stringResource(R.string.settings_connect_lastfm)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(end = 12.dp),
            )
            StatusChip(state, paused)
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                !state.available -> Description(stringResource(R.string.settings_no_api_key_description))
                account == null || account.signInNeeded -> SignInForm(state, actions)
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Description(lastSent(state.lastSentAt, zone, nowMs), Modifier.weight(1f).padding(end = 12.dp))
                        if (state.pending > 0) PendingBadge(state.pending)
                    }
                    ConnectedActions(state, actions)
                }
            }
        }
        if (account != null && !account.signInNeeded) {
            InsetDivider()
            SwitchRow(stringResource(R.string.settings_library_only), stringResource(R.string.settings_library_only_description), account.libraryOnly) {
                actions.setLibraryOnly(state.service, it)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectedActions(state: ScrobbleServiceState, actions: ScrobblingActions) {
    var confirming by rememberSaveable(state.service) { mutableStateOf(false) }
    if (confirming) {
        val service = state.service.displayName()
        Text(
            if (state.pending > 0) {
                pluralStringResource(R.plurals.settings_disconnect_confirm_pending, state.pending, state.pending, service)
            } else {
                stringResource(R.string.settings_disconnect_confirm, service)
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(stringResource(R.string.settings_disconnect), { confirming = false; actions.disconnect(state.service) })
            PillButton(stringResource(R.string.settings_stay_connected), { confirming = false }, primary = false)
        }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.pending > 0) PillButton(stringResource(R.string.settings_send_now), actions::sendNow)
            PillButton(stringResource(R.string.settings_disconnect), { confirming = true }, primary = false)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignInForm(state: ScrobbleServiceState, actions: ScrobblingActions) {
    val step = state.signIn
    if (state.account?.signInNeeded == true) {
        val service = state.service.displayName()
        Description(
            if (state.pending > 0) {
                pluralStringResource(R.plurals.settings_sign_in_ended_pending, state.pending, state.pending, service)
            } else {
                stringResource(R.string.settings_sign_in_ended, service)
            },
        )
    }
    when (state.service) {
        ScrobbleService.LISTENBRAINZ -> {
            // Not saveable: a token never goes into the state the system keeps for the activity.
            var token by remember { mutableStateOf("") }
            val problem = (step as? SignInStep.Failed)?.problem
            val problemText = problem?.let { problemWords(state.service, it) }
            OutlinedTextField(
                token, { token = it; if (problem != null) actions.cancelSignIn(state.service) }, Modifier.fillMaxWidth().testTag("listenbrainz-token"),
                label = { Text(stringResource(R.string.settings_user_token)) },
                singleLine = true,
                enabled = step != SignInStep.Checking,
                visualTransformation = PasswordVisualTransformation(),
                isError = problem != null,
                supportingText = {
                    Text(
                        problemText ?: stringResource(R.string.settings_token_help),
                        Modifier.semantics { if (problem != null) liveRegion = LiveRegionMode.Polite },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (token.isNotBlank()) actions.connectListenBrainz(token) }),
            )
            if (step == SignInStep.Checking) {
                Description(stringResource(R.string.settings_checking_token))
            } else if (token.isNotBlank()) {
                PillButton(stringResource(R.string.settings_connect), { actions.connectListenBrainz(token) })
            }
        }
        ScrobbleService.LASTFM -> when (step) {
            SignInStep.InBrowser -> {
                Description(stringResource(R.string.settings_lastfm_allow))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(stringResource(R.string.settings_allowed_it), actions::finishLastFmSignIn)
                    PillButton(stringResource(R.string.settings_cancel), { actions.cancelSignIn(state.service) }, primary = false)
                }
            }
            SignInStep.Checking -> Description(stringResource(R.string.settings_checking_lastfm))
            else -> {
                val problem = (step as? SignInStep.Failed)?.problem
                Description(problem?.let { problemWords(state.service, it) } ?: stringResource(R.string.settings_lastfm_sign_in_help))
                PillButton(stringResource(if (state.account == null) R.string.settings_sign_in_lastfm else R.string.settings_sign_in_again), actions::signInToLastFm)
            }
        }
    }
}

/** A one-line state with a dot; its words carry the meaning, the dot's colour only repeats it. */
@Composable
private fun StatusChip(state: ScrobbleServiceState, paused: Boolean) {
    val colors = MaterialTheme.colorScheme
    val account = state.account
    val (labelRes, dot) = when {
        !state.available -> R.string.settings_status_unavailable to colors.outline
        account == null -> R.string.settings_status_not_connected to colors.outline
        account.signInNeeded -> R.string.settings_sign_in_again to colors.error
        paused -> R.string.settings_status_paused to colors.outline
        else -> R.string.settings_status_connected to colors.primary
    }
    val label = stringResource(labelRes)
    val description = stringResource(R.string.settings_status_description, state.service.displayName(), label)
    Row(
        Modifier.clip(CircleShape).background(colors.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurface, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun PendingBadge(count: Int) {
    val spoken = waitingWords(count)
    Text(
        pluralStringResource(R.plurals.settings_pending_badge, count, count),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 10.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = spoken },
    )
}

@Composable
private fun Description(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun waitingWords(count: Int) = pluralStringResource(R.plurals.settings_plays_waiting, count, count)

@Composable
private fun problemWords(service: ScrobbleService, problem: SignInProblem) = when (problem) {
    SignInProblem.OFFLINE -> stringResource(R.string.settings_problem_offline, service.displayName())
    SignInProblem.REFUSED -> when (service) {
        ScrobbleService.LISTENBRAINZ -> stringResource(R.string.settings_problem_refused_listenbrainz)
        ScrobbleService.LASTFM -> stringResource(R.string.settings_problem_refused_lastfm)
    }
}

/** "Last sent today at 10:42", or on an earlier day its date. */
@Composable
private fun lastSent(sentAt: Long?, zone: ZoneId, nowMs: Long): String {
    sentAt ?: return stringResource(R.string.settings_nothing_sent)
    val sent = Instant.ofEpochMilli(sentAt).atZone(zone)
    return if (sent.toLocalDate() == Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()) {
        stringResource(R.string.settings_last_sent_today, sent.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
    } else {
        stringResource(R.string.settings_last_sent, sent.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
    }
}
