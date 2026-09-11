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
 * Named for the reported bug: typing `test` (8378) offered `verte` (83783) first,
 * because `verte` is very common in Spanish while `test` is rare there — a completion
 * outranking an exact match.
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

    @Test fun `an exact match outranks a far heavier completion`() {
        val e = engine()
        type(e, "test")
        val first = e.composition().candidates.first()
        assertEquals("test", first.text)
        assertEquals(CandidateSource.DICTIONARY, first.source)
    }

    @Test fun `the completion is still offered, just not first`() {
        val e = engine()
        type(e, "test")
        val texts = e.composition().candidates.map { it.text }
        assertTrue("expected verte offered, got $texts", texts.contains("verte"))
        assertTrue(texts.indexOf("test") < texts.indexOf("verte"))
    }

    @Test fun `every exact match precedes every completion`() {
        val e = engine()
        type(e, "test")
        val sources = e.composition().candidates
            .filter { it.source != CandidateSource.LITERAL }
            .map { it.source }
        val lastExact = sources.indexOfLast { it == CandidateSource.DICTIONARY || it == CandidateSource.USER }
        val firstCompletion = sources.indexOfFirst { it == CandidateSource.COMPLETION }
        if (lastExact >= 0 && firstCompletion >= 0) {
            assertTrue("exact matches must all come first", lastExact < firstCompletion)
        }
    }

    // ---- inline text == what space commits ------------------------------------

    @Test fun `space commits the word shown inline`() {
        val e = engine()
        type(e, "test")
        val shown = e.composition().composing
        assertEquals("test", shown)
        assertEquals("the editor must not change under the user", shown, e.commitInline())
    }

    @Test fun `inline text and the first candidate agree`() {
        val e = engine()
        type(e, "test")
        val c = e.composition()
        assertEquals(c.composing, c.candidates.first().text)
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

    @Test fun `a phrase still outranks an exact word`() {
        val e = engine()
        e.userDictionary.addPhrase("test@example.com")
        type(e, "test")
        assertEquals("test@example.com", e.composition().candidates.first().source
            .let { _ -> e.composition().candidates.first().text })
    }

    @Test fun `the word being edited still outranks everything`() {
        val e = engine()
        e.userDictionary.addPhrase("test@example.com")
        val resumed = e.resumeEditing("test")!!
        assertEquals("test", resumed.candidates.first().text)
        assertEquals(CandidateSource.EDITING, resumed.candidates.first().source)
    }

    @Test fun `weight can never promote a candidate into the tier above`() {
        val e = engine()
        type(e, "test")
        val exact = e.composition().candidates.first { it.source == CandidateSource.DICTIONARY }
        val completion = e.composition().candidates.firstOrNull { it.source == CandidateSource.COMPLETION }
        assertNotNull(completion)
        assertTrue("exact ${exact.score} must beat completion ${completion!!.score}",
            exact.score > completion.score)
    }
}
