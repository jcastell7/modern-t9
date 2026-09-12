package io.github.jcastell7.modernt9.engine.trie

import io.github.jcastell7.modernt9.engine.EditorContext
import io.github.jcastell7.modernt9.engine.EngineResources
import io.github.jcastell7.modernt9.engine.Keypad
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Bilingual mode: both dictionaries offer candidates at once. */
class BilingualTest {

    private lateinit var tmp: File

    private val english = "hello\t900\nthe\t1000\nsole\t100\n"
    // "hola" is 4652 — a sequence with no English word. "sol" is in both.
    private val spanish = "hola\t900\nsol\t800\ngracias\t700\n"

    private fun engine(active: String = "en") = TrieEngine(object : EngineResources {
        override fun openAsset(path: String): InputStream? = when {
            path.endsWith("en.txt") -> ByteArrayInputStream(english.toByteArray())
            path.endsWith("es.txt") -> ByteArrayInputStream(spanish.toByteArray())
            else -> null
        }
        override fun dataDir(): File = tmp
        override val languageTag = active
    }).apply { initialize(); startSession(EditorContext()) }

    private fun words(e: TrieEngine, word: String): List<String> {
        e.reset()
        Keypad.encode(word)!!.forEach { e.onDigit(it) }
        return e.composition().candidates.map { it.text }
    }

    @Before fun setUp() {
        tmp = File.createTempFile("t9bi", "").apply { delete(); mkdirs() }
    }

    @Test fun `a single language offers only its own words`() {
        val e = engine("en")
        assertFalse(words(e, "hola").contains("hola"))
    }

    @Test fun `bilingual mode is selected with a plus`() {
        val e = engine("en")
        assertTrue(e.switchLanguage("en+es"))
        assertEquals("en+es", e.activeLanguage)
    }

    @Test fun `bilingual mode offers words from both dictionaries`() {
        val e = engine("en")
        e.switchLanguage("en+es")
        assertTrue(words(e, "hola").contains("hola"))       // Spanish
        assertTrue(words(e, "hello").contains("hello"))     // English
    }

    @Test fun `a word in both dictionaries appears once`() {
        val e = engine("en")
        e.switchLanguage("en+es")
        val list = words(e, "sol")
        assertEquals(1, list.count { it == "sol" })
    }

    @Test fun `a shared word keeps its higher weight`() {
        val e = engine("en")
        e.switchLanguage("en+es")
        // "sol" is 800 in Spanish and absent in English; "sole" (100) is a completion.
        val list = words(e, "sol")
        assertTrue(list.indexOf("sol") < list.indexOf("sole"))
    }

    @Test fun `switching back to one language drops the other`() {
        val e = engine("en")
        e.switchLanguage("en+es")
        e.switchLanguage("en")
        assertEquals("en", e.activeLanguage)
        assertFalse(words(e, "hola").contains("hola"))
    }

    @Test fun `bilingual hints include the enye`() {
        val e = engine("en")
        e.switchLanguage("en+es")
        e.reset(); e.onDigit('6')
        assertTrue(e.lastKeyLetters().contains("ñ"))
    }

    @Test fun `isKnown consults every active dictionary`() {
        val e = engine("en")
        assertFalse(e.isKnown("gracias"))
        e.switchLanguage("en+es")
        assertTrue(e.isKnown("gracias"))
        assertTrue(e.isKnown("hello"))
    }

    @Test fun `an unknown tag in the set is refused`() {
        val e = engine("en")
        assertFalse(e.switchLanguage("en+de"))
        assertEquals("en", e.activeLanguage)
    }

    @Test fun `order and whitespace in the tag are tolerated`() {
        val e = engine("en")
        assertTrue(e.switchLanguage(" es + en "))
        assertTrue(words(e, "hola").contains("hola"))
    }
}
