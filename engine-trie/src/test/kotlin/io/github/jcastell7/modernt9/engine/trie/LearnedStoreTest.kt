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
            "# modern-t9 learned words v2 (word<TAB>uses)\ngood\t100\nbroken-line-no-tab\nalso\tnot-a-number\nfine\t42\n"
        )
        val s = LearnedStore(dir).apply { load() }
        assertEquals(100, s.unigrams["good"])
        assertEquals(42, s.unigrams["fine"])
        assertEquals(2, s.unigrams.size)
    }
}

class LearnedStoreMigrationTest {
    @org.junit.Test fun `a file without the header is read as legacy weights`() {
        val dir = java.io.File.createTempFile("t9legacy", "").apply { delete(); mkdirs() }
        java.io.File(dir, "learned-words.tsv").writeText("hello\t24\nworld\t8\nodd\t3\n")
        val s = LearnedStore(dir).apply { load() }
        org.junit.Assert.assertEquals(3, s.unigrams["hello"])
        org.junit.Assert.assertEquals(1, s.unigrams["world"])
        org.junit.Assert.assertEquals(1, s.unigrams["odd"])
        s.flush()                                              // rewritten with the header
        val again = LearnedStore(dir).apply { load() }
        org.junit.Assert.assertEquals(3, again.unigrams["hello"])
    }
}

class AddedTimestampTest {
    private fun dir() = java.io.File.createTempFile("t9added", "").apply { delete(); mkdirs() }

    @org.junit.Test fun `the first use stamps the word and later uses keep it`() {
        val d = dir()
        val s = LearnedStore(d).apply { load() }
        s.noteWord("hello", 1, 100, now = 1000L)
        s.noteWord("hello", 1, 100, now = 2000L)
        s.noteWord("world", 1, 100, now = 3000L)
        s.flush()
        val again = LearnedStore(d).apply { load() }
        org.junit.Assert.assertEquals(1000L, again.added["hello"])
        org.junit.Assert.assertEquals(3000L, again.added["world"])
        org.junit.Assert.assertEquals(2, again.unigrams["hello"])
    }

    @org.junit.Test fun `a v2 file without timestamps still loads as counts`() {
        val d = dir()
        java.io.File(d, "learned-words.tsv").writeText("# modern-t9 learned words v2 (word<TAB>uses)\nhello\t7\n")
        val s = LearnedStore(d).apply { load() }
        org.junit.Assert.assertEquals(7, s.unigrams["hello"])
        org.junit.Assert.assertNull(s.added["hello"])
    }

    @org.junit.Test fun `phrases are stamped too`() {
        val d = dir()
        val p = PhraseStore(d).apply { load() }
        p.add("me@example.com", 10, now = 5000L)
        p.flush()
        val again = PhraseStore(d).apply { load() }
        org.junit.Assert.assertEquals(5000L, again.added["me@example.com"])
        org.junit.Assert.assertEquals(10, again.phrases["me@example.com"])
    }

    @org.junit.Test fun `entries expose the timestamp`() {
        val d = dir()
        val e = TrieEngine(object : io.github.jcastell7.modernt9.engine.EngineResources {
            override fun openAsset(path: String) = java.io.ByteArrayInputStream("the\t10\n".toByteArray())
            override fun dataDir() = d
            override val languageTag = "en"
        }).apply { initialize(); startSession(io.github.jcastell7.modernt9.engine.EditorContext()) }
        e.learn("zork")
        val w = e.userDictionary.novelWords().single()
        org.junit.Assert.assertEquals("zork", w.word)
        org.junit.Assert.assertTrue(w.addedAt > 0)
    }
}

