package io.github.jcastell7.modernt9.engine.trie

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LearnedStoreTest {

    private lateinit var dir: File

    @Before fun setUp() {
        dir = File.createTempFile("t9store", "").apply { delete(); mkdirs() }
    }

    @Test fun `noteWord accumulates`() {
        val s = LearnedStore(dir)
        assertEquals(5, s.noteWord("hello", 5, 1000))
        assertEquals(10, s.noteWord("hello", 5, 1000))
        assertEquals(10, s.unigrams["hello"])
    }

    @Test fun `noteWord honours the cap`() {
        val s = LearnedStore(dir)
        s.noteWord("hello", 90, 100)
        assertEquals(100, s.noteWord("hello", 50, 100))
    }

    @Test fun `bigrams accumulate per previous word`() {
        val s = LearnedStore(dir)
        s.noteBigram("hello", "world", 1000)
        s.noteBigram("hello", "world", 1000)
        s.noteBigram("hello", "there", 1000)
        assertEquals(2, s.bigrams["hello"]?.get("world"))
        assertEquals(1, s.bigrams["hello"]?.get("there"))
    }

    @Test fun `forget removes a word and reports whether it existed`() {
        val s = LearnedStore(dir)
        s.noteWord("hello", 5, 1000)
        assertTrue(s.forget("hello"))
        assertFalse(s.forget("hello"))
        assertNull(s.unigrams["hello"])
    }

    @Test fun `flush then load round-trips words and bigrams`() {
        LearnedStore(dir).apply {
            noteWord("hello", 7, 1000)
            noteWord("world", 3, 1000)
            noteBigram("hello", "world", 1000)
            flush()
        }
        val loaded = LearnedStore(dir).apply { load() }
        assertEquals(7, loaded.unigrams["hello"])
        assertEquals(3, loaded.unigrams["world"])
        assertEquals(1, loaded.bigrams["hello"]?.get("world"))
    }

    @Test fun `clear wipes both stores and persists the wipe`() {
        LearnedStore(dir).apply {
            noteWord("hello", 7, 1000)
            noteBigram("hello", "world", 1000)
            flush()
            clear()
        }
        val loaded = LearnedStore(dir).apply { load() }
        assertTrue(loaded.unigrams.isEmpty())
        assertTrue(loaded.bigrams.isEmpty())
    }

    @Test fun `load on an empty directory is harmless`() {
        val s = LearnedStore(File(dir, "does-not-exist"))
        s.load()
        assertTrue(s.unigrams.isEmpty())
    }

    @Test fun `flush is a no-op when nothing changed`() {
        val s = LearnedStore(dir)
        s.flush()
        assertFalse(File(dir, "learned-words.tsv").exists())
    }

    @Test fun `malformed lines are skipped rather than throwing`() {
        dir.mkdirs()
        File(dir, "learned-words.tsv").writeText(
            "good\t100\nbroken-line-no-tab\nalso\tnot-a-number\nfine\t42\n"
        )
        val s = LearnedStore(dir).apply { load() }
        assertEquals(100, s.unigrams["good"])
        assertEquals(42, s.unigrams["fine"])
        assertEquals(2, s.unigrams.size)
    }
}
