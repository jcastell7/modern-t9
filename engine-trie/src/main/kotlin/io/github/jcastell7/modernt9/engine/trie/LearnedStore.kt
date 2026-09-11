package io.github.jcastell7.modernt9.engine.trie

import java.io.File

/**
 * Persistent learned words and bigrams, written as plain TSV so it can be inspected,
 * edited, backed up and diffed by hand. Small enough that rewriting the whole file on
 * flush is cheaper than any incremental scheme.
 */
internal class LearnedStore(private val dir: File) {

    val unigrams = HashMap<String, Int>()
    /** previousWord -> (nextWord -> count) */
    val bigrams = HashMap<String, HashMap<String, Int>>()

    private var dirty = false

    private val unigramFile get() = File(dir, "learned-words.tsv")
    private val bigramFile get() = File(dir, "learned-bigrams.tsv")

    fun load() {
        runCatching {
            if (unigramFile.exists()) unigramFile.forEachLine { line ->
                val parts = line.split('\t')
                if (parts.size == 2) parts[1].toIntOrNull()?.let { unigrams[parts[0]] = it }
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

    fun noteWord(word: String, delta: Int, cap: Int): Int {
        val next = minOf((unigrams[word] ?: 0) + delta, cap)
        unigrams[word] = next
        dirty = true
        return next
    }

    fun noteBigram(previous: String, next: String, cap: Int) {
        val m = bigrams.getOrPut(previous) { HashMap() }
        m[next] = minOf((m[next] ?: 0) + 1, cap)
        dirty = true
    }

    fun forget(word: String): Boolean {
        val had = unigrams.remove(word) != null
        if (had) dirty = true
        return had
    }

    fun clear() {
        unigrams.clear()
        bigrams.clear()
        dirty = true
        flush()
    }

    fun flush() {
        if (!dirty) return
        runCatching {
            dir.mkdirs()
            unigramFile.bufferedWriter().use { w ->
                unigrams.entries.sortedByDescending { it.value }.forEach { (word, count) ->
                    w.write(word); w.write("\t"); w.write(count.toString()); w.newLine()
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
