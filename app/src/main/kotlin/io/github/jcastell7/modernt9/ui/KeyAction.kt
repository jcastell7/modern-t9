package io.github.jcastell7.modernt9.ui

import io.github.jcastell7.modernt9.engine.Candidate

/** Everything the keyboard UI can ask the service to do. */
sealed interface KeyAction {
    data class Digit(val digit: Char) : KeyAction
    data class SelectCandidate(val index: Int) : KeyAction
    data class NextWord(val candidate: Candidate) : KeyAction

    /** A literal string typed directly — symbol keys, long-press picks, the strip. */
    data class Literal(val text: String) : KeyAction

    /** A letter chosen from the left strip, pinned at the caret's position. */
    data class LockLetter(val letter: String) : KeyAction

    /** The `@`/1 key: extends a phrase match when one exists, else inserts `@`. */
    data object SymbolDigit : KeyAction

    data object Space : KeyAction
    data object Backspace : KeyAction
    data object Shift : KeyAction
    data object Enter : KeyAction
    data object SwitchIme : KeyAction
    data object SwitchLanguage : KeyAction
    data object TogglePrediction : KeyAction
    data object OpenSettings : KeyAction
    data object ToggleGestures : KeyAction
    /** Enter or leave live-resize mode. */
    data object ToggleResize : KeyAction
    data object ExpandCandidates : KeyAction

    data class ShowLayer(val layer: KeyboardLayer) : KeyAction

    /** Add-to-dictionary offer for an unrecognised word (`new-word.png`). */
    data class SaveNewWord(val word: String) : KeyAction
    data object DismissNewWord : KeyAction

    // Editing pane (`arrows-view.png`)
    data class Cursor(val dx: Int, val dy: Int) : KeyAction
    data object SelectToggle : KeyAction
    data object SelectAll : KeyAction
    data object Copy : KeyAction
    data object Cut : KeyAction
    data object Paste : KeyAction
    data object Undo : KeyAction
    data object Home : KeyAction
    data object End : KeyAction
    data object Clipboard : KeyAction
    data class ForgetClip(val text: String) : KeyAction
    data object ClearClips : KeyAction
}
