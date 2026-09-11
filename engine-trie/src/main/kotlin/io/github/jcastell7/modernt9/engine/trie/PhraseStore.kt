package io.github.jcastell7.modernt9.engine.trie

import io.github.jcastell7.modernt9.engine.Keypad
import java.io.File

/**
 * User-added phrases — email addresses, URLs, handles, anything with digits or symbols
 * that no baseline dictionary will ever contain.
 *
 * Kept apart from learned words because the rules differ: phrases preserve case, are
 * never auto-learned (only added deliberately), and are encoded with
 * [Keypad.encodeExtended] rather than the strict letters-only encoding.
 *
 * Persisted as TSV — `phrase<TAB>weight` — so it can be inspected, edited by hand, and
 * moved between devices.
 */
internal class PhraseStore(private val dir: File) {

    /** phrase (verbatim, case preserved) -> weight */
    val phrases = LinkedHashMap<String, Int>()

    private var dirty = false
    private val file get() = File(dir, "user-phrases.tsv")

    fun load() {
        runCatching {
            if (file.exists()) file.forEachLine { line ->
                if (line.isBlank() || line.startsWith('#')) return@forEachLine
                val parts = line.split('\t')
                if (parts.size == 2) parts[1].toIntOrNull()?.let { phrases[parts[0]] = it }
            }
        }
    }

    fun add(phrase: String, weight: Int) {
        val p = phrase.trim()
        if (p.isEmpty()) return
        phrases[p] = maxOf(phrases[p] ?: 0, weight)
        dirty = true
    }

    fun remove(phrase: String): Boolean {
        val removed = phrases.remove(phrase.trim()) != null
        if (removed) dirty = true
        return removed
    }

    fun bump(phrase: String, delta: Int, cap: Int) {
        val current = phrases[phrase] ?: return
        phrases[phrase] = minOf(current + delta, cap)
        dirty = true
    }

    fun clear() {
        phrases.clear()
        dirty = true
        flush()
    }

    fun flush() {
        if (!dirty) return
        runCatching {
            dir.mkdirs()
            file.bufferedWriter().use { w ->
                w.write("# user phrases — phrase<TAB>weight\n")
                phrases.entries.sortedByDescending { it.value }.forEach { (p, weight) ->
                    w.write(p); w.write("\t"); w.write(weight.toString()); w.newLine()
                }
            }
            dirty = false
        }
    }
}
