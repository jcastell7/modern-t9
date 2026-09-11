package io.github.jcastell7.modernt9

import io.github.jcastell7.modernt9.engine.FieldType
import io.github.jcastell7.modernt9.engine.Punctuation

/**
 * Multi-tap state for the punctuation key.
 *
 * Tapping `1` inserts `.`; tapping again replaces it with `,`, then `?`, and so on —
 * the same interaction every phone keypad has used, and the reason `@`, `:` and `?`
 * are reachable without a symbol panel.
 *
 * The cycle is context-sensitive: an email field starts at `@`, a URL field at `.`
 * followed by `/` and `:`. Spanish adds `¿` and `¡`, which open a clause.
 */
class PunctuationCycler {

    private var cycle: List<String> = Punctuation.cycleFor(FieldType.TEXT, "en")
    private var index = -1

    /** True while a punctuation mark is being cycled and can still be replaced. */
    val isActive: Boolean get() = index >= 0

    /** The mark currently shown, or null when inactive. */
    val current: String? get() = cycle.getOrNull(index)

    /** All marks in the active cycle — used to fill the candidate strip. */
    fun options(): List<String> = cycle

    fun configure(fieldType: FieldType, languageTag: String) {
        cycle = Punctuation.cycleFor(fieldType, languageTag)
        index = -1
    }

    /** Advance the cycle and return the mark to display. */
    fun next(): String {
        index = if (index < 0) 0 else (index + 1) % cycle.size
        return cycle[index]
    }

    /** Jump straight to a mark the user tapped in the candidate strip. */
    fun select(mark: String): String {
        val i = cycle.indexOf(mark)
        index = if (i >= 0) i else -1
        return mark
    }

    /** Stop cycling; the mark is committed. */
    fun finish() {
        index = -1
    }
}
