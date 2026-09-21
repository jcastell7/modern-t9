package io.github.jcastell7.modernt9

import io.github.jcastell7.modernt9.engine.Keypad
import io.github.jcastell7.modernt9.engine.UserWord
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * The export/import format for the user's own words — a plain text file meant to be
 * read, edited and moved between phones by hand.
 *
 * ```
 * # Modern T9 — my words
 * Tomasito	phrase	4	2026-09-11T10:12:03Z
 * spotify	word	2	2026-09-20T08:00:00Z
 * karcher
 * ```
 *
 * One entry per line: `text<TAB>kind<TAB>uses<TAB>added`. Everything after the text is
 * optional, so a hand-written list of bare words imports too. Lines starting with `#`
 * are comments. Kind is `word` (learned, lower-cased, letters only) or `phrase` (kept
 * verbatim — emails, URLs, capitalised names); when it is missing, anything that needs
 * a symbol or digit key is a phrase and the rest are words.
 */
object WordsFile {

    data class Entry(val word: UserWord, val isPhrase: Boolean)

    data class Parsed(val entries: List<Entry>, val skippedLines: Int)

    private const val KIND_WORD = "word"
    private const val KIND_PHRASE = "phrase"

    fun write(words: List<UserWord>, phrases: List<UserWord>): String = buildString {
        appendLine("# Modern T9 — my words")
        appendLine("# One entry per line: text<TAB>kind<TAB>uses<TAB>added")
        appendLine("#   kind  = word | phrase      uses = times chosen      added = when first added (UTC)")
        appendLine("# Lines starting with # are ignored. A line with just a word is accepted too.")
        phrases.forEach { appendLine(line(it, KIND_PHRASE)) }
        words.forEach { appendLine(line(it, KIND_WORD)) }
    }

    private fun line(w: UserWord, kind: String): String = buildString {
        append(w.word.replace('\t', ' ')).append('\t').append(kind).append('\t').append(w.uses).append('\t')
        if (w.addedAt > 0) append(Instant.ofEpochMilli(w.addedAt).toString())
    }

    fun parse(text: String): Parsed {
        val entries = ArrayList<Entry>()
        var skipped = 0
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank() || line.trimStart().startsWith('#')) continue
            val parts = line.split('\t')
            val word = parts[0].trim()
            if (word.isEmpty()) { skipped++; continue }
            val kind = parts.getOrNull(1)?.trim()?.lowercase()
            val isPhrase = when (kind) {
                KIND_PHRASE -> true
                KIND_WORD -> false
                null, "" -> Keypad.isComplexToken(word)
                else -> { skipped++; continue }
            }
            val uses = parts.getOrNull(2)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val added = parts.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }?.let { stamp ->
                stamp.toLongOrNull() ?: runCatching { Instant.parse(stamp).toEpochMilli() }
                    .getOrElse { if (it is DateTimeParseException) 0L else throw it }
            } ?: 0L
            entries += Entry(UserWord(word, uses, added, uses), isPhrase)
        }
        return Parsed(entries, skipped)
    }
}
