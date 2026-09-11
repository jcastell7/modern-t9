package io.github.jcastell7.modernt9.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Live geometry adjustment, reached from the ∿ icon in the top strip.
 *
 * Two independent controls, because "the keyboard is wrong" usually means one of two
 * different things:
 *
 *  * **height** — `−` / `+`, or drag the handle
 *  * **lift**   — `▼` / `▲`, how far the keys sit above the system navigation bar
 *
 * Both persist immediately.
 */
@Composable
fun ResizeBar(
    metrics: KeyboardMetrics,
    onScaleChange: (Float) -> Unit,
    onInsetChange: (Dp) -> Unit,
    onDone: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(T9Theme.keyFlat),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepButton("−") {
            onScaleChange((metrics.scale - STEP).coerceAtLeast(KeyboardMetrics.MIN_SCALE))
        }
        StepButton("+") {
            onScaleChange((metrics.scale + STEP).coerceAtMost(KeyboardMetrics.MAX_SCALE))
        }

        // Drag up to grow, down to shrink.
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .pointerInput(metrics.scale) {
                    detectVerticalDragGestures { _, drag ->
                        onScaleChange(
                            (metrics.scale - drag / DRAG_DIVISOR)
                                .coerceIn(KeyboardMetrics.MIN_SCALE, KeyboardMetrics.MAX_SCALE)
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(T9Theme.accent),
                )
                Text(
                    "  ${(metrics.scale * 100).toInt()}%  ·  lift ${metrics.bottomInset.value.toInt()}",
                    color = T9Theme.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        StepButton("▼") {
            onInsetChange((metrics.bottomInset - INSET_STEP).coerceAtLeast(0.dp))
        }
        StepButton("▲") {
            onInsetChange((metrics.bottomInset + INSET_STEP).coerceAtMost(MAX_INSET))
        }

        Text(
            "Done",
            color = T9Theme.accent,
            fontSize = 15.sp,
            modifier = Modifier
                .clickable(onClick = onDone)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun StepButton(glyph: String, onClick: () -> Unit) = Text(
    text = glyph,
    color = T9Theme.textPrimary,
    fontSize = 19.sp,
    modifier = Modifier
        .clickable(onClick = onClick)
        .padding(horizontal = 11.dp, vertical = 6.dp),
)

private const val STEP = 0.06f
/** Pixels of drag per unit of scale — a full swipe covers the whole range. */
private const val DRAG_DIVISOR = 320f
private val INSET_STEP = 6.dp
private val MAX_INSET = 72.dp
