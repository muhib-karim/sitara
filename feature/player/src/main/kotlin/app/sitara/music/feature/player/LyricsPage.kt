package app.sitara.music.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sitara.music.core.designsystem.LocalReducedMotion
import app.sitara.music.core.designsystem.SitaraMotion
import app.sitara.music.core.model.Lyrics
import app.sitara.music.core.model.LyricsLine
import app.sitara.music.source.lyrics.LyricsCursor

private val LineStyle = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)

/** Word-level karaoke lyrics: the active line is in focus, words fill as they are sung. */
@Composable
fun LyricsPage(
    lyrics: Lyrics,
    positionMs: Long,
    contentColor: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lyrics.lines.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp)) { Text("No lyrics for this song yet", color = contentColor.copy(alpha = 0.7f)) }
        return
    }
    val active = LyricsCursor.activeLineIndex(lyrics.lines, positionMs)
    val listState = rememberLazyListState()
    val reduced = LocalReducedMotion.current
    LaunchedEffect(active) {
        if (active >= 0) {
            if (reduced) listState.scrollToItem(active) else listState.animateScrollToItem(active)
        }
    }
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 24.dp, bottom = 240.dp)) {
        itemsIndexed(lyrics.lines) { i, line ->
            val distance = if (active < 0) 2 else kotlin.math.abs(i - active)
            val scale by animateFloatAsState(if (i == active) 1f else 0.94f, SitaraMotion.orReduced(reduced, SitaraMotion.gentle()), label = "lineScale")
            val alpha by animateFloatAsState(if (i == active) 1f else if (i < active) 0.35f else 0.5f, SitaraMotion.fade(), label = "lineAlpha")
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSeek(line.startMs) }
                    .padding(horizontal = 28.dp, vertical = 10.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    }
                    .blur(if (reduced || distance == 0) 0.dp else (distance.coerceAtMost(3) * 1.2f).dp),
            ) {
                if (i == active && line.isWordSynced) KaraokeLine(line, positionMs, contentColor) else Text(line.text, style = LineStyle, color = contentColor)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KaraokeLine(line: LyricsLine, positionMs: Long, color: Color) {
    FlowRow {
        line.words.forEach { word ->
            val progress = LyricsCursor.wordProgress(word, positionMs)
            Box(Modifier.padding(end = 8.dp)) {
                Text(word.text, style = LineStyle, color = color.copy(alpha = 0.35f))
                Text(
                    word.text,
                    style = LineStyle,
                    color = color,
                    modifier = Modifier
                        .clipToBounds()
                        .drawWithContent { clipRect(right = size.width * progress) { this@drawWithContent.drawContent() } },
                )
            }
        }
    }
}
