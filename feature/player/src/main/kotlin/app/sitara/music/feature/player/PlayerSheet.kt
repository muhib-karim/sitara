package app.sitara.music.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.sitara.music.core.common.formatDuration
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.ArtworkPalette
import app.sitara.music.core.designsystem.LocalReducedMotion
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraMotion
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.model.RepeatMode
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val MiniHeight = 64.dp

/**
 * The mini player and the full player are one surface driven by a single progress
 * value (0 = docked mini player, 1 = full screen) on a critically damped spring.
 * Drags move the progress directly; on release the drag velocity is handed to the
 * spring so the sheet keeps the thumb's momentum.
 */
@Composable
fun PlayerSheet(
    state: PlayerUiState,
    actions: PlayerActions,
    bottomReserved: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    initialPage: Int = 0,
) {
    val track = state.track ?: return
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val progress = remember { Animatable(if (initiallyExpanded) 1f else 0f) }
    var hidden by remember { mutableStateOf(false) }
    var lastTrackId by remember { mutableStateOf(track.id) }
    if (track.id != lastTrackId) {
        lastTrackId = track.id
        hidden = false
    }
    val dark = MaterialTheme.colorScheme.background.let { ArtworkPalette.relativeLuminance(it.toArgbCompat()) < 0.2 }
    val palette = remember(track.artwork, dark) { ArtworkPalette.themeFrom(track.artwork.colors, dark) }
    val top by animateColorAsState(Color(palette.backdropTop), SitaraMotion.fade(SitaraMotion.FADE_LONG_MS), label = "top")
    val bottom by animateColorAsState(Color(palette.backdropBottom), SitaraMotion.fade(SitaraMotion.FADE_LONG_MS), label = "bottom")
    val content = Color(palette.content)

    fun settle(target: Float, velocity: Float = 0f) {
        scope.launch {
            if (reduced) progress.snapTo(target) else progress.animateTo(target, SitaraMotion.smooth(), initialVelocity = velocity)
        }
    }
    BackHandler(enabled = progress.targetValue > 0.5f) { settle(0f) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fullH = constraints.maxHeight.toFloat()
        val miniH = with(density) { MiniHeight.toPx() }
        val collapsedTop = fullH - with(density) { bottomReserved.toPx() } - miniH
        val p = progress.value
        val hideOffset = remember { Animatable(0f) }
        val swipeX = remember { Animatable(0f) }
        if (hidden && p == 0f) return@BoxWithConstraints

        val y = lerp(collapsedTop, 0f, p) + hideOffset.value
        val h = lerp(miniH, fullH, p)
        val side = lerp(with(density) { 12.dp.toPx() }, 0f, p)
        val radius = lerp(22f, 0f, p)

        Box(
            Modifier
                .offset { IntOffset(side.roundToInt(), y.roundToInt()) }
                .width(with(density) { (constraints.maxWidth - 2 * side).toDp() })
                .height(with(density) { h.toDp() })
                .shadow(if (p < 1f) 16.dp else 0.dp, RoundedCornerShape(radius.dp))
                .clip(RoundedCornerShape(radius.dp))
                .background(Brush.verticalGradient(listOf(top, bottom)))
                .pointerInput(collapsedTop) {
                    val tracker = VelocityTracker()
                    var axis = 0 // 0 undecided, 1 vertical, 2 horizontal
                    detectDragGestures(
                        onDragStart = {
                            axis = 0
                            tracker.resetTracking()
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            val fling = SitaraMotion.FLING_VELOCITY_DP * density.density
                            when {
                                axis == 2 -> {
                                    val dx = swipeX.value
                                    if (abs(dx) > size.width * 0.25f || abs(v.x) > fling) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (dx < 0) actions.next() else actions.previous()
                                    }
                                    scope.launch { swipeX.animateTo(0f, SitaraMotion.snappy()) }
                                }
                                hideOffset.value > 0f -> {
                                    if (hideOffset.value > miniH * 0.6f || v.y > fling) {
                                        hidden = true
                                    }
                                    scope.launch { hideOffset.snapTo(0f) }
                                }
                                else -> {
                                    val target = when {
                                        v.y < -fling -> 1f
                                        v.y > fling -> 0f
                                        else -> if (progress.value > 0.5f) 1f else 0f
                                    }
                                    settle(target, -v.y / collapsedTop)
                                }
                            }
                        },
                    ) { change, drag ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (axis == 0) axis = if (abs(drag.x) > abs(drag.y) && progress.value == 0f) 2 else 1
                        change.consume()
                        scope.launch {
                            when {
                                axis == 2 -> swipeX.snapTo(swipeX.value + drag.x)
                                progress.value == 0f && drag.y > 0 || hideOffset.value > 0f ->
                                    hideOffset.snapTo((hideOffset.value + drag.y).coerceAtLeast(0f))
                                else -> progress.snapTo((progress.value - drag.y / collapsedTop).coerceIn(0f, 1f))
                            }
                        }
                    }
                },
        ) {
            if (p < 0.6f) {
                MiniPlayer(
                    state = state,
                    actions = actions,
                    content = content,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = 1f - p / 0.6f
                            translationX = swipeX.value
                        }
                        .clickable { settle(1f) },
                )
            }
            if (p > 0.05f) {
                FullPlayer(
                    state = state,
                    actions = actions,
                    content = content,
                    accent = Color(palette.accent),
                    onAccent = Color(palette.onAccent),
                    onCollapse = { settle(0f) },
                    initialPage = initialPage,
                    modifier = Modifier.graphicsLayer { alpha = ((p - 0.05f) / 0.6f).coerceIn(0f, 1f) },
                )
            }
        }
    }
}

