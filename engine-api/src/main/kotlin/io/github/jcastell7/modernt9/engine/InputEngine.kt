package io.github.jcastell7.modernt9.engine

/**
 * A T9 prediction backend.
 *
 * **This is the seam.** Everything above it (the IME, the keypad, the candidate strip)
 * is engine-agnostic; everything below it is free to be a trie, an n-gram model, a neural
 * net, or a reverse-engineered native library behind JNI.
 *
 * ### Design notes
 *
 * The interface is modelled on the `Okinawa` contract extracted from TouchPal v5 and v7
 * (see `keyboard-engine/v7/reference/okinawa-v5-vs-v7.txt` — 120 of its 121 native methods
 * were unchanged across six years, which is why that shape is worth borrowing). It is
 * deliberately *not* a literal port: Okinawa's 121 `fire*Operation` methods are 2013 JNI
 * ergonomics. What is kept is the underlying state machine.
 *
 * ### Lifecycle
 *
 * ```
 * startSession(context)
 *   onDigit / onBackspace / ...   -> Composition
 *   selectCandidate(i)            -> committed text
 *   learn(text)
 * endSession()
 * ```
 *
 * ### Threading
 *
 * Implementations are called from a single thread (the IME's main thread) and need not be
 * thread-safe. Anything slow — loading a dictionary, warming a model — belongs in
 * [initialize], which is called off the main thread.
 */
interface InputEngine : AutoCloseable {

    val descriptor: EngineDescriptor

    /**
     * Prepare the engine. Called once, off the main thread, before any other method.
     * Throw to signal the engine is unusable; the IME will fall back to another.
     */
    fun initialize()

    /** Begin editing a field. Resets any pending composition. */
    fun startSession(context: EditorContext)

    /** Finish editing. The engine should flush anything it wants to persist. */
    fun endSession()

    // ---- languages ------------------------------------------------------------

    /**
     * Languages this engine currently has loaded and can switch between without
     * re-initialising, e.g. `["en", "es"]`. May be narrower than
     * [EngineDescriptor.supportedLanguages] if some dictionaries are absent.
     */
    val availableLanguages: List<String>

    /** The active language tag. */
    val activeLanguage: String

    /**
     * Switch language mid-session — the user tapping the language key.
     *
     * Must be cheap: everything needed should already be loaded by [initialize]. Any
     * pending composition is discarded.
     *
     * @return true if the switch happened; false if the language is unavailable.
     */
    fun switchLanguage(languageTag: String): Boolean

    // ---- input ----------------------------------------------------------------

    /** Append a digit ('2'..'9', and '0'/'1' if the engine assigns them meaning). */
    fun onDigit(digit: Char): Composition

    /** Delete the last pending digit. When nothing is pending, returns [Composition.Empty]. */
    fun onBackspace(): Composition

    /** Abandon the pending composition without committing. */
    fun reset(): Composition

    /**
     * The `@` / punctuation key pressed while composing.
     *
     * TouchPal's behaviour (`custom-word-appears.png`): after typing `Juan`, pressing `@`
     * does not insert a character — it recognises that a saved phrase continues from
     * there and offers `user@mail.com`.
     *
     * @return the extended composition when at least one phrase still matches, or null
     *   when nothing does, in which case the caller should treat the key as literal `@`.
     */
    fun onSymbolKey(): Composition?

    /**
     * True when [text] is already known — present in the dictionary, learned, or saved
     * as a phrase. The IME uses this to decide whether to offer the add-to-dictionary
     * bar (`new-word.png`).
     */
    fun isKnown(text: String): Boolean

    /** The letters of the most recently pressed key, for the left strip. */
    fun lastKeyLetters(): List<String>

    /**
     * Pin [letter] at the caret's position in the composition.
     *
     * This is what the left-hand strip is for: the key is ambiguous, and choosing a
     * letter says which one was meant. Candidates are then filtered to words that have
     * that letter there, instead of the letter being appended to the end of the word.
     *
     * @return the updated composition, or null if nothing is being composed.
     */
    fun lockLetter(letter: String): Composition?

