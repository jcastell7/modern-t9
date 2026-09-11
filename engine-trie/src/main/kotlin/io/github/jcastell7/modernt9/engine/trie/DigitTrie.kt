package io.github.jcastell7.modernt9.engine.trie

/**
 * A trie keyed by T9 digit sequences.
 *
 * Each node is one digit; the words reachable at a node are every dictionary word whose
 * digit encoding is that path. "43556" -> ["hello", "gekko", ...] ranked by weight.
 *
 * Children are a 10-slot array rather than a map: digit lookup is the hot path, and this
 * removes hashing and boxing from it.
 */
internal class DigitTrie {

    private class Node {
        var children: Array<Node?>? = null
        /** Words whose encoding ends exactly here, kept sorted by weight descending. */
        var words: MutableList<Entry>? = null

        fun child(d: Int): Node? = children?.get(d)

        fun childOrCreate(d: Int): Node {
            val c = children ?: arrayOfNulls<Node>(10).also { children = it }
            return c[d] ?: Node().also { c[d] = it }
        }
    }

    data class Entry(val word: String, var weight: Int)

    private val root = Node()
    var size: Int = 0
        private set

    fun insert(digits: String, word: String, weight: Int) {
        var node = root
        for (ch in digits) {
            val d = ch - '0'
            if (d !in 0..9) return
            node = node.childOrCreate(d)
        }
        val list = node.words ?: ArrayList<Entry>(2).also { node.words = it }
        val existing = list.firstOrNull { it.word == word }
        if (existing != null) {
            if (weight > existing.weight) {
                existing.weight = weight
                list.sortByDescending { it.weight }
            }
            return
        }
        list.add(Entry(word, weight))
        list.sortByDescending { it.weight }
        size++
    }

    /** Bump a word's weight, inserting it if absent. Used by learning. */
    fun reinforce(digits: String, word: String, delta: Int, cap: Int): Int {
        var node = root
        for (ch in digits) {
            val d = ch - '0'
            if (d !in 0..9) return 0
            node = node.childOrCreate(d)
        }
        val list = node.words ?: ArrayList<Entry>(2).also { node.words = it }
        val existing = list.firstOrNull { it.word == word }
        val newWeight: Int
        if (existing == null) {
            newWeight = delta
            list.add(Entry(word, newWeight))
            size++
        } else {
            newWeight = minOf(existing.weight + delta, cap)
            existing.weight = newWeight
        }
        list.sortByDescending { it.weight }
        return newWeight
    }

    /** Drop everything. Used when a single entry must be removed. */
    fun clear() {
        root.children = null
        root.words = null
        size = 0
    }

    private fun find(digits: String): Node? {
        var node = root
        for (ch in digits) {
            val d = ch - '0'
            if (d !in 0..9) return null
            node = node.child(d) ?: return null
        }
        return node
    }

    /** Words matching [digits] exactly, best first. */
    fun exact(digits: String, limit: Int): List<Entry> =
        find(digits)?.words?.take(limit).orEmpty()

    /**
     * Words that *start* with [digits] but are longer — word completions.
     * Breadth-first so shorter completions surface before deeper ones.
     */
    fun completions(digits: String, limit: Int): List<Entry> {
        val start = find(digits) ?: return emptyList()
        val out = ArrayList<Entry>(limit)
        val queue = ArrayDeque<Node>()
        start.children?.forEach { it?.let(queue::addLast) }
        while (queue.isNotEmpty() && out.size < limit) {
            val n = queue.removeFirst()
            n.words?.let { words ->
                for (e in words) {
                    out.add(e)
                    if (out.size >= limit) break
                }
            }
            n.children?.forEach { it?.let(queue::addLast) }
        }
        out.sortByDescending { it.weight }
        return out
    }
}
