package app.sitara.music.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.GlassIconButton
import app.sitara.music.core.designsystem.GlassSurface
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraColors
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.model.Lyrics
import app.sitara.music.source.lyrics.LyricsMatch
import app.sitara.music.source.lyrics.TranslationProblem
import java.util.Locale

/**
 * Above found lyrics: which source they came from, as a chip that opens every source's match, Translate, and Share,
 * which starts picking lines for a card at the one being sung. While
 * Translate is on, a line under them says how the translation is going and which language it is into.
 */
@Composable
internal fun LyricsToolbar(state: PlayerUiState, actions: PlayerActions, onShare: () -> Unit, modifier: Modifier = Modifier) {
    val found = state.lyrics as? LyricsState.Found ?: return
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceChip(found.lyrics, { picking = true }, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(0.01f))
            TranslateToggle(state.translation != TranslationState.Off, actions::toggleTranslation)
            GlassIconButton(SitaraIcons.Share, stringResource(R.string.player_share_lyrics), onShare, tint = SitaraColors.StageContent)
        }
        TranslationStatus(state.translation, actions)
    }
    if (picking) LyricsSourceSheet(state, actions) { picking = false }
}

@Composable
private fun TranslateToggle(on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.heightIn(min = SitaraSpacing.TouchTarget).toggleable(on, role = Role.Switch, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassSurface(shape = CircleShape, tint = if (on) SitaraColors.StageContent else MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                stringResource(R.string.player_translate), style = MaterialTheme.typography.labelLarge, color = if (on) SitaraColors.Stage else SitaraColors.StageContent,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** How the translation is going, MyMemory's credit, and the language it is into, which opens the other languages. */
@Composable
private fun TranslationStatus(translation: TranslationState, actions: PlayerActions) {
    val (language, message, retry) = when (translation) {
        TranslationState.Off -> return
        is TranslationState.Translating -> Triple(translation.language, stringResource(R.string.player_translating_to, languageName(translation.language)), false)
        is TranslationState.Translated -> Triple(
            translation.translation.toLanguage,
            translation.translation.fromLanguage?.let { stringResource(R.string.player_translated_from, languageName(it)) } ?: stringResource(R.string.player_translated),
            false,
        )
        is TranslationState.Failed -> when (translation.problem) {
            TranslationProblem.OFFLINE -> Triple(translation.language, stringResource(R.string.player_translation_offline), true)
            TranslationProblem.DAILY_LIMIT -> Triple(translation.language, stringResource(R.string.player_translation_daily_limit), false)
            TranslationProblem.SAME_LANGUAGE -> Triple(translation.language, stringResource(R.string.player_translation_same_language, languageName(translation.language)), false)
            TranslationProblem.SERVICE -> Triple(translation.language, stringResource(R.string.player_translation_service), true)
        }
    }
    var choosing by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = SitaraColors.StageSecondary, modifier = Modifier.weight(1f))
        if (retry) PillButton(stringResource(R.string.player_try_again), actions::retryTranslation, primary = false)
        val name = languageName(language)
        val changeLanguage = stringResource(R.string.player_translate_into_change, name)
        Row(
            Modifier.heightIn(min = SitaraSpacing.TouchTarget).clickable(role = Role.Button) { choosing = true }
                .semantics { contentDescription = changeLanguage },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassSurface(shape = CircleShape) {
                Text(name, style = MaterialTheme.typography.labelLarge, color = SitaraColors.StageContent, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
    if (choosing) {
        SitaraSheet({ choosing = false }) { close -> TranslationLanguages(language) { code -> close { actions.setTranslationLanguage(code) } } }
    }
}

// The app's language first, then languages people often sing in; MyMemory takes ISO 639-1 codes ("zh-CN" for Simplified Chinese).
private val COMMON_LANGUAGES = listOf("en", "bn", "hi", "ur", "ar", "es", "pt", "fr", "de", "it", "tr", "ru", "id", "ja", "ko", "zh-CN")

/** The languages lyrics can be translated into, [current] marked; the content of the language sheet. */
@Composable
fun TranslationLanguages(current: String, onChoose: (language: String) -> Unit) {
    val appLanguage = Locale.getDefault().toLanguageTag().substringBefore('-')
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(
            stringResource(R.string.player_translate_into), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
        )
        (listOf(appLanguage) + COMMON_LANGUAGES).distinct().forEach { code ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(code == current, role = Role.RadioButton) { onChoose(code) }
                    .padding(horizontal = SitaraSpacing.Gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(languageName(code), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (code == current) Icon(SitaraIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** "Spanish" for "es", in the phone's language. */
private fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Composable
private fun SourceChip(lyrics: Lyrics, onClick: () -> Unit, modifier: Modifier) {
    // The touch target is the full row height; the glass pill inside stays compact.
    Row(modifier.heightIn(min = SitaraSpacing.TouchTarget).clickable(role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        GlassSurface(shape = CircleShape) {
            Text(
                sourceLabel(lyrics),
                style = MaterialTheme.typography.labelLarge,
                color = SitaraColors.StageContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun sourceLabel(lyrics: Lyrics): String {
    val source = lyrics.source ?: stringResource(R.string.player_source_unknown)
    return stringResource(if (lyrics.synced) R.string.player_source_synced else R.string.player_source_plain, source)
}

@Composable
private fun LyricsSourceSheet(state: PlayerUiState, actions: PlayerActions, onDismiss: () -> Unit) {
    SitaraSheet(onDismiss) { close -> LyricsSourceList(state) { id -> close { actions.chooseLyrics(id) } } }
}

/** Every source's lyrics for the song, how they are timed, how many lines and how their length compares, with their first lines; the content of the source sheet. */
@Composable
fun LyricsSourceList(state: PlayerUiState, onChoose: (providerId: String) -> Unit) {
    val found = state.lyrics as? LyricsState.Found ?: return
    val songMs = state.track?.durationMs ?: 0
    val missing = state.lyricsSources.filter { name -> found.matches.none { it.lyrics.source == name } }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(
            stringResource(R.string.player_lyrics_source), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
        )
        found.matches.forEach { match -> SourceRow(match, selected = match.lyrics == found.lyrics, songMs) { onChoose(match.providerId) } }
        missing.forEach { name ->
            Text(
                stringResource(R.string.player_source_has_none, name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SourceRow(match: LyricsMatch, selected: Boolean, songMs: Long, onClick: () -> Unit) {
    val lyrics = match.lyrics
    val details = listOfNotNull(
        stringResource(if (lyrics.synced) R.string.player_synced else R.string.player_plain),
        pluralStringResource(R.plurals.player_line_count, lyrics.lines.size, lyrics.lines.size),
        lengthLabel(lyrics.matched?.durationMs, songMs),
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = SitaraSpacing.Gutter, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(lyrics.source, lyrics.licensor).joinToString(" · "), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                lyrics.lines.take(2).joinToString("\n") { it.text },
                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (selected) Icon(SitaraIcons.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) else Spacer(Modifier.width(24.dp))
    }
}

/** How much longer or shorter the song the lyrics were filed under is, to the second; null when either length is unknown. */
@Composable
internal fun lengthLabel(matchedMs: Long?, songMs: Long): String? {
    if (matchedMs == null || matchedMs <= 0 || songMs <= 0) return null
    val seconds = (matchedMs - songMs) / 1_000
    return when {
        seconds == 0L -> stringResource(R.string.player_same_length)
        seconds > 0 -> stringResource(R.string.player_seconds_longer, seconds)
        else -> stringResource(R.string.player_seconds_shorter, -seconds)
    }
}
