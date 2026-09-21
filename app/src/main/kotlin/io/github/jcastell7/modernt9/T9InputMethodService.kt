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
    /** The active language tag, as Compose state so the space-bar indicator redraws. */
    private var languageTag by mutableStateOf("en")
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

    /** Where the selection grows from while [selecting]; -1 when unknown. */
    private var selectionAnchor = -1

    /**
     * The shift state a multi-tap letter was started with. Cycling the same key must
     * keep that case — "A" then "B" then "C" — rather than dropping to lower case after
     * the first tap because shift-once had already been consumed.
     */
    private var multiTapShift: ShiftState? = null

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
        DebugLog.configure(this)
        DebugLog.i("ime", "onCreate")
        ClipboardHistory.start(this)
        metrics = KeyboardMetrics(
            scale = Preferences.keyboardScale(this),
            bottomInset = Preferences.bottomInset(this).dp,
        )
        // Dictionary loading is I/O; keep it off the main thread.
        scope.launch {
            val started = android.os.SystemClock.elapsedRealtime()
            val loaded = try {
                withContext(Dispatchers.IO) {
                    Engines.create(this@T9InputMethodService, Preferences.engineId(this@T9InputMethodService))
                }
            } catch (t: Throwable) {
                DebugLog.e("engine", "failed to load", t)
                throw t
            }
            // Restore the language the user last chose.
            Preferences.language(this@T9InputMethodService)?.let(loaded::switchLanguage)
            languageTag = loaded.activeLanguage
            engine = loaded
            DebugLog.i("engine", "${loaded.descriptor.id} v${loaded.descriptor.version} ready in " +
                "${android.os.SystemClock.elapsedRealtime() - started}ms · language ${loaded.activeLanguage} · " +
                "${pendingActions.size} queued actions")
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
                    languageTag = languageTag,
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
        DebugLog.i("ime", "onStartInput ${info?.packageName} type=${fieldTypeFor(info?.inputType ?: 0)} " +
            "restarting=$restarting prediction=$predictionOn")
        // A commit made while prediction was off never had its selection update seen
        // (those are ignored in ABC mode), so the flag would otherwise stay armed and
        // swallow the next real caret move.
        justCommitted = false
        selecting = false
        selectionAnchor = -1
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
        languageTag = engine.activeLanguage
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
        val engine = engine ?: return
        val ic = currentInputConnection ?: return

        if (newSelStart != newSelEnd) {
            // A selection, not a caret. Whatever word was open for editing is closed:
            // the next key must act on the selection, not on the composition.
            if (!composition.isEmpty) {
                ic.finishComposingText()
                composition = engine.reset()
                stripLetters = emptyList()
            }
            return
        }

        // With prediction off, placing the caret in a word must not hand it to the
        // predictor. ABC mode edits letter by letter, exactly where the caret is.
        if (!predictionOn) return

        if (justCommitted) { justCommitted = false; return }

        val before = ic.getTextBeforeCursor(CONTEXT_CHARS, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(CONTEXT_CHARS, 0)?.toString().orEmpty()
        val left = before.takeLastWhile { it.isWordChar() }
        val right = after.takeWhile { it.isWordChar() }
        val word = left + right

        if (!composition.isEmpty) {
            val inside = candidatesStart >= 0 && newSelStart in candidatesStart..candidatesEnd
            if (inside) {
                // Our own edits always leave the caret where the engine says it is.
                // Anywhere else inside the word means the user moved it — the engine's
                // caret must follow, or the next backspace deletes the wrong letter and
                // then, at caret 0, nothing at all. (Reported as: "it deletes the letter
                // I just added and then won't delete the rest of the word".)
                val offset = newSelStart - candidatesStart
                if (offset == composition.cursor) return
                val moved = engine.resumeEditing(word, left.length)
                if (moved != null) {
                    DebugLog.i("edit", "caret moved inside word: ${composition.cursor} -> ${left.length}")
                    composition = moved
                    stripLetters = engine.lastKeyLetters()
                    return
                }
            }
            // The caret has left the word we were composing: close it before looking at
            // whatever the caret landed in. Without this, moving from one word to
            // another kept the first word's candidates on screen.
            ic.finishComposingText()
            composition = engine.reset()
            stripLetters = emptyList()
        }

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
        DebugLog.i("ime", "onDestroy")
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
            DebugLog.w("ime", "engine not ready, queued ${action.name()} (${pendingActions.size})")
            return
        }
        // A throwing handler must not take the keyboard down with it — log, recover.
        DebugLog.guard("action ${action.name()}", Unit) { dispatch(engine, action) }
    }

    private fun dispatch(engine: InputEngine, action: KeyAction) {
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
                // With text selected, backspace deletes the selection — nothing else.
                if (currentInputConnection?.getSelectedText(0)?.isNotEmpty() == true) {
                    settleMultiTap()
                    finishPunctuation()
                    currentInputConnection?.commitText("", 1)
                    clearComposingState()
                    nextWords = emptyList()
                    return
                }
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
                // Deleting is never a request for what might come next: the offers
                // for the previous word must not reappear as this one is erased.
                nextWords = emptyList()
                if (composition.isEmpty) {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                } else {
                    composition = engine.onBackspace()
                    newWord = null
                    stripLetters = engine.lastKeyLetters()
                    // The last key gone: the composing text must go with it.
                    // finishComposingText() alone would *keep* that letter in the
                    // editor as ordinary text.
                    if (composition.isEmpty) currentInputConnection?.commitText("", 1)
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
                DebugLog.i("ime", "prediction ${if (predictionOn) "on" else "off"}")
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
                    // Cycle: each language on its own, then all of them together
                    // (bilingual mode), then back to the first.
                    val modes = languages + languages.joinToString("+")
                    val next = modes[(modes.indexOf(engine.activeLanguage) + 1) % modes.size]
                    if (engine.switchLanguage(next)) {
                        DebugLog.i("ime", "language -> $next")
                        Preferences.setLanguage(this, next)
                        languageTag = next
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

            KeyAction.ToggleGestures -> Unit   // not implemented yet

            // ---- editing pane ----
            is KeyAction.Cursor ->
                if (selecting) extendSelection(action.dx, action.dy) else moveCursor(action.dx, action.dy)
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

            // Select is a mode: while on, the arrow keys extend a selection from the
            // caret position at the moment it was switched on.
            KeyAction.SelectToggle -> {
                selecting = !selecting
                selectionAnchor = if (selecting) snapshot()?.selStart ?: -1 else -1
                DebugLog.i("edit", "select ${if (selecting) "on @$selectionAnchor" else "off"}")
            }
            KeyAction.Home -> if (selecting) extendToLineEdge(start = true)
                else sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_HOME, 0)
            KeyAction.End -> if (selecting) extendToLineEdge(start = false)
                else sendKeyWithMeta(KeyEvent.KEYCODE_MOVE_END, 0)
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
        val now = System.currentTimeMillis()
        if (!multiTap.continues(digit, now)) {
            // A new letter: the previous one is final (and spends shift-once, if it
            // used it). The case of this one is decided now and kept while it cycles.
            settleMultiTap()
            multiTapShift = shiftState
        }
        val result = multiTap.tap(digit, letters, now)
        ic.setComposingText(applyShift(result.letter, multiTapShift ?: shiftState), 1)
        composition = Composition.Empty
        nextWords = emptyList()
    }

    /**
     * Settle any pending multi-tap letter before something else happens. Shift-once is
     * spent here, when the letter is final, not on the first tap.
     */
    private fun settleMultiTap() {
        if (multiTapShift == ShiftState.ONCE && shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF
        multiTapShift = null
        if (multiTap.pending == null) return
        currentInputConnection?.finishComposingText()
        multiTap.finish()
    }

    /** Commit a character directly, accepting any pending word first. */
    private fun insertLiteral(raw: String) {
        val engine = engine ?: return
        finishPunctuation()
        settleMultiTap()
        if (!composition.isEmpty) {
            engine.commitInline()?.let {
                currentInputConnection?.commitText(applyShift(it), 1)
                if (shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF
            }
        }
        val ic = currentInputConnection
        // A letter picked from the long-press panel is typed like any other letter:
        // it takes the shift state, and spends shift-once.
        val text = if (raw.any { it.isLetter() }) applyShift(raw).also {
            if (shiftState == ShiftState.ONCE) shiftState = ShiftState.OFF
        } else raw
        if (predictionOn && text in Punctuation.CLOSING) {
            // Prediction leaves a space after every accepted word. A closing mark must
            // attach to that word, so the space is swallowed, the mark inserted, and a
            // space put back after it for the next word.
            if (precededByAutoSpace()) ic?.deleteSurroundingText(1, 0)
            ic?.commitText(text, 1)
            if (!nextCharIsSeparator()) ic?.commitText(" ", 1)
        } else {
            // ABC mode has no automatic spaces, so there is nothing to swallow: the
            // character goes exactly where the caret is.
            ic?.commitText(text, 1)
        }
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


    private fun moveCursor(dx: Int, dy: Int) = moveCursorWithMeta(dx, dy, 0)

    /**
     * The editor's text and selection as absolute offsets. Null when the editor does
     * not support extraction — WebViews and some custom fields — in which case select
     * mode falls back to shift+arrow key events.
     */
    private class TextSnapshot(val text: CharSequence, val start: Int, val selStart: Int, val selEnd: Int)

    private fun snapshot(): TextSnapshot? {
        val ic = currentInputConnection ?: return null
        val ex = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return null
        val text = ex.text ?: return null
        if (ex.selectionStart < 0 || ex.selectionEnd < 0) return null
        return TextSnapshot(text, ex.startOffset, ex.startOffset + ex.selectionStart, ex.startOffset + ex.selectionEnd)
    }

    /**
     * Grow or shrink the selection by one step in select mode.
     *
     * Done with `setSelection` on the extracted text rather than shift+arrow key events:
     * an IME's synthetic key events carry no real modifier state, and most editors
     * ignore the shift flag on them — the caret moved, but nothing was ever selected.
     */
    private fun extendSelection(dx: Int, dy: Int) {
        val snap = snapshot()
        val ic = currentInputConnection
        if (snap == null || ic == null) {
            // Best effort where the text cannot be read.
            moveCursorWithMeta(dx, dy, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
            return
        }
        if (selectionAnchor < 0) selectionAnchor = snap.selStart
        val anchor = selectionAnchor
        // The end that moves is whichever one is not the anchor.
        val moving = if (snap.selStart == anchor) snap.selEnd else snap.selStart
        val target = when {
            dx != 0 -> moving + dx
            else -> lineStep(snap, moving, dy)
        }.coerceIn(snap.start, snap.start + snap.text.length)
        ic.setSelection(anchor, target)
    }

    /** Select from the anchor to the start or end of the moving end's line. */
    private fun extendToLineEdge(start: Boolean) {
        val snap = snapshot()
        val ic = currentInputConnection
        if (snap == null || ic == null) {
            sendKeyWithMeta(
                if (start) KeyEvent.KEYCODE_MOVE_HOME else KeyEvent.KEYCODE_MOVE_END,
                KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON,
            )
            return
        }
        if (selectionAnchor < 0) selectionAnchor = snap.selStart
        val moving = if (snap.selStart == selectionAnchor) snap.selEnd else snap.selStart
        val rel = moving - snap.start
        val target = if (start) snap.text.lastIndexOf('\n', rel - 1) + 1
            else snap.text.indexOf('\n', rel).let { if (it < 0) snap.text.length else it }
        ic.setSelection(selectionAnchor, snap.start + target)
    }

    /** The offset one line up (dy < 0) or down from [from], keeping the column. */
    private fun lineStep(snap: TextSnapshot, from: Int, dy: Int): Int {
        val text = snap.text
        val rel = (from - snap.start).coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', rel - 1) + 1
        val column = rel - lineStart
        val target = if (dy < 0) {
            if (lineStart == 0) return snap.start
            val prevStart = text.lastIndexOf('\n', lineStart - 2) + 1
            minOf(prevStart + column, lineStart - 1)
        } else {
            val lineEnd = text.indexOf('\n', rel).let { if (it < 0) text.length else it }
            if (lineEnd == text.length) return snap.start + text.length
            val nextStart = lineEnd + 1
            val nextEnd = text.indexOf('\n', nextStart).let { if (it < 0) text.length else it }
            minOf(nextStart + column, nextEnd)
        }
        return snap.start + target
    }

    private fun moveCursorWithMeta(dx: Int, dy: Int, meta: Int) {
        val code = when {
            dx < 0 -> KeyEvent.KEYCODE_DPAD_LEFT
            dx > 0 -> KeyEvent.KEYCODE_DPAD_RIGHT
            dy < 0 -> KeyEvent.KEYCODE_DPAD_UP
            else -> KeyEvent.KEYCODE_DPAD_DOWN
        }
        sendKeyWithMeta(code, meta)
    }

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
            // Reached by backspacing the last key away. Nothing was finished, so there
            // is nothing to predict a successor for — the strip goes back to idle.
            ic.finishComposingText()
            nextWords = emptyList()
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
    /**
     * True when the caret is right after a single space that follows a word — the space
     * prediction adds automatically, as opposed to a deliberate double space or the start
     * of the text.
     */
    private fun precededByAutoSpace(): Boolean {
        val before = currentInputConnection?.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        return before.length == 2 && before[1] == ' ' && !before[0].isWhitespace()
    }

    private fun nextCharIsSeparator(): Boolean {
        val next = currentInputConnection?.getTextAfterCursor(1, 0)?.toString().orEmpty()
        val first = next.firstOrNull() ?: return false
        return first.isWhitespace() || first in SEPARATORS
    }

    private fun applyShift(text: String, state: ShiftState = shiftState): String = when (state) {
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
