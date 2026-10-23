package app.sitara.music.feature.player

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.LocalReducedMotion
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.ReorderState
import app.sitara.music.core.designsystem.SitaraColors
import app.sitara.music.core.designsystem.SitaraIcons
import app.sitara.music.core.designsystem.TrackRow
import app.sitara.music.core.designsystem.dragHandle
import app.sitara.music.core.designsystem.rememberReorderState
import app.sitara.music.core.designsystem.reorderableRow
import app.sitara.music.core.model.Track
import app.sitara.music.core.model.isLocal
import kotlinx.coroutines.launch

/** The queue page: Start radio, Save as playlist, Up next with Undo after a removal, and the transport. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UpNextPage(state: PlayerUiState, actions: PlayerActions, onNowPlaying: () -> Unit, onSaveQueue: (List<Track>) -> Unit) {
    val track = state.track ?: return
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val undo = stringResource(R.string.player_undo)
    // The queue keeps only the last removal, so a new one replaces the previous offer to undo.
    val remove = { index: Int, removed: Track ->
        actions.removeUpNext(index)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(resources.getString(R.string.player_removed, removed.title), actionLabel = undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                actions.undoRemoveUpNext()
            }
        }
        Unit
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.player_up_next), style = MaterialTheme.typography.headlineMedium, color = SitaraColors.StageContent, modifier = Modifier.weight(1f))
            PillButton(stringResource(R.string.player_now_playing), onNowPlaying, primary = false)
        }
        FlowRow(
            Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            // Radio starts from a catalogue song; a song on the phone has none to start from.
            if (state.track?.isLocal != true) RadioControl(state.radio, actions::startRadio)
            if (state.upNext.isNotEmpty()) PillButton(stringResource(R.string.player_save_as_playlist), { onSaveQueue(listOf(track) + state.upNext) }, primary = false)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.upNext.isEmpty()) {
                Text(stringResource(R.string.player_queue_end), color = SitaraColors.StageSecondary, modifier = Modifier.padding(24.dp))
            } else {
                UpNextList(state.upNext, state.upNextFromRadio, actions, remove, Modifier.fillMaxSize())
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
        PlayerControls(state, actions, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    }
}

/**
 * Up next: the listener's songs, then radio's under a heading of their own. Each part reorders within itself, by drag
 * (a haptic tick per row passed) or TalkBack's Move up and Move down; a swipe left, the X or TalkBack's Remove takes a
 * song out. A moved song shows in its new place before the queue confirms it.
 */
@Composable
private fun UpNextList(upNext: List<Track>, fromRadio: Int, actions: PlayerActions, onRemove: (Int, Track) -> Unit, modifier: Modifier) {
    var order by remember(upNext) { mutableStateOf(upNext) }
    val radio = fromRadio.coerceIn(0, order.size)
    val ownReorder = rememberReorderState()
    val radioReorder = rememberReorderState()
    val removeAction = stringResource(R.string.player_remove_from_queue)
    fun LazyListScope.part(first: Int, count: Int, reorder: ReorderState) {
        val move = { from: Int, to: Int ->
            order = order.toMutableList().apply { add(first + to, removeAt(first + from)) }
            actions.moveUpNext(first + from, first + to)
        }
        itemsIndexed(order.subList(first, first + count), key = { i, item -> "${first + i}-${item.id}" }, contentType = { _, _ -> "queue track" }) { i, item ->
            val remove = { onRemove(first + i, item) }
            QueueRow(
                item, onPlay = { actions.playQueueIndex(first + i) }, onRemove = remove,
                row = Modifier.reorderableRow(reorder, i, count, move, listOf(CustomAccessibilityAction(removeAction) { remove(); true })),
                handle = Modifier.dragHandle(reorder, i, count, move),
            )
        }
    }
    LazyColumn(modifier.testTag("up-next")) {
        part(0, order.size - radio, ownReorder)
        if (radio > 0) {
            item(key = "radio-heading") {
                Text(
                    stringResource(R.string.player_up_next_from_radio), style = MaterialTheme.typography.titleSmall, color = SitaraColors.StageSecondary,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                )
            }
            part(order.size - radio, radio, radioReorder)
        }
    }
}

/** A queued song; swiped left past a third of its width it is removed, a shorter swipe springs back. */
@Composable
private fun QueueRow(track: Track, onPlay: () -> Unit, onRemove: () -> Unit, row: Modifier, handle: Modifier) {
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current
    var swipe by remember { mutableFloatStateOf(0f) }
    // The page recomposes as playback moves on; the gesture must not restart with each new lambda.
    val remove by rememberUpdatedState(onRemove)
    val swipeToRemove = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Only a leftward swipe is the row's; a rightward one is left to the pager, back to Lyrics.
            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over -> if (over < 0) change.consume() } ?: return@awaitEachGesture
            horizontalDrag(start.id) { change ->
                swipe = (swipe + change.positionChange().x).coerceAtMost(0f)
                change.consume()
            }
            if (swipe < -size.width / 3f) {
                remove()
            } else {
                scope.launch { if (reduced) swipe = 0f else animate(swipe, 0f) { value, _ -> swipe = value } }
            }
        }
    }
    TrackRow(track, onClick = onPlay, modifier = row.then(swipeToRemove).graphicsLayer { translationX = swipe }) {
        IconButton(onClick = onRemove) {
            Icon(SitaraIcons.Close, stringResource(R.string.player_remove_track_from_queue, track.title), tint = SitaraColors.StageSecondary)
        }
        Icon(SitaraIcons.DragHandle, null, tint = SitaraColors.StageSecondary, modifier = Modifier.size(48.dp).then(handle).padding(12.dp))
    }
}

@Composable
private fun RadioControl(status: RadioStatus, onStart: () -> Unit, modifier: Modifier = Modifier) {
    when (status) {
        RadioStatus.READY -> PillButton(stringResource(R.string.player_start_radio), onStart, modifier, primary = false)
        RadioStatus.LOADING -> Text(stringResource(R.string.player_radio_loading), color = SitaraColors.StageSecondary, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        RadioStatus.UNAVAILABLE -> Text(stringResource(R.string.player_radio_unavailable), color = SitaraColors.StageSecondary, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
    }
}
