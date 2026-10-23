package app.sitara.music.core.designsystem

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** Where a row dragged from [from] by [offset] lands, in whole rows of [rowHeight], inside 0 until [count]. */
fun reorderTarget(from: Int, offset: Float, rowHeight: Float, count: Int): Int =
    if (rowHeight <= 0f || count <= 0) from else (from + (offset / rowHeight).roundToInt()).coerceIn(0, count - 1)

/** How many rows the row at [index] steps aside while the row from [from] hovers over [target]. */
fun reorderShift(index: Int, from: Int, target: Int): Int = when {
    from < target && index in from + 1..target -> -1
    target < from && index in target until from -> 1
    else -> 0
}

/**
 * One drag at a time over a list of equal-height rows; [onStep] runs each time the dragged row passes onto another row.
 * [moveUpLabel] and [moveDownLabel] name the TalkBack actions; [rememberReorderState] passes the localized words.
 */
@Stable
class ReorderState(
    private val onStep: () -> Unit = {},
    internal val moveUpLabel: String = "Move up",
    internal val moveDownLabel: String = "Move down",
) {
    var dragged by mutableIntStateOf(-1)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    internal var rowHeight = 0f
    private var count = 0

    internal fun target(count: Int = this.count) = reorderTarget(dragged, offset, rowHeight, count)

    internal fun start(index: Int, count: Int) {
        dragged = index
        offset = 0f
        this.count = count
    }

    internal fun drag(by: Float) {
        val before = target()
        offset += by
        if (target() != before) onStep()
    }

    /** Ends the drag and returns where the row moved, or null when it stayed. */
    internal fun finish(): Pair<Int, Int>? {
        val move = (dragged to target()).takeIf { dragged >= 0 && it.first != it.second }
        dragged = -1
        offset = 0f
        return move
    }
}

/** A [ReorderState] whose drags tick the phone's haptics once per row passed. */
@Composable
fun rememberReorderState(): ReorderState {
    val haptics = LocalHapticFeedback.current
    val moveUp = stringResource(R.string.ds_move_up)
    val moveDown = stringResource(R.string.ds_move_down)
    return remember(haptics, moveUp, moveDown) {
        ReorderState(onStep = { haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }, moveUpLabel = moveUp, moveDownLabel = moveDown)
    }
}

/**
 * A row of a reorderable list: the dragged row follows the finger above the others, which step aside. TalkBack users
 * get "Move up" and "Move down" actions instead of the drag, after any [actions] of the row's own.
 */
fun Modifier.reorderableRow(
    state: ReorderState,
    index: Int,
    count: Int,
    onMove: (from: Int, to: Int) -> Unit,
    actions: List<CustomAccessibilityAction> = emptyList(),
): Modifier = this
    .onSizeChanged { state.rowHeight = it.height.toFloat() }
    .zIndex(if (state.dragged == index) 1f else 0f)
    .graphicsLayer {
        translationY = when (state.dragged) {
            -1 -> 0f
            index -> state.offset
            else -> reorderShift(index, state.dragged, state.target(count)) * state.rowHeight
        }
    }
    .semantics {
        customActions = actions + listOfNotNull(
            CustomAccessibilityAction(state.moveUpLabel) { onMove(index, index - 1); true }.takeIf { index > 0 },
            CustomAccessibilityAction(state.moveDownLabel) { onMove(index, index + 1); true }.takeIf { index < count - 1 },
        )
    }

/** The handle that drags its [reorderableRow]; [onMove] runs once, when the row is dropped somewhere new. */
fun Modifier.dragHandle(state: ReorderState, index: Int, count: Int, onMove: (from: Int, to: Int) -> Unit): Modifier = pointerInput(index, count) {
    detectDragGestures(
        onDragStart = { _: Offset -> state.start(index, count) },
        onDragEnd = { state.finish()?.let { (from, to) -> onMove(from, to) } },
        onDragCancel = { state.finish() },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)
        },
    )
}
