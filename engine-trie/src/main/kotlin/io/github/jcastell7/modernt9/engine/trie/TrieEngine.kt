package io.github.jcastell7.modernt9.engine.trie

import io.github.jcastell7.modernt9.engine.Candidate
import io.github.jcastell7.modernt9.engine.CandidateSource
import io.github.jcastell7.modernt9.engine.Composition
import io.github.jcastell7.modernt9.engine.EditorContext
import io.github.jcastell7.modernt9.engine.EngineDescriptor
import io.github.jcastell7.modernt9.engine.EngineFactory
import io.github.jcastell7.modernt9.engine.EngineResources
import io.github.jcastell7.modernt9.engine.FieldType
import io.github.jcastell7.modernt9.engine.InputEngine
import io.github.jcastell7.modernt9.engine.Keypad
import io.github.jcastell7.modernt9.engine.UserDictionary
import io.github.jcastell7.modernt9.engine.UserWord

/**
 * The default backend: per-language digit tries over frequency-ranked word lists, with
 * learned words, learned bigrams and user phrases layered on top.
 *
 * All configured languages are loaded up front so [switchLanguage] is instant — the user
 * tapping the language key must not wait for I/O.
 *
 * Ranking, highest first:
 *  1. user phrases     (email addresses, URLs — deliberate additions)
 *  2. learned words    (the user's own typing)
 *  3. exact dictionary matches of the typed length
 *  4. longer completions of the typed prefix
 *  5. the literal digits, always last, so numbers stay typeable
 */
