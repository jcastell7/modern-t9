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
 * Phrases — email addresses, URLs, handles. The things no baseline dictionary contains
 * and that a T9 keyboard is otherwise miserable at typing.
 */
class PhraseTest {

    private lateinit var tmp: File

    private val dictionary = """
        juan	500
        just	900
        hello	900
    """.trimIndent()

    private fun resources(dir: File = tmp) = object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path == "dict/en.txt") ByteArrayInputStream(dictionary.toByteArray()) else null
        override fun dataDir(): File = dir
        override val languageTag = "en"
    }

    private fun engine(res: EngineResources = resources()) =
        TrieEngine(res).apply { initialize(); startSession(EditorContext()) }

    @Before fun setUp() {
        tmp = File.createTempFile("t9phrase", "").apply { delete(); mkdirs() }
    }

    private fun type(e: TrieEngine, digits: String) = digits.forEach { e.onDigit(it) }

    @Test fun `a phrase is offered from its letter prefix`() {
        val e = engine()
        e.userDictionary.addPhrase("user@gmail.com")
        type(e, "5826")                                   // j-u-a-n
        val top = e.composition().candidates.first()
        assertEquals("user@gmail.com", top.text)
        assertEquals(CandidateSource.PHRASE, top.source)
    }

    @Test fun `a phrase outranks a dictionary word on the same prefix`() {
        val e = engine()
        e.userDictionary.addPhrase("user@gmail.com")
        type(e, "5826")
        // "juan" is in the dictionary at weight 500; the phrase must still win.
        assertEquals("user@gmail.com", e.composition().candidates.first().text)
        assertNotNull(e.composition().candidates.firstOrNull { it.text == "juan" })
    }

    @Test fun `a url is offered from its prefix`() {
        val e = engine()
        e.userDictionary.addPhrase("https://example.com/docs")
        type(e, "4887")                                   // h-t-t-p
        assertEquals("https://example.com/docs", e.composition().candidates.first().text)
    }

    @Test fun `case is preserved for phrases`() {
        val e = engine()
        e.userDictionary.addPhrase("Juan.Castellanos@Work.com")
        type(e, "5826")
        assertEquals("Juan.Castellanos@Work.com", e.composition().candidates.first().text)
    }

    @Test fun `a phrase containing digits is stored and offered`() {
        val e = engine()
        e.userDictionary.addPhrase("juan2024@mail.com")
        type(e, "5826")
        assertTrue(e.composition().candidates.any { it.text == "juan2024@mail.com" })
    }

    @Test fun `selecting a phrase returns it verbatim`() {
        val e = engine()
        e.userDictionary.addPhrase("user@gmail.com")
        type(e, "5826")
        assertEquals("user@gmail.com", e.selectCandidate(0))
        assertTrue(e.composition().isEmpty)
    }

    @Test fun `a used phrase is reinforced, not learned as a word`() {
        val e = engine()
        e.userDictionary.addPhrase("user@gmail.com")
        type(e, "5826")
        e.selectCandidate(0)
        // It must not have leaked into the ordinary learned-word store.
        assertFalse(e.userDictionary.contains("user@gmail.com"))
        assertTrue(e.userDictionary.phrases().any { it.word == "user@gmail.com" })
    }

    @Test fun `phrases list is ordered by weight`() {
        val e = engine()
        e.userDictionary.addPhrase("low@x.com", 10)
        e.userDictionary.addPhrase("high@x.com", 9000)
        assertEquals("high@x.com", e.userDictionary.phrases().first().word)
    }

    @Test fun `removing a phrase stops it being offered`() {
        val e = engine()
        e.userDictionary.addPhrase("user@gmail.com")
        type(e, "5826")
        assertTrue(e.composition().candidates.any { it.source == CandidateSource.PHRASE })

        e.reset()
        assertTrue(e.userDictionary.removePhrase("user@gmail.com"))
        type(e, "5826")
        assertTrue(e.composition().candidates.none { it.source == CandidateSource.PHRASE })
    }

    @Test fun `removing an absent phrase reports false`() {
        assertFalse(engine().userDictionary.removePhrase("nope@x.com"))
    }

    @Test fun `removing one phrase keeps the others`() {
        val e = engine()
        e.userDictionary.addPhrase("user@gmail.com")
        e.userDictionary.addPhrase("user@work.com")
        e.userDictionary.removePhrase("user@gmail.com")
        type(e, "5826")
        val phrases = e.composition().candidates.filter { it.source == CandidateSource.PHRASE }
        assertEquals(1, phrases.size)
        assertEquals("user@work.com", phrases.first().text)
    }

    @Test fun `phrases survive a restart`() {
        engine().apply {
            userDictionary.addPhrase("user@gmail.com")
            endSession()
            close()
        }
        val revived = engine()
        type(revived, "5826")
        assertEquals("user@gmail.com", revived.composition().candidates.first().text)
    }

    @Test fun `phrases are language independent`() {
        val e = engine()
        e.userDictionary.addPhrase("user@gmail.com")
        e.switchLanguage("es")     // refused here (no es dictionary), but must not lose it
        type(e, "5826")
        assertTrue(e.composition().candidates.any { it.text == "user@gmail.com" })
    }

    @Test fun `blank phrases are rejected`() {
        val e = engine()
        e.userDictionary.addPhrase("   ")
        assertTrue(e.userDictionary.phrases().isEmpty())
    }

    @Test fun `clear removes phrases as well as learned words`() {
        val e = engine()
        e.userDictionary.addPhrase("user@gmail.com")
        e.learn("hello")
        e.userDictionary.clear()
        assertTrue(e.userDictionary.phrases().isEmpty())
        assertTrue(e.userDictionary.entries().isEmpty())
    }

    @Test fun `the digit code shown to the user matches what types it`() {
        // The settings screen shows Keypad.encodeExtended; typing its prefix must work.
        val phrase = "user@gmail.com"
        val e = engine()
        e.userDictionary.addPhrase(phrase)
        val code = Keypad.encodeExtended(phrase)
        type(e, code.take(4))
        assertEquals(phrase, e.composition().candidates.first().text)
    }
}
