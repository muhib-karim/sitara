package app.sitara.music.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sitara.music.core.designsystem.ArtworkScrim
import app.sitara.music.core.designsystem.Inter
import app.sitara.music.core.designsystem.LocalReducedMotion
import app.sitara.music.core.designsystem.LyricStyle
import app.sitara.music.core.designsystem.SitaraMotion
import app.sitara.music.core.model.Lyrics
import app.sitara.music.source.lyrics.LyricsCursor
import kotlin.math.abs
import kotlinx.coroutines.flow.first

private val LineStyle = LyricStyle
private val PlainStyle = TextStyle(fontFamily = Inter, fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
private const val LINE_CONTENT_TYPE = "lyric"

/**
 * Synced lyrics: the active line stays white and in focus; tapping a line seeks.
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
    LaunchedEffect(lyrics, active, reduced) {
        if (active >= 0) {
            // Await measurement, then keep enough preceding context above the active line.
            val viewport = snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
            val offset = -(viewport * 0.35f).toInt()
            if (reduced) listState.scrollToItem(active, offset) else listState.animateScrollToItem(active, offset)
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        LazyColumn(
            Modifier.fillMaxSize().fadingEdges(),
            state = listState,
            contentPadding = PaddingValues(top = 24.dp, bottom = viewportHeight * 0.65f),
        ) {
            itemsIndexed(lyrics.lines, key = { i, _ -> i }, contentType = { _, _ -> LINE_CONTENT_TYPE }) { i, line ->
                val distance = if (active < 0) 2 else abs(i - active)
                val scale by animateFloatAsState(if (i == active) 1.04f else 1f, SitaraMotion.orReduced(reduced, SitaraMotion.fade(SitaraMotion.LYRIC_FOCUS_MS)), label = "lineScale")
                val alpha by animateFloatAsState(if (i == active) 1f else ArtworkScrim.InactiveLyric, SitaraMotion.orReduced(reduced, SitaraMotion.fade(SitaraMotion.LYRIC_FOCUS_MS)), label = "lineAlpha")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onSeek(line.startMs) }
                        .semantics { selected = i == active }
                        .padding(horizontal = 28.dp, vertical = 10.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .blur(if (reduced || distance == 0) 0.dp else distance.coerceAtMost(2).dp),
                ) {
                    Text(line.text, style = LineStyle, color = contentColor)
                }
            }
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
