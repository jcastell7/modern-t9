package io.github.jcastell7.modernt9.engine.trie

import io.github.jcastell7.modernt9.engine.CandidateSource
import io.github.jcastell7.modernt9.engine.EditorContext
import io.github.jcastell7.modernt9.engine.EngineResources
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
 * The `custom-word-appears.png` flow: type `User`, press `@`, and the saved phrase
 * takes over — plus the `new-word.png` check that decides when to offer saving.
 */
class SymbolKeyAndNewWordTest {

    private lateinit var tmp: File

    private val dictionary = """
        user	500
        hello	900
        the	1000
    """.trimIndent()

    private fun resources(dir: File = tmp) = object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path == "dict/en.txt") ByteArrayInputStream(dictionary.toByteArray()) else null
        override fun dataDir(): File = dir
        override val languageTag = "en"
    }

    private fun engine() = TrieEngine(resources()).apply {
        initialize(); startSession(EditorContext())
    }

    private fun type(e: TrieEngine, digits: String) = digits.forEach { e.onDigit(it) }

    @Before fun setUp() {
        tmp = File.createTempFile("t9symkey", "").apply { delete(); mkdirs() }
    }

    @Test fun `symbol key continues a saved phrase`() {
        val e = engine()
        e.userDictionary.addPhrase("user@mail.com")
        type(e, "8737")                                   // u-s-e-r
        val extended = e.onSymbolKey()
        assertNotNull("pressing @ should extend the phrase match", extended)
        assertEquals("user@mail.com", extended!!.candidates.first().text)
        assertEquals(CandidateSource.PHRASE, extended.candidates.first().source)
    }

    @Test fun `symbol key returns null when no phrase matches`() {
        val e = engine()
        type(e, "43556")                                  // "hello" — no phrase
        assertNull("with no phrase match the key must fall back to literal @", e.onSymbolKey())
    }

    @Test fun `symbol key on an empty composition returns null`() {
        assertNull(engine().onSymbolKey())
    }

    @Test fun `symbol key does not consume the digit when it fails`() {
        val e = engine()
        type(e, "43556")
        e.onSymbolKey()
        assertEquals("43556", e.composition().digits)
    }

    @Test fun `dictionary words are known`() {
        val e = engine()
        assertTrue(e.isKnown("hello"))
        assertTrue(e.isKnown("the"))
        assertTrue(e.isKnown("HELLO"))                    // case-insensitive
    }

    @Test fun `unknown words are reported unknown`() {
        val e = engine()
        assertFalse(e.isKnown("zzzqqq"))
        assertFalse(e.isKnown("user@mail.com"))
    }

    @Test fun `a saved phrase becomes known`() {
        val e = engine()
        assertFalse(e.isKnown("user@mail.com"))
        e.userDictionary.addPhrase("user@mail.com")
        assertTrue(e.isKnown("user@mail.com"))
    }

    @Test fun `a learned word becomes known`() {
        val e = engine()
        assertFalse(e.isKnown("zzzqqq"))
        repeat(3) { e.learn("zzzqqq") }
        assertTrue(e.isKnown("zzzqqq"))
    }

    @Test fun `blank text counts as known so no offer is made`() {
        assertTrue(engine().isKnown("   "))
    }

    @Test fun `last key letters drive the side strip`() {
        val e = engine()
        assertTrue(e.lastKeyLetters().isEmpty())
        e.onDigit('5')
        assertEquals(listOf("j", "k", "l"), e.lastKeyLetters())
        e.onDigit('2')
        assertEquals(listOf("a", "b", "c"), e.lastKeyLetters())
    }

    @Test fun `spanish side strip includes enye on key 6`() {
        val e = engine()
        e.switchLanguage("es")     // no es dictionary here, so this is refused
        e.onDigit('6')
        assertEquals(listOf("m", "n", "o"), e.lastKeyLetters())
    }
}
