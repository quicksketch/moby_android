package org.mobyapp.android.ui.editor

import android.os.SystemClock
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Drag and drop reordering for the items of a horizontal lazy list. The
 * dragged item follows the finger, and swaps places with an item once its
 * middle passes over it. Dragging near either end scrolls the list.
 */
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    /** The key of the item being dragged, or null. */
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    /** How far the dragged item is drawn from its place in the list. */
    var offset by mutableFloatStateOf(0f)
        private set

    private var dragEndedAt = 0L

    /**
     * Whether a tap should be ignored because it ends a drag. Releasing a long
     * press would otherwise also count as a tap.
     */
    val ignoreTap get() = SystemClock.uptimeMillis() - dragEndedAt < TAP_GRACE_MS

    fun start(key: Any) {
        draggingKey = key
        offset = 0f
    }

    fun drag(dx: Float) {
        if (draggingKey == null) return
        offset += dx
        swapIfNeeded()
    }

    fun end() {
        draggingKey = null
        offset = 0f
        dragEndedAt = SystemClock.uptimeMillis()
    }

    private fun swapIfNeeded() {
        val key = draggingKey ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == key } ?: return
        val middle = current.offset + offset + current.size / 2f
        val target = items.firstOrNull { it.key != key && middle >= it.offset && middle <= it.offset + it.size } ?: return
        // A lazy list keeps its first visible item in place by key, so moving
        // that item would scroll the list. Keep the scroll position instead.
        val first = listState.firstVisibleItemIndex
        if (current.index == first || target.index == first) {
            listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
        }
        onMove(current.index, target.index)
        // The item is now laid out where the target was, so keep it drawn
        // under the finger.
        offset += current.offset - target.offset
    }

    /** How far to scroll this frame, when the dragged item is past an end. */
    internal fun edgeScroll(): Float {
        val key = draggingKey ?: return 0f
        val info = listState.layoutInfo
        val current = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return 0f
        val start = current.offset + offset
        val end = start + current.size
        return when {
            start < info.viewportStartOffset -> -minOf((info.viewportStartOffset - start) * EDGE_SPEED, MAX_SCROLL_PX)
            end > info.viewportEndOffset -> minOf((end - info.viewportEndOffset) * EDGE_SPEED, MAX_SCROLL_PX)
            else -> 0f
        }
    }

    internal suspend fun autoScroll() {
        while (draggingKey != null) {
            val delta = edgeScroll()
            if (delta != 0f) {
                // Scrolling moves the item with the list, so move it back.
                offset += listState.scrollBy(delta)
                swapIfNeeded()
            }
            withFrameNanos { }
        }
    }

    private companion object {
        const val TAP_GRACE_MS = 300L
        const val EDGE_SPEED = 0.15f
        const val MAX_SCROLL_PX = 30f
    }
}

@Composable
fun rememberReorderState(listState: LazyListState, onMove: (from: Int, to: Int) -> Unit): ReorderState {
    val currentOnMove by rememberUpdatedState(onMove)
    val state = remember(listState) { ReorderState(listState) { from, to -> currentOnMove(from, to) } }
    LaunchedEffect(state, state.draggingKey) { state.autoScroll() }
    return state
}
