package io.github.jcastell7.modernt9

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.jcastell7.modernt9.engine.Candidate
import io.github.jcastell7.modernt9.engine.Composition
import io.github.jcastell7.modernt9.engine.EditorContext
import io.github.jcastell7.modernt9.engine.FieldType
import io.github.jcastell7.modernt9.engine.CandidateSource
import io.github.jcastell7.modernt9.engine.InputEngine
import io.github.jcastell7.modernt9.engine.Punctuation
import io.github.jcastell7.modernt9.ui.KeyAction
import io.github.jcastell7.modernt9.ui.KeyboardLayer
import io.github.jcastell7.modernt9.ui.KeyboardMetrics
import io.github.jcastell7.modernt9.ui.KeyboardScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The keyboard.
 *
 * Holds no prediction logic of its own — it translates Android's editor events into
 * [InputEngine] calls and renders whatever [Composition] comes back. Swapping the engine
 * changes the suggestions and nothing else.
 */
class T9InputMethodService :
    android.inputmethodservice.InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var engine: InputEngine? = null
    private var composition by mutableStateOf(Composition.Empty)
    private var nextWords by mutableStateOf(emptyList<Candidate>())
    private var shiftState by mutableStateOf(ShiftState.OFF)
    private var languageLabel by mutableStateOf("EN")
    private val punctuation = PunctuationCycler()

    private var layer by mutableStateOf(KeyboardLayer.MAIN)
    private var predictionOn by mutableStateOf(true)
    private var newWord by mutableStateOf<String?>(null)
    private var longPressOptions by mutableStateOf(emptyList<String>())
    private var stripLetters by mutableStateOf(emptyList<String>())
    private var metrics by mutableStateOf(KeyboardMetrics())
    private var resizing by mutableStateOf(false)
    private var clips by mutableStateOf(emptyList<String>())

    /** Stops the word we have just committed being immediately re-opened for editing. */
    private var justCommitted = false

    /** Select mode in the editing pane: arrow keys extend a selection while on. */
    private var selecting by mutableStateOf(false)

    /**
     * Keys pressed before the dictionary finished loading.
     *
     * Loading 160k words takes a moment, and the first taps used to be swallowed. They
     * are queued here and replayed once the engine arrives, so nothing is lost.
     */
    private val pendingActions = ArrayDeque<KeyAction>()

    /** Letter-at-a-time entry, used while prediction is off. */
    private val multiTap = MultiTap()

    override fun onCreate() {
        savedStateController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        ClipboardHistory.start(this)
        metrics = KeyboardMetrics(
            scale = Preferences.keyboardScale(this),
            bottomInset = Preferences.bottomInset(this).dp,
        )
        // Dictionary loading is I/O; keep it off the main thread.
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                Engines.create(this@T9InputMethodService, Preferences.engineId(this@T9InputMethodService))
            }
            // Restore the language the user last chose.
            Preferences.language(this@T9InputMethodService)?.let(loaded::switchLanguage)
            languageLabel = loaded.activeLanguage.uppercase()
            engine = loaded
            // Replay anything typed while we were loading.
            while (pendingActions.isNotEmpty()) handleAction(pendingActions.removeFirst())
        }
    }

    override fun onCreateInputView(): View {
        // Compose resolves its window recomposer from the ROOT of the view hierarchy, not
        // from the ComposeView. InputMethodService.setInputView() attaches our view inside
        // the IME's own decor (android:id/parentPanel), whose root carries no owners — so
        // without this the first show crashes with:
        //     IllegalStateException: ViewTreeLifecycleOwner not found from ... parentPanel
        attachOwnersToWindow()

        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@T9InputMethodService)
            setViewTreeViewModelStoreOwner(this@T9InputMethodService)
            setViewTreeSavedStateRegistryOwner(this@T9InputMethodService)
            setContent {
                KeyboardScreen(
                    composition = composition,
                    nextWords = nextWords,
                    layer = layer,
                    metrics = metrics,
                    resizing = resizing,
                    shiftState = shiftState,
                    languageTag = engine?.activeLanguage ?: "en",
                    predictionOn = predictionOn,
                    newWord = newWord,
                    longPressOptions = longPressOptions,
                    stripLetters = stripLetters,
                    clips = clips,
                    selecting = selecting,
                    onAction = ::handleAction,
                    onLongPressKey = { longPressOptions = it },
                    onDismissLongPress = { longPressOptions = emptyList() },
                    onScaleChange = { scale ->
                        metrics = metrics.copy(scale = scale)
                        Preferences.setKeyboardScale(this@T9InputMethodService, scale)
                    },
                    onInsetChange = { inset ->
                        metrics = metrics.copy(bottomInset = inset)
                        Preferences.setBottomInset(this@T9InputMethodService, inset.value)
                    },
                )
            }
        }
    }

    /**
     * Put the ViewTree owners on the IME window's decor view. Idempotent, and safe to
     * call before the window exists — it is retried on every view creation and window
     * show, which covers the orders Android actually uses.
     */
    private fun attachOwnersToWindow() {
        val decor = window?.window?.decorView ?: return
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        attachOwnersToWindow()
        // An IME window becoming visible is the real "started/resumed" moment.
        if (lifecycleRegistry.currentState != Lifecycle.State.RESUMED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        val engine = engine ?: return
        engine.startSession(
            EditorContext(
                fieldType = fieldTypeFor(info?.inputType ?: 0),
                precedingText = currentInputConnection
                    ?.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty(),
                languageTag = resources.configuration.locales[0].language ?: "en",
                incognito = isIncognito(info?.imeOptions ?: 0),
            )
        )
        composition = Composition.Empty
        nextWords = engine.predictNextWord()
        punctuation.configure(fieldTypeFor(info?.inputType ?: 0), engine.activeLanguage)
        layer = KeyboardLayer.MAIN
        resizing = false
        newWord = null
        longPressOptions = emptyList()
        stripLetters = emptyList()
        languageLabel = engine.activeLanguage.uppercase()
        // Arm shift only when the caret really is at the start of a sentence — the
        // field asking for sentence capitalisation is not enough on its own, since the
        // keyboard often opens with the caret in the middle of existing text.
        shiftState = if (startsSentence(info?.inputType ?: 0) && atSentenceStart())
            ShiftState.ONCE else ShiftState.OFF
    }

    /** Reset to the base pane every time the keyboard is shown. */
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        layer = KeyboardLayer.MAIN
        resizing = false
        longPressOptions = emptyList()
        clips = ClipboardHistory.items()
    }

    /**
     * Re-open a word for correction when the caret is placed inside it.
     *
     * The existing word becomes the composing region, so picking a different prediction
     * replaces it in place — no deleting and retyping.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd,
        )
        if (newSelStart != newSelEnd) return          // a selection, not a caret
        val engine = engine ?: return
        val ic = currentInputConnection ?: return

        // With prediction off, placing the caret in a word must not hand it to the
        // predictor. ABC mode edits letter by letter, exactly where the caret is.
        if (!predictionOn) return

        // Android tells us the composing region directly. A caret still inside it means
        // this update is our own doing — far more reliable than a "suppress" flag, which
        // could swallow a real tap whenever updates and edits did not pair up exactly.
        if (candidatesStart >= 0 && newSelStart in candidatesStart..candidatesEnd) return

        if (justCommitted) { justCommitted = false; return }

        // The caret has left the word we were composing: close it before looking at
        // whatever the caret landed in. Without this, moving from one word to another
        // kept the first word's candidates on screen.
        if (!composition.isEmpty) {
            ic.finishComposingText()
            composition = engine.reset()
            stripLetters = emptyList()
        }

        val before = ic.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(CONTEXT_CHARS, 0)?.toString().orEmpty()
        val left = before.takeLastWhile { it.isWordChar() }
        val right = after.takeWhile { it.isWordChar() }
        val word = left + right
        if (word.length < MIN_EDITABLE_WORD) return

        // left.length is where the caret sits inside the word.
        val resumed = engine.resumeEditing(word, left.length) ?: return
        ic.setComposingRegion(newSelStart - left.length, newSelStart + right.length)
        composition = resumed
        newWord = null
        stripLetters = engine.lastKeyLetters()
    }

    override fun onFinishInput() {
        engine?.endSession()
        composition = Composition.Empty
        nextWords = emptyList()
        super.onFinishInput()
    }

    override fun onDestroy() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        ClipboardHistory.stop()
        // The engine is cached for the process, so it is deliberately not closed here.
        engine?.endSession()
        scope.cancel()
        viewModelStore.clear()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    // ---- input handling -------------------------------------------------------

    private fun handleAction(action: KeyAction) {
        val engine = engine ?: run {
            // Not ready yet — remember it rather than dropping it.
            if (pendingActions.size < MAX_PENDING) pendingActions.addLast(action)
            return
        }
        when (action) {
            is KeyAction.Digit -> {
                finishPunctuation()
                if (!predictionOn) { multiTapDigit(action.digit); return }
                composition = engine.onDigit(action.digit)
                newWord = null
                stripLetters = engine.lastKeyLetters()
                showComposing()
            }

            // The @ key: continue a saved phrase if one matches, else insert "@".
            KeyAction.SymbolDigit -> {
                val extended = engine.onSymbolKey()
                if (extended != null) {
                    composition = extended
                    newWord = null
                    showComposing()
                } else {
                    insertLiteral("@")
                }
            }

            is KeyAction.Literal -> insertLiteral(action.text)

            is KeyAction.LockLetter -> {
                // Choosing from the left strip says which letter that key meant — it
                // must not be appended to the end of the word.
                val updated = engine.lockLetter(action.letter)
                if (updated != null) {
                    composition = updated
                    showComposing()
                } else {
                    insertLiteral(action.letter)
                }
            }

            is KeyAction.SelectCandidate -> {
                val text = engine.selectCandidate(action.index) ?: return
                commit(applyShift(text), addSpace = true)
                // Choosing a word settles it: the strip goes quiet until you type again.
                nextWords = emptyList()
            }

            is KeyAction.NextWord -> {
                engine.learn(action.candidate.text)
                commit(applyShift(action.candidate.text), addSpace = true)
                nextWords = emptyList()
            }

            KeyAction.Space -> {
                finishPunctuation()
                settleMultiTap()
                val wasComposing = !composition.isEmpty
                if (wasComposing) {
                    engine.commitInline()?.let { commit(applyShift(it)) }
                }
                currentInputConnection?.commitText(" ", 1)
                clearComposingState()
                // Finishing a word dismisses the offer. Pressing space again with the
                // cursor already after a word re-offers it, which is how you go back and
                // save something you skipped.
                newWord = if (wasComposing) null else unknownWordBeforeCursor()
                // First space after a word offers what might come next; a second space
                // means the user is not interested, so the strip clears.
                nextWords = if (wasComposing) engine.predictNextWord() else emptyList()
            }

            KeyAction.Backspace -> {
                if (multiTap.pending != null) {
                    settleMultiTap()
                    currentInputConnection?.deleteSurroundingText(1, 0)
                    return
                }
                if (punctuation.isActive) {
                    finishPunctuation()
                    currentInputConnection?.finishComposingText()
                    currentInputConnection?.deleteSurroundingText(1, 0)
                    return
                }
                if (composition.isEmpty) {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                } else {
                    composition = engine.onBackspace()
                    newWord = null
                    stripLetters = engine.lastKeyLetters()
                    showComposing()
                }
            }

            KeyAction.Shift -> {
                shiftState = shiftState.next()
                // Re-render so the change is visible on the word in progress: one tap
                // capitalises its first letter, a second upper-cases the whole word.
                if (!composition.isEmpty) showComposing()
            }

            KeyAction.Enter -> {
                if (!composition.isEmpty) {
                    engine.commitInline()?.let { commit(applyShift(it)) }
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
            }

            KeyAction.TogglePrediction -> {
                predictionOn = !predictionOn
                // Leaving either mode must not strand a half-finished word.
                settleMultiTap()
                if (!composition.isEmpty) engine.commitInline()?.let { commit(applyShift(it)) }
                clearComposingState()
                nextWords = emptyList()
            }

            is KeyAction.ShowLayer -> {
                layer = action.layer
                longPressOptions = emptyList()
                nextWords = emptyList()
                if (action.layer != KeyboardLayer.EDIT) selecting = false
                if (action.layer == KeyboardLayer.CLIPBOARD) {
                    ClipboardHistory.capture(
                        getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    )
                    clips = ClipboardHistory.items()
                }
            }

            is KeyAction.SaveNewWord -> {
                engine.userDictionary.addPhrase(action.word)
                newWord = null
            }

            KeyAction.DismissNewWord -> { newWord = null }

            KeyAction.ExpandCandidates -> Unit   // the strip already scrolls

            KeyAction.SwitchLanguage -> {
                val languages = engine.availableLanguages
                if (languages.size > 1) {
                    val next = languages[(languages.indexOf(engine.activeLanguage) + 1) % languages.size]
                    if (engine.switchLanguage(next)) {
                        Preferences.setLanguage(this, next)
                        languageLabel = next.uppercase()
                        punctuation.configure(fieldTypeFor(currentInputEditorInfo?.inputType ?: 0), next)
                        finishPunctuation()
                        currentInputConnection?.finishComposingText()
                        clearComposingState()
                        nextWords = engine.predictNextWord()
                    }
                }
            }

            KeyAction.SwitchIme ->
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()

            KeyAction.OpenSettings -> {
                val intent = android.content.Intent(this, SettingsActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }

            KeyAction.ToggleResize -> {
                resizing = !resizing
                longPressOptions = emptyList()
            }

            KeyAction.Voice, KeyAction.ToggleGestures -> Unit   // not implemented yet

            // ---- editing pane ----
            is KeyAction.Cursor -> moveCursor(action.dx, action.dy)
            KeyAction.SelectAll -> currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
            KeyAction.Copy -> currentInputConnection?.performContextMenuAction(android.R.id.copy)
            KeyAction.Cut -> currentInputConnection?.performContextMenuAction(android.R.id.cut)
            KeyAction.Paste -> currentInputConnection?.performContextMenuAction(android.R.id.paste)
            // A bare KEYCODE_Z types the letter "z". Undo is the editor's own action
            // where it has one (TextView, API 23+), else Ctrl+Z.
            KeyAction.Undo -> {
                val ic = currentInputConnection
                if (ic == null || !ic.performContextMenuAction(android.R.id.undo)) {
                    sendKeyWithMeta(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)
                }
            }

            // Select is a mode: while on, the arrow keys extend a selection.
            KeyAction.SelectToggle -> selecting = !selecting
            KeyAction.Home -> sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_HOME, selectionMeta())
            KeyAction.End -> sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_END, selectionMeta())
            KeyAction.Clipboard -> layer = KeyboardLayer.CLIPBOARD

            is KeyAction.ForgetClip -> {
                ClipboardHistory.remove(action.text)
                clips = ClipboardHistory.items()
            }

            KeyAction.ClearClips -> {
                ClipboardHistory.clear()
                clips = emptyList()
            }
        }
    }

    /**
     * Letter-at-a-time entry for ABC mode.
     *
     * The pending letter is held as composing text so a repeat tap replaces it rather
     * than adding another character.
     */
    private fun multiTapDigit(digit: Char) {
        val ic = currentInputConnection ?: return
        val letters = MultiTap.lettersFor(digit, engine?.activeLanguage ?: "en")
        val result = multiTap.tap(digit, letters, System.currentTimeMillis())
        if (result.append) ic.finishComposingText()
        ic.setComposingText(applyShift(result.letter), 1)
        if (shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF
        composition = Composition.Empty
        nextWords = emptyList()
    }

    /** Settle any pending multi-tap letter before something else happens. */
    private fun settleMultiTap() {
        if (multiTap.pending == null) return
        currentInputConnection?.finishComposingText()
        multiTap.finish()
    }

    /** Commit a character directly, accepting any pending word first. */
    private fun insertLiteral(text: String) {
        val engine = engine ?: return
        finishPunctuation()
        settleMultiTap()
        if (!composition.isEmpty) {
            engine.commitInline()?.let {
                currentInputConnection?.commitText(applyShift(it), 1)
            }
        }
        currentInputConnection?.commitText(text, 1)
        composition = Composition.Empty
        stripLetters = emptyList()
        if (text in Punctuation.SENTENCE_ENDING && shiftState == ShiftState.OFF) {
            shiftState = ShiftState.ONCE
        }
        nextWords = engine.predictNextWord()
    }

    /**
     * Offer to save the word just finished, when the engine does not recognise it —
     * `touchpal-layout/new-word.png`.
     *
     * The candidate is read back from the editor rather than tracked as keystrokes: that
     * is the only way to get the *word* (`user@mail.com`) instead of the digits pressed,
     * and it means the offer reappears if the user returns to a word and presses space
     * again.
     */
    private fun unknownWordBeforeCursor(): String? {
        val engine = engine ?: return null
        return wordBeforeCursor()?.takeIf { it.length in 2..64 && !engine.isKnown(it) }
    }

    /**
     * True when the caret sits at the beginning of a sentence: nothing before it, or
     * only whitespace after a sentence-ending mark.
     */
    private fun atSentenceStart(): Boolean {
        val before = currentInputConnection
            ?.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty()
        val trimmed = before.trimEnd()
        if (trimmed.isEmpty()) return true
        return trimmed.last().toString() in Punctuation.SENTENCE_ENDING
    }

    /** The whitespace-delimited token immediately left of the cursor. */
    private fun wordBeforeCursor(): String? {
        val before = currentInputConnection
            ?.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty()
        val token = before.trimEnd().takeLastWhile { !it.isWhitespace() }
        return token.takeIf { it.isNotBlank() }
    }

    private fun clearComposingState() {
        composition = Composition.Empty
        stripLetters = emptyList()
    }


    private fun moveCursor(dx: Int, dy: Int) {
        val code = when {
            dx < 0 -> KeyEvent.KEYCODE_DPAD_LEFT
            dx > 0 -> KeyEvent.KEYCODE_DPAD_RIGHT
            dy < 0 -> KeyEvent.KEYCODE_DPAD_UP
            else -> KeyEvent.KEYCODE_DPAD_DOWN
        }
        sendKeyWithMeta(code, selectionMeta())
    }

    /** Shift held while Select mode is on, so cursor keys grow a selection. */
    private fun selectionMeta(): Int =
        if (selecting) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0

    /**
     * Send a key with modifier flags — `sendDownUpKeyEvents` cannot carry any, which is
     * why undo (Ctrl+Z) and shift-selection need this.
     */
    private fun sendKeyWithMeta(keyCode: Int, meta: Int) {
        val ic = currentInputConnection ?: return
        val now = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }


    /**
     * Commit whatever punctuation was being cycled and leave multi-tap mode.
     * Capitalisation is armed after a sentence-ending mark.
     */
    private fun finishPunctuation() {
        val mark = punctuation.current ?: return
        currentInputConnection?.finishComposingText()
        if (mark in Punctuation.SENTENCE_ENDING && shiftState == ShiftState.OFF) {
            shiftState = ShiftState.ONCE
        }
        punctuation.finish()
    }

    /** Show the engine's best guess inline, so the user sees the word forming. */
    private fun showComposing() {
        val ic = currentInputConnection ?: return
        if (composition.isEmpty) {
            ic.finishComposingText()
            nextWords = engine?.predictNextWord().orEmpty()
            return
        }

        val text = applyShift(composition.composing)
        ic.setComposingText(text, 1)
        placeCaretWithinComposition(ic, text.length, composition.cursor)
        nextWords = emptyList()
    }

    /**
     * Put the editor caret where the user placed it inside the word.
     *
     * `setComposingText` can only leave the caret at the ends of the text — its
     * `newCursorPosition` cannot address a point inside — so a caret in the middle needs
     * an explicit `setSelection`. Skipped entirely in the common case where the caret is
     * already at the end.
     */
    private fun placeCaretWithinComposition(
        ic: android.view.inputmethod.InputConnection,
        length: Int,
        cursor: Int,
    ) {
        if (cursor >= length) return
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val caretAtEnd = extracted.startOffset + extracted.selectionStart
        val target = caretAtEnd - (length - cursor)
        if (target >= 0) ic.setSelection(target, target)
    }

    /**
     * @param addSpace append a separator after the word, unless one is already there —
     *   choosing a prediction should not need a second tap on space.
     */
    private fun commit(text: String, addSpace: Boolean = false) {
        val ic = currentInputConnection
        val separator = if (addSpace && !nextCharIsSeparator()) " " else ""
        justCommitted = true
        ic?.commitText(text + separator, 1)
        composition = Composition.Empty
        nextWords = engine?.predictNextWord().orEmpty()
        if (shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF
    }

    /**
     * True when the caret already sits before a space or closing punctuation.
     *
     * End of text is deliberately NOT a separator — that is exactly where the automatic
     * space is wanted.
     */
    private fun nextCharIsSeparator(): Boolean {
        val next = currentInputConnection?.getTextAfterCursor(1, 0)?.toString().orEmpty()
        val first = next.firstOrNull() ?: return false
        return first.isWhitespace() || first in SEPARATORS
    }

    private fun applyShift(text: String): String = when (shiftState) {
        ShiftState.OFF -> text
        ShiftState.ONCE -> text.replaceFirstChar { it.uppercase() }
        ShiftState.LOCK -> text.uppercase()
    }

    private companion object {
        const val CONTEXT_CHARS = 64
        const val MAX_PENDING = 24
        const val MIN_EDITABLE_WORD = 2
        const val SEPARATORS = ".,;:!?)]}\"'"
    }
}

enum class ShiftState {
    OFF, ONCE, LOCK;

    fun next(): ShiftState = when (this) {
        OFF -> ONCE
        ONCE -> LOCK
        LOCK -> OFF
    }
}

/** Letters and the apostrophe form a word for the purposes of re-editing. */
private fun Char.isWordChar(): Boolean = isLetter() || this == '\''
