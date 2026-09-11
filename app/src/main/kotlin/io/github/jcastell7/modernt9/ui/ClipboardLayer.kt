package io.github.jcastell7.modernt9.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Recently copied text, reached from the Clipboard key in the editing pane.
 *
 * Tapping an entry inserts it; `✕` forgets it. History is in memory only — clipboard
 * contents are often sensitive, so they are not written to disk.
 */
@Composable
fun ClipboardLayer(
    metrics: KeyboardMetrics,
    items: List<String>,
    onAction: (KeyAction) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(T9Theme.background).height(metrics.paneHeight)) {
        Box(Modifier.fillMaxWidth().height(metrics.contentHeight)) {
            if (items.isEmpty()) {
                Text(
                    "Nothing copied yet",
                    color = T9Theme.textSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items) { entry ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = entry,
                                color = T9Theme.textPrimary,
                                fontSize = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onAction(KeyAction.Literal(entry)) }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                            )
                            Text(
                                "✕",
                                color = T9Theme.textSecondary,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .clickable { onAction(KeyAction.ForgetClip(entry)) }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = T9Theme.divider)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(metrics.toolbarHeight)) {
            KeySurface(
                modifier = Modifier.weight(1f).fillMaxSize(),
                label = "abc",
                labelColor = T9Theme.accent,
                labelSize = 17,
                background = T9Theme.keyFlat,
                onClick = { onAction(KeyAction.ShowLayer(KeyboardLayer.MAIN)) },
            )
            KeySurface(
                modifier = Modifier.weight(1f).fillMaxSize(),
                label = "⌶",
                labelColor = T9Theme.accent,
                labelSize = 18,
                background = T9Theme.keyFlat,
                onClick = { onAction(KeyAction.ShowLayer(KeyboardLayer.EDIT)) },
            )
            KeySurface(
                modifier = Modifier.weight(1f).fillMaxSize(),
                label = "Clear",
                labelColor = T9Theme.textSecondary,
                labelSize = 15,
                background = T9Theme.keyFlat,
                onClick = { onAction(KeyAction.ClearClips) },
            )
        }
    }
}
