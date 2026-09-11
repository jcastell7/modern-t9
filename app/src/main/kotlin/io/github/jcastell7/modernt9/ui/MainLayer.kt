package io.github.jcastell7.modernt9.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jcastell7.modernt9.engine.Keypad
import io.github.jcastell7.modernt9.ShiftState

/**
 * The main T9 pane — `touchpal-layout/basic-layout.png`.
 *
 * ```
 * ┌──────┬─────────┬─────────┬─────────┬────────┐
 * │ sym  │  @   1  │ abc  2  │ def  3  │   ⌫    │
 * │ strip├─────────┼─────────┼─────────┼────────┤
 * │  !   │ ghi  4  │ jkl  5  │ mno  6  │  a✓  0 │
 * │  ?   ├─────────┼─────────┼─────────┼────────┤
 * │  '   │ pqrs 7  │ tuv  8  │ wxyz 9  │   ⇧    │
 * ├──────┼───┬─────┴─────────┴───┬─────┼────────┤
 * │ 12#  │ , │      space        │  .  │ Search │
 * └──────┴───┴───────────────────┴─────┴────────┘
 * ```
 */
@Composable
fun MainLayer(
    metrics: KeyboardMetrics,
    shiftState: ShiftState,
    languageTag: String,
    predictionOn: Boolean,
    stripLetters: List<String>,
    /** The letter the word currently has at the strip's position — drawn in accent. */
    activeLetter: String?,
    onAction: (KeyAction) -> Unit,
    onLongPressKey: (List<String>) -> Unit,
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
    )

    Column(Modifier.fillMaxWidth().background(T9Theme.background)) {
        Row(Modifier.fillMaxWidth().height(metrics.rowHeight * 3)) {

            // Left: symbols when idle, the last key's letters while composing.
            SideStrip(
                entries = stripLetters.ifEmpty { SymbolPages.QUICK_SYMBOLS },
                active = if (stripLetters.isEmpty()) null else activeLetter,
                modifier = Modifier.weight(T9Theme.WEIGHT_SIDE).fillMaxSize(),
                onPick = { onAction(KeyAction.LockLetter(it)) },
            )

            // Centre: the three T9 columns.
            Column(Modifier.weight(T9Theme.WEIGHT_LETTER * 3)) {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        row.forEach { digit ->
                            LetterKey(
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                digit = digit,
                                shiftState = shiftState,
                                languageTag = languageTag,
                                onAction = onAction,
                                onLongPressKey = onLongPressKey,
                            )
                        }
                    }
                }
            }

            // Right: backspace, prediction toggle, shift.
            Column(Modifier.weight(T9Theme.WEIGHT_SIDE)) {
                IconKeySurface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onClick = { onAction(KeyAction.Backspace) },
                    onRepeat = { onAction(KeyAction.Backspace) },
                ) { Glyph("⌫", T9Theme.accent, 21) }

                IconKeySurface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onClick = { onAction(KeyAction.TogglePrediction) },
                ) {
                    Glyph(
                        if (predictionOn) "a✓" else "a",
                        if (predictionOn) T9Theme.accent else T9Theme.textSecondary,
                        20,
                    )
                }

                // Off: plain. Once: filled accent. Caps lock: filled accent + underline.
                IconKeySurface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    background = if (shiftState == ShiftState.OFF) T9Theme.keyFlat else T9Theme.shiftActive,
                    onClick = { onAction(KeyAction.Shift) },
                ) {
                    Text(
                        text = "⇧",
                        color = if (shiftState == ShiftState.OFF) T9Theme.textSecondary else T9Theme.accent,
                        fontSize = 22.sp,
                        textDecoration = if (shiftState == ShiftState.LOCK)
                            TextDecoration.Underline else null,
                    )
                }
            }
        }

        BottomRow(metrics, onAction)
    }
}

