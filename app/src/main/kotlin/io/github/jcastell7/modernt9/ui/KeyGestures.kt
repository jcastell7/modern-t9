package io.github.jcastell7.modernt9.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One gesture recogniser for every key, because tap / long-press / swipe-down / hold-to-
 * repeat all have to come from the same pointer stream — stacking separate
 * `detectTapGestures` and `detectVerticalDragGestures` modifiers makes them fight over
 * the event.
 *
 * Resolution order after the finger goes down:
 *
 *  1. flicked clearly downward     → [onSwipeDown] (types the key's digit)
 *  2. held past the long-press time → [onRepeat] if the key repeats, else [onLongPress]
 *  3. lifted before either          → [onTap]
 */
fun Modifier.keyGestures(
    key: Any?,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
    onPressedChange: (Boolean) -> Unit = {},
): Modifier = pointerInput(key, onLongPress, onSwipeDown, onRepeat) {
    val slop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPressedChange(true)

        var swiped = false
        var lifted = false

        // Phase 1 — wait for a downward swipe, a lift, or the long-press deadline.
        val settledEarly = withTimeoutOrNull(longPressMs) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) { lifted = true; break }
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                // A deliberate flick, not a fast tap that drifted. Three conditions
                // together: real distance (several times the system slop), clearly
                // vertical (twice as far down as sideways), and nothing already typed
                // by this press. 1.5x slop was firing on ordinary fast typing.
                if (onSwipeDown != null &&
                    dy > slop * SWIPE_SLOP_MULTIPLIER &&
                    dy > abs(dx) * SWIPE_VERTICAL_DOMINANCE
                ) {
                    swiped = true
                    change.consume()
                    break
                }
            }
            true
        }

        when {
            swiped -> {
                onSwipeDown?.invoke()
                waitForUpOrCancellation()
            }

            lifted -> onTap()

            // Timed out: the finger is still down, so this is a hold.
            settledEarly == null -> {
                if (onRepeat != null) {
                    repeatWhileHeld(onRepeat)
                } else {
                    onLongPress?.invoke()
                    waitForUpOrCancellation()
                }
            }

            else -> {
                // The loop exited without a verdict (pointer vanished); treat as a tap.
                onTap()
            }
        }

        onPressedChange(false)
    }
}

/**
 * Fire [action] repeatedly while the key is held, accelerating from [START_MS] down to
 * [MIN_MS] — the behaviour expected of a backspace key.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.repeatWhileHeld(
    action: () -> Unit,
) {
    var interval = START_MS
    while (true) {
        action()
        val released = withTimeoutOrNull(interval) {
            waitForUpOrCancellation()
            true
        }
        if (released == true) break
        interval = (interval * DECAY).toLong().coerceAtLeast(MIN_MS)
    }
}

/** Distance a swipe must travel, as a multiple of the system touch slop (~8dp). */
private const val SWIPE_SLOP_MULTIPLIER = 5f
/** How much further down than sideways the finger must go to count as a swipe. */
private const val SWIPE_VERTICAL_DOMINANCE = 2f

private const val START_MS = 180L
private const val MIN_MS = 22L
private const val DECAY = 0.74f
