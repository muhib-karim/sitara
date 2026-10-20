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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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

/** One drag at a time over a list of equal-height rows. */
@Stable
class ReorderState {
    var dragged by mutableIntStateOf(-1)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    internal var rowHeight = 0f

    internal fun target(count: Int) = reorderTarget(dragged, offset, rowHeight, count)

    internal fun start(index: Int) {
        dragged = index
        offset = 0f
    }

    internal fun drag(by: Float) {
        offset += by
    }

    /** Ends the drag and returns where the row moved, or null when it stayed. */
    internal fun finish(count: Int): Pair<Int, Int>? {
        val move = (dragged to target(count)).takeIf { dragged >= 0 && it.first != it.second }
        dragged = -1
        offset = 0f
        return move
    }
}

@Composable
fun rememberReorderState(): ReorderState = remember { ReorderState() }

/**
 * A row of a reorderable list: the dragged row follows the finger above the others, which step aside. TalkBack users
 * get "Move up" and "Move down" actions instead of the drag.
 */
fun Modifier.reorderableRow(state: ReorderState, index: Int, count: Int, onMove: (from: Int, to: Int) -> Unit): Modifier = this
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
        customActions = listOfNotNull(
            CustomAccessibilityAction("Move up") { onMove(index, index - 1); true }.takeIf { index > 0 },
            CustomAccessibilityAction("Move down") { onMove(index, index + 1); true }.takeIf { index < count - 1 },
        )
    }

/** The handle that drags its [reorderableRow]; [onMove] runs once, when the row is dropped somewhere new. */
fun Modifier.dragHandle(state: ReorderState, index: Int, count: Int, onMove: (from: Int, to: Int) -> Unit): Modifier = pointerInput(index, count) {
    detectDragGestures(
        onDragStart = { _: Offset -> state.start(index) },
        onDragEnd = { state.finish(count)?.let { (from, to) -> onMove(from, to) } },
        onDragCancel = { state.finish(count) },
        onDrag = { change, amount ->
            change.consume()
            state.drag(amount.y)
        },
    )
}
