package io.github.jcastell7.modernt9.engine.trie

import io.github.jcastell7.modernt9.engine.EditorContext
import io.github.jcastell7.modernt9.engine.EngineResources
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

/** Letter pinning from the left strip, and apostrophe-insensitive lookup. */
class LocksAndApostropheTest {

    private lateinit var tmp: File

    private val dictionary = """
        don't	9000
        dont	10
        won't	8000
        good	950
        gone	800
        home	850
        hood	400
    """.trimIndent()

    private fun engine() = TrieEngine(object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path.endsWith(".txt")) ByteArrayInputStream(dictionary.toByteArray()) else null
        override fun dataDir(): File = tmp
        override val languageTag = "en"
    }).apply { initialize(); startSession(EditorContext()) }

    private fun type(e: TrieEngine, digits: String) = digits.forEach { e.onDigit(it) }

    @Before fun setUp() {
        tmp = File.createTempFile("t9lock", "").apply { delete(); mkdirs() }
    }

    // ---- apostrophes ----------------------------------------------------------

    @Test fun `an apostrophe is silent when encoding`() {
        assertEquals(Keypad.encode("dont"), Keypad.encode("don't"))
        assertEquals("3668", Keypad.encode("don't"))
        assertEquals(Keypad.encode("wont"), Keypad.encode("won't"))
    }

    @Test fun `typing dont offers the contraction`() {
        val e = engine()
        type(e, "3668")
        val words = e.composition().candidates.map { it.text }
        assertTrue("expected don't offered, got $words", words.contains("don't"))
    }

    @Test fun `the contraction outranks the bare spelling`() {
        val e = engine()
        type(e, "3668")
        assertEquals("don't", e.composition().candidates.first().text)
    }

    @Test fun `curly apostrophes encode the same`() {
        assertEquals(Keypad.encode("don't"), Keypad.encode("don’t"))
    }

    // ---- letter pinning -------------------------------------------------------

    @Test fun `pinning a letter filters the candidates`() {
        val e = engine()
        type(e, "4663")                                   // good / gone / home / hood
        assertTrue(e.composition().candidates.any { it.text == "good" })
        // The strip offers the letters of the LAST key pressed — here the 3 (def).
        // Pinning "e" keeps only words with e in that position.
        e.lockLetter("e")
        val words = e.composition().candidates.map { it.text }
        assertTrue("expected home, got $words", words.contains("home"))
        assertTrue("good/hood end in d, got $words", words.none { it == "good" || it == "hood" })
    }

    @Test fun `pinning applies at the caret, not the end of the word`() {
        val e = engine()
        type(e, "4663")
        e.onBackspace()                                   // caret now after the third key
        e.lockLetter("n")                                 // that 6 meant "n"
        assertEquals('n', e.composition().composing[2])
    }

    @Test fun `pinning shows in the plain-letters fallback`() {
        val e = engine()
        type(e, "9999")                                   // no dictionary word
        e.lockLetter("z")
        assertEquals('z', e.composition().composing.last())
    }

    @Test fun `pinning with nothing composed is refused`() {
        assertNull(engine().lockLetter("a"))
    }

    @Test fun `a pin survives further typing`() {
        val e = engine()
        type(e, "466")
        e.lockLetter("n")                                 // the second 6 meant "n"
        type(e, "3")
        val words = e.composition().candidates.map { it.text }
        assertTrue("expected gone, got $words", words.contains("gone"))
        assertTrue("good has o there, got $words", words.none { it == "good" })
    }

    @Test fun `backspacing past a pin clears it`() {
        val e = engine()
        type(e, "4663")
        e.lockLetter("e")
        repeat(4) { e.onBackspace() }
        type(e, "4663")
        assertTrue("the pin should be gone", e.composition().candidates.any { it.text == "good" })
    }

    @Test fun `a pin is cleared when the word is committed`() {
        val e = engine()
        type(e, "4663")
        e.lockLetter("e")
        e.commitInline()
        type(e, "4663")
        assertTrue(e.composition().candidates.any { it.text == "good" })
    }

    @Test fun `pinning returns an updated composition`() {
        val e = engine()
        type(e, "4663")
        assertNotNull(e.lockLetter("e"))
    }
}
