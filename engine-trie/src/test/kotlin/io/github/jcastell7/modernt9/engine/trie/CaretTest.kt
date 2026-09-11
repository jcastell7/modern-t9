package io.github.jcastell7.modernt9.engine.trie

import io.github.jcastell7.modernt9.engine.EditorContext
import io.github.jcastell7.modernt9.engine.EngineResources
import io.github.jcastell7.modernt9.engine.Keypad
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Editing a word from the middle. Keys must insert at the caret and backspace must
 * delete just before it, rather than both acting on the end of the word.
 */
class CaretTest {

    private lateinit var tmp: File

    private val dictionary = """
        gone	800
        good	950
        home	850
        the	1000
    """.trimIndent()

    private fun engine() = TrieEngine(object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path.endsWith(".txt")) ByteArrayInputStream(dictionary.toByteArray()) else null
        override fun dataDir(): File = tmp
        override val languageTag = "en"
    }).apply { initialize(); startSession(EditorContext()) }

    @Before fun setUp() {
        tmp = File.createTempFile("t9caret", "").apply { delete(); mkdirs() }
    }

    @Test fun `typing appends at the end by default`() {
        val e = engine()
        "466".forEach { e.onDigit(it) }
        assertEquals("466", e.composition().digits)
        assertEquals(3, e.composition().cursor)
    }

    @Test fun `resuming places the caret where the user tapped`() {
        val e = engine()
        val c = e.resumeEditing("gone", caretOffset = 2)!!   // go|ne
        assertEquals(Keypad.encode("gone"), c.digits)
        assertEquals(2, c.cursor)
    }

    @Test fun `resuming defaults the caret to the end`() {
        val e = engine()
        val c = e.resumeEditing("gone")!!
        assertEquals(4, c.cursor)
    }

    @Test fun `a key inserts at the caret, not at the end`() {
        val e = engine()
        e.resumeEditing("gone", caretOffset = 2)             // go|ne
        e.onDigit('4')                                       // go4|ne
        val digits = e.composition().digits
        val expected = Keypad.encode("gone")!!.let { it.take(2) + "4" + it.drop(2) }
        assertEquals(expected, digits)
        assertEquals(3, e.composition().cursor)
    }

    @Test fun `backspace deletes just before the caret`() {
        val e = engine()
        e.resumeEditing("gone", caretOffset = 2)             // go|ne
        e.onBackspace()                                      // g|ne
        val encoded = Keypad.encode("gone")!!
        assertEquals(encoded.take(1) + encoded.drop(2), e.composition().digits)
        assertEquals(1, e.composition().cursor)
    }

    @Test fun `backspace at the start of a word does nothing`() {
        val e = engine()
        e.resumeEditing("gone", caretOffset = 0)
        val before = e.composition().digits
        e.onBackspace()
        assertEquals("nothing to delete before the caret", before, e.composition().digits)
        assertEquals(0, e.composition().cursor)
    }

    @Test fun `backspace at the end still deletes the last key`() {
        val e = engine()
        e.resumeEditing("gone")                              // gone|
        e.onBackspace()
        assertEquals(3, e.composition().digits.length)
        assertEquals(3, e.composition().cursor)
    }

    @Test fun `the caret never exceeds the composition`() {
        val e = engine()
        val c = e.resumeEditing("gone", caretOffset = 99)!!
        assertEquals(4, c.cursor)
        assertTrue(c.cursor <= c.composing.length)
    }

    @Test fun `a negative caret is clamped to the start`() {
        val e = engine()
        assertEquals(0, e.resumeEditing("gone", caretOffset = -5)!!.cursor)
    }

    @Test fun `reset clears the caret`() {
        val e = engine()
        e.resumeEditing("gone", caretOffset = 2)
        e.reset()
        assertEquals(0, e.composition().cursor)
    }

    @Test fun `starting a session clears the caret`() {
        val e = engine()
        e.resumeEditing("gone", caretOffset = 2)
        e.startSession(EditorContext())
        e.onDigit('4')
        assertEquals(1, e.composition().cursor)
    }

    @Test fun `mid-word edits still produce candidates`() {
        val e = engine()
        e.resumeEditing("gone", caretOffset = 2)
        e.onBackspace()                                      // now 3 digits
        assertNotNull(e.composition().candidates.firstOrNull())
        assertEquals(3, e.composition().composing.length)
    }
}
