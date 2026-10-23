@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.sitara.music.feature.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.ArtworkImage
import app.sitara.music.core.designsystem.GlassSurface
import app.sitara.music.core.designsystem.LocalArtworkWash
import app.sitara.music.core.designsystem.LocalReducedMotion
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.SitaraMotion
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.animateArtworkColor
import app.sitara.music.core.designsystem.rememberArtworkTheme
import app.sitara.music.core.model.Track
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** One dock, one artwork element: expansion carries the cover into the listening stage. */
@Composable
fun PlayerSheet(
    state: PlayerUiState,
    actions: PlayerActions,
    bottomReserved: Dp,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    /** Each one opens Now Playing (the home-screen widget asks this way). */
    expandRequests: Flow<Unit> = emptyFlow(),
    initialPage: Int = 0,
    haze: dev.chrisbanes.haze.HazeState? = null,
    folded: Boolean = false,
    navigation: @Composable (accent: Color, folded: Boolean) -> Unit = { _, _ -> },
    onDockHeightChanged: (Dp) -> Unit = {},
    onArtist: (artistId: String) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
    onSaveQueue: (List<Track>) -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val reduced = LocalReducedMotion.current
    val currentActions by rememberUpdatedState(actions)
    val density = LocalDensity.current
    val track = state.track
    val open = expanded && track != null
    PlayerSystemBars(open)
    // Predictive back: while the gesture is held the stage shrinks and drops toward the dock; letting go collapses it,
    // cancelling springs it back. With reduced motion it only dims.
    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled = open) { events ->
        try {
            events.collect { backProgress = it.progress }
            // Kept as it is while the stage animates out, so it leaves from where the gesture left it.
            expanded = false
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }
    // Opened again, the stage starts whole.
    LaunchedEffect(open) { if (open) backProgress = 0f }
    LaunchedEffect(expandRequests) { expandRequests.collect { expanded = true } }
    SharedTransitionLayout(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = open,
            transitionSpec = {
                val fade = if (reduced) snap<Float>() else SitaraMotion.fade<Float>()
                fadeIn(fade) togetherWith fadeOut(fade)
            },
            label = "player expansion",
        ) { full ->
            val coverModifier = if (track == null) {
                Modifier
            } else {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState("cover-${track.id}"),
                    animatedVisibilityScope = this,
                    boundsTransform = { _, _ -> SitaraMotion.orReduced(reduced, SitaraMotion.smooth()) },
                )
            }
            if (full && track != null) {
                FullPlayer(
                    state, actions, onCollapse = { expanded = false }, initialPage = initialPage, artworkModifier = coverModifier,
                    modifier = Modifier.graphicsLayer {
                        val p = backProgress
                        if (reduced) {
                            alpha = 1f - 0.4f * p
                        } else {
                            scaleX = 1f - BACK_SHRINK * p
                            scaleY = 1f - BACK_SHRINK * p
                            translationY = BACK_DROP_DP.dp.toPx() * p
                            shape = RoundedCornerShape((SitaraRadius.Artwork.toPx() * p).coerceAtLeast(0f))
                            clip = p > 0f
                        }
                    }.testTag("full-player"),
                    onArtist = { expanded = false; onArtist(it) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onSaveQueue = onSaveQueue,
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    // The cover's accent for the dock's surface (light or dark), on the selected tab and the progress line.
                    val wash = LocalArtworkWash.current
                    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    val glass = MaterialTheme.colorScheme.surfaceContainerHigh
                    val accent = if (track != null && wash) {
                        animateArtworkColor(rememberArtworkTheme(track.artwork, dark).accent, "dock accent")
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    GlassSurface(
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = SitaraSpacing.Gutter).padding(bottom = bottomReserved).fillMaxWidth()
                            .animateContentSize(SitaraMotion.orReduced(reduced, SitaraMotion.smooth())),
                        haze = haze,
                        tint = if (track != null && wash) accent.copy(alpha = 0.10f).compositeOver(glass) else glass,
                    ) {
                        val dock = Modifier.testTag("playback-dock").onSizeChanged { size ->
                            onDockHeightChanged(with(density) { size.height.toDp() })
                        }
                        val mini: @Composable (Modifier) -> Unit = { placement ->
                            if (track != null) {
                                MiniPlayer(
                                    state, actions, coverModifier, accent, folded,
                                    placement.pointerInput(track.id) {
                                        var dx = 0f
                                        var dy = 0f
                                        detectDragGestures(
                                            onDragStart = { dx = 0f; dy = 0f },
                                            onDragEnd = {
                                                val threshold = 48.dp.toPx()
                                                when {
                                                    abs(dx) > threshold && abs(dx) > abs(dy) -> if (dx < 0) currentActions.next() else currentActions.previous()
                                                    dy < -threshold -> expanded = true
                                                }
                                            },
                                        ) { change, drag ->
                                            change.consume()
                                            dx += drag.x
                                            dy += drag.y
                                        }
                                    },
                                    onExpand = { expanded = true },
                                )
                            }
                        }
                        // Folded while the listener reads on: one pill row, the open tab beside the song.
                        if (folded && track != null) {
                            Row(dock, verticalAlignment = Alignment.CenterVertically) {
                                navigation(accent, true)
                                mini(Modifier.weight(1f))
                            }
                        } else {
                            Column(dock) {
                                mini(Modifier)
                                navigation(accent, false)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    state: PlayerUiState,
    actions: PlayerActions,
    artworkModifier: Modifier,
    accent: Color,
    folded: Boolean,
    modifier: Modifier,
    onExpand: () -> Unit,
) {
    val track = state.track ?: return
    val content = MaterialTheme.colorScheme.onSurface
    val height = SitaraSpacing.MiniPlayerHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val openPlayer = stringResource(R.string.player_open_player)
    val nextTrack = stringResource(R.string.player_next_track)
    val previousTrack = stringResource(R.string.player_previous_track)
    Column(modifier.fillMaxWidth().height(height)) {
        Row(Modifier.weight(1f).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClick = onExpand).semantics {
                    contentDescription = openPlayer
                    customActions = listOf(
                        CustomAccessibilityAction(nextTrack) { actions.next(); true },
                        CustomAccessibilityAction(previousTrack) { actions.previous(); true },
                    )
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(track.artwork, artworkModifier.size(if (folded) 36.dp else 44.dp), cornerRadius = SitaraRadius.Cover)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = content, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Folded, the row is shared with the open tab: the title stays, the artist goes, a failure still shows.
                    if (!folded || state.failed) {
                        Text(
                            if (state.failed) stringResource(R.string.player_playback_failed) else track.artist,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
            if (state.failed) {
                IconButton(onClick = actions::retryPlayback, modifier = Modifier.size(48.dp)) {
                    Icon(SitaraIcons.Retry, stringResource(R.string.player_try_again), tint = content)
                }
                IconButton(onClick = actions::next, modifier = Modifier.size(48.dp)) {
                    Icon(SitaraIcons.Next, stringResource(R.string.player_skip_to_next), tint = content)
                }
            } else {
                IconButton(onClick = actions::togglePlay, modifier = Modifier.size(48.dp)) {
                    Icon(if (state.isPlaying) SitaraIcons.Pause else SitaraIcons.Play, stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play), tint = content)
                }
            }
        }
        val fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        Box(Modifier.fillMaxWidth().height(2.dp).background(content.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(2.dp).background(accent))
        }
    }
}

// How far the stage shrinks and drops at the end of a back gesture.
private const val BACK_SHRINK = 0.1f
private const val BACK_DROP_DP = 96
