package io.github.jcastell7.modernt9.engine.trie

import io.github.jcastell7.modernt9.engine.CandidateSource
import io.github.jcastell7.modernt9.engine.EditorContext
import io.github.jcastell7.modernt9.engine.EngineResources
import io.github.jcastell7.modernt9.engine.FieldType
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behaviour beyond the happy path in [TrieEngineTest]: session handling, field policy,
 * malformed input, and the descriptor contract.
 */
class TrieEngineBehaviourTest {

    private lateinit var tmp: File

    private val dictionary = """
        hello	900
        good	950
        gone	800
        home	850
        gooey	40
        the	1000
        world	600
    """.trimIndent()

    private fun resources(
        dir: File = tmp,
        dict: String? = dictionary,
        language: String = "en",
    ) = object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path == "dict/$language.txt" || path == "dict/en.txt")
                dict?.let { ByteArrayInputStream(it.toByteArray()) }
            else null
        override fun dataDir(): File = dir
        override val languageTag = language
    }

    private fun engine(res: EngineResources = resources()) =
        TrieEngine(res).apply { initialize(); startSession(EditorContext()) }

    @Before fun setUp() {
        tmp = File.createTempFile("t9behaviour", "").apply { delete(); mkdirs() }
    }

    // ---- descriptor -----------------------------------------------------------

    @Test fun `descriptor advertises its capabilities`() {
        val d = engine().descriptor
        assertEquals(TrieEngine.ID, d.id)
        assertTrue(d.supportsLearning)
        assertTrue(d.supportsNextWordPrediction)
        assertTrue(d.supportedLanguages.contains("en"))
    }

    @Test fun `factory produces a working engine`() {
        val e = TrieEngineFactory().create(resources())
        e.initialize()
        e.startSession(EditorContext())
        "843".forEach { e.onDigit(it) }
        assertEquals("the", e.composition().candidates.first().text)
        assertEquals(TrieEngine.ID, TrieEngineFactory().descriptor.id)
    }

    // ---- composition state ----------------------------------------------------

    @Test fun `a fresh engine has an empty composition`() {
        assertTrue(engine().composition().isEmpty)
    }

    @Test fun `composition is idempotent`() {
        val e = engine()
        "4663".forEach { e.onDigit(it) }
        assertEquals(e.composition(), e.composition())
    }

    @Test fun `reset clears the composition`() {
        val e = engine()
        "4663".forEach { e.onDigit(it) }
        assertFalse(e.composition().isEmpty)
        assertTrue(e.reset().isEmpty)
        assertTrue(e.composition().isEmpty)
    }

    @Test fun `backspace on an empty composition is harmless`() {
        val e = engine()
        assertTrue(e.onBackspace().isEmpty)
        assertTrue(e.onBackspace().isEmpty)
    }

    @Test fun `non-letter digits are ignored`() {
        val e = engine()
        e.onDigit('1')
        e.onDigit('0')
        assertTrue("only 2-9 compose", e.composition().isEmpty)
    }

    @Test fun `composing text is the top candidate`() {
        val e = engine()
        "4663".forEach { e.onDigit(it) }
        val c = e.composition()
        assertEquals(c.candidates.first().text, c.composing)
        assertEquals("4663", c.digits)
    }

    @Test fun `an unknown sequence still offers the literal digits`() {
        val e = engine()
        "2222".forEach { e.onDigit(it) }
        val candidates = e.composition().candidates
        assertEquals(1, candidates.size)
        assertEquals(CandidateSource.LITERAL, candidates.first().source)
        // Composing shows one letter per key, not the digits — the digits stay available
        // as the literal candidate.
        assertEquals("aaaa", e.composition().composing)
    }

    // ---- sessions -------------------------------------------------------------

    @Test fun `starting a session clears a pending composition`() {
        val e = engine()
        "4663".forEach { e.onDigit(it) }
        e.startSession(EditorContext())
        assertTrue(e.composition().isEmpty)
    }

    @Test fun `preceding text seeds next-word context`() {
        val e = engine()
        repeat(5) { e.learn("hello"); e.learn("world") }
        e.startSession(EditorContext(precedingText = "well hello"))
        assertTrue(e.predictNextWord().any { it.text == "world" })
    }

    @Test fun `no next-word prediction without context`() {
        assertTrue(engine().predictNextWord().isEmpty())
    }

    @Test fun `endSession clears the composition`() {
        val e = engine()
        "4663".forEach { e.onDigit(it) }
        e.endSession()
        assertTrue(e.composition().isEmpty)
    }

    // ---- learning policy ------------------------------------------------------

    @Test fun `number fields are not learned from`() {
        val e = engine()
        e.startSession(EditorContext(fieldType = FieldType.NUMBER))
        repeat(20) { e.learn("secret") }
        assertFalse(e.userDictionary.contains("secret"))
    }

    @Test fun `incognito fields are not learned from`() {
        val e = engine()
        e.startSession(EditorContext(incognito = true))
        repeat(20) { e.learn("secret") }
        assertFalse(e.userDictionary.contains("secret"))
    }

    @Test fun `email fields are learned from`() {
        val e = engine()
        e.startSession(EditorContext(fieldType = FieldType.EMAIL))
        repeat(20) { e.learn("hello") }
        assertTrue(e.userDictionary.contains("hello"))
    }

    @Test fun `learning lower-cases but keeps accents`() {
        val e = engine()
        repeat(10) { e.learn("CAFÉ") }
        // The accented spelling is what gets stored, so it can be offered back correctly.
        assertTrue(e.userDictionary.contains("café"))
        assertTrue(e.userDictionary.entries().any { it.word == "café" })
    }

    @Test fun `unlearnable text does not crash and resets context`() {
        val e = engine()
        e.learn("hello")
        e.learn("!!!  ")          // nothing learnable
        assertTrue(e.predictNextWord().isEmpty())
    }

    // ---- user dictionary ------------------------------------------------------

    @Test fun `user dictionary lists entries by weight`() {
        val e = engine()
        e.userDictionary.add("alpha", 10)
        e.userDictionary.add("beta", 900)
        assertEquals("beta", e.userDictionary.entries().first().word)
    }

    @Test fun `user dictionary clear empties it`() {
        val e = engine()
        e.userDictionary.add("alpha", 10)
        e.userDictionary.clear()
        assertTrue(e.userDictionary.entries().isEmpty())
    }

    @Test fun `removing an absent word reports false`() {
        assertFalse(engine().userDictionary.remove("nothing"))
    }

    @Test fun `words with no keypad encoding are rejected`() {
        val e = engine()
        e.userDictionary.add("two words", 100)
        assertFalse(e.userDictionary.contains("two words"))
    }

    // ---- selection ------------------------------------------------------------

    @Test fun `selecting out of range returns null and keeps state`() {
        val e = engine()
        "4663".forEach { e.onDigit(it) }
        assertNull(e.selectCandidate(-1))
        assertNull(e.selectCandidate(999))
        assertFalse(e.composition().isEmpty)
    }

    @Test fun `selecting on an empty composition returns null`() {
        assertNull(engine().selectCandidate(0))
    }

    @Test fun `selecting a candidate teaches the engine`() {
        val e = engine()
        "4663".forEach { e.onDigit(it) }
        val index = e.composition().candidates.indexOfFirst { it.text == "gone" }
        assertTrue(index >= 0)
        repeat(20) {
            e.reset()
            "4663".forEach { d -> e.onDigit(d) }
            val i = e.composition().candidates.indexOfFirst { c -> c.text == "gone" }
            e.selectCandidate(i)
        }
        e.reset()
        "4663".forEach { e.onDigit(it) }
        assertEquals("gone", e.composition().candidates.first().text)
    }

    // ---- dictionary loading ---------------------------------------------------

    @Test fun `a missing dictionary yields literals only, not a crash`() {
        val e = TrieEngine(resources(dict = null)).apply {
            initialize(); startSession(EditorContext())
        }
        "4663".forEach { e.onDigit(it) }
        assertEquals(CandidateSource.LITERAL, e.composition().candidates.single().source)
    }

    @Test fun `entries without a weight default to 1`() {
        val e = TrieEngine(resources(dict = "solo")).apply {
            initialize(); startSession(EditorContext())
        }
        "7656".forEach { e.onDigit(it) }
        assertNotNull(e.composition().candidates.firstOrNull { it.text == "solo" })
    }

    @Test fun `comments and blank lines are skipped`() {
        val e = TrieEngine(resources(dict = "# a comment\n\nhello\t5\n")).apply {
            initialize(); startSession(EditorContext())
        }
        "43556".forEach { e.onDigit(it) }
        assertEquals("hello", e.composition().candidates.first().text)
    }

    @Test fun `an unsupported language falls back to the english dictionary`() {
        val e = TrieEngine(resources(language = "fr")).apply {
            initialize(); startSession(EditorContext())
        }
        "843".forEach { e.onDigit(it) }
        assertEquals("the", e.composition().candidates.first().text)
    }

    @Test fun `close flushes learned data`() {
        val e = engine()
        repeat(10) { e.learn("gone") }
        e.close()
        val revived = TrieEngine(resources()).apply { initialize() }
        assertTrue(revived.userDictionary.contains("gone"))
    }
}