private fun Color.toArgbCompat(): Int {
    fun c(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (c(alpha) shl 24) or (c(red) shl 16) or (c(green) shl 8) or c(blue)
}

@Composable
private fun MiniPlayer(state: PlayerUiState, actions: PlayerActions, content: Color, modifier: Modifier = Modifier) {
    val track = state.track ?: return
    Column(modifier.fillMaxWidth().height(MiniHeight)) {
        Row(
            Modifier.weight(1f).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(track.artwork, Modifier.size(44.dp), cornerRadius = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = content, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = content.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = actions::togglePlay) {
                Icon(if (state.isPlaying) SitaraIcons.Pause else SitaraIcons.Play, if (state.isPlaying) "Pause" else "Play", tint = content)
            }
            IconButton(onClick = actions::next) { Icon(SitaraIcons.Next, "Next", tint = content) }
        }
        val fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        Box(Modifier.fillMaxWidth().height(2.dp).background(content.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(2.dp).background(content))
        }
    }
}

@Composable
private fun FullPlayer(
    state: PlayerUiState,
    actions: PlayerActions,
    content: Color,
    accent: Color,
    onAccent: Color,
    onCollapse: () -> Unit,
    initialPage: Int,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    val pager = rememberPagerState(initialPage = initialPage) { 3 }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) { Icon(SitaraIcons.ChevronDown, "Collapse player", tint = content) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                listOf("Playing", "Lyrics", "Up next").forEachIndexed { i, label ->
                    Text(
                        label,
                        color = if (pager.currentPage == i) content else content.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { scope.launch { pager.animateScrollToPage(i) } }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
            IconButton(onClick = actions::cycleSleepTimer) {
                Icon(SitaraIcons.Timer, state.sleepTimerLabel?.let { "Sleep timer $it" } ?: "Sleep timer", tint = if (state.sleepTimerLabel != null) accent else content)
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            when (page) {
                0 -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    val scale = if (state.isPlaying) 1f else 0.86f
                    ArtworkImage(
                        track.artwork,
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .shadow(24.dp, RoundedCornerShape(20.dp)),
                        cornerRadius = 20.dp,
                    )
                }
                1 -> LyricsPage(state.lyrics, state.positionMs, content, actions::seekTo)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(state.upNext, key = { i, t -> "$i-${t.id}" }) { i, t -> TrackRow(t, onClick = { actions.playQueueIndex(i) }) }
                }
            }
        }
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(track.title, color = content, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = content.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Scrubber(state, actions, content)
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions::toggleShuffle) {
                    Icon(SitaraIcons.Shuffle, if (state.shuffled) "Shuffle on" else "Shuffle off", tint = if (state.shuffled) accent else content.copy(alpha = 0.6f))
                }
                IconButton(onClick = actions::previous, Modifier.size(56.dp)) { Icon(SitaraIcons.Previous, "Previous", tint = content, modifier = Modifier.size(32.dp)) }
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable(onClick = actions::togglePlay)
                        .semantics { contentDescription = if (state.isPlaying) "Pause" else "Play" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (state.isPlaying) SitaraIcons.Pause else SitaraIcons.Play, null, tint = onAccent, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = actions::next, Modifier.size(56.dp)) { Icon(SitaraIcons.Next, "Next", tint = content, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = actions::cycleRepeat) {
                    Icon(
                        SitaraIcons.Repeat,
                        "Repeat ${state.repeatMode.name.lowercase()}",
                        tint = if (state.repeatMode == RepeatMode.OFF) content.copy(alpha = 0.6f) else accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun Scrubber(state: PlayerUiState, actions: PlayerActions, content: Color) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1)
    val value = if (dragging) dragValue else (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    Slider(
        value = value,
        onValueChange = {
            dragging = true
            dragValue = it
        },
        onValueChangeFinished = {
            actions.seekTo((dragValue * duration).toLong())
            dragging = false
        },
        colors = SliderDefaults.colors(thumbColor = content, activeTrackColor = content, inactiveTrackColor = content.copy(alpha = 0.2f)),
        modifier = Modifier.semantics { contentDescription = "Seek" },
    )
    Row(Modifier.fillMaxWidth()) {
        Text(formatDuration((value * duration).toLong()), color = content.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        Text("-" + formatDuration(duration - (value * duration).toLong()), color = content.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
    }
}
