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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/**
 * The cursor / editing pane — `touchpal-layout/arrows-view.png`.
 *
 * ```
 * ┌───────────┬────┬───────┬───────┬─────┐
 * │ ClipBoard │ ⌃  │  All  │ Copy  │  ⌫  │
 * ├───────────┼────┼───────┼───────┼─────┤
 * │     ‹     │Sel │   ›   │  Cut  │  ␣  │
 * ├───────────┼────┼───────┼───────┼─────┤
 * │   Home    │ ⌄  │  End  │ Paste │  ↰  │
 * └───────────┴────┴───────┴───────┴─────┘
 * ```
 * Reached from the cursor icon in the top strip; the undo key at bottom-right reverts
 * the last edit.
 */
@Composable
fun EditLayer(metrics: KeyboardMetrics, selecting: Boolean, onAction: (KeyAction) -> Unit) {
    Column(Modifier.fillMaxWidth().background(T9Theme.background).height(metrics.paneHeight)) {
        EditRow {
            TextKey("Clip\nBoard", 1.8f) { onAction(KeyAction.ShowLayer(KeyboardLayer.CLIPBOARD)) }
            AccentKey("⌃", 1f) { onAction(KeyAction.Cursor(0, -1)) }
            TextKey("All", 1.5f) { onAction(KeyAction.SelectAll) }
            TextKey("Copy", 1.5f) { onAction(KeyAction.Copy) }
            AccentKey("⌫", 1f, repeats = true) { onAction(KeyAction.Backspace) }
        }
        EditRow {
            AccentKey("‹", 1.8f) { onAction(KeyAction.Cursor(-1, 0)) }
            // Lit while Select mode is on, so it is obvious the arrows now extend a selection.
            KeySurface(
                modifier = Modifier.weight(1f).fillMaxSize(),
                label = "Select",
                labelSize = 19,
                labelColor = if (selecting) T9Theme.accent else T9Theme.textPrimary,
                background = if (selecting) T9Theme.shiftActive else T9Theme.key,
                onClick = { onAction(KeyAction.SelectToggle) },
            )
            AccentKey("›", 1.5f) { onAction(KeyAction.Cursor(1, 0)) }
            TextKey("Cut", 1.5f) { onAction(KeyAction.Cut) }
            AccentKey("␣", 1f) { onAction(KeyAction.Space) }
        }
        EditRow {
            TextKey("Home", 1.8f) { onAction(KeyAction.Home) }
            AccentKey("⌄", 1f) { onAction(KeyAction.Cursor(0, 1)) }
            TextKey("End", 1.5f) { onAction(KeyAction.End) }
            TextKey("Paste", 1.5f) { onAction(KeyAction.Paste) }
            AccentKey("↰", 1f) { onAction(KeyAction.Undo) }
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
                label = "\u21B5",
                labelColor = T9Theme.accent,
                labelSize = 21,
                background = T9Theme.keyFlat,
                onClick = { onAction(KeyAction.Enter) },
            )
        }
    }
}

@Composable
private fun ColumnScopeRow(content: @Composable () -> Unit) = content()

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.EditRow(
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) = Row(Modifier.fillMaxWidth().weight(1f), content = content)

@Composable
private fun androidx.compose.foundation.layout.RowScope.TextKey(
    label: String,
    weight: Float,
    onClick: () -> Unit,
) = KeySurface(
    modifier = Modifier.weight(weight).fillMaxSize(),
    label = label,
    labelSize = 19,
    onClick = onClick,
)

@Composable
private fun androidx.compose.foundation.layout.RowScope.AccentKey(
    glyph: String,
    weight: Float,
    repeats: Boolean = false,
    onClick: () -> Unit,
) = IconKeySurface(
    modifier = Modifier.weight(weight).fillMaxSize(),
    onClick = onClick,
    onRepeat = if (repeats) onClick else null,
) { Text(glyph, color = T9Theme.accent, fontSize = 22.sp) }
