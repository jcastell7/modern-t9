package io.github.jcastell7.modernt9.engine.trie

import io.github.jcastell7.modernt9.engine.CandidateSource
import io.github.jcastell7.modernt9.engine.EditorContext
import io.github.jcastell7.modernt9.engine.EngineResources
import io.github.jcastell7.modernt9.engine.FieldType
import io.github.jcastell7.modernt9.engine.Keypad
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * These run on the JVM with no emulator — which is the point of keeping `engine-api`
 * free of Android dependencies. Any replacement backend should pass an equivalent suite.
 */
class TrieEngineTest {

    private lateinit var engine: TrieEngine
    private lateinit var tmp: File

    private val dictionary = """
        # word<TAB>weight
        hello	900
        gekko	10
        good	950
        gone	800
        home	850
        gold	700
        gooey	40
        gopher	30
        the	1000
        world	600
    """.trimIndent()

    private fun resources(dir: File) = object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path == "dict/en.txt") ByteArrayInputStream(dictionary.toByteArray()) else null
        override fun dataDir(): File = dir
        override val languageTag = "en"
    }

    @Before fun setUp() {
        tmp = File.createTempFile("t9engine", "").apply { delete(); mkdirs() }
        engine = TrieEngine(resources(tmp))
        engine.initialize()
        engine.startSession(EditorContext())
    }

    @Test fun `keypad encodes words to digits`() {
        assertEquals("43556", Keypad.encode("hello"))
        assertEquals("4663", Keypad.encode("good"))
        assertNull(Keypad.encode("hi there"))       // space has no key
        assertEquals("cafe", Keypad.foldToAscii("café"))
    }

    @Test fun `exact match ranks by weight`() {
        "4663".forEach { engine.onDigit(it) }        // good / gone / home / gold
        val words = engine.composition().candidates
            .filter { it.source != CandidateSource.LITERAL }
            .map { it.text }
        assertEquals("good", words.first())          // weight 950 beats home 850, gone 800
        assertTrue(words.containsAll(listOf("good", "gone", "home")))
        // "gold" is 4653 (l=5), not 4663 — a good check that encoding is exact
        assertEquals("4653", Keypad.encode("gold"))
        assertTrue(words.none { it == "gold" })
    }

    @Test fun `literal digits are always offered last`() {
        "4663".forEach { engine.onDigit(it) }
        val last = engine.composition().candidates.last()
        assertEquals(CandidateSource.LITERAL, last.source)
        assertEquals("4663", last.text)
    }

    @Test fun `completions appear for a prefix`() {
        "466".forEach { engine.onDigit(it) }         // prefix of good, gone, gold, gooey...
        val comps = engine.composition().candidates.filter {
            it.source == CandidateSource.COMPLETION
        }
        assertTrue("expected completions, got ${engine.composition().candidates}", comps.isNotEmpty())
        assertTrue(comps.none { it.isExactLength })
    }

    @Test fun `backspace shortens the composition`() {
        "4663".forEach { engine.onDigit(it) }
        assertEquals("4663", engine.composition().digits)
        engine.onBackspace()
        assertEquals("466", engine.composition().digits)
        repeat(3) { engine.onBackspace() }
        assertTrue(engine.composition().isEmpty)
    }

    @Test fun `learning promotes a word above a higher-weighted rival`() {
        "4663".forEach { engine.onDigit(it) }
        assertEquals("good", engine.composition().candidates.first().text)

        repeat(20) { engine.learn("gone") }

        engine.reset()
        "4663".forEach { engine.onDigit(it) }
        val top = engine.composition().candidates.first()
        assertEquals("gone", top.text)
        assertEquals(CandidateSource.USER, top.source)
    }

    @Test fun `selectCandidate returns text and clears composition`() {
        "43556".forEach { engine.onDigit(it) }
        val committed = engine.selectCandidate(0)
        assertEquals("hello", committed)
        assertTrue(engine.composition().isEmpty)
        assertNull(engine.selectCandidate(99))
    }

    @Test fun `next-word prediction uses learned bigrams`() {
        repeat(3) {
            engine.learn("hello")
            engine.learn("world")
        }
        engine.learn("hello")
        val predictions = engine.predictNextWord().map { it.text }
        assertTrue("expected 'world' in $predictions", predictions.contains("world"))
    }

    @Test fun `password fields are not learned from`() {
        engine.startSession(EditorContext(fieldType = FieldType.PASSWORD))
        repeat(20) { engine.learn("hunter") }
        assertTrue(engine.userDictionary.entries().none { it.word == "hunter" })
    }

    @Test fun `user dictionary add and remove`() {
        engine.userDictionary.add("zsh", 500)
        assertTrue(engine.userDictionary.contains("zsh"))
        engine.reset()
        "974".forEach { engine.onDigit(it) }
        assertNotNull(engine.composition().candidates.firstOrNull { it.text == "zsh" })
        assertTrue(engine.userDictionary.remove("zsh"))
        assertTrue(!engine.userDictionary.contains("zsh"))
    }

    @Test fun `learned words survive a restart`() {
        repeat(20) { engine.learn("gone") }
        engine.endSession()
        engine.close()

        val revived = TrieEngine(resources(tmp))
        revived.initialize()
        revived.startSession(EditorContext())
        "4663".forEach { revived.onDigit(it) }
        assertEquals("gone", revived.composition().candidates.first().text)
    }
}
