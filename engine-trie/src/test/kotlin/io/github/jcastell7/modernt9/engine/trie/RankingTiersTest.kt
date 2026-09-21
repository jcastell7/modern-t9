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
 * Rank ordering, and the invariant that what the editor shows is what space commits.
 *
 * Named for the reported bug: typing `test` (8378) turned into `verte` (83783) on
 * space, because `verte` is very common in Spanish while `test` is rare there. The
 * strip may well list `verte` first — it is the likelier word — but the inline text
 * is always a word of the typed length, and space commits that and nothing else.
 */
class RankingTiersTest {

    private lateinit var tmp: File

    // Deliberately lopsided: the completion is 100x the weight of the exact match.
    private val dictionary = """
        test	500
        verte	50000
        verde	48000
        the	90000
    """.trimIndent()

    private fun engine() = TrieEngine(object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path.endsWith(".txt")) ByteArrayInputStream(dictionary.toByteArray()) else null
        override fun dataDir(): File = tmp
        override val languageTag = "en"
    }).apply { initialize(); startSession(EditorContext()) }

    private fun type(e: TrieEngine, word: String) =
        Keypad.encode(word)!!.forEach { e.onDigit(it) }

    @Before fun setUp() {
        tmp = File.createTempFile("t9rank", "").apply { delete(); mkdirs() }
    }

    @Test fun `test and verte really do share a prefix`() {
        assertEquals("8378", Keypad.encode("test"))
        assertEquals("83783", Keypad.encode("verte"))
        assertTrue(Keypad.encode("verte")!!.startsWith(Keypad.encode("test")!!))
    }

    @Test fun `the strip is ordered by likelihood, not by length`() {
        val e = engine()
        type(e, "test")
        val texts = e.composition().candidates.map { it.text }
        assertTrue("expected verte offered, got $texts", texts.contains("verte"))
        assertTrue("both offered: $texts", texts.contains("test"))
        // 100x the weight, even halved for being a guess, puts the completion first.
        assertTrue("verte is far likelier: $texts", texts.indexOf("verte") < texts.indexOf("test"))
    }

    @Test fun `the exact match still leads when it is the likelier word`() {
        val e = engine()
        type(e, "the")                                  // 843 — "the" 90000, no rival
        assertEquals("the", e.composition().candidates.first().text)
        assertEquals(CandidateSource.DICTIONARY, e.composition().candidates.first().source)
    }

    @Test fun `a completion is worth half its weight, an exact match all of it`() {
        val e = engine()
        type(e, "ver")                                  // 837: verde and verte complete it
        val verde = e.composition().candidates.first { it.text == "verde" }
        val verte = e.composition().candidates.first { it.text == "verte" }
        assertTrue(verte.score > verde.score)
        type(e, "de")                                   // 83733: verde exactly
        val exact = e.composition().candidates.first { it.text == "verde" }
        assertTrue("exact scores its full weight", exact.score > verde.score)
    }

    // ---- inline text == what space commits ------------------------------------

    @Test fun `space commits the word shown inline`() {
        val e = engine()
        type(e, "test")
        val shown = e.composition().composing
        assertEquals("test", shown)
        assertEquals("the editor must not change under the user", shown, e.commitInline())
    }

    @Test fun `inline text is the best exact-length candidate, wherever it sits`() {
        val e = engine()
        type(e, "test")
        val c = e.composition()
        assertEquals("test", c.composing)
        assertTrue(c.candidates.first { it.isExactLength }.text == c.composing)
    }

    @Test fun `with no exact match space commits the plain letters`() {
        val e = engine()
        type(e, "verd")                                  // 8373 — no 4-letter word here
        val shown = e.composition().composing
        assertEquals(4, shown.length)
        assertEquals(shown, e.commitInline())
    }

    @Test fun `commitInline clears the composition`() {
        val e = engine()
        type(e, "test")
        e.commitInline()
        assertTrue(e.composition().isEmpty)
    }

    @Test fun `commitInline on an empty composition returns null`() {
        assertNull(engine().commitInline())
    }

    @Test fun `a learned word still outranks its dictionary rivals`() {
        val e = engine()
        repeat(30) { e.learn("test") }
        e.reset()
        type(e, "test")
        assertEquals("test", e.composition().candidates.first().text)
        assertEquals(CandidateSource.USER, e.composition().candidates.first().source)
    }

    @Test fun `a fresh phrase ranks like a word used once, not above everything`() {
        val e = engine()
        e.userDictionary.addPhrase("test@example.com")
        type(e, "test")
        val texts = e.composition().candidates.map { it.text }
        // verte (50000, halved) beats it; test (500) does not.
        assertTrue("$texts", texts.indexOf("verte") < texts.indexOf("test@example.com"))
        assertTrue("$texts", texts.indexOf("test@example.com") < texts.indexOf("test"))
    }

    @Test fun `a phrase climbs as it is chosen`() {
        val e = engine()
        e.userDictionary.addPhrase("test@example.com")
        repeat(6) {
            e.reset(); type(e, "test")
            e.selectCandidate(e.composition().candidates.indexOfFirst { it.text == "test@example.com" })
        }
        e.reset(); type(e, "test")
        assertEquals("test@example.com", e.composition().candidates.first().text)
    }

    @Test fun `the word being edited still outranks everything`() {
        val e = engine()
        e.userDictionary.addPhrase("test@example.com")
        val resumed = e.resumeEditing("test")!!
        assertEquals("test", resumed.candidates.first().text)
        assertEquals(CandidateSource.EDITING, resumed.candidates.first().source)
    }

    @Test fun `use can never promote a word above the one being edited`() {
        val e = engine()
        repeat(500) { e.learn("verte") }                 // saturates the usage bonus
        e.reset()
        val resumed = e.resumeEditing("Test")!!          // as it stands in the document
        val editing = resumed.candidates.first { it.source == CandidateSource.EDITING }
        val word = resumed.candidates.first { it.text == "verte" }
        assertNotNull(word)
        assertTrue("editing ${editing.score} must beat word ${word.score}", editing.score > word.score)
    }
}
