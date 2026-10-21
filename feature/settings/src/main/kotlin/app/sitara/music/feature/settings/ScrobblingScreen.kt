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
        get() {
            val connected = services.filter { it.account != null }
            return when {
                connected.isEmpty() -> "Off"
                paused -> "Paused"
                else -> connected.joinToString(" · ") { it.service.label }
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
            GlassIconButton(SitaraIcons.Back, "Back", onBack, Modifier.padding(start = 12.dp, top = 8.dp))
            LargeTitle("Scrobbling")
            Text(
                "Send the songs you listen to to your Last.fm or ListenBrainz profile. A song counts once half of it, or 4 minutes, is heard; " +
                    "plays wait on this phone while you’re offline and each is sent once.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SitaraSpacing.Gutter, end = SitaraSpacing.Gutter, bottom = 20.dp),
            )
        }
        if (state.services.any { it.account != null }) {
            item(key = "pause") {
                SettingsGroup("All services") {
                    SwitchRow("Pause scrobbling", "New plays aren’t sent or kept for sending", state.paused, actions::setPaused)
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
    SettingsGroup(state.service.label) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                account?.let { "Signed in as ${it.userName}" } ?: state.service.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            StatusChip(state, paused)
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                !state.available -> Description("This build of Sitara was made without a Last.fm API key, so it can’t sign in to Last.fm.")
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
            SwitchRow("Only songs in your library", "Liked songs and songs in your playlists", account.libraryOnly) {
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
        val plays = if (state.pending == 1) "1 play waiting to be sent is" else "${state.pending} plays waiting to be sent are"
        Text(
            "Disconnect ${state.service.label}? " + (if (state.pending > 0) "$plays dropped. " else "") + "Scrobbles already sent stay on your profile.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Disconnect", { confirming = false; actions.disconnect(state.service) })
            PillButton("Stay connected", { confirming = false }, primary = false)
        }
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.pending > 0) PillButton("Send now", actions::sendNow)
            PillButton("Disconnect", { confirming = true }, primary = false)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignInForm(state: ScrobbleServiceState, actions: ScrobblingActions) {
    val step = state.signIn
    if (state.account?.signInNeeded == true) {
        val waiting = if (state.pending > 0) " ${waitingWords(state.pending).replaceFirstChar(Char::uppercase)} until you do." else ""
        Description("${state.service.label} ended the sign-in. Sign in again to keep scrobbling.$waiting")
    }
    when (state.service) {
        ScrobbleService.LISTENBRAINZ -> {
            // Not saveable: a token never goes into the state the system keeps for the activity.
            var token by remember { mutableStateOf("") }
            val problem = (step as? SignInStep.Failed)?.problem
            OutlinedTextField(
                token, { token = it; if (problem != null) actions.cancelSignIn(state.service) }, Modifier.fillMaxWidth().testTag("listenbrainz-token"),
                label = { Text("User token") },
                singleLine = true,
                enabled = step != SignInStep.Checking,
                visualTransformation = PasswordVisualTransformation(),
                isError = problem != null,
                supportingText = {
                    Text(
                        problem?.let { problemWords(state.service, it) } ?: "Copy it from listenbrainz.org/settings",
                        Modifier.semantics { if (problem != null) liveRegion = LiveRegionMode.Polite },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (token.isNotBlank()) actions.connectListenBrainz(token) }),
            )
            if (step == SignInStep.Checking) {
                Description("Checking the token…")
            } else if (token.isNotBlank()) {
                PillButton("Connect", { actions.connectListenBrainz(token) })
            }
        }
        ScrobbleService.LASTFM -> when (step) {
            SignInStep.InBrowser -> {
                Description("Allow Sitara on the Last.fm page in your browser, then come back here.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("I’ve allowed it", actions::finishLastFmSignIn)
                    PillButton("Cancel", { actions.cancelSignIn(state.service) }, primary = false)
                }
            }
            SignInStep.Checking -> Description("Checking with Last.fm…")
            else -> {
                val problem = (step as? SignInStep.Failed)?.problem
                Description(problem?.let { problemWords(state.service, it) } ?: "Sign in on last.fm in your browser; Sitara never sees your password.")
                PillButton(if (state.account == null) "Sign in with Last.fm" else "Sign in again", actions::signInToLastFm)
            }
        }
    }
}

/** A one-line state with a dot; its words carry the meaning, the dot's colour only repeats it. */
@Composable
private fun StatusChip(state: ScrobbleServiceState, paused: Boolean) {
    val colors = MaterialTheme.colorScheme
    val account = state.account
    val (label, dot) = when {
        !state.available -> "Unavailable" to colors.outline
        account == null -> "Not connected" to colors.outline
        account.signInNeeded -> "Sign in again" to colors.error
        paused -> "Paused" to colors.outline
        else -> "Connected" to colors.primary
    }
    Row(
        Modifier.clip(CircleShape).background(colors.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${state.service.label}: $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurface, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun PendingBadge(count: Int) {
    Text(
        "$count waiting",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimary,
        maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 10.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = waitingWords(count) },
    )
}

@Composable
private fun Description(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

private fun waitingWords(count: Int) = if (count == 1) "1 play waiting to be sent" else "$count plays waiting to be sent"

private fun problemWords(service: ScrobbleService, problem: SignInProblem) = when (problem) {
    SignInProblem.OFFLINE -> "${service.label} can’t be reached. Check your connection and try again."
    SignInProblem.REFUSED -> when (service) {
        ScrobbleService.LISTENBRAINZ -> "ListenBrainz doesn’t know this token. Copy it again from listenbrainz.org/settings."
        ScrobbleService.LASTFM -> "Last.fm didn’t allow access. Sign in again and choose Yes, allow access."
    }
}

/** "Last sent today at 10:42", or on an earlier day its date. */
private fun lastSent(sentAt: Long?, zone: ZoneId, nowMs: Long): String {
    sentAt ?: return "Nothing sent yet"
    val sent = Instant.ofEpochMilli(sentAt).atZone(zone)
    return if (sent.toLocalDate() == Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()) {
        "Last sent today at ${sent.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}"
    } else {
        "Last sent ${sent.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}"
    }
}
