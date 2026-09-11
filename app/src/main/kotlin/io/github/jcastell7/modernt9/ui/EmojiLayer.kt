package io.github.jcastell7.modernt9.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.compose.ui.unit.sp

/**
 * Emoji, reached from the ☺ in the top strip, from `2/2`, or by holding Enter.
 *
 * Backed by **`androidx.emoji2:emoji2-emojipicker`** rather than a hand-written list:
 * it ships the current Unicode set, handles categories, recents, skin-tone variants and
 * font fallback on older devices, and is updated by AndroidX rather than by us.
 *
 * It is a View, so it comes in through [AndroidView]; there is no Compose equivalent.
 */
@Composable
fun EmojiLayer(metrics: KeyboardMetrics, onAction: (KeyAction) -> Unit) {
    Column(Modifier.fillMaxWidth().background(T9Theme.background).height(metrics.paneHeight)) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(metrics.contentHeight),
            factory = { context ->
                EmojiPickerView(context).apply {
                    emojiGridColumns = 9
                    setOnEmojiPickedListener { picked ->
                        onAction(KeyAction.Literal(picked.emoji))
                    }
                }
            },
        )

        Row(Modifier.fillMaxWidth().height(metrics.toolbarHeight)) {
            KeySurface(
                modifier = Modifier.weight(2f).fillMaxSize(),
                label = "abc",
                labelColor = T9Theme.accent,
                labelSize = 17,
                background = T9Theme.keyFlat,
                onClick = { onAction(KeyAction.ShowLayer(KeyboardLayer.MAIN)) },
            )
            KeySurface(
                modifier = Modifier.weight(1f).fillMaxSize(),
                label = " ",
                background = T9Theme.key,
                onClick = { onAction(KeyAction.Space) },
            )
            IconKeySurface(
                modifier = Modifier.weight(1f).fillMaxSize(),
                onClick = { onAction(KeyAction.Backspace) },
                onRepeat = { onAction(KeyAction.Backspace) },
            ) { Text("⌫", color = T9Theme.accent, fontSize = 18.sp) }
            KeySurface(
                modifier = Modifier.weight(1f).fillMaxSize(),
                label = "↵",
                labelColor = T9Theme.accent,
                labelSize = 21,
                background = T9Theme.keyFlat,
                onClick = { onAction(KeyAction.Enter) },
            )
        }
    }
}
