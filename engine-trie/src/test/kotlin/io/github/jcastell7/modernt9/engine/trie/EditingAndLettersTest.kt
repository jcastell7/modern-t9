package io.github.jcastell7.modernt9.engine.trie

import io.github.jcastell7.modernt9.engine.CandidateSource
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

/**
 * Correcting a word already in the document, and the letters-first behaviour of a
 * single keypress.
 */
class EditingAndLettersTest {

    private lateinit var tmp: File

    private val english = """
        good	950
        gone	800
        home	850
        hood	400
        the	1000
    """.trimIndent()

    private val spanish = """
        mañana	900
        mano	500
    """.trimIndent()

    private fun resources(active: String = "en") = object : EngineResources {
        override fun openAsset(path: String): InputStream? = when {
            path.endsWith("en.txt") -> ByteArrayInputStream(english.toByteArray())
            path.endsWith("es.txt") -> ByteArrayInputStream(spanish.toByteArray())
            else -> null
        }
        override fun dataDir(): File = tmp
        override val languageTag = active
    }

    private fun engine(active: String = "en") = TrieEngine(resources(active)).apply {
        initialize(); startSession(EditorContext())
    }

    @Before fun setUp() {
        tmp = File.createTempFile("t9edit", "").apply { delete(); mkdirs() }
    }

    // ---- re-editing -----------------------------------------------------------

    @Test fun `resuming a word rebuilds its digit sequence`() {
        val e = engine()
        val resumed = e.resumeEditing("gone")
        assertNotNull(resumed)
        assertEquals(Keypad.encode("gone"), resumed!!.digits)
    }

    @Test fun `the word being edited is offered first`() {
        val e = engine()
        // "good" outranks "gone" normally…
        "4663".forEach { e.onDigit(it) }
        assertEquals("good", e.composition().candidates.first().text)
        // …but not while "gone" is the word under the caret.
        e.reset()
        val resumed = e.resumeEditing("gone")!!
        assertEquals("gone", resumed.candidates.first().text)
        assertEquals(CandidateSource.EDITING, resumed.candidates.first().source)
    }

    @Test fun `the alternatives are still offered while editing`() {
        val e = engine()
        val words = e.resumeEditing("gone")!!.candidates.map { it.text }
        assertTrue("expected alternatives in $words", words.containsAll(listOf("good", "home")))
    }

    @Test fun `a word that cannot be typed is refused`() {
        assertNull(engine().resumeEditing("juan@mail.com"))
        assertNull(engine().resumeEditing(""))
    }

    @Test fun `typing a new key ends the correction`() {
        val e = engine()
        e.resumeEditing("gone")
        e.reset()
        e.onDigit('4')
        assertTrue(e.composition().candidates.none { it.source == CandidateSource.EDITING })
    }

    @Test fun `choosing a candidate ends the correction`() {
        val e = engine()
        e.resumeEditing("gone")
        assertEquals("gone", e.selectCandidate(0))
        assertTrue(e.composition().isEmpty)
    }

    @Test fun `an accented word can be re-edited`() {
        val e = engine("es")
        val resumed = e.resumeEditing("mañana")
        assertNotNull(resumed)
        assertEquals("mañana", resumed!!.candidates.first().text)
    }

    // ---- one press, letters first ---------------------------------------------

    @Test fun `a single press offers the key's letters first`() {
        val e = engine()
        e.onDigit('4')
        val top = e.composition().candidates.take(3).map { it.text }
        assertEquals(listOf("g", "h", "i"), top)
    }

    @Test fun `letters are marked as such`() {
        val e = engine()
        e.onDigit('4')
        assertEquals(CandidateSource.LETTER, e.composition().candidates.first().source)
    }

    @Test fun `a single press offers no words at all`() {
        val e = engine()
        e.onDigit('8')                                 // "the" starts here
        val words = e.composition().candidates.map { it.text }
        // Committing a whole word off one tap is a guess too far.
        assertTrue("no words on one press, got $words", !words.contains("the"))
        assertEquals(listOf("t", "u", "v", "8"), words)
    }

