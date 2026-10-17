package app.sitara.music.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sitara.music.core.designsystem.LocalReducedMotion
import app.sitara.music.core.designsystem.SitaraMotion
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.LyricsLine
import app.sitara.music.source.lyrics.LyricsCursor
import kotlin.math.abs

private val LineStyle = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
private val PlainStyle = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
private const val LINE_CONTENT_TYPE = "lyric"

/**
 * Synced lyrics: the active line is in focus and words fill as they are sung; tapping a line seeks.
 * Plain lyrics have no timing, so they are shown as static text with no highlight, scrolling or seeking.
 */
@Composable
fun LyricsPage(
    lyrics: Lyrics,
    positionMs: Long,
    contentColor: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        lyrics.lines.isEmpty() -> Box(modifier.fillMaxSize().padding(32.dp)) {
            Text("No lyrics for this song yet", color = contentColor.copy(alpha = 0.7f))
        }
        lyrics.synced -> SyncedLyrics(lyrics, positionMs, contentColor, onSeek, modifier)
        else -> PlainLyrics(lyrics, contentColor, modifier)
    }
}

@Composable
private fun PlainLyrics(lyrics: Lyrics, contentColor: Color, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize().fadingEdges(), contentPadding = PaddingValues(top = 24.dp, bottom = 240.dp)) {
        item(contentType = "caption") {
            Text(
                "These lyrics are not time-synced",
                style = MaterialTheme.typography.labelLarge,
                color = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }
        itemsIndexed(lyrics.lines, key = { i, _ -> i }, contentType = { _, _ -> LINE_CONTENT_TYPE }) { _, line ->
            Text(line.text, style = PlainStyle, color = contentColor, modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics, positionMs: Long, contentColor: Color, onSeek: (Long) -> Unit, modifier: Modifier) {
    val active = LyricsCursor.activeLineIndex(lyrics, positionMs)
    val listState = rememberLazyListState()
    val reduced = LocalReducedMotion.current
    LaunchedEffect(active) {
        if (active >= 0) {
            // Park the active line a quarter of the way down, so the previous line stays in view for context.
            val offset = -listState.layoutInfo.viewportSize.height / 4
            if (reduced) listState.scrollToItem(active, offset) else listState.animateScrollToItem(active, offset)
        }
    }
    LazyColumn(
        modifier.fillMaxSize().fadingEdges(),
        state = listState,
        contentPadding = PaddingValues(top = 24.dp, bottom = 240.dp),
    ) {
        itemsIndexed(lyrics.lines, key = { i, _ -> i }, contentType = { _, _ -> LINE_CONTENT_TYPE }) { i, line ->
            val distance = if (active < 0) 2 else abs(i - active)
            val scale by animateFloatAsState(if (i == active) 1f else 0.94f, SitaraMotion.orReduced(reduced, SitaraMotion.gentle()), label = "lineScale")
            val targetAlpha = when {
                i == active -> 1f
                i < active -> 0.35f
                else -> 0.5f
            }
            val alpha by animateFloatAsState(targetAlpha, SitaraMotion.fade(), label = "lineAlpha")
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.startMs) }
                    .padding(horizontal = 28.dp, vertical = 10.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .blur(if (reduced || distance == 0) 0.dp else (distance.coerceAtMost(3) * 1.2f).dp),
            ) {
                if (i == active && line.isWordSynced) KaraokeLine(line, positionMs, contentColor) else Text(line.text, style = LineStyle, color = contentColor)
            }
        }
    }
}

/** Lines dissolve into the backdrop at the top and bottom instead of being cut off by the page edge. */
private fun Modifier.fadingEdges(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = FADE_EDGE.toPx()
        drawRect(Brush.verticalGradient(0f to Color.Transparent, fade / size.height to Color.Black), blendMode = BlendMode.DstIn)
        drawRect(
            Brush.verticalGradient((size.height - fade) / size.height to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

private val FADE_EDGE = 48.dp

/**
 * The line is laid out once, exactly as written, and a bright copy is clipped to the sung
 * characters, so syllables such as "Hel" + "lo" stay one word and wrapping matches the dim copy.
 */
@Composable
private fun KaraokeLine(line: LyricsLine, positionMs: Long, color: Color) {
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
    val sweep = LyricsCursor.sweep(line, positionMs)
    Box {
        Text(line.text, style = LineStyle, color = color.copy(alpha = 0.35f), onTextLayout = { layout = it })
        Text(
            line.text,
            style = LineStyle,
            color = color,
            modifier = Modifier.drawWithContent {
                val text = layout ?: return@drawWithContent
                val sung = text.getPathForRange(0, sweep.sungEnd)
                if (sweep.activeEnd > sweep.activeStart) {
                    val first = text.getLineForOffset(sweep.activeStart)
                    if (first == text.getLineForOffset(sweep.activeEnd - 1)) {
                        val left = text.getBoundingBox(sweep.activeStart).left
                        val right = text.getBoundingBox(sweep.activeEnd - 1).right
                        sung.addRect(Rect(left, text.getLineTop(first), left + (right - left) * sweep.activeProgress, text.getLineBottom(first)))
                    } else if (sweep.activeProgress >= 0.5f) {
                        sung.addPath(text.getPathForRange(sweep.activeStart, sweep.activeEnd))
                    }
                }
                clipPath(sung) { this@drawWithContent.drawContent() }
            },
        )
    }
}