class PhraseUsesTest {
    @org.junit.Test fun `choosing a phrase counts a use, and it survives a restart`() {
        val d = java.io.File.createTempFile("t9puse", "").apply { delete(); mkdirs() }
        val res = object : io.github.jcastell7.modernt9.engine.EngineResources {
            override fun openAsset(path: String) = java.io.ByteArrayInputStream("the\t10\n".toByteArray())
            override fun dataDir() = d
            override val languageTag = "en"
        }
        val e = TrieEngine(res).apply { initialize(); startSession(io.github.jcastell7.modernt9.engine.EditorContext()) }
        e.userDictionary.addPhrase("me@x.com")
        repeat(3) {
            e.reset(); "63".forEach { c -> e.onDigit(c) }
            val i = e.composition().candidates.indexOfFirst { it.text == "me@x.com" }
            e.selectCandidate(i)
        }
        org.junit.Assert.assertEquals(3, e.userDictionary.phrases().single().uses)
        e.close()
        val again = TrieEngine(res).apply { initialize() }
        org.junit.Assert.assertEquals(3, again.userDictionary.phrases().single().uses)
        org.junit.Assert.assertTrue(again.userDictionary.phrases().single().addedAt > 0)
    }
}

class ImportMergeTest {
    private fun engine(d: java.io.File) = TrieEngine(object : io.github.jcastell7.modernt9.engine.EngineResources {
        override fun openAsset(path: String) = java.io.ByteArrayInputStream("the\t10\n".toByteArray())
        override fun dataDir() = d
        override val languageTag = "en"
    }).apply { initialize(); startSession(io.github.jcastell7.modernt9.engine.EditorContext()) }

    @org.junit.Test fun `import adds, merges by max uses and earliest date, and is idempotent`() {
        val d = java.io.File.createTempFile("t9imp", "").apply { delete(); mkdirs() }
        val e = engine(d)
        e.learn("zork")                                          // uses 1, added now
        val w = io.github.jcastell7.modernt9.engine.UserWord::class
        org.junit.Assert.assertTrue(e.userDictionary.importWord(io.github.jcastell7.modernt9.engine.UserWord("zork", 5, 1000L, 5)))
        org.junit.Assert.assertTrue(e.userDictionary.importWord(io.github.jcastell7.modernt9.engine.UserWord("blorp", 2, 2000L, 2)))
        org.junit.Assert.assertTrue(e.userDictionary.importPhrase(io.github.jcastell7.modernt9.engine.UserWord("me@x.com", 0, 3000L, 4)))
        // Second pass: nothing changes.
        org.junit.Assert.assertFalse(e.userDictionary.importWord(io.github.jcastell7.modernt9.engine.UserWord("zork", 3, 5000L, 3)))
        org.junit.Assert.assertFalse(e.userDictionary.importPhrase(io.github.jcastell7.modernt9.engine.UserWord("me@x.com", 0, 9000L, 1)))

        val words = e.userDictionary.novelWords().associateBy { it.word }
        org.junit.Assert.assertEquals(5, words.getValue("zork").uses)
        org.junit.Assert.assertEquals(1000L, words.getValue("zork").addedAt)
        org.junit.Assert.assertEquals(2, words.getValue("blorp").uses)
        val phrase = e.userDictionary.phrases().single()
        org.junit.Assert.assertEquals(4, phrase.uses)
        org.junit.Assert.assertEquals(3000L, phrase.addedAt)

        // Imported words are live in the predictions, and survive a restart.
        e.reset(); "25677".forEach { c -> e.onDigit(c) }
        org.junit.Assert.assertTrue(e.composition().candidates.any { it.text == "blorp" })
        e.close()
        org.junit.Assert.assertEquals(2, engine(d).userDictionary.novelWords().first { it.word == "blorp" }.uses)
    }
}

class ImportUndatedTest {
    @org.junit.Test fun `an undated new entry is stamped now, an existing one keeps its date`() {
        val d = java.io.File.createTempFile("t9und", "").apply { delete(); mkdirs() }
        val s = LearnedStore(d).apply { load() }
        org.junit.Assert.assertTrue(s.merge("fresh", 1, 0L, now = 4242L))
        org.junit.Assert.assertEquals(4242L, s.added["fresh"])
        s.noteWord("old", 1, 100, now = 10L)
        org.junit.Assert.assertFalse(s.merge("old", 1, 0L, now = 4242L))
        org.junit.Assert.assertEquals(10L, s.added["old"])
        val p = PhraseStore(d).apply { load() }
        org.junit.Assert.assertTrue(p.merge("a@b.c", 10, 0, 0L, now = 99L))
        org.junit.Assert.assertEquals(99L, p.added["a@b.c"])
    }
}
