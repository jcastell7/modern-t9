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
        /**
         * The heaviest word anywhere in this subtree. Lets [completions] search
         * best-first and stop early, instead of walking a whole subtree breadth-first
         * and hoping the frequent words happened to be near the top.
         */
        var best: Int = 0

        fun child(d: Int): Node? = children?.get(d)

        fun childOrCreate(d: Int): Node {
            val c = children ?: arrayOfNulls<Node>(10).also { children = it }
            return c[d] ?: Node().also { c[d] = it }
        }
    }

    /** [learned] marks a word that came from the user, not the dictionary — the only kind [remove] will drop. */
    data class Entry(val word: String, var weight: Int, val learned: Boolean = false)

    private val root = Node()
    var size: Int = 0
        private set

    fun insert(digits: String, word: String, weight: Int) {
        val node = descend(digits) ?: return
        val list = node.words ?: ArrayList<Entry>(2).also { node.words = it }
        val existing = list.firstOrNull { it.word == word }
        if (existing != null) {
            if (weight > existing.weight) {
                existing.weight = weight
                list.sortByDescending { it.weight }
                raiseBest(digits, weight)
            }
            return
        }
        list.add(Entry(word, weight))
        list.sortByDescending { it.weight }
        size++
        raiseBest(digits, weight)
    }

    /** Bump a word's weight, inserting it if absent. Used by learning. */
    fun reinforce(digits: String, word: String, delta: Int, cap: Int): Int {
        val node = descend(digits) ?: return 0
        val list = node.words ?: ArrayList<Entry>(2).also { node.words = it }
        val existing = list.firstOrNull { it.word == word }
        val newWeight: Int
        if (existing == null) {
            newWeight = delta
            list.add(Entry(word, newWeight, learned = true))
            size++
        } else {
            newWeight = minOf(existing.weight + delta, cap)
            existing.weight = newWeight
        }
        list.sortByDescending { it.weight }
        raiseBest(digits, newWeight)
        return newWeight
    }

    /** The node for [digits], creating the path. Null if a character is not a digit. */
    private fun descend(digits: String): Node? {
        var node = root
        for (ch in digits) {
            val d = ch - '0'
            if (d !in 0..9) return null
            node = node.childOrCreate(d)
        }
        return node
    }

    /** Propagate a new weight up the path so every ancestor's [Node.best] stays a true maximum. */
    private fun raiseBest(digits: String, weight: Int) {
        var node = root
        if (weight > node.best) node.best = weight
        for (ch in digits) {
            node = node.child(ch - '0') ?: return
            if (weight > node.best) node.best = weight
        }
    }

    /**
     * Forget a word the user taught the trie. Dictionary words stay: forgetting a use
     * count must not delete "the". Returns true if an entry was removed.
     *
     * [Node.best] is left as it was — possibly now too high, which only makes the
     * completion search prune a little less, never wrongly.
     */
    fun remove(digits: String, word: String): Boolean {
        val node = find(digits) ?: return false
        val list = node.words ?: return false
        val removed = list.removeAll { it.word == word && it.learned }
        if (removed) size--
        return removed
    }

    /** True when [word] is stored here and came from the user rather than the dictionary. */
    fun isLearned(digits: String, word: String): Boolean =
        find(digits)?.words?.firstOrNull { it.word == word }?.learned == true

    /** Drop everything. Used when a single entry must be removed. */
    fun clear() {
        root.children = null
        root.words = null
        root.best = 0
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
     * The [limit] heaviest words that *start* with [digits] but are longer — word
     * completions — best first.
     *
     * Best-first over [Node.best]: subtrees are visited heaviest first, and the search
     * stops as soon as no unvisited subtree can beat the lightest word already kept. So
     * "this" surfaces on the second key, however many rarer words share its prefix.
     *
     * The previous breadth-first walk took the first [limit] words it *met* and only
     * then sorted them — with two keys pressed that was a handful of short, obscure
     * words, and the common longer ones never appeared until they were typed in full.
     */
    fun completions(digits: String, limit: Int): List<Entry> {
        if (limit <= 0) return emptyList()
        val start = find(digits) ?: return emptyList()
        val out = ArrayList<Entry>(limit + 1)
        val queue = java.util.PriorityQueue<Node>(16) { a, b -> b.best.compareTo(a.best) }
        start.children?.forEach { it?.let(queue::add) }
        while (queue.isNotEmpty()) {
            val n = queue.poll()
            // Everything left is lighter than what we already hold: done.
            if (out.size >= limit && n.best <= out.last().weight) break
            n.words?.forEach { e -> offer(out, e, limit) }
            n.children?.forEach { it?.let(queue::add) }
        }
        return out
    }

    /** Insert [e] into [out], which stays sorted heaviest first and no longer than [limit]. */
    private fun offer(out: ArrayList<Entry>, e: Entry, limit: Int) {
        if (out.size >= limit && e.weight <= out.last().weight) return
        var i = out.size
        while (i > 0 && out[i - 1].weight < e.weight) i--
        out.add(i, e)
        if (out.size > limit) out.removeAt(out.size - 1)
    }
}
