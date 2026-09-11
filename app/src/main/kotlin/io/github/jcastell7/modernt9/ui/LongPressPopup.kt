package io.github.jcastell7.modernt9.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The alternates panel shown while a key is held — `touchpal-layout/long-press.png`.
 *
 * Horizontally scrollable, since a letter key with every accent easily exceeds the
 * screen. Chevrons appear on whichever side still has content, so it is obvious the row
 * scrolls rather than being truncated.
 */
@Composable
fun LongPressPopup(
    options: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val canScrollLeft by remember { derivedStateOf { listState.canScrollBackward } }
    val canScrollRight by remember { derivedStateOf { listState.canScrollForward } }

    Box(
        modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(T9Theme.popup)
            .height(58.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chevron("‹", visible = canScrollLeft)

            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(options) { option ->
                    val isFirst = option == options.firstOrNull()
                    Text(
                        text = option,
                        color = T9Theme.textPrimary,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isFirst) T9Theme.keyPressed else T9Theme.popup)
                            .clickable { onPick(option) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Chevron("›", visible = canScrollRight)
        }
    }
}

/** Kept in the layout when hidden so the row does not shift as it scrolls. */
@Composable
private fun Chevron(glyph: String, visible: Boolean) = Text(
    text = glyph,
    color = if (visible) T9Theme.textPrimary else T9Theme.popup,
    fontSize = 22.sp,
    modifier = Modifier.padding(horizontal = 6.dp),
)
