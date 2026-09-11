package io.github.jcastell7.modernt9.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The symbol panes — `symbol-view-1.png` and `symbol-view-2.png`.
 *
 * Page 1 keeps the digits on the left three columns, so it doubles as a numeric pad.
 * Both pages share the bottom row, whose `1/2` ↔ `2/2` key flips between them.
 */
@Composable
fun SymbolLayer(
    metrics: KeyboardMetrics,
    page: KeyboardLayer,
    onAction: (KeyAction) -> Unit,
    onLongPressKey: (List<String>) -> Unit,
) {
    val rows = if (page == KeyboardLayer.SYMBOLS_2) SymbolPages.PAGE_2 else SymbolPages.PAGE_1
    val other = if (page == KeyboardLayer.SYMBOLS_2) KeyboardLayer.SYMBOLS_1 else KeyboardLayer.SYMBOLS_2
    val pageLabel = if (page == KeyboardLayer.SYMBOLS_2) "2/2" else "1/2"
    // Every row is padded to the widest so the columns line up across rows.
    val columns = rows.maxOf { it.size }

    Column(Modifier.fillMaxWidth().background(T9Theme.background)) {
        Column(Modifier.fillMaxWidth().height(metrics.rowHeight * 3)) {
            rows.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    row.forEach { key ->
                        KeySurface(
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            label = key.label,
                            sub = key.sub,
                            labelSize = 21,
                            onClick = { onAction(KeyAction.Literal(key.label)) },
                            onLongPress = { onLongPressKey(LongPressAlternates.forSymbol(key)) },
                        )
                    }
                    // The last row is short; backspace fills the gap, as in the reference.
                    val slack = columns - row.size
                    if (slack > 0) {
                        if (index == rows.lastIndex) {
                            IconKeySurface(
                                modifier = Modifier.weight(slack.toFloat()).fillMaxSize(),
                                onClick = { onAction(KeyAction.Backspace) },
                                onRepeat = { onAction(KeyAction.Backspace) },
                            ) { Text("⌫", color = T9Theme.accent, fontSize = 20.sp) }
                        } else {
                            Box(Modifier.weight(slack.toFloat()))
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(metrics.bottomRowHeight)) {
            KeySurface(
                modifier = Modifier.weight(1.4f).fillMaxSize(),
                label = "abc",
                labelColor = T9Theme.accent,
                labelSize = 19,
                background = T9Theme.keyFlat,
                onClick = { onAction(KeyAction.ShowLayer(KeyboardLayer.MAIN)) },
            )
            if (page == KeyboardLayer.SYMBOLS_1) {
                KeySurface(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    label = "0",
                    labelSize = 21,
                    onClick = { onAction(KeyAction.Literal("0")) },
                )
                KeySurface(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    label = ".",
                    labelSize = 21,
                    onClick = { onAction(KeyAction.Literal(".")) },
                )
            } else {
                KeySurface(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    label = "\u263A\uFE0E",
                    labelColor = T9Theme.accent,
                    labelSize = 20,
                    onClick = { onAction(KeyAction.ShowLayer(KeyboardLayer.EMOJI)) },
                )
            }
            Box(Modifier.weight(if (page == KeyboardLayer.SYMBOLS_1) 3.4f else 4.4f).fillMaxSize()) {
                KeySurface(
                    modifier = Modifier.fillMaxSize(),
                    label = "",
                    onClick = { onAction(KeyAction.Space) },
                )
                Text(
                    "\u25C9", color = T9Theme.textSecondary, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 10.dp),
                )
            }
            KeySurface(
                modifier = Modifier.weight(1.5f).fillMaxSize(),
                label = pageLabel,
                labelColor = T9Theme.accent,
                labelSize = 21,
                background = T9Theme.keyFlat,
                onClick = { onAction(KeyAction.ShowLayer(other)) },
            )
            KeySurface(
                modifier = Modifier.weight(1.5f).fillMaxSize(),
                label = "↵",
                labelColor = T9Theme.accent,
                labelSize = 17,
                background = T9Theme.keyFlat,
                onClick = { onAction(KeyAction.Enter) },
            )
        }
    }
}
