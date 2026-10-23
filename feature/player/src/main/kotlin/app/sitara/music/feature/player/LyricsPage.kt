package app.sitara.music.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sitara.music.core.designsystem.ArtworkScrim
import app.sitara.music.core.designsystem.Inter
import app.sitara.music.core.designsystem.LocalReducedMotion
import app.sitara.music.core.designsystem.LyricStyle
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraMotion
import app.sitara.music.core.model.Lyrics
import app.sitara.music.source.lyrics.LyricsCursor
import kotlin.math.abs
import kotlinx.coroutines.flow.first

// Text follows the app's layout direction unless told to read it from the text: a right-to-left line must start at the right.
private val LineStyle = LyricStyle.copy(textDirection = TextDirection.Content)
private val PlainStyle = TextStyle(fontFamily = Inter, fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content)
private val TranslationStyle = TextStyle(fontFamily = Inter, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, textDirection = TextDirection.Content)
private const val LINE_CONTENT_TYPE = "lyric"

/**
 * Synced lyrics: the active line stays white and in focus and the list follows it; tapping a line seeks. Scrolling by
 * hand stops the following until "Back to current line" resumes it.
 * Plain lyrics have no timing, so they are shown as static text with no highlight, scrolling or seeking.
 * Lines in right-to-left scripts (Urdu, Arabic) start at the right edge. While Translate is on, each line's translation sits under it. Both end with a credit to where they came from. With none found, the sources asked are named and can be asked again.
 */
@Composable
fun LyricsPage(
    state: LyricsState,
    sources: List<String>,
    positionMs: Long,
    contentColor: Color,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    translation: List<String>? = null,
    selection: IntRange? = null,
    onSelectionChange: (IntRange?) -> Unit = {},
    onShare: () -> Unit = {},
) {
    when (state) {
        LyricsState.Loading -> LyricsMessage(stringResource(R.string.player_lyrics_loading), contentColor, modifier)
        LyricsState.NotFound -> LyricsMessage(notFoundText(sources), contentColor, modifier) {
            PillButton(stringResource(R.string.player_try_again), onRetry, Modifier.padding(top = 16.dp))
        }
        LyricsState.Failed -> LyricsMessage(stringResource(R.string.player_lyrics_failed), contentColor, modifier) {
            PillButton(stringResource(R.string.player_try_again), onRetry, Modifier.padding(top = 16.dp))
        }
        is LyricsState.Found -> Box(modifier.fillMaxSize()) {
            val picking = LinePicking(selection, onSelectionChange)
            if (state.lyrics.synced) {
                SyncedLyrics(state.lyrics, translation, positionMs, contentColor, onSeek, picking)
            } else {
                PlainLyrics(state.lyrics, translation, contentColor, picking)
            }
            if (selection != null) {
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(stringResource(R.string.player_cancel), { onSelectionChange(null) }, primary = false)
                    val count = selection.last - selection.first + 1
                    PillButton(pluralStringResource(R.plurals.player_share_lines, count, count), onShare)
                }
            }
        }
    }
}

/** "No lyrics found in LRCLIB or YouTube Music". */
@Composable
private fun notFoundText(sources: List<String>): String = when (sources.size) {
    0 -> stringResource(R.string.player_lyrics_not_found)
    1 -> stringResource(R.string.player_lyrics_not_found_in, sources[0])
    else -> stringResource(R.string.player_lyrics_not_found_in_either, sources.dropLast(1).joinToString(", "), sources.last())
}

@Composable
private fun LyricsMessage(text: String, contentColor: Color, modifier: Modifier, action: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxSize().padding(32.dp)) {
        Text(text, color = contentColor.copy(alpha = 0.7f))
        action()
    }
}

/** The lines picked for a share card, null when none are; a long press on a line starts picking from it. */
private class LinePicking(val selection: IntRange?, val onChange: (IntRange?) -> Unit) {
    fun longPress(line: Int) = onChange(line..line)

    /** A tap while picking moves the run ([extendSelection]); false when not picking, so the tap does what it normally does. */
    fun tap(line: Int): Boolean {
        onChange(extendSelection(selection ?: return false, line))
        return true
    }
}

