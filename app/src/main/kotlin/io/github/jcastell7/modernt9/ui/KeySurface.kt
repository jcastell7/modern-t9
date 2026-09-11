package io.github.jcastell7.modernt9.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One key: a large centred label, an optional grey digit in the bottom-right corner, and
 * an optional corner glyph reachable by holding.
 *
 * @param onSwipeDown a downward flick types the key's digit, so numbers need no mode
 *   switch.
 * @param onRepeat when set the key repeats while held, accelerating (backspace).
 */
@Composable
fun KeySurface(
    modifier: Modifier = Modifier,
    label: String,
    digit: String? = null,
    sub: String? = null,
    labelColor: Color = T9Theme.textPrimary,
    labelSize: Int = 22,
    background: Color = T9Theme.key,
    bold: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier
            .padding(T9Theme.keyGap)
            .clip(RoundedCornerShape(T9Theme.keyCorner))
            .background(if (pressed) T9Theme.keyPressed else background)
            .keyGestures(
                key = label,
                onTap = onClick,
                onLongPress = onLongPress,
                onSwipeDown = onSwipeDown,
                onRepeat = onRepeat,
                onPressedChange = { pressed = it },
            ),
        contentAlignment = Alignment.Center,
    ) {
        KeyLabel(label, labelColor, labelSize, bold)

        if (digit != null) {
            KeyLabel(
                digit, T9Theme.textSecondary, 13, false,
                Modifier.align(Alignment.BottomEnd).padding(end = 7.dp, bottom = 3.dp),
            )
        }
        if (sub != null) {
            KeyLabel(
                sub, T9Theme.textSecondary, 11, false,
                Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun KeyLabel(
    text: String,
    color: Color,
    size: Int,
    bold: Boolean,
    modifier: Modifier = Modifier,
) = Text(
    text = text,
    color = color,
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
    modifier = modifier,
)

/** A key whose face is an icon. Used for the right-hand column and the editing pane. */
@Composable
fun IconKeySurface(
    modifier: Modifier = Modifier,
    background: Color = T9Theme.keyFlat,
    onLongPress: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .padding(T9Theme.keyGap)
            .clip(RoundedCornerShape(T9Theme.keyCorner))
            .background(if (pressed) T9Theme.keyPressed else background)
            .keyGestures(
                key = background,
                onTap = onClick,
                onLongPress = onLongPress,
                onSwipeDown = onSwipeDown,
                onRepeat = onRepeat,
                onPressedChange = { pressed = it },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}
