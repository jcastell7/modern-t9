package io.github.jcastell7.modernt9.engine

/**
 * The ITU-T E.161 keypad, extended for Spanish and for tokens that are not plain words.
 *
 * Lives in the API rather than in an engine because it describes the *keyboard*: the UI
 * needs it to label keys, and every engine needs it to interpret a digit sequence.
 */
object Keypad {

    /** The punctuation key. Carries no letters; multi-tap cycles [Punctuation]. */
    const val PUNCTUATION_KEY = '1'

    /** The space key. */
    const val SPACE_KEY = '0'

    /** Digit -> the letters printed on that key, in order. */
    val letters: Map<Char, String> = mapOf(
        '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
        '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz",
    )

    /**
     * Extra letters shown on a key for a given language, e.g. Spanish `ñ` on 6.
     * They fold to a base letter for encoding, so they never change a digit sequence.
     */
    fun letterHints(digit: Char, languageTag: String): String? {
        val base = letters[digit] ?: return null
        if (!languageTag.startsWith("es")) return base
        return when (digit) {
            '6' -> base + "ñ"
            else -> base
        }
    }

    private val letterToDigit: Map<Char, Char> = buildMap {
        letters.forEach { (digit, chars) -> chars.forEach { put(it, digit) } }
    }

    /** Digits that carry letters. */
    val letterDigits: Set<Char> = letters.keys

    fun isLetterDigit(c: Char): Boolean = c in letterDigits

    /**
     * Encode a plain dictionary word: "hello" -> "43556".
     *
     * Accents and `ñ` are folded first, so "señor" encodes exactly as "senor" and
     * "café" as "cafe" — a Spanish user never has to think about diacritics.
     *
     * Returns null if any character has no letter key, which is how a dictionary
     * builder filters its input. For emails, URLs and other mixed tokens use
     * [encodeExtended].
     */
    fun encode(word: String): String? {
        val folded = foldToAscii(word.lowercase())
        val sb = StringBuilder(folded.length)
        for (ch in folded) {
            // Apostrophes are silent: nobody types a key for the one in "don't", so it
            // encodes exactly like "dont" and both spellings share a sequence.
            if (ch in APOSTROPHES) continue
            sb.append(letterToDigit[ch] ?: return null)
        }
        return sb.toString()
    }

    /** Straight, curly and prime apostrophes all appear in real word lists. */
    val APOSTROPHES = setOf('\'', '\u2019', '\u02BC')

    /**
     * Encode any token the user might want as a shortcut — an email address, a URL, a
     * password-ish handle, a product code.
     *
     * ```
     * letters      a-z          -> 2..9   (after accent folding)
     * digits       0-9          -> themselves
     * anything else (@ . : / - _ + …) -> '1', the punctuation key
     * ```
     *
     * So `juan@gmail.com` -> `58261462451266`. The value is not that anyone types all of
     * it, but that the *prefix* matches early: four taps of `5826` surfaces the whole
     * address as a completion.
     *
     * Never returns null — every character maps to something.
     */
    fun encodeExtended(token: String): String {
        val folded = foldToAscii(token.lowercase())
        val sb = StringBuilder(folded.length)
        for (ch in folded) {
            sb.append(
                when {
                    letterToDigit.containsKey(ch) -> letterToDigit.getValue(ch)
                    ch in '0'..'9' -> ch
                    else -> PUNCTUATION_KEY
                }
            )
        }
        return sb.toString()
    }

    /** True when the token needs [encodeExtended] rather than [encode]. */
    fun isComplexToken(token: String): Boolean = encode(token) == null

    /** Fold diacritics to ASCII, so "señor"/"café"/"über" encode as plain letters. */
    fun foldToAscii(word: String): String = buildString(word.length) {
        for (ch in word) append(ACCENT_FOLD[ch] ?: ch)
    }

    private val ACCENT_FOLD: Map<Char, Char> = buildMap {
        "àáâãäåāăą".forEach { put(it, 'a') }
        "çćĉċč".forEach { put(it, 'c') }
        "èéêëēĕėęě".forEach { put(it, 'e') }
        "ìíîïĩīĭįı".forEach { put(it, 'i') }
        "ñńņňŉ".forEach { put(it, 'n') }
        "òóôõöøōŏő".forEach { put(it, 'o') }
        "ùúûüũūŭůűų".forEach { put(it, 'u') }
        "ýÿŷ".forEach { put(it, 'y') }
        "śŝşš".forEach { put(it, 's') }
        "žźż".forEach { put(it, 'z') }
        "ğĝġģ".forEach { put(it, 'g') }
        "ĺļľłl".forEach { put(it, 'l') }
        "ŕŗř".forEach { put(it, 'r') }
        "ţťt".forEach { put(it, 't') }
        "ďđ".forEach { put(it, 'd') }
    }
}
