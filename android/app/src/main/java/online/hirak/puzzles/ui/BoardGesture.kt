package online.hirak.puzzles.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Callbacks get the board's width in px so callers can derive the cell size.
 *
 * One-finger press → move → release on a game board. Every event is
 * consumed so the surrounding scroll view can't steal the drag, and the
 * board is excluded from Android's edge back-swipe — the counterpart of
 * iOS `disablesSwipeBack()`, so drawing a bridge or track toward the screen
 * edge never navigates away mid-game.
 */
fun Modifier.boardDrag(
    key: Any?,
    onStart: (pos: Offset, width: Float) -> Unit = { _, _ -> },
    onMove: (pos: Offset, width: Float) -> Unit = { _, _ -> },
    onEnd: (start: Offset, end: Offset, width: Float) -> Unit = { _, _, _ -> },
): Modifier = systemGestureExclusion().pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val width = size.width.toFloat()
        onStart(down.position, width)
        var last = down.position
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.position != last) {
                last = change.position
                onMove(last, width)
            }
            change.consume()
            if (!change.pressed) break
        }
        onEnd(down.position, last, width)
    }
}
