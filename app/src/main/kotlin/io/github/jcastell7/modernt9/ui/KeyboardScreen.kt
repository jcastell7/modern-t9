package io.github.jcastell7.modernt9.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.jcastell7.modernt9.engine.Candidate
import io.github.jcastell7.modernt9.engine.Composition
import io.github.jcastell7.modernt9.ShiftState

/**
 * The whole keyboard.
 *
 * Stateless: it renders what it is given and reports [KeyAction]s upward, so the engine
 * and the UI stay independent — the seam described in `docs/modern-t9.md`.
 */
@Composable
fun KeyboardScreen(
    composition: Composition,
    nextWords: List<Candidate>,
    layer: KeyboardLayer,
    metrics: KeyboardMetrics,
    resizing: Boolean,
    shiftState: ShiftState,
    languageTag: String,
    predictionOn: Boolean,
    newWord: String?,
    longPressOptions: List<String>,
    stripLetters: List<String>,
    clips: List<String>,
    selecting: Boolean,
    onAction: (KeyAction) -> Unit,
    onLongPressKey: (List<String>) -> Unit,
    onDismissLongPress: () -> Unit,
    onScaleChange: (Float) -> Unit,
    onInsetChange: (androidx.compose.ui.unit.Dp) -> Unit,
) {
    // The system draws its gesture bar / navigation buttons over the IME window, so the
    // keys must be lifted clear of it. Whichever is larger wins: the reported inset, or
    // a small floor for devices that report none.
    val systemInset = WindowInsets.navigationBars.asPaddingValues()
        .calculateBottomPadding()
    val bottomPad = maxOf(systemInset, T9Theme.minBottomInset) + metrics.bottomInset

    Box(Modifier.fillMaxWidth().background(T9Theme.background)) {
        Column(Modifier.fillMaxWidth().padding(bottom = bottomPad)) {
            if (resizing) {
                ResizeBar(
                    metrics = metrics,
                    onScaleChange = onScaleChange,
                    onInsetChange = onInsetChange,
                    onDone = { onAction(KeyAction.ToggleResize) },
                )
            } else {
                // Shown on every pane, so the keyboard never changes height when the
                // user switches to symbols, editing, emoji or the clipboard.
                TopStrip(
                    candidates = if (layer == KeyboardLayer.MAIN)
                        composition.candidates.ifEmpty { nextWords } else emptyList(),
                    isPrediction = composition.candidates.isEmpty(),
                    newWord = if (layer == KeyboardLayer.MAIN) newWord else null,
                    onAction = onAction,
                )
            }

            when (layer) {
                KeyboardLayer.MAIN -> MainLayer(
                    metrics = metrics,
                    shiftState = shiftState,
                    languageTag = languageTag,
                    predictionOn = predictionOn,
                    stripLetters = stripLetters,
                    // The character the composing word has where the strip applies —
                    // just before the caret.
                    activeLetter = composition.composing
                        .getOrNull(composition.cursor - 1)?.toString(),
                    onAction = onAction,
                    onLongPressKey = onLongPressKey,
                )
                KeyboardLayer.SYMBOLS_1, KeyboardLayer.SYMBOLS_2 ->
                    SymbolLayer(metrics, layer, onAction, onLongPressKey)
                KeyboardLayer.EDIT -> EditLayer(metrics, selecting, onAction)
                KeyboardLayer.EMOJI -> EmojiLayer(metrics, onAction)
                KeyboardLayer.CLIPBOARD -> ClipboardLayer(metrics, clips, onAction)
            }
        }

        if (longPressOptions.isNotEmpty()) {
            // A scrim over the whole keyboard: touching anywhere outside the panel
            // dismisses it, rather than leaving it stuck until a key is chosen.
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures { onDismissLongPress() }
                    },
            )
            LongPressPopup(
                options = longPressOptions,
                onPick = { onAction(KeyAction.Literal(it)); onDismissLongPress() },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
