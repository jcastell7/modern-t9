package io.github.jcastell7.modernt9.engine.trie

import io.github.jcastell7.modernt9.engine.EditorContext
import io.github.jcastell7.modernt9.engine.EngineResources
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Switching dictionaries mid-session must be instant and must not lose learned data. */
class MultiLanguageTest {

    private lateinit var tmp: File

    private val english = """
        good	950
        hello	900
        the	1000
    """.trimIndent()

    // "como" and "para" collide with English words on other sequences; "hola" is es-only.
    private val spanish = """
        hola	900
        gracias	800
        ustedes	700
        senor	600
        que	1000
    """.trimIndent()

    private fun resources(
        dir: File = tmp,
        langs: Map<String, String> = mapOf("en" to english, "es" to spanish),
        active: String = "en",
    ) = object : EngineResources {
        override fun openAsset(path: String): InputStream? {
            val tag = path.removePrefix("dict/").removeSuffix(".txt")
            return langs[tag]?.let { ByteArrayInputStream(it.toByteArray()) }
        }
        override fun dataDir(): File = dir
        override val languageTag = active
    }

    private fun engine(res: EngineResources = resources()) =
        TrieEngine(res).apply { initialize(); startSession(EditorContext()) }

    @Before fun setUp() {
        tmp = File.createTempFile("t9lang", "").apply { delete(); mkdirs() }
    }

    @Test fun `both dictionaries load`() {
        assertEquals(listOf("en", "es"), engine().availableLanguages)
    }

    @Test fun `the host language decides the initial dictionary`() {
        assertEquals("en", engine().activeLanguage)
        assertEquals("es", engine(resources(active = "es")).activeLanguage)
    }

    @Test fun `a region tag resolves to its base language`() {
        assertEquals("es", engine(resources(active = "es-419")).activeLanguage)
    }

    @Test fun `an unavailable host language falls back to the first loaded`() {
        assertEquals("en", engine(resources(active = "de")).activeLanguage)
    }

    @Test fun `switching changes which words are offered`() {
        val e = engine()
        "4652".forEach { e.onDigit(it) }        // h-o-l-a
        assertTrue(e.composition().candidates.none { it.text == "hola" })

        assertTrue(e.switchLanguage("es"))
        assertEquals("es", e.activeLanguage)
        "4652".forEach { e.onDigit(it) }
        assertEquals("hola", e.composition().candidates.first().text)
    }

    @Test fun `switching to an unknown language is refused and changes nothing`() {
        val e = engine()
        assertFalse(e.switchLanguage("de"))
        assertEquals("en", e.activeLanguage)
    }

    @Test fun `switching accepts a region tag`() {
        val e = engine()
        assertTrue(e.switchLanguage("es-419"))
        assertEquals("es", e.activeLanguage)
    }

    @Test fun `switching discards a pending composition`() {
        val e = engine()
        "4663".forEach { e.onDigit(it) }
        assertFalse(e.composition().isEmpty)
        e.switchLanguage("es")
        assertTrue(e.composition().isEmpty)
    }

    @Test fun `switching back and forth is stable`() {
        val e = engine()
        repeat(3) { e.switchLanguage("es"); e.switchLanguage("en") }
        assertEquals("en", e.activeLanguage)
        "4663".forEach { e.onDigit(it) }
        assertEquals("good", e.composition().candidates.first().text)
    }

    @Test fun `learned words are available in both languages`() {
        val e = engine()
        repeat(20) { e.learn("hello") }
        e.switchLanguage("es")
        "43556".forEach { e.onDigit(it) }
        assertNotNull(
            "a learned word should follow the user across languages",
            e.composition().candidates.firstOrNull { it.text == "hello" },
        )
    }

    @Test fun `spanish words with enye are found by typing plain letters`() {
        val e = engine(resources(active = "es"))
        "73667".forEach { e.onDigit(it) }       // s-e-n-o-r
        assertEquals("senor", e.composition().candidates.first().text)
    }

    @Test fun `a single-language setup still works`() {
        val e = engine(resources(langs = mapOf("en" to english)))
        assertEquals(listOf("en"), e.availableLanguages)
        assertFalse(e.switchLanguage("es"))
    }

    @Test fun `descriptor advertises both configured languages`() {
        assertEquals(listOf("en", "es"), TrieEngine.DESCRIPTOR.supportedLanguages)
    }
}