class TrieEngine internal constructor(
    private val resources: EngineResources,
) : InputEngine {

    override val descriptor = DESCRIPTOR

    /** One trie per language. Learned words are folded into the active language's trie. */
    private val tries = LinkedHashMap<String, DigitTrie>()

    /** Phrases are language-independent — an email address is not English or Spanish. */
    private val phraseTrie = DigitTrie()

    private val learned = LearnedStore(resources.dataDir())
    private val phraseStore = PhraseStore(resources.dataDir())

    private var context = EditorContext()
    private val digits = StringBuilder()
    private var lastCommitted: String? = null

    /** The word being corrected in place, pinned first in the candidate list. */
    private var editing: String? = null

    /** Its digit sequence, so the pin drops as soon as the user edits the digits. */
    private var editingDigits: String? = null

    /** Caret within [digits]. Keys insert here; backspace deletes just before it. */
    private var caret = 0

    /** Positions the user has disambiguated from the left strip: index -> letter. */
    private val locked = HashMap<Int, Char>()

    private var language: String = DEFAULT_LANGUAGE
    override val activeLanguage: String get() = language
    /** Everything configured, whether or not its dictionary has been parsed yet. */
    override val availableLanguages: List<String>
        get() = CONFIGURED_LANGUAGES.filter { it in tries || resources.openAsset("dict/$it.txt") != null }

    private val activeTrie: DigitTrie get() = tries[language] ?: tries.values.firstOrNull() ?: DigitTrie()

    // ---- user dictionary ------------------------------------------------------

    private val userDict = object : UserDictionary {
        override fun add(word: String, weight: Int) {
            val w = normalise(word) ?: return
            val encoded = Keypad.encode(w) ?: return
            val value = learned.noteWord(w, weight.coerceAtLeast(1), WEIGHT_CAP)
            tries.values.forEach { it.reinforce(encoded, w, 0, WEIGHT_CAP) }
            activeTrie.reinforce(encoded, w, value, WEIGHT_CAP)
            learned.flush()
        }

        override fun remove(word: String): Boolean {
            val w = normalise(word) ?: return false
            val removed = learned.forget(w)
            if (removed) learned.flush()
            return removed
        }

        override fun contains(word: String): Boolean =
            normalise(word)?.let { learned.unigrams.containsKey(it) } == true

        override fun entries(): List<UserWord> =
            learned.unigrams.entries
                .sortedByDescending { it.value }
                .map { UserWord(it.key, it.value) }

        override fun clear() {
            learned.clear()
            phraseStore.clear()
        }

        override fun addPhrase(phrase: String, weight: Int) {
            val p = phrase.trim()
            if (p.isEmpty()) return
            phraseStore.add(p, weight)
            phraseTrie.reinforce(Keypad.encodeExtended(p), p, weight, WEIGHT_CAP)
            phraseStore.flush()
        }

        override fun removePhrase(phrase: String): Boolean {
            val removed = phraseStore.remove(phrase)
            if (removed) {
                // Cheapest correct way to drop one entry from the trie is to rebuild it.
                rebuildPhraseTrie()
                phraseStore.flush()
            }
            return removed
        }

        override fun phrases(): List<UserWord> =
            phraseStore.phrases.entries
                .sortedByDescending { it.value }
                .map { UserWord(it.key, it.value) }
    }

    override val userDictionary: UserDictionary get() = userDict

    // ---- lifecycle ------------------------------------------------------------

    override fun initialize() {
        // Every configured language is parsed up front so switching is instant. The cost
        // is paid once: Engines keeps the built engine for the life of the process, so a
        // keyboard reopened later reuses it rather than reloading.
        for (tag in CONFIGURED_LANGUAGES) {
            loadDictionary(tag)?.let { tries[tag] = it }
        }
        if (tries.isEmpty()) tries[DEFAULT_LANGUAGE] = DigitTrie()
        val preferred = resources.languageTag.take(2)
        language = if (preferred in tries) preferred else tries.keys.first()

        learned.load()
        learned.unigrams.forEach { (word, weight) ->
            Keypad.encode(word)?.let { encoded ->
                tries.values.forEach { t -> t.reinforce(encoded, word, weight, WEIGHT_CAP) }
            }
        }

        phraseStore.load()
        rebuildPhraseTrie()
    }

    private fun rebuildPhraseTrie() {
        phraseTrie.clear()
        phraseStore.phrases.forEach { (phrase, weight) ->
            phraseTrie.reinforce(Keypad.encodeExtended(phrase), phrase, weight, WEIGHT_CAP)
        }
    }

    private fun loadDictionary(tag: String): DigitTrie? {
        val stream = resources.openAsset("dict/$tag.txt") ?: return null
        val trie = DigitTrie()
        stream.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isEmpty() || line.startsWith('#')) continue
                val tab = line.indexOf('\t')
                val word: String
                val weight: Int
                if (tab < 0) {
                    word = line
                    weight = 1
                } else {
                    word = line.substring(0, tab)
                    weight = line.substring(tab + 1).trim().toIntOrNull() ?: 1
                }
                val w = normalise(word) ?: continue
                Keypad.encode(w)?.let { trie.insert(it, w, weight) }
            }
        }
        return trie
    }

    override fun switchLanguage(languageTag: String): Boolean {
        val tag = languageTag.take(2)
        if (!tries.containsKey(tag)) {
            // First use of this language: parse it now, then fold in learned words.
            val loaded = loadDictionary(tag) ?: return false
            learned.unigrams.forEach { (word, weight) ->
                Keypad.encode(word)?.let { loaded.reinforce(it, word, weight, WEIGHT_CAP) }
            }
            tries[tag] = loaded
        }
        language = tag
        digits.setLength(0)
        return true
    }

    override fun startSession(context: EditorContext) {
        this.context = context
        digits.setLength(0)
        caret = 0
        locked.clear()
        lastCommitted = context.precedingText
            .trimEnd()
            .substringAfterLast(' ', "")
            .takeIf { it.isNotEmpty() }
    }

    override fun endSession() {
        digits.setLength(0)
        learned.flush()
        phraseStore.flush()
    }

    override fun close() {
        learned.flush()
        phraseStore.flush()
    }

    // ---- input ----------------------------------------------------------------

    override fun onDigit(digit: Char): Composition {
        // Typing a new key ends the correction of an existing word.
        if (digits.isEmpty()) { editing = null; editingDigits = null; caret = 0 }
        // Only 2-9 compose. '1' is the punctuation key and '0' is space, so the UI never
        // sends them here. A phrase containing symbols is reached by its letter prefix:
        // four taps of "5826" surfaces juan@gmail.com.
        if (Keypad.isLetterDigit(digit)) {
            val at = caret.coerceIn(0, digits.length)
            shiftLocks(from = at, by = 1)
            digits.insert(at, digit)
            caret++
        }
        return composition()
    }

    override fun onBackspace(): Composition {
        // Delete just before the caret, not blindly off the end.
        if (digits.isNotEmpty() && caret > 0) {
            locked.remove(caret - 1)
            digits.deleteCharAt(caret - 1)
            shiftLocks(from = caret, by = -1)
            caret--
        }
        return composition()
    }

    override fun reset(): Composition {
        digits.setLength(0)
        caret = 0
        locked.clear()
        editing = null
        editingDigits = null
        return Composition.Empty
    }

    override fun resumeEditing(word: String, caretOffset: Int): Composition? {
        if (word.isBlank()) return null
        val encoded = Keypad.encode(word)?.takeIf { it.isNotEmpty() } ?: return null
        digits.setLength(0)
        digits.append(encoded)
        caret = caretOffset.coerceIn(0, encoded.length)
        locked.clear()
        // Kept exactly as it appears in the document, so choosing it is a no-op rather
        // than silently re-casing the user's text.
        editing = word
        editingDigits = encoded
        return composition()
    }

    override fun onSymbolKey(): Composition? {
        if (digits.isEmpty()) return null
        // The symbol key contributes '1' to the extended encoding, which is how a saved
        // phrase such as "juan@gmail.com" (5826-1-4624...) continues past its letters.
        val extended = digits.toString() + Keypad.PUNCTUATION_KEY
        val matches = phraseTrie.exact(extended, 1).isNotEmpty() ||
            phraseTrie.completions(extended, 1).isNotEmpty()
        if (!matches) return null
        digits.append(Keypad.PUNCTUATION_KEY)
        return composition()
    }

    override fun isKnown(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        if (phraseStore.phrases.containsKey(trimmed)) return true
        val word = normalise(trimmed) ?: return false
        if (learned.unigrams.containsKey(word)) return true
        val encoded = Keypad.encode(word) ?: return false
        return activeTrie.exact(encoded, 32).any { it.word == word }
    }

    private fun candidatesInline(candidates: List<Candidate>, seq: String): String =
        candidates.firstOrNull { it.isExactLength && it.source != CandidateSource.LITERAL }
            ?.text
            ?: defaultLetters(seq)

    /** Keep pinned positions aligned when the sequence grows or shrinks. */
    private fun shiftLocks(from: Int, by: Int) {
        if (locked.isEmpty()) return
        val moved = locked.filterKeys { it >= from }
        moved.keys.forEach(locked::remove)
        moved.forEach { (index, ch) -> locked[index + by] = ch }
    }

    /**
     * One letter per key pressed — the plain reading of the digit sequence, with any
     * letters the user pinned from the left strip substituted in.
     */
    private fun defaultLetters(seq: String): String = buildString {
        seq.forEachIndexed { index, d ->
            append(locked[index] ?: Keypad.letters[d]?.firstOrNull() ?: d)
        }
    }

    override fun lockLetter(letter: String): Composition? {
        val ch = letter.firstOrNull()?.lowercaseChar() ?: return null
        val position = caret - 1
        if (position < 0 || position >= digits.length) return null
        locked[position] = ch
        return composition()
    }

    /** True when [text] agrees with every letter the user has pinned. */
    private fun matchesLocks(text: String): Boolean {
        if (locked.isEmpty()) return true
        return locked.all { (index, ch) ->
            val actual = text.getOrNull(index)?.lowercaseChar() ?: return@all false
            actual == ch || Keypad.foldToAscii(actual.toString()).firstOrNull() == ch
        }
    }

    override fun lastKeyLetters(): List<String> {
        val last = digits.lastOrNull() ?: return emptyList()
        val base = Keypad.letterHints(last, language)?.map { it.toString() } ?: return emptyList()

        // Add the characters that actually occur in this position across the current
        // candidates. That is what surfaces "ñ", "í" and other accented forms — they
        // are only worth offering when a real word uses them here.
        val position = digits.length - 1
        val predicted = buildCandidates(digits.toString())
            .asSequence()
            .filter { it.source != CandidateSource.LITERAL }
            .mapNotNull { it.text.getOrNull(position)?.lowercaseChar()?.toString() }
            .filter { it !in base }
            .distinct()
            .take(MAX_PREDICTED_STRIP)
            .toList()

        return base + predicted
    }

    override fun composition(): Composition {
        if (digits.isEmpty()) return Composition.Empty
        val seq = digits.toString()
        val candidates = buildCandidates(seq)
        val inline = candidatesInline(candidates, seq)
        return Composition(
            digits = seq,
            // Only a same-length word may be shown inline. A completion is a guess about
            // letters not yet typed, and showing it makes one keypress look like a whole
            // word appearing.
            composing = inline,
            candidates = candidates,
            // The caret tracks the digit sequence; inline text of the same length maps
            // one-to-one, and anything else is clamped.
            cursor = caret.coerceIn(0, inline.length),
        )
    }

    private fun buildCandidates(seq: String): List<Candidate> {
        val out = ArrayList<Candidate>(MAX_CANDIDATES + 1)
        val seen = HashSet<String>()

        // 0. The word being corrected in place stays first, so re-opening a word never
        //    hides the spelling already in the document.
        // Only while the digits still spell the original word. Once a key is added or
        // removed the pin must go, or `composing` would keep showing the old spelling
        // and the editor would never change.
        if (seq == editingDigits) {
            editing?.let { original ->
                if (seen.add(original)) {
                    out += Candidate(original, EDITING_RANK, CandidateSource.EDITING, true)
                    // Suppress the dictionary's own copy so the word is not listed twice
                    // with different casing.
                    seen.add(original.lowercase())
                }
            }
        }

        // 1. One key pressed: offer that key's letters and nothing else. Committing a
        //    whole word off a single tap is a guess too far — it takes two keys before a
        //    word is worth suggesting.
        if (seq.length == 1) {
            Keypad.letterHints(seq[0], language)?.forEachIndexed { index, letter ->
                val text = letter.toString()
                if (seen.add(text)) {
                    // Letters that are words — "I" in English, "y" in Spanish — climb the
                    // list as they are used, so a frequent one becomes the default rather
                    // than staying stuck in keypad order. Learned weight is bounded by
                    // WEIGHT_CAP, so this stays inside the LETTER tier.
                    val used = learned.unigrams[text] ?: 0
                    val isWord = used > 0
                    // A letter chosen from the left strip is pinned at position 0. This
                    // branch returns early and skipped matchesLocks(), so the pin was
                    // silently ignored — tapping the strip on a single press did nothing.
                    // The pinned letter must come first, above any learned weight.
                    val pinned = locked[0]?.let { Keypad.foldToAscii(letter.toString()).first() == it || letter == it } == true
                    out += Candidate(
                        text = text,
                        score = LETTER_RANK + (if (pinned) PIN_BONUS else 0) + used - index,
                        source = if (isWord) CandidateSource.USER else CandidateSource.LETTER,
                        isExactLength = true,
                    )
                }
            }
            out.sortByDescending { it.score }
            out += Candidate(seq, Int.MIN_VALUE, CandidateSource.LITERAL)
            return out
        }

        // 2. user phrases — exact, then as completions of the typed prefix
        for (e in phraseTrie.exact(seq, MAX_PHRASES)) {
            if (seen.add(e.word)) {
                out += Candidate(e.word, PHRASE_TIER + e.weight, CandidateSource.PHRASE, true)
            }
        }
        for (e in phraseTrie.completions(seq, MAX_PHRASES)) {
            if (seen.add(e.word)) {
                out += Candidate(e.word, PHRASE_TIER + e.weight - 1, CandidateSource.PHRASE, false)
            }
        }

        // 3/4. dictionary and learned words for the active language
        val trie = activeTrie
        for (e in trie.exact(seq, MAX_CANDIDATES)) {
            if (!matchesLocks(e.word)) continue
            if (!seen.add(e.word)) continue
            val isLearned = learned.unigrams.containsKey(e.word)
            out += Candidate(
                text = e.word,
                score = EXACT_TIER + e.weight + if (isLearned) USER_BONUS else 0,
                source = if (isLearned) CandidateSource.USER else CandidateSource.DICTIONARY,
                isExactLength = true,
            )
        }

        // 5. longer completions
        val room = MAX_CANDIDATES - out.size
        if (room > 0) {
            for (e in trie.completions(seq, room)) {
                if (!matchesLocks(e.word)) continue
                if (!seen.add(e.word)) continue
                out += Candidate(e.word, COMPLETION_TIER + e.weight / 2, CandidateSource.COMPLETION, false)
            }
        }

        out.sortByDescending { it.score }
        // 6. the literal digits, always available
        out += Candidate(seq, Int.MIN_VALUE, CandidateSource.LITERAL)
        return out
    }

    // ---- committing -----------------------------------------------------------

    /**
     * Commit exactly what is shown inline.
     *
     * Space used to commit `candidates[0]` while the editor displayed the first
     * *same-length* candidate. When those differed, pressing space silently replaced the
     * word the user could see. Both now come from one place.
     */
    override fun commitInline(): String? {
        if (digits.isEmpty()) return null
        val text = composition().composing
        val index = composition().candidates.indexOfFirst { it.text == text }
        return if (index >= 0) selectCandidate(index) else {
            digits.setLength(0)
            caret = 0
            locked.clear()
            editing = null
            editingDigits = null
            learn(text)
            text
        }
    }

    override fun selectCandidate(index: Int): String? {
        val candidates = composition().candidates
        val chosen = candidates.getOrNull(index) ?: return null
        digits.setLength(0)
        caret = 0
        locked.clear()
        editing = null
        editingDigits = null
        if (chosen.source == CandidateSource.PHRASE) {
            // Phrases are stored verbatim; reinforce rather than re-learn as a word.
            phraseStore.bump(chosen.text, PHRASE_USE_DELTA, WEIGHT_CAP)
            phraseTrie.reinforce(Keypad.encodeExtended(chosen.text), chosen.text, PHRASE_USE_DELTA, WEIGHT_CAP)
            lastCommitted = null
        } else {
            learn(chosen.text)
        }
        return chosen.text
    }

    override fun learn(text: String) {
        val word = normalise(text) ?: run { lastCommitted = null; return }
        if (!shouldLearn()) { lastCommitted = word; return }

        Keypad.encode(word)?.let { encoded ->
            val weight = learned.noteWord(word, LEARN_DELTA, WEIGHT_CAP)
            // Applied to every language: a name or a borrowing is the user's word,
            // whichever dictionary happens to be active.
            tries.values.forEach { it.reinforce(encoded, word, 0, WEIGHT_CAP) }
            tries.values.forEach { it.reinforce(encoded, word, weight, WEIGHT_CAP) }
        }
        lastCommitted?.let { learned.noteBigram(it, word, WEIGHT_CAP) }
        lastCommitted = word
    }

    private fun shouldLearn(): Boolean =
        !context.incognito &&
            context.fieldType != FieldType.PASSWORD &&
            context.fieldType != FieldType.NUMBER

    override fun predictNextWord(): List<Candidate> {
        val previous = lastCommitted ?: return emptyList()
        val nexts = learned.bigrams[previous] ?: return emptyList()
        return nexts.entries
            .sortedByDescending { it.value }
            .take(MAX_NEXT_WORDS)
            .map { Candidate(it.key, it.value, CandidateSource.NEXT_WORD) }
    }

    /**
     * Validate a word while **preserving its accents**.
     *
     * Folding is only used to check the word is keypad-typeable — the accented spelling
     * is what gets stored and offered, so a Spanish user sees "mañana", not "manana".
     * [Keypad.encode] folds internally, so both spellings still reach the same digits.
     */
    private fun normalise(raw: String): String? {
        val word = raw.trim().lowercase()
        if (word.isEmpty()) return null
        val folded = Keypad.foldToAscii(word)
        return if (folded.all { it in 'a'..'z' || it == '\'' }) word else null
    }

    companion object {
        const val ID = "trie"
        const val DEFAULT_LANGUAGE = "en"

        /** Languages we try to load. A missing dictionary is simply skipped. */
        val CONFIGURED_LANGUAGES = listOf("en", "es")

        val DESCRIPTOR = EngineDescriptor(
            id = ID,
            displayName = "Built-in (trie)",
            version = "1.1",
            supportedLanguages = CONFIGURED_LANGUAGES,
            supportsLearning = true,
            supportsNextWordPrediction = true,
        )

        private const val MAX_CANDIDATES = 12
        private const val MAX_PHRASES = 4
        private const val MAX_NEXT_WORDS = 3
        private const val LEARN_DELTA = 8
        private const val PHRASE_USE_DELTA = 50
        private const val WEIGHT_CAP = 1_000_000
        private const val USER_BONUS = 500
        /** Puts phrases above any dictionary word. */
        private const val MAX_PREDICTED_STRIP = 6

        /**
         * Candidate ranking is tiered, and a word's weight only ever breaks ties *within*
         * a tier. The gap is wider than [WEIGHT_CAP] plus [USER_BONUS], so no amount of
         * frequency can push a candidate into the tier above.
         *
         * This is what stops a common completion displacing an exact match — the bug
         * where typing "test" offered "verte" first, because "verte" is a five-letter
         * word on the same first four keys.
         */
        private const val TIER = 2_000_000
        private const val EDITING_RANK = 5 * TIER
        private const val PHRASE_TIER = 4 * TIER
        private const val LETTER_RANK = 3 * TIER
        private const val EXACT_TIER = 2 * TIER
        private const val COMPLETION_TIER = 1 * TIER
        /** Puts a strip-pinned letter above any learned weight, still inside its tier. */
        private const val PIN_BONUS = WEIGHT_CAP + 1
    }
}

/** Registers [TrieEngine]. See `Engines.kt` in the app module. */
class TrieEngineFactory : EngineFactory {
    override val descriptor = TrieEngine.DESCRIPTOR
    override fun create(resources: EngineResources): InputEngine = TrieEngine(resources)
}
