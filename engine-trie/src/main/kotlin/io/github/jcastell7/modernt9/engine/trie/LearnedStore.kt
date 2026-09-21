package io.github.jcastell7.modernt9.engine.trie

import java.io.File

/**
 * Persistent learned words and bigrams, written as plain TSV so it can be inspected,
 * edited, backed up and diffed by hand. Small enough that rewriting the whole file on
 * flush is cheaper than any incremental scheme.
 */
internal class LearnedStore(private val dir: File) {

    val unigrams = HashMap<String, Int>()
    /** word -> when it was first learned, epoch millis. Absent for pre-v3 entries. */
    val added = HashMap<String, Long>()
    /** previousWord -> (nextWord -> count) */
    val bigrams = HashMap<String, HashMap<String, Int>>()

    private var dirty = false

    private companion object {
        /** Any versioned header marks a file whose numbers are use counts. */
        const val HEADER_PREFIX = "# modern-t9 learned words v"
        const val HEADER = "${HEADER_PREFIX}3 (word<TAB>uses<TAB>added-epoch-ms)"
        /** What one use added in files without the header. */
        const val LEGACY_DELTA = 8
    }

    private val unigramFile get() = File(dir, "learned-words.tsv")
    private val bigramFile get() = File(dir, "learned-bigrams.tsv")

    fun load() {
        runCatching {
            if (unigramFile.exists()) {
                // Files written before the header stored a weight that grew by 8 per
                // use, not a use count. Read those as counts, or every old word would
                // look eight times as used as it was.
                var legacy = true
                unigramFile.forEachLine { line ->
                    if (line.startsWith(HEADER_PREFIX)) { legacy = false; return@forEachLine }
                    val parts = line.split('\t')
                    if (parts.size < 2) return@forEachLine
                    val count = parts[1].toIntOrNull() ?: return@forEachLine
                    unigrams[parts[0]] = count
                    parts.getOrNull(2)?.toLongOrNull()?.let { added[parts[0]] = it }
                }
                if (legacy && unigrams.isNotEmpty()) {
                    unigrams.replaceAll { _, v -> maxOf(1, v / LEGACY_DELTA) }
                    dirty = true
                }
            }
        }
        runCatching {
            if (bigramFile.exists()) bigramFile.forEachLine { line ->
                val parts = line.split('\t')
                if (parts.size == 3) parts[2].toIntOrNull()?.let { c ->
                    bigrams.getOrPut(parts[0]) { HashMap() }[parts[1]] = c
                }
            }
        }
    }

    fun noteWord(word: String, delta: Int, cap: Int, now: Long = System.currentTimeMillis()): Int {
        val next = minOf((unigrams[word] ?: 0) + delta, cap)
        if (word !in unigrams) added[word] = now
        unigrams[word] = next
        dirty = true
        return next
    }

    /** Import: keep the higher count and the earlier date. True if anything changed. */
    fun merge(word: String, uses: Int, addedAt: Long, now: Long = System.currentTimeMillis()): Boolean {
        val count = maxOf(unigrams[word] ?: 0, uses)
        // An undated entry that is new here counts as added now.
        val date = listOfNotNull(added[word], addedAt.takeIf { it > 0 }).minOrNull()
            ?: if (word in unigrams) null else now
        val changed = count != unigrams[word] || date != added[word]
        if (!changed) return false
        unigrams[word] = count
        if (date != null) added[word] = date
        dirty = true
        return true
    }

    fun noteBigram(previous: String, next: String, cap: Int) {
        val m = bigrams.getOrPut(previous) { HashMap() }
        m[next] = minOf((m[next] ?: 0) + 1, cap)
        dirty = true
    }

    /** Forget a word: its count, and every bigram it appears in. */
    fun forget(word: String): Boolean {
        var had = unigrams.remove(word) != null
        added.remove(word)
        if (bigrams.remove(word) != null) had = true
        for (nexts in bigrams.values) if (nexts.remove(word) != null) had = true
        bigrams.values.removeAll { it.isEmpty() }
        if (had) dirty = true
        return had
    }

    fun clear() {
        unigrams.clear()
        added.clear()
        bigrams.clear()
        dirty = true
        flush()
    }

    fun flush() {
        if (!dirty) return
        runCatching {
            dir.mkdirs()
            unigramFile.bufferedWriter().use { w ->
                w.write(HEADER); w.newLine()
                unigrams.entries.sortedByDescending { it.value }.forEach { (word, count) ->
                    w.write(word); w.write("\t"); w.write(count.toString())
                    added[word]?.let { w.write("\t"); w.write(it.toString()) }
                    w.newLine()
                }
            }
            bigramFile.bufferedWriter().use { w ->
                bigrams.forEach { (prev, nexts) ->
                    nexts.forEach { (next, count) ->
                        w.write(prev); w.write("\t"); w.write(next); w.write("\t")
                        w.write(count.toString()); w.newLine()
                    }
                }
            }
            dirty = false
        }
    }
}
