package io.github.jcastell7.modernt9

import io.github.jcastell7.modernt9.engine.Keypad

/**
 * Classic multi-tap letter entry, used when prediction is switched off.
 *
 * Tapping `2` once gives `a`, twice `b`, three times `c`; pausing, or moving to another
 * key, accepts the current letter and starts the next. No prediction is involved, so the
 * word being typed is never rewritten.
 */
class MultiTap {

    private var key: Char? = null
    private var index = 0
    private var lastTapAt = 0L

    /** The letter currently shown, or null when nothing is pending. */
    var pending: String? = null
        private set

    /** True when the next tap on [digit] would cycle rather than start a new letter. */
    fun continues(digit: Char, now: Long): Boolean =
        key == digit && now - lastTapAt <= TIMEOUT_MS

    /**
     * Register a tap.
     *
     * @return [Result.replace] when the pending letter should be replaced in place, or
     *   [Result.append] when the previous letter is finished and a new one begins.
     */
    fun tap(digit: Char, letters: String, now: Long): Result {
        if (letters.isEmpty()) return Result(append = true, letter = digit.toString())
        val cycling = continues(digit, now)
        index = if (cycling) (index + 1) % letters.length else 0
        key = digit
        lastTapAt = now
        val letter = letters[index].toString()
        pending = letter
        return Result(append = !cycling, letter = letter)
    }

    /** Accept whatever is pending and stop cycling. */
    fun finish() {
        key = null
        index = 0
        pending = null
    }

    /** True once the cycle window has closed, so the letter is settled. */
    fun hasExpired(now: Long): Boolean = pending != null && now - lastTapAt > TIMEOUT_MS

    data class Result(val append: Boolean, val letter: String)

    companion object {
        /** How long the same key keeps cycling. Matches the classic phone feel. */
        const val TIMEOUT_MS = 900L

        fun lettersFor(digit: Char, languageTag: String): String =
            Keypad.letterHints(digit, languageTag) ?: digit.toString()
    }
}