    @Test fun `two presses do offer words`() {
        val e = engine()
        "84".forEach { e.onDigit(it) }                 // t-h of "the"
        assertTrue(e.composition().candidates.any { it.text == "the" })
    }

    @Test fun `the editing pin drops once the digits change`() {
        val e = engine()
        e.resumeEditing("gone")
        e.onDigit('4')                                 // now 5 digits, not "gone"
        val candidates = e.composition().candidates
        assertTrue(candidates.none { it.source == CandidateSource.EDITING })
        // …and the inline text must follow the new digits, not the old word.
        assertTrue(e.composition().composing != "gone")
    }

    @Test fun `backspacing while editing shortens the composition`() {
        val e = engine()
        e.resumeEditing("gone")
        assertEquals(4, e.composition().digits.length)
        e.onBackspace()
        assertEquals(3, e.composition().digits.length)
        assertTrue(e.composition().composing.length == 3)
    }

    @Test fun `letters appear only for a single press`() {
        val e = engine()
        "46".forEach { e.onDigit(it) }
        assertTrue(e.composition().candidates.none { it.source == CandidateSource.LETTER })
    }

    @Test fun `spanish shows enye among the letters of key 6`() {
        val e = engine("es")
        e.onDigit('6')
        val letters = e.composition().candidates
            .filter { it.source == CandidateSource.LETTER }
            .map { it.text }
        assertEquals(listOf("m", "n", "o", "ñ"), letters)
    }

    // ---- lazy language loading ------------------------------------------------

    @Test fun `both languages are parsed at startup`() {
        val e = engine("en")
        assertEquals("en", e.activeLanguage)
        assertTrue(e.availableLanguages.containsAll(listOf("en", "es")))
    }

    @Test fun `switching is immediate`() {
        val e = engine("en")
        assertTrue(e.switchLanguage("es"))
        assertEquals("es", e.activeLanguage)
        Keypad.encode("mano")!!.forEach { e.onDigit(it) }
        assertTrue(e.composition().candidates.any { it.text == "mano" })
    }

    @Test fun `learned words reach a lazily loaded language`() {
        val e = engine("en")
        repeat(20) { e.learn("gone") }
        e.switchLanguage("es")
        Keypad.encode("gone")!!.forEach { e.onDigit(it) }
        assertTrue(e.composition().candidates.any { it.text == "gone" })
    }
}

/** Ordering guarantees for the word already present in the document. */
class EditingOrderTest {

    private lateinit var tmp: File

    private val dictionary = """
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

    @Before fun setUp() {
        tmp = File.createTempFile("t9order", "").apply { delete(); mkdirs() }
    }

    @Test fun `the word in the document is first, whatever its natural rank`() {
        listOf("gone", "home", "hood", "good").forEach { word ->
            val e = engine()
            val first = e.resumeEditing(word)!!.candidates.first()
            assertEquals("re-editing $word", word, first.text)
            assertEquals(CandidateSource.EDITING, first.source)
        }
    }

    @Test fun `its capitalisation is preserved`() {
        val e = engine()
        val first = e.resumeEditing("Gone")!!.candidates.first()
        assertEquals("Gone", first.text)
    }

    @Test fun `it is not listed twice`() {
        val e = engine()
        val texts = e.resumeEditing("Gone")!!.candidates.map { it.text }
        assertEquals("no lower-case duplicate", 0, texts.count { it == "gone" })
        assertEquals(1, texts.count { it.equals("gone", ignoreCase = true) })
    }

    @Test fun `it outranks a user phrase on the same keys`() {
        val e = engine()
        e.userDictionary.addPhrase("gone@example.com")
        assertEquals("gone", e.resumeEditing("gone")!!.candidates.first().text)
    }

    @Test fun `re-editing a different word repins correctly`() {
        val e = engine()
        assertEquals("gone", e.resumeEditing("gone")!!.candidates.first().text)
        assertEquals("home", e.resumeEditing("home")!!.candidates.first().text)
        assertEquals("good", e.resumeEditing("good")!!.candidates.first().text)
    }
}