/** A line's translation under it, smaller and dimmer; nothing for a line left untranslated. */
@Composable
private fun TranslatedLine(text: String?, contentColor: Color) {
    if (text.isNullOrEmpty()) return
    Text(text, style = TranslationStyle, color = contentColor.copy(alpha = 0.72f), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
}

/**
 * Whether [text] reads right to left (Urdu, Arabic, Hebrew): its first character with a strong direction does, as
 * [TextDirection.Content] decides for the line's layout; this tells the focus scale where to grow from.
 */
internal fun isRightToLeft(text: String): Boolean {
    for (char in text) {
        when (Character.getDirectionality(char)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
        }
    }
    return false
}

/** "Lyrics from LRCLIB", under the last line. */
@Composable
private fun SourceCredit(source: String?, contentColor: Color) {
    if (source == null) return
    Text(
        stringResource(R.string.player_lyrics_credit, source),
        style = MaterialTheme.typography.labelLarge,
        color = contentColor.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
    )
}

@Composable
private fun PlainLyrics(lyrics: Lyrics, translation: List<String>?, contentColor: Color, picking: LinePicking) {
    val notSynced = stringResource(R.string.player_lyrics_not_synced)
    val pickLabel = stringResource(R.string.player_pick_for_card)
    LazyColumn(Modifier.fillMaxSize().fadingEdges(), contentPadding = PaddingValues(top = 24.dp, bottom = 240.dp)) {
        item(contentType = "caption") {
            Text(
                notSynced,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }
        itemsIndexed(lyrics.lines, key = { i, _ -> i }, contentType = { _, _ -> LINE_CONTENT_TYPE }) { i, line ->
            // Plain lines have nothing to seek to: a tap starts picking lines for a card, like a long press.
            Column(
                Modifier.fillMaxWidth()
                    .combinedClickable(onClickLabel = pickLabel, onLongClick = { picking.longPress(i) }) { if (!picking.tap(i)) picking.longPress(i) }
                    .graphicsLayer { alpha = if (picking.selection == null || i in picking.selection) 1f else ArtworkScrim.InactiveLyric }
                    .padding(horizontal = 28.dp, vertical = 6.dp),
            ) {
                Text(line.text, style = PlainStyle, color = contentColor, modifier = Modifier.fillMaxWidth())
                TranslatedLine(translation?.getOrNull(i), contentColor)
            }
        }
        item(contentType = "credit") { SourceCredit(lyrics.source, contentColor) }
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics, translation: List<String>?, positionMs: Long, contentColor: Color, onSeek: (Long) -> Unit, picking: LinePicking) {
    val active = LyricsCursor.activeLineIndex(lyrics, positionMs)
    val listState = rememberLazyListState()
    val reduced = LocalReducedMotion.current
    val pickLabel = stringResource(R.string.player_pick_for_card)
    // A hand scroll means the listener is reading elsewhere: stop following until they ask to come back.
    var following by remember(lyrics) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) following = false }
    }
    // Picking lines for a card holds the list still.
    val isPicking = picking.selection != null
    LaunchedEffect(lyrics, active, reduced, following, isPicking) {
        if (following && !isPicking && active >= 0) {
            // Await measurement, then keep enough preceding context above the active line.
            val viewport = snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
            val offset = -(viewport * 0.35f).toInt()
            if (reduced) listState.scrollToItem(active, offset) else listState.animateScrollToItem(active, offset)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        LazyColumn(
            Modifier.fillMaxSize().fadingEdges(),
            state = listState,
            contentPadding = PaddingValues(top = 24.dp, bottom = viewportHeight * 0.65f),
        ) {
            itemsIndexed(lyrics.lines, key = { i, _ -> i }, contentType = { _, _ -> LINE_CONTENT_TYPE }) { i, line ->
                val rtl = isRightToLeft(line.text)
                // While picking, the picked lines are the ones in focus.
                val focused = picking.selection?.contains(i) ?: (i == active)
                val distance = if (isPicking || active < 0) (if (focused) 0 else 2) else abs(i - active)
                val scale by animateFloatAsState(if (focused) 1.04f else 1f, SitaraMotion.orReduced(reduced, SitaraMotion.fade(SitaraMotion.LYRIC_FOCUS_MS)), label = "lineScale")
                val alpha by animateFloatAsState(if (focused) 1f else ArtworkScrim.InactiveLyric, SitaraMotion.orReduced(reduced, SitaraMotion.fade(SitaraMotion.LYRIC_FOCUS_MS)), label = "lineAlpha")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(role = Role.Button, onLongClickLabel = pickLabel, onLongClick = { picking.longPress(i) }) {
                            if (!picking.tap(i)) onSeek(line.startMs)
                        }
                        .semantics { selected = i == active }
                        .padding(horizontal = 28.dp, vertical = 10.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            // A right-to-left line grows from its right edge, where it starts.
                            transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                        }
                        // Softer the further a line is from the one being sung; static, so reduced motion keeps it.
                        .blur((distance * 1.2f).coerceAtMost(3f).dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(line.text, style = LineStyle, color = contentColor, modifier = Modifier.fillMaxWidth())
                        TranslatedLine(translation?.getOrNull(i), contentColor)
                    }
                }
            }
            item(contentType = "credit") { SourceCredit(lyrics.source, contentColor) }
        }
        if (!following && !isPicking) {
            PillButton(stringResource(R.string.player_back_to_current_line), { following = true }, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
        }
    }
}

/** Lines dissolve into the backdrop at the top and bottom instead of being cut off by the page edge. */
private fun Modifier.fadingEdges(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = FADE_EDGE.toPx()
        drawRect(Brush.verticalGradient(0f to Color.Transparent, (fade / size.height.coerceAtLeast(1f)).coerceIn(0f, 0.5f) to Color.Black), blendMode = BlendMode.DstIn)
        drawRect(
            Brush.verticalGradient(((size.height - fade) / size.height.coerceAtLeast(1f)).coerceIn(0.5f, 1f) to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

private val FADE_EDGE = 48.dp
