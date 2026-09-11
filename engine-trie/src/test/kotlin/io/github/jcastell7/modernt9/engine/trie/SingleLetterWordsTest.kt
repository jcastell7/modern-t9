package io.github.jcastell7.modernt9.engine.trie

import io.github.jcastell7.modernt9.engine.CandidateSource
import io.github.jcastell7.modernt9.engine.EditorContext
import io.github.jcastell7.modernt9.engine.EngineResources
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Letters that are words — "I" in English, "y" in Spanish — must climb the single-press
 * list as they are used, instead of staying stuck in keypad order.
 */
class SingleLetterWordsTest {

    private lateinit var tmp: File

    private fun engine(lang: String = "en") = TrieEngine(object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path.endsWith(".txt")) ByteArrayInputStream("the\t1000\n".toByteArray()) else null
        override fun dataDir(): File = tmp
        override val languageTag = lang
    }).apply { initialize(); startSession(EditorContext()) }

    private fun letters(e: TrieEngine, digit: Char): List<String> {
        e.reset()
        e.onDigit(digit)
        return e.composition().candidates
            .filter { it.source == CandidateSource.LETTER || it.source == CandidateSource.USER }
            .map { it.text }
    }

    @Before fun setUp() {
        tmp = File.createTempFile("t9letters", "").apply { delete(); mkdirs() }
    }

    @Test fun `letters start in keypad order`() {
        assertEquals(listOf("g", "h", "i"), letters(engine(), '4'))
    }

    @Test fun `a used single-letter word rises to first`() {
        val e = engine()
        assertEquals("g", letters(e, '4').first())
        repeat(3) { e.learn("i") }                        // "I" used as a word
        assertEquals("i", letters(e, '4').first())
    }

    @Test fun `the used letter is marked as a word`() {
        val e = engine()
        repeat(3) { e.learn("i") }
        e.reset(); e.onDigit('4')
        val i = e.composition().candidates.first { it.text == "i" }
        assertEquals(CandidateSource.USER, i.source)
    }

    @Test fun `unused letters keep keypad order among themselves`() {
        val e = engine()
        repeat(3) { e.learn("i") }
        assertEquals(listOf("i", "g", "h"), letters(e, '4'))
    }

    @Test fun `spanish y rises on key 9`() {
        val e = engine("es")
        assertEquals("w", letters(e, '9').first())
        repeat(3) { e.learn("y") }
        assertEquals("y", letters(e, '9').first())
    }

    @Test fun `the inline text follows the learned letter`() {
        val e = engine()
        repeat(3) { e.learn("i") }
        e.reset(); e.onDigit('4')
        assertEquals("i", e.composition().composing)
    }

    @Test fun `committing a single letter counts as using it`() {
        val e = engine()
        repeat(3) {
            e.reset(); e.onDigit('4')
            val index = e.composition().candidates.indexOfFirst { it.text == "i" }
            e.selectCandidate(index)
        }
        assertEquals("i", letters(e, '4').first())
    }

    @Test fun `more use ranks higher`() {
        val e = engine()
        repeat(2) { e.learn("h") }
        repeat(5) { e.learn("i") }
        assertEquals(listOf("i", "h", "g"), letters(e, '4'))
    }

    @Test fun `the literal digit is still last`() {
        val e = engine()
        repeat(3) { e.learn("i") }
        e.reset(); e.onDigit('4')
        assertEquals(CandidateSource.LITERAL, e.composition().candidates.last().source)
        assertTrue(e.composition().candidates.size == 4)
    }
}

/** The left strip must work on a single press too — it was silently ignored there. */
class SinglePressPinTest {

    private lateinit var tmp: File

    private fun engine() = TrieEngine(object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path.endsWith(".txt")) ByteArrayInputStream("the\t1000\n".toByteArray()) else null
        override fun dataDir(): File = tmp
        override val languageTag = "en"
    }).apply { initialize(); startSession(EditorContext()) }

    @Before fun setUp() {
        tmp = File.createTempFile("t9pin1", "").apply { delete(); mkdirs() }
    }

    @Test fun `pinning a letter on a single press makes it first`() {
        val e = engine()
        e.onDigit('4')
        assertEquals("g", e.composition().composing)
        e.lockLetter("i")
        assertEquals("i", e.composition().composing)
        assertEquals("i", e.composition().candidates.first().text)
    }

    @Test fun `the pin beats a heavily used rival letter`() {
        val e = engine()
        repeat(50) { e.learn("g") }                       // g is the habitual choice
        e.reset(); e.onDigit('4')
        assertEquals("g", e.composition().composing)
        e.lockLetter("h")                                  // but this time the user wants h
        assertEquals("h", e.composition().composing)
    }

    @Test fun `committing after a pin commits the pinned letter`() {
        val e = engine()
        e.onDigit('4')
        e.lockLetter("i")
        assertEquals("i", e.commitInline())
    }

    @Test fun `the other letters remain available below the pin`() {
        val e = engine()
        e.onDigit('4')
        e.lockLetter("i")
        val texts = e.composition().candidates.map { it.text }
        assertEquals(listOf("i", "g", "h", "4"), texts)
    }

    @Test fun `a pin does not leak into the next word`() {
        val e = engine()
        e.onDigit('4'); e.lockLetter("i")
        e.reset()                                          // abandon, so nothing is learned
        e.onDigit('4')
        assertEquals("the pin must not survive the composition", "g", e.composition().composing)
    }

    @Test fun `committing a pinned letter teaches it, which is different from leaking`() {
        val e = engine()
        e.onDigit('4'); e.lockLetter("i"); e.commitInline()
        e.onDigit('4')
        // "i" is first now because it was USED, not because the pin persisted.
        assertEquals("i", e.composition().composing)
        assertEquals(CandidateSource.USER, e.composition().candidates.first().source)
    }
}