@Composable
private fun LetterKey(
    modifier: Modifier,
    digit: Char,
    shiftState: ShiftState,
    languageTag: String,
    onAction: (KeyAction) -> Unit,
    onLongPressKey: (List<String>) -> Unit,
) {
    // Key 1 shows "@" — TouchPal's placement, and what makes an email typeable.
    val isAt = digit == Keypad.PUNCTUATION_KEY
    // The printed face is the plain E.161 letters in every language. Accented forms
    // such as "ñ" are still reachable — by holding the key, from the left strip, and in
    // ABC mode — they are simply not printed, which keeps the keypad uncluttered.
    val letters = Keypad.letters[digit]
    val face = when {
        isAt -> "@"
        letters == null -> digit.toString()
        shiftState == ShiftState.OFF -> letters
        else -> letters.uppercase()
    }
    KeySurface(
        modifier = modifier,
        label = face,
        digit = digit.toString(),
        labelSize = if (isAt) 26 else 24,
        onClick = {
            if (isAt) onAction(KeyAction.SymbolDigit) else onAction(KeyAction.Digit(digit))
        },
        // A downward flick types the number itself, no mode switch needed.
        onSwipeDown = { onAction(KeyAction.Literal(digit.toString())) },
        onLongPress = {
            onLongPressKey(
                if (isAt) listOf("@", "1", "&", "＠")
                else LongPressAlternates.forDigit(digit, letters.orEmpty())
            )
        },
    )
}

/** The narrow scrollable column on the left. */
@Composable
private fun SideStrip(
    entries: List<String>,
    active: String?,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit,
) {
    // A plain scrolling Column rather than LazyColumn: the list is a dozen short items,
    // and LazyColumn's item recycling made a strip this small feel sticky to drag.
    Box(modifier.padding(T9Theme.keyGap).clip(RoundedCornerShape(T9Theme.keyCorner)).background(T9Theme.keyFlat)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            entries.forEach { entry ->
                // The letter the word currently uses is drawn in accent, so tapping it
                // reads as "already selected" rather than as a tap that did nothing.
                val isActive = active != null && entry.equals(active, ignoreCase = true)
                Text(
                    text = entry,
                    color = if (isActive) T9Theme.accent else T9Theme.textPrimary,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(entry) }
                        .padding(vertical = 8.dp),
                )
                HorizontalDivider(
                    Modifier.padding(horizontal = 14.dp),
                    thickness = 1.dp,
                    color = T9Theme.divider,
                )
            }
        }
    }
}

@Composable
private fun BottomRow(metrics: KeyboardMetrics, onAction: (KeyAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(metrics.bottomRowHeight),
        horizontalArrangement = Arrangement.Start,
    ) {
        KeySurface(
            modifier = Modifier.weight(T9Theme.WEIGHT_SYM_KEY).fillMaxSize(),
            label = "12#",
            labelColor = T9Theme.accent,
            labelSize = 19,
            background = T9Theme.keyFlat,
            onClick = { onAction(KeyAction.ShowLayer(KeyboardLayer.SYMBOLS_1)) },
        )
        KeySurface(
            modifier = Modifier.weight(T9Theme.WEIGHT_COMMA).fillMaxSize(),
            label = ",",
            labelSize = 22,
            onClick = { onAction(KeyAction.Literal(",")) },
            onLongPress = { onAction(KeyAction.SwitchLanguage) },
            onSwipeDown = { onAction(KeyAction.Literal(";")) },
        )
        // Space, with the mic hint TouchPal draws in its top-right.
        Box(Modifier.weight(T9Theme.WEIGHT_SPACE).fillMaxSize()) {
            KeySurface(
                modifier = Modifier.fillMaxSize(),
                label = "",
                digit = "0",
                onClick = { onAction(KeyAction.Space) },
                // The space bar is the "0" key; a downward flick types the digit.
                onSwipeDown = { onAction(KeyAction.Literal("0")) },
            )
            Text(
                "\u25C9",
                color = T9Theme.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 14.dp, top = 10.dp)
                    .clickable { onAction(KeyAction.Voice) },
            )
        }
        KeySurface(
            modifier = Modifier.weight(T9Theme.WEIGHT_COMMA).fillMaxSize(),
            label = ".",
            labelSize = 22,
            onClick = { onAction(KeyAction.Literal(".")) },
            onSwipeDown = { onAction(KeyAction.Literal(":")) },
        )
        Box(Modifier.weight(T9Theme.WEIGHT_SEARCH).fillMaxSize()) {
            KeySurface(
                modifier = Modifier.fillMaxSize(),
                label = "↵",
                labelColor = T9Theme.accent,
                labelSize = 17,
                background = T9Theme.keyFlat,
                onClick = { onAction(KeyAction.Enter) },
                // Long-pressing Search opens emoji, as described in the reference.
                onLongPress = { onAction(KeyAction.ShowLayer(KeyboardLayer.EMOJI)) },
            )
            Text(
                "\u263A\uFE0E",
                color = T9Theme.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun Glyph(text: String, color: androidx.compose.ui.graphics.Color, size: Int) =
    Text(text = text, color = color, fontSize = size.sp)