    /**
     * Re-open an already-typed word for correction.
     *
     * The caller has put the caret inside [word]; the engine reconstructs the digit
     * sequence from it so the user can pick a different prediction instead of deleting
     * and retyping. [word] itself is pinned first in the candidate list.
     *
     * @param caretOffset where the caret sits inside [word], 0..word.length. Subsequent
     *   keys insert there and backspace deletes just before it.
     * @return the composition, or null if the word cannot be typed on the keypad.
     */
    fun resumeEditing(word: String, caretOffset: Int = word.length): Composition?

    /** The current composition, without changing state. */
    fun composition(): Composition

    // ---- committing -----------------------------------------------------------

    /**
     * Accept the candidate at [index] from the current [Composition.candidates].
     *
     * @return the text to insert into the editor, or null if [index] is out of range.
     *   The engine clears its pending composition.
     */
    fun selectCandidate(index: Int): String?

    /**
     * Accept the composition as it appears inline — what space and enter do.
     *
     * Kept separate from `selectCandidate(0)` because the inline text is the first
     * *same-length* candidate, which is not always the highest-ranked one.
     *
     * @return the committed text, or null when nothing is being composed.
     */
    fun commitInline(): String?

    /**
     * Tell the engine that [text] was committed to the editor, so it can update
     * next-word context and (if [EngineDescriptor.supportsLearning]) its frequencies.
     *
     * Called for text the engine produced *and* for text typed by other means, so an
     * engine's next-word context stays accurate.
     */
    fun learn(text: String)

    /**
     * Suggestions to show when nothing is being composed — typically next-word
     * predictions from the preceding context. Return empty if unsupported.
     */
    fun predictNextWord(): List<Candidate>

    // ---- user dictionary ------------------------------------------------------

    val userDictionary: UserDictionary

    override fun close() = Unit
}

/**
 * The user's personal words. Split out from [InputEngine] so a settings screen can manage
 * it without knowing which engine is active.
 */
interface UserDictionary {
    fun add(word: String, weight: Int = DEFAULT_WEIGHT)
    fun remove(word: String): Boolean
    fun contains(word: String): Boolean
    /** All user words, highest weight first. */
    fun entries(): List<UserWord>
    fun clear()

    /**
     * Add a *phrase* — a token that is not a plain word: an email address, a URL, a
     * handle, a product code. Anything containing digits or symbols.
     *
     * Stored verbatim and encoded with [Keypad.encodeExtended], so a short digit prefix
     * surfaces the whole thing as a completion: four taps of `5826` offers
     * `user@gmail.com`.
     *
     * Case is preserved (unlike [add], which lower-cases), because `MyBank.com` and a
     * capitalised handle matter.
     */
    fun addPhrase(phrase: String, weight: Int = PHRASE_WEIGHT)

    fun removePhrase(phrase: String): Boolean

    /** All user phrases, highest weight first. */
    fun phrases(): List<UserWord>

    companion object {
        const val DEFAULT_WEIGHT = 1
        /** Phrases are deliberate additions, so they outrank ordinary learned words. */
        const val PHRASE_WEIGHT = 10_000
    }
}

data class UserWord(val word: String, val weight: Int)

/**
 * Creates engines. Register one per backend; the IME picks by
 * [EngineDescriptor.id].
 *
 * Kept separate from [InputEngine] so the app can list available engines without
 * constructing (and paying to load) any of them.
 */
interface EngineFactory {
    val descriptor: EngineDescriptor

    /**
     * @param resources platform hooks — reading dictionary assets, a writable directory
     *   for learned data. Keeps engines free of Android imports.
     */
    fun create(resources: EngineResources): InputEngine
}

/**
 * The only thing an engine may assume about its host. Implemented by the app over
 * Android assets and files; implemented over the filesystem by desktop tests and by the
 * reverse-engineering harness.
 */
interface EngineResources {
    /** Open a bundled read-only asset, e.g. "dict/en.txt". Null when absent. */
    fun openAsset(path: String): java.io.InputStream?

    /** A directory the engine may read and write — learned words, caches, models. */
    fun dataDir(): java.io.File

    /** Current UI language tag, e.g. "en". */
    val languageTag: String
}
