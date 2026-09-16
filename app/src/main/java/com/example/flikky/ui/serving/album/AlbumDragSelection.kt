package com.example.flikky.ui.serving.album

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

/** A fresh long press owns each drag; ordinary swipes remain the grid's scroll gesture. */
@Composable
internal fun Modifier.albumDragSelection(
    gridState: LazyGridState,
    orderedIds: List<String>,
    selected: Set<String>,
    onSelect: (String) -> Unit,
): Modifier {
    val indices = remember(orderedIds) { orderedIds.withIndex().associate { it.value to it.index } }
    val latestSelected by rememberUpdatedState(selected)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    val maxSpeed = with(LocalDensity.current) { 900.dp.toPx() }
    var position by remember(orderedIds) { mutableStateOf<Offset?>(null) }
    val visited = remember(orderedIds) { mutableSetOf<String>() }
    // Selection updates must not restart pointerInput and cancel the held finger.
    val lastIndex = remember(orderedIds) { intArrayOf(-1) }

    fun itemAt(point: Offset): Int? = gridState.layoutInfo.visibleItemsInfo
        .firstOrNull { item ->
            point.x >= item.offset.x && point.x < item.offset.x + item.size.width &&
                point.y >= item.offset.y && point.y < item.offset.y + item.size.height &&
                item.key in indices
        }?.key?.let { indices[it] }

    fun selectThrough(index: Int) {
        val previous = lastIndex[0].takeIf { it >= 0 } ?: index
        for (i in minOf(previous, index)..maxOf(previous, index)) {
            val id = orderedIds[i]
            if (visited.add(id) && id !in latestSelected) latestOnSelect(id)
        }
        lastIndex[0] = index
    }

    LaunchedEffect(orderedIds, position != null) {
        var previousFrame = withFrameNanos { it }
        while (isActive && position != null) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.032f)
            previousFrame = frame
            val point = position ?: break
            val height = gridState.layoutInfo.viewportSize.height.toFloat()
            val strength = when {
                point.y < edge -> -((edge - point.y) / edge).coerceIn(0f, 1f)
                point.y > height - edge -> ((point.y - height + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (strength != 0f) {
                gridState.scrollBy(strength * maxSpeed * seconds)
                // Scrolling reveals items even while the finger stays still.
                itemAt(point.copy(y = point.y.coerceIn(0f, height - 1f)))?.let(::selectThrough)
            }
        }
    }

    return pointerInput(gridState, orderedIds) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = itemAt(down.position) ?: return@awaitEachGesture
            val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            visited.clear()
            lastIndex[0] = -1
            position = held.position
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            selectThrough(start)
            try {
                // Claim movement in Initial only after the long press. In Main,
                // LazyVerticalGrid's scroll handler can consume the first move
                // before its ancestor sees it and cancel selection immediately.
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == held.id } ?: break
                    if (change.isConsumed) break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    position = change.position
                    itemAt(change.position)?.let(::selectThrough)
                    change.consume()
                }
            } finally {
                position = null
                visited.clear()
                lastIndex[0] = -1
            }
        }
    }
}
