package io.github.jcastell7.modernt9.engine.trie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Direct tests of the data structure, independent of ranking policy. */
class DigitTrieTest {

    private fun trie(vararg entries: Triple<String, String, Int>) = DigitTrie().apply {
        entries.forEach { (digits, word, weight) -> insert(digits, word, weight) }
    }

    @Test fun `exact returns words for the full sequence, best first`() {
        val t = trie(
            Triple("4663", "good", 900),
            Triple("4663", "gone", 500),
            Triple("4663", "home", 700),
        )
        assertEquals(listOf("good", "home", "gone"), t.exact("4663", 10).map { it.word })
    }

    @Test fun `exact respects the limit`() {
        val t = trie(
            Triple("4663", "good", 900),
            Triple("4663", "gone", 500),
            Triple("4663", "home", 700),
        )
        assertEquals(2, t.exact("4663", 2).size)
    }

    @Test fun `exact on an unknown sequence is empty`() {
        val t = trie(Triple("4663", "good", 900))
        assertTrue(t.exact("9999", 10).isEmpty())
        assertTrue(t.exact("46639", 10).isEmpty())
    }

    @Test fun `exact does not return longer words`() {
        val t = trie(Triple("46639", "gooey", 100))
        assertTrue(t.exact("4663", 10).isEmpty())
    }

    @Test fun `completions returns strictly longer words`() {
        val t = trie(
            Triple("4663", "good", 900),
            Triple("46639", "gooey", 100),
            Triple("466377", "gooses", 50),
        )
        val comps = t.completions("4663", 10).map { it.word }
        assertTrue(comps.containsAll(listOf("gooey", "gooses")))
        assertTrue("exact match must not appear as a completion", !comps.contains("good"))
    }

    @Test fun `completions are ranked by weight`() {
        val t = trie(
            Triple("46639", "low", 10),
            Triple("46638", "high", 900),
        )
        assertEquals("high", t.completions("4663", 10).first().word)
    }

    @Test fun `completions respects the limit`() {
        val t = DigitTrie().apply {
            repeat(20) { insert("4663$it".take(5), "w$it", it) }
        }
        assertTrue(t.completions("4663", 3).size <= 3)
    }

    @Test fun `size counts distinct words`() {
        val t = trie(
            Triple("4663", "good", 900),
            Triple("4663", "gone", 500),
        )
        assertEquals(2, t.size)
    }

    @Test fun `inserting the same word twice does not duplicate it`() {
        val t = trie(
            Triple("4663", "good", 100),
            Triple("4663", "good", 900),
        )
        assertEquals(1, t.size)
        assertEquals(1, t.exact("4663", 10).size)
        // the higher weight wins
        assertEquals(900, t.exact("4663", 10).first().weight)
    }

    @Test fun `insert keeps the higher weight, never lowers it`() {
        val t = trie(
            Triple("4663", "good", 900),
            Triple("4663", "good", 10),
        )
        assertEquals(900, t.exact("4663", 10).first().weight)
    }

    @Test fun `reinforce adds an unknown word`() {
        val t = DigitTrie()
        val w = t.reinforce("4663", "gone", 50, 1000)
        assertEquals(50, w)
        assertEquals(1, t.size)
        assertEquals("gone", t.exact("4663", 10).first().word)
    }

    @Test fun `reinforce accumulates and re-sorts`() {
        val t = trie(
            Triple("4663", "good", 100),
            Triple("4663", "gone", 10),
        )
        t.reinforce("4663", "gone", 200, 100_000)
        assertEquals("gone", t.exact("4663", 10).first().word)
        assertEquals(210, t.exact("4663", 10).first().weight)
    }

    @Test fun `reinforce honours the cap`() {
        val t = trie(Triple("4663", "good", 90))
        val w = t.reinforce("4663", "good", 50, 100)
        assertEquals(100, w)
    }

    @Test fun `non-digit characters are ignored safely`() {
        val t = DigitTrie()
        t.insert("46a3", "bad", 10)          // 'a' is not 0..9
        assertTrue(t.exact("46a3", 10).isEmpty())
        assertEquals(0, t.size)
        assertTrue(t.exact("!!", 10).isEmpty())
    }

    @Test fun `empty digit sequence addresses the root`() {
        val t = DigitTrie()
        t.insert("", "root", 5)
        assertEquals("root", t.exact("", 10).first().word)
    }
}

/** The best-first completion search must find the heaviest words wherever they sit. */
class DigitTrieCompletionSearchTest {

    @Test fun `heavy deep words beat light shallow ones`() {
        val t = DigitTrie().apply {
            // Ten light 3-key words, one heavy 8-key word under the same prefix.
            for (d in 2..9) insert("84$d", "w$d", d)
            insert("84474464", "thinking", 6803)
            insert("8447", "this", 199341)
        }
        assertEquals(listOf("this", "thinking"), t.completions("84", 2).map { it.word })
    }

    @Test fun `the search is exact for any limit`() {
        val t = DigitTrie()
        val words = (0 until 300).map { i -> "w$i" to (i * 7919) % 1000 }
        words.forEachIndexed { i, (w, weight) ->
            t.insert("2" + (i.toString(3).map { it + 2 }.joinToString("")), w, weight)
        }
        for (limit in listOf(1, 5, 20, 100)) {
            val expected = words.sortedByDescending { it.second }.take(limit).map { it.second }
            assertEquals(expected, t.completions("2", limit).map { it.weight })
        }
    }

    @Test fun `reinforce raises a subtree so a learned word is found`() {
        val t = DigitTrie().apply {
            insert("2222", "aaaa", 100)
            insert("2223", "aaad", 90)
        }
        t.reinforce("22234", "aaadg", 500, 1000)
        assertEquals("aaadg", t.completions("2", 1).single().word)
    }
}
