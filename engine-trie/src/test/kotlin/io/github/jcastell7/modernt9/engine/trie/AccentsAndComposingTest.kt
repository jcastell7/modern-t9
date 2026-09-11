package io.github.jcastell7.modernt9.engine.trie

import io.github.jcastell7.modernt9.engine.CandidateSource
import io.github.jcastell7.modernt9.engine.EditorContext
import io.github.jcastell7.modernt9.engine.EngineResources
import io.github.jcastell7.modernt9.engine.Keypad
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Accented Spanish must be *offered* with its accents, while still being typed on plain
 * keys — and one keypress must not put a whole predicted word in the editor.
 */
class AccentsAndComposingTest {

    private lateinit var tmp: File

    // Accented spellings, exactly as the dictionary builder now emits them.
    private val spanish = """
        mañana	900
        señor	800
        años	700
        más	1000
        niño	600
        mano	500
        nube	400
    """.trimIndent()

    private fun resources(dict: String = spanish) = object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path.endsWith("es.txt") || path.endsWith("en.txt"))
                ByteArrayInputStream(dict.toByteArray()) else null
        override fun dataDir(): File = tmp
        override val languageTag = "es"
    }

    private fun engine(dict: String = spanish) = TrieEngine(resources(dict)).apply {
        initialize(); startSession(EditorContext())
    }

    private fun type(e: TrieEngine, digits: String) = digits.forEach { e.onDigit(it) }

    @Before fun setUp() {
        tmp = File.createTempFile("t9accents", "").apply { delete(); mkdirs() }
    }

    // ---- accents preserved ----------------------------------------------------

    @Test fun `an accented word is offered with its accents`() {
        val e = engine()
        type(e, Keypad.encode("manana")!!)          // 6262626
        val words = e.composition().candidates.map { it.text }
        assertTrue("expected mañana in $words", words.contains("mañana"))
        assertFalse("the folded spelling must not be offered", words.contains("manana"))
    }

    @Test fun `enye words are reachable by typing plain letters`() {
        val e = engine()
        assertEquals(Keypad.encode("senor"), Keypad.encode("señor"))
        type(e, Keypad.encode("senor")!!)
        assertEquals("señor", e.composition().candidates.first().text)
    }

    @Test fun `accented vowels are preserved`() {
        val e = engine()
        type(e, Keypad.encode("mas")!!)
        assertEquals("más", e.composition().candidates.first().text)
    }

    @Test fun `an accented word can be learned and comes back accented`() {
        val e = engine()
        repeat(20) { e.learn("años") }
        assertTrue(e.userDictionary.contains("años"))
        e.reset()
        type(e, Keypad.encode("anos")!!)
        assertEquals("años", e.composition().candidates.first().text)
    }

    @Test fun `isKnown accepts either spelling`() {
        val e = engine()
        assertTrue(e.isKnown("mañana"))
        assertTrue(e.isKnown("MAÑANA"))
    }

    // ---- one key, one letter --------------------------------------------------

    @Test fun `a single keypress composes a single letter`() {
        val e = engine()
        e.onDigit('6')
        assertEquals("one key must not put a whole word inline", 1, e.composition().composing.length)
    }

    @Test fun `composing never shows a longer completion`() {
        val e = engine()
        type(e, "62")                                // prefix of "mano", "mañana"…
        val composing = e.composition().composing
        assertEquals("composing must match the digits typed", 2, composing.length)
    }

    @Test fun `composing uses an exact-length word when one exists`() {
        val e = engine()
        type(e, Keypad.encode("mano")!!)             // 6266
        assertEquals("mano", e.composition().composing)
    }

    @Test fun `composing falls back to plain letters when nothing matches`() {
        val e = engine()
        type(e, "7777")
        assertEquals("pppp", e.composition().composing)
    }

    // ---- side strip -----------------------------------------------------------

    @Test fun `the side strip lists the key's own letters`() {
        val e = engine()
        e.onDigit('6')
        val strip = e.lastKeyLetters()
        assertTrue(strip.containsAll(listOf("m", "n", "o")))
    }

    @Test fun `the side strip surfaces accented letters that real words use`() {
        val e = engine()
        type(e, "626")                               // m-a-n… of "mañana"
        val strip = e.lastKeyLetters()
        assertTrue("expected ñ offered, got $strip", strip.contains("ñ"))
    }

    @Test fun `the side strip does not repeat the base letters`() {
        val e = engine()
        e.onDigit('6')
        val strip = e.lastKeyLetters()
        assertEquals("no duplicates", strip.size, strip.toSet().size)
    }

    @Test fun `the side strip is empty before anything is typed`() {
        assertTrue(engine().lastKeyLetters().isEmpty())
    }

    @Test fun `the literal digits are still always offered last`() {
        val e = engine()
        type(e, "9999")
        assertEquals(CandidateSource.LITERAL, e.composition().candidates.last().source)
    }

    @Test fun `an accented phrase keeps its case and accents`() {
        val e = engine()
        e.userDictionary.addPhrase("José@correo.mx")
        type(e, "5673")                              // j-o-s-e
        assertNotNull(e.composition().candidates.firstOrNull { it.text == "José@correo.mx" })
    }
}
