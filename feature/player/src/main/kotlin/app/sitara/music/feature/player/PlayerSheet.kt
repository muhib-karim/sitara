@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.sitara.music.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
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

/** One dock, one artwork element: expansion carries the cover into the listening stage. */
@Composable
fun PlayerSheet(
    state: PlayerUiState,
    actions: PlayerActions,
    bottomReserved: Dp,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    initialPage: Int = 0,
    haze: dev.chrisbanes.haze.HazeState? = null,
    navigation: @Composable () -> Unit = {},
    onDockHeightChanged: (Dp) -> Unit = {},
    onArtist: (artistId: String) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val reduced = LocalReducedMotion.current
    val currentActions by rememberUpdatedState(actions)
    val density = LocalDensity.current
    val track = state.track
    val open = expanded && track != null
    PlayerSystemBars(open)
    BackHandler(enabled = open) { expanded = false }
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
                    onArtist = { expanded = false; onArtist(it) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                )
            } else {
                Box(Modifier.fillMaxSize()) {
                    GlassSurface(
                        Modifier.align(Alignment.BottomCenter).padding(horizontal = SitaraSpacing.Gutter).padding(bottom = bottomReserved).fillMaxWidth(),
                        haze = haze,
                    ) {
                        Column(
                            Modifier.testTag("playback-dock").onSizeChanged { size ->
                                onDockHeightChanged(with(density) { size.height.toDp() })
                            },
                        ) {
                            if (track != null) {
                                val wash = LocalArtworkWash.current
                                val tint = animateArtworkColor(rememberArtworkTheme(track.artwork).accent, "mini player tint")
                                MiniPlayer(
                                    state,
                                    actions,
                                    coverModifier,
                                    Modifier.background(if (wash) tint.copy(alpha = 0.08f) else Color.Transparent)
                                        .pointerInput(track.id) {
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
                            navigation()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(state: PlayerUiState, actions: PlayerActions, artworkModifier: Modifier, modifier: Modifier, onExpand: () -> Unit) {
    val track = state.track ?: return
    val content = MaterialTheme.colorScheme.onSurface
    val height = SitaraSpacing.MiniPlayerHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(modifier.fillMaxWidth().height(height)) {
        Row(Modifier.weight(1f).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClick = onExpand).semantics {
                    contentDescription = "Open player"
                    customActions = listOf(
                        CustomAccessibilityAction("Next track") { actions.next(); true },
                        CustomAccessibilityAction("Previous track") { actions.previous(); true },
                    )
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(track.artwork, artworkModifier.size(44.dp), cornerRadius = SitaraRadius.Cover)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = content, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = actions::togglePlay, modifier = Modifier.size(48.dp)) {
                Icon(if (state.isPlaying) SitaraIcons.Pause else SitaraIcons.Play, if (state.isPlaying) "Pause" else "Play", tint = content)
            }
        }
        val fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(2.dp).background(MaterialTheme.colorScheme.primary))
        }
    }
}
