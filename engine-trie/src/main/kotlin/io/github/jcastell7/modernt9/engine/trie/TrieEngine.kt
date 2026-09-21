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
 *  1. the word being corrected in place
 *  2. every word — dictionary, learned, or a saved phrase — scored by dictionary
 *     frequency (none for the last two) plus how often the user has chosen it (see
 *     [usageBonus]); so a word you actually use overtakes its rivals, and one you
 *     saved but never pick sits below the common words on the same keys
 *  3. on a single key, the key's letters — after any word suggestions
 *  4. the literal digits, always last, so numbers stay typeable
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

    /** Active dictionaries. One entry normally; several in bilingual mode. */
    private var languages: List<String> = listOf(DEFAULT_LANGUAGE)

    /** The active selection as a tag: "en", "es", or "en+es" for bilingual mode. */
    override val activeLanguage: String get() = languages.joinToString(BILINGUAL_SEPARATOR)

    /** For hints and multi-tap: the richer alphabet when Spanish is among the active set. */
    private val hintLanguage: String get() = if ("es" in languages) "es" else languages.first()
    /** Everything configured, whether or not its dictionary has been parsed yet. */
    override val availableLanguages: List<String>
        get() = CONFIGURED_LANGUAGES.filter { it in tries || resources.openAsset("dict/$it.txt") != null }

    private val activeTries: List<DigitTrie>
        get() = languages.mapNotNull { tries[it] }.ifEmpty { listOfNotNull(tries.values.firstOrNull()) }

    /** The first active trie — where a single-language operation should go. */
    private val activeTrie: DigitTrie get() = activeTries.firstOrNull() ?: DigitTrie()

    // ---- user dictionary ------------------------------------------------------

    private val userDict = object : UserDictionary {
        override fun add(word: String, weight: Int) {
            val w = normalise(word) ?: return
            val encoded = Keypad.encode(w) ?: return
            learned.noteWord(w, weight.coerceAtLeast(1), USE_CAP)
            // Present in every trie so it is offered whichever language is active. Its
            // rank comes from usageBonus, not from the trie weight.
            tries.values.forEach { it.reinforce(encoded, w, LEARN_DELTA, WEIGHT_CAP) }
            learned.flush()
        }

        override fun remove(word: String): Boolean = forgetWord(word)

        override fun contains(word: String): Boolean =
            normalise(word)?.let { learned.unigrams.containsKey(it) } == true

        override fun entries(): List<UserWord> =
            learned.unigrams.entries
                .sortedByDescending { it.value }
                .map { UserWord(it.key, it.value, learned.added[it.key] ?: 0L) }

        /** Learned words absent from every dictionary — including ones not parsed yet. */
        override fun novelWords(): List<UserWord> =
            learned.unigrams.entries
                .filter { (word, _) ->
                    val encoded = Keypad.encode(word) ?: return@filter false
                    tries.values.all { it.isLearned(encoded, word) }
                }
                .sortedByDescending { it.value }
                .map { UserWord(it.key, it.value, learned.added[it.key] ?: 0L) }

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
            // A saved word was also learned as a plain word the moment it was typed, so
            // removing the phrase alone left it in the predictions. Both go together.
            return forgetWord(phrase) || removed
        }

        override fun importWord(entry: UserWord): Boolean {
            val w = normalise(entry.word) ?: return false
            val encoded = Keypad.encode(w) ?: return false
            val changed = learned.merge(w, entry.uses.coerceAtLeast(1), entry.addedAt)
            if (changed) {
                tries.values.forEach { it.reinforce(encoded, w, LEARN_DELTA, WEIGHT_CAP) }
                learned.flush()
            }
            return changed
        }

        override fun importPhrase(entry: UserWord): Boolean {
            val p = entry.word.trim()
            if (p.isEmpty()) return false
            val changed = phraseStore.merge(p, UserDictionary.PHRASE_WEIGHT, entry.uses, entry.addedAt)
            if (changed) {
                phraseTrie.reinforce(Keypad.encodeExtended(p), p, 0, WEIGHT_CAP)
                rebuildPhraseTrie()
                phraseStore.flush()
            }
            return changed
        }

        override fun phrases(): List<UserWord> =
            phraseStore.phrases.entries
                .sortedByDescending { it.value }
                .map { UserWord(it.key, it.value, phraseStore.added[it.key] ?: 0L, phraseStore.uses[it.key] ?: 0) }
    }

    override val userDictionary: UserDictionary get() = userDict

    /** Drop a learned word everywhere: the store, its bigrams, and every trie. */
    private fun forgetWord(word: String): Boolean {
        val w = normalise(word) ?: return false
        val removed = learned.forget(w)
        Keypad.encode(w)?.let { encoded -> tries.values.forEach { it.remove(encoded, w) } }
        if (removed) learned.flush()
        if (lastCommitted == w) lastCommitted = null
        return removed
    }

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
        languages = listOf(if (preferred in tries) preferred else tries.keys.first())

        learned.load()
        learned.unigrams.keys.forEach { word ->
            Keypad.encode(word)?.let { encoded ->
                tries.values.forEach { t -> t.reinforce(encoded, word, LEARN_DELTA, WEIGHT_CAP) }
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

    /**
     * Accepts a single tag ("es"), or several joined with "+" ("en+es") for bilingual
     * mode, where candidates from every listed dictionary are offered together.
     */
    override fun switchLanguage(languageTag: String): Boolean {
        val tags = languageTag.split(BILINGUAL_SEPARATOR).map { it.trim().take(2) }
            .filter { it.isNotEmpty() }.distinct()
        if (tags.isEmpty()) return false
        for (tag in tags) {
            if (!tries.containsKey(tag)) {
                // First use of this language: parse it now, then fold in learned words.
                val loaded = loadDictionary(tag) ?: return false
                learned.unigrams.keys.forEach { word ->
                    Keypad.encode(word)?.let { loaded.reinforce(it, word, LEARN_DELTA, WEIGHT_CAP) }
                }
                tries[tag] = loaded
            }
        }
        languages = tags
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
        // four taps of "5826" surfaces user@gmail.com.
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
        // phrase such as "user@gmail.com" (5826-1-4624...) continues past its letters.
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
        return activeTries.any { trie -> trie.exact(encoded, 32).any { it.word == word } }
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
        val base = Keypad.letterHints(last, hintLanguage)?.map { it.toString() } ?: return emptyList()

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
        val out = ArrayList<Candidate>(MAX_CANDIDATES + 2)
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

        // 1. One key pressed: word suggestions first, then the key's own letters.
        //    The letters stay the exact-length candidates, so a bare letter is still
        //    what space commits — the words are one tap away on the strip.
        if (seq.length == 1) {
            addCompletions(out, seen, seq)
            val letters = ArrayList<Candidate>(4)
            Keypad.letterHints(seq[0], hintLanguage)?.forEachIndexed { index, letter ->
                val text = letter.toString()
                if (seen.add(text)) {
                    // Letters that are words — "I" in English, "y" in Spanish — climb the
                    // list as they are used, so a frequent one becomes the default rather
                    // than staying stuck in keypad order.
                    val used = usageBonus(text)
                    // A letter chosen from the left strip is pinned at position 0. This
                    // branch skips matchesLocks(), so the pin must be honoured here — it
                    // comes first, above any learned weight.
                    val pinned = locked[0]?.let { Keypad.foldToAscii(text).first() == it || letter == it } == true
                    letters += Candidate(
                        text = text,
                        score = LETTER_RANK + (if (pinned) PIN_BONUS else 0) + used - index,
                        source = if (used > 0) CandidateSource.USER else CandidateSource.LETTER,
                        isExactLength = true,
                    )
                }
            }
            out.sortByDescending { it.score }
            letters.sortByDescending { it.score }
            out += letters
            out += Candidate(seq, Int.MIN_VALUE, CandidateSource.LITERAL)
            return out
        }

        // 2. user phrases — exact, then as completions of the typed prefix. Scored on
        //    the same scale as every other word: a phrase has no dictionary frequency,
        //    so it starts where a word used once starts and climbs only as it is chosen.
        //    Saving something does not make it beat words you actually type.
        for (e in phraseTrie.exact(seq, MAX_PHRASES)) {
            if (seen.add(e.word)) {
                out += Candidate(e.word, WORD_TIER + phraseScore(e.word), CandidateSource.PHRASE, true)
            }
        }
        for (e in phraseTrie.completions(seq, MAX_PHRASES)) {
            if (seen.add(e.word)) {
                out += Candidate(e.word, WORD_TIER + phraseScore(e.word), CandidateSource.PHRASE, false)
            }
        }

        // 3. dictionary and learned words: words of the typed length and longer
        //    completions together, ranked by frequency plus the user's own use of
        //    each. The strip is ordered by likelihood — "work" before "wop" — while the
        //    inline text is still the best word of the typed length (candidatesInline).
        // In bilingual mode every active dictionary contributes. Words shared by both
        // languages are collapsed, keeping whichever weight is higher.
        val exact = LinkedHashMap<String, Int>()
        for (trie in activeTries) {
            for (e in trie.exact(seq, MAX_CANDIDATES)) {
                if (!matchesLocks(e.word)) continue
                exact[e.word] = maxOf(exact[e.word] ?: 0, e.weight)
            }
        }
        for ((word, weight) in exact) {
            if (!seen.add(word)) continue
            val used = usageBonus(word)
            out += Candidate(
                text = word,
                score = WORD_TIER + weight + used,
                source = if (used > 0) CandidateSource.USER else CandidateSource.DICTIONARY,
                isExactLength = true,
            )
        }
        addCompletions(out, seen, seq)

        out.sortByDescending { it.score }

        // 4. What space will commit — the best word of the typed length — must be on
        //    the strip, however far down its weight puts it: it is kept through the cut
        //    below, and where nothing of that length is known the plain letters of the
        //    keys pressed take its place. Either way it comes after the real words.
        val inline = out.firstOrNull { it.isExactLength }
        while (out.size > MAX_CANDIDATES) out.removeAt(out.size - 1)
        when {
            inline == null -> {
                val plain = defaultLetters(seq)
                if (seen.add(plain)) out += Candidate(plain, LETTER_RANK, CandidateSource.LETTER, true)
            }
            inline !in out -> {
                out.removeAt(out.size - 1)
                out += inline
            }
        }
        // 5. the literal digits, always available
        out += Candidate(seq, Int.MIN_VALUE, CandidateSource.LITERAL)
        return out
    }

    /**
     * Add the heaviest completions of [seq] from every active dictionary. Halved, since
     * they guess at letters not yet typed — but the user's own use of a word counts in
     * full, so a habitual word is offered as soon as its first keys are down.
     */
    private fun addCompletions(out: MutableList<Candidate>, seen: MutableSet<String>, seq: String) {
        val completions = LinkedHashMap<String, Int>()
        for (trie in activeTries) {
            for (e in trie.completions(seq, MAX_COMPLETIONS)) {
                if (!matchesLocks(e.word)) continue
                completions[e.word] = maxOf(completions[e.word] ?: 0, e.weight)
            }
        }
        for ((word, weight) in completions) {
            if (!seen.add(word)) continue
            out += Candidate(
                text = word,
                score = WORD_TIER + weight / 2 + usageBonus(word),
                source = CandidateSource.COMPLETION,
                isExactLength = false,
            )
        }
    }

    /**
     * What a word's own history is worth, on the dictionary's weight scale.
     *
     * Quadratic at first: a word typed once or twice moves a little, one used a dozen
     * times overtakes even a very common rival on the same keys, and by
     * [SATURATION_USES] it has matched the heaviest dictionary weight. Past that it
     * keeps climbing, linearly at [LATE_STEP] per use, so two habitual words on the same
     * keys still order by which is used more — 75 uses of "un" must beat 37 of "to",
     * however common "to" is. Bounded by [USAGE_MAX], which keeps the tier above out of
     * reach. Dictionary weights run from 1 to [WEIGHT_CAP], so a single use (= [USE_STEP])
     * is already worth more than most of the long tail.
     */
    private fun usageBonus(word: String): Int = usageBonus(learned.unigrams[word] ?: 0)

    private fun usageBonus(uses: Int): Int {
        val u = uses.toLong()
        val bonus = if (u <= SATURATION_USES) u * u * USE_STEP
            else SATURATION_USES * SATURATION_USES * USE_STEP + (u - SATURATION_USES) * LATE_STEP
        return bonus.coerceAtMost(USAGE_MAX.toLong()).toInt()
    }

    /** A saved phrase's worth: adding it counts as one use, every pick adds another. */
    private fun phraseScore(phrase: String): Int = usageBonus((phraseStore.uses[phrase] ?: 0) + 1)

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
            learned.noteWord(word, 1, USE_CAP)
            // Present in every language: a name or a borrowing is the user's word,
            // whichever dictionary happens to be active. The trie weight only decides
            // retrieval order inside a node; the rank comes from usageBonus.
            tries.values.forEach { it.reinforce(encoded, word, LEARN_DELTA, WEIGHT_CAP) }
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
        /** Joins tags in a bilingual selection: "en+es". */
        const val BILINGUAL_SEPARATOR = "+"

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
        /** Completions fetched per dictionary; the strip then keeps the best overall. */
        private const val MAX_COMPLETIONS = 8
        private const val MAX_PHRASES = 4
        private const val MAX_NEXT_WORDS = 3
        /** Trie weight of a learned word: enough to be retrieved, not enough to rank. */
        private const val LEARN_DELTA = 8
        private const val PHRASE_USE_DELTA = 50
        private const val WEIGHT_CAP = 1_000_000
        /** Learned use counts stop growing here. */
        private const val USE_CAP = 100_000
        /** One use, squared, on the dictionary weight scale — see [usageBonus]. */
        private const val USE_STEP = 2_000L
        /** Uses at which the quadratic part has matched the heaviest dictionary word. */
        private const val SATURATION_USES = 22L
        /** Each use beyond that is worth this much — see [usageBonus]. */
        private const val LATE_STEP = 20_000L
        private const val MAX_PREDICTED_STRIP = 6

        /**
         * Candidate ranking is tiered, and a word's score only ever breaks ties *within*
         * a tier. The gap is wider than [WEIGHT_CAP], so no amount of frequency or use
         * can push a candidate into the tier above: the word being edited beats
         * everything.
         *
         * Dictionary words, learned words, saved phrases, words of the typed length and
         * completions all share one tier, scored as frequency (zero for anything the
         * dictionary lacks) plus [usageBonus] — the strip is ordered by how likely each
         * entry is, whatever its origin. What keeps "test" from
         * turning into "verte" under the user is that the inline text is always the
         * best *exact-length* word, and space commits exactly that (see [commitInline]).
         */
        // Frequency (≤ WEIGHT_CAP) plus usage (≤ USAGE_MAX) is never clipped: two words
        // both heavily used must still order by which is used more. Their sum fits in
        // one tier by construction of USAGE_MAX; 5 × TIER still fits an Int.
        private const val TIER = 250_000_000
        /** The usage bonus never exceeds this, so frequency + usage stays inside one tier. */
        private const val USAGE_MAX = TIER - WEIGHT_CAP - 1
        private const val EDITING_RANK = 5 * TIER
        /** Dictionary words, learned words and saved phrases all rank here, by frequency plus use. */
        private const val WORD_TIER = 2 * TIER
        /** Bare letters — a single key's, or the plain reading of several — below every word. */
        private const val LETTER_RANK = 0
        private const val PIN_BONUS = USAGE_MAX + 10
    }
}

/** Registers [TrieEngine]. See `Engines.kt` in the app module. */
class TrieEngineFactory : EngineFactory {
    override val descriptor = TrieEngine.DESCRIPTOR
    override fun create(resources: EngineResources): InputEngine = TrieEngine(resources)
}
