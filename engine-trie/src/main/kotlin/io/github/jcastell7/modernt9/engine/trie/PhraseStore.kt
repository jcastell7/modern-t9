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
 * Persisted as TSV — `phrase<TAB>weight<TAB>added-epoch-ms<TAB>uses` — so it can be inspected,
 * edited by hand, and moved between devices.
 */
internal class PhraseStore(private val dir: File) {

    /** phrase (verbatim, case preserved) -> weight */
    val phrases = LinkedHashMap<String, Int>()
    /** phrase -> when it was added, epoch millis. Absent for entries from older files. */
    val added = HashMap<String, Long>()
    /** phrase -> times chosen from the strip. */
    val uses = HashMap<String, Int>()

    private var dirty = false
    private val file get() = File(dir, "user-phrases.tsv")

    fun load() {
        runCatching {
            if (file.exists()) file.forEachLine { line ->
                if (line.isBlank() || line.startsWith('#')) return@forEachLine
                val parts = line.split('\t')
                if (parts.size < 2) return@forEachLine
                parts[1].toIntOrNull()?.let { phrases[parts[0]] = it } ?: return@forEachLine
                parts.getOrNull(2)?.toLongOrNull()?.let { added[parts[0]] = it }
                parts.getOrNull(3)?.toIntOrNull()?.let { uses[parts[0]] = it }
            }
        }
    }

    fun add(phrase: String, weight: Int, now: Long = System.currentTimeMillis()) {
        val p = phrase.trim()
        if (p.isEmpty()) return
        if (p !in phrases) added[p] = now
        phrases[p] = maxOf(phrases[p] ?: 0, weight)
        dirty = true
    }

    /** Import: keep the higher use count and the earlier date. True if anything changed. */
    fun merge(phrase: String, weight: Int, useCount: Int, addedAt: Long, now: Long = System.currentTimeMillis()): Boolean {
        val p = phrase.trim()
        if (p.isEmpty()) return false
        val w = maxOf(phrases[p] ?: 0, weight)
        val u = maxOf(uses[p] ?: 0, useCount)
        // An undated entry that is new here counts as added now.
        val date = listOfNotNull(added[p], addedAt.takeIf { it > 0 }).minOrNull()
            ?: if (p in phrases) null else now
        val changed = w != phrases[p] || u != (uses[p] ?: 0) || date != added[p]
        if (!changed) return false
        phrases[p] = w
        if (u > 0) uses[p] = u
        if (date != null) added[p] = date
        dirty = true
        return true
    }

    fun remove(phrase: String): Boolean {
        val removed = phrases.remove(phrase.trim()) != null
        added.remove(phrase.trim())
        uses.remove(phrase.trim())
        if (removed) dirty = true
        return removed
    }

    fun bump(phrase: String, delta: Int, cap: Int) {
        val current = phrases[phrase] ?: return
        phrases[phrase] = minOf(current + delta, cap)
        uses[phrase] = (uses[phrase] ?: 0) + 1
        dirty = true
    }

    fun clear() {
        phrases.clear()
        added.clear()
        uses.clear()
        dirty = true
        flush()
    }

    fun flush() {
        if (!dirty) return
        runCatching {
            dir.mkdirs()
            file.bufferedWriter().use { w ->
                w.write("# user phrases — phrase<TAB>weight<TAB>added-epoch-ms<TAB>uses\n")
                phrases.entries.sortedByDescending { it.value }.forEach { (p, weight) ->
                    w.write(p); w.write("\t"); w.write(weight.toString())
                    w.write("\t"); w.write((added[p] ?: 0L).toString())
                    w.write("\t"); w.write((uses[p] ?: 0).toString())
                    w.newLine()
                }
            }
            dirty = false
        }
    }
}
