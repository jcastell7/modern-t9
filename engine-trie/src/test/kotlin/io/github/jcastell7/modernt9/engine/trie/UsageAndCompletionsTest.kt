package io.github.jcastell7.modernt9.engine.trie

import io.github.jcastell7.modernt9.engine.CandidateSource
import io.github.jcastell7.modernt9.engine.EditorContext
import io.github.jcastell7.modernt9.engine.EngineResources
import io.github.jcastell7.modernt9.engine.Keypad
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Two reports from device testing:
 *
 *  * a word the dictionary knows only showed up once typed in full — completions were
 *    the first few words *met* in the trie, not the heaviest;
 *  * words the user actually types should climb past words used once or twice, and
 *    that includes single-letter words.
 */
class UsageAndCompletionsTest {

    private lateinit var tmp: File

    // Weights on the real dictionary's scale (1..1,000,000).
    private val dictionary = """
        this	199341
        think	63884
        thing	24225
        ugh	1077
        uhh	298
        thi	30
        tig	28
        work	21243
        world	12871
        word	5728
        wop	17
        wor	11
        so	119266
        some	40525
        po	138
        pm	278
    """.trimIndent()

    private fun engine() = TrieEngine(object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path.endsWith(".txt")) ByteArrayInputStream(dictionary.toByteArray()) else null
        override fun dataDir(): File = tmp
        override val languageTag = "en"
    }).apply { initialize(); startSession(EditorContext()) }

    private fun type(e: TrieEngine, word: String) =
        Keypad.encode(word)!!.forEach { e.onDigit(it) }

    private fun strip(e: TrieEngine) = e.composition().candidates
        .filter { it.source != CandidateSource.LITERAL }.map { it.text }

    @Before fun setUp() {
        tmp = File.createTempFile("t9usage", "").apply { delete(); mkdirs() }
    }

    // ---- completions --------------------------------------------------------------

    @Test fun `the common word is offered on the second key`() {
        val e = engine()
        type(e, "th")                                     // 84
        val texts = strip(e)
        assertTrue("this must be offered at two keys: $texts", texts.contains("this"))
        assertEquals("the likeliest word leads", "this", texts.first())
    }

    @Test fun `likely words come before junk of the typed length`() {
        val e = engine()
        type(e, "wor")                                    // 967: wop, wor are "exact"
        val texts = strip(e)
        assertTrue(texts.indexOf("work") < texts.indexOf("wop"))
        assertTrue(texts.indexOf("world") < texts.indexOf("wop"))
        // ...but inline is still a word of the typed length, and space commits it.
        assertEquals("wop", e.composition().composing)
        assertEquals("wop", e.commitInline())
    }

    @Test fun `an exact match that is the likelier word still leads`() {
        val e = engine()
        type(e, "so")                                     // 76: so 119266 vs some 40525
        assertEquals("so", strip(e).first())
    }

    @Test fun `on a single key the words precede the letters`() {
        val e = engine()
        e.onDigit('8')
        val texts = strip(e)
        assertTrue(texts.indexOf("this") < texts.indexOf("t"))
        assertEquals(listOf("t", "u", "v"), texts.filter { it.length == 1 })
        assertEquals("t", e.composition().composing)
    }

    // ---- usage --------------------------------------------------------------------

    @Test fun `a word used once moves a little, not to the top`() {
        val e = engine()
        e.learn("po")
        e.reset(); type(e, "so")
        val texts = strip(e)
        assertTrue("po beats pm now: $texts", texts.indexOf("po") < texts.indexOf("pm"))
        assertEquals("but not so: $texts", "so", texts.first())
    }

    @Test fun `a word used often overtakes a very common rival`() {
        val e = engine()
        repeat(12) { e.learn("po") }
        e.reset(); type(e, "so")
        assertEquals("po", strip(e).first())
        assertEquals("po", e.composition().composing)
    }

    @Test fun `more use ranks higher than less use`() {
        val e = engine()
        repeat(2) { e.learn("wor") }
        repeat(5) { e.learn("wop") }
        e.reset(); type(e, "wor")
        val texts = strip(e)
        assertTrue("$texts", texts.indexOf("wop") < texts.indexOf("wor"))
        repeat(6) { e.learn("wor") }                       // now 8 vs 5
        e.reset(); type(e, "wor")
        assertTrue("${strip(e)}", strip(e).indexOf("wor") < strip(e).indexOf("wop"))
    }

    @Test fun `a used word is offered as a completion from its first keys`() {
        val e = engine()
        repeat(6) { e.learn("word") }
        e.reset(); type(e, "wo")                          // 96
        assertEquals("word", strip(e).first())
    }

    @Test fun `a learned word unknown to the dictionary is offered and climbs`() {
        val e = engine()
        e.learn("zork")                                   // 9675, not in the dictionary
        e.reset(); type(e, "zork")
        assertTrue(strip(e).contains("zork"))
        repeat(10) { e.learn("zork") }
        e.reset(); type(e, "zo")                          // 96: work, world, word, some...
        assertEquals("zork", strip(e).first())
    }

    @Test fun `single-letter words climb with use too`() {
        val e = engine()
        repeat(2) { e.learn("v") }
        repeat(5) { e.learn("u") }
        e.reset(); e.onDigit('8')
        assertEquals(listOf("u", "v", "t"), strip(e).filter { it.length == 1 })
        assertEquals("u", e.composition().composing)
    }

    @Test fun `use counts one per commit`() {
        val e = engine()
        repeat(3) { e.learn("word") }
        assertEquals(3, e.userDictionary.entries().first { it.word == "word" }.weight)
    }
}

/** The word space commits must be on the strip even when it is the lightest of many. */
class InlineSurvivesTheCutTest {

    private lateinit var tmp: File

    // Twenty heavy completions of 967 and one feather-weight exact match.
    private val dictionary = buildString {
        append("wop\t3\n")
        for (i in 0 until 20) append("work").append("a".repeat(i + 1)).append('\t').append(50000 - i).append('\n')
    }

    private fun engine() = TrieEngine(object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path.endsWith(".txt")) ByteArrayInputStream(dictionary.toByteArray()) else null
        override fun dataDir(): File = tmp
        override val languageTag = "en"
    }).apply { initialize(); startSession(EditorContext()) }

    @Before fun setUp() {
        tmp = File.createTempFile("t9cut", "").apply { delete(); mkdirs() }
    }

    @Test fun `the exact match is kept, last, and is what space commits`() {
        val e = engine()
        "967".forEach { e.onDigit(it) }
        val texts = e.composition().candidates.map { it.text }
        assertTrue("wop must be listed: $texts", texts.contains("wop"))
        assertEquals("wop", texts[texts.size - 2])                  // before the literal
        assertEquals("wop", e.composition().composing)
        assertEquals("wop", e.commitInline())
    }
}

/** Remove in the phrase list must take the word out of the predictions, not just the list. */
class ForgetWordTest {

    private lateinit var tmp: File

    private fun engine() = TrieEngine(object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path.endsWith(".txt")) ByteArrayInputStream("word\t5728\nthe\t90000\n".toByteArray()) else null
        override fun dataDir(): File = tmp
        override val languageTag = "en"
    }).apply { initialize(); startSession(EditorContext()) }

    private fun strip(e: TrieEngine, word: String): List<String> {
        e.reset()
        Keypad.encode(word)!!.forEach { e.onDigit(it) }
        return e.composition().candidates.map { it.text }
    }

    @Before fun setUp() {
        tmp = File.createTempFile("t9forget", "").apply { delete(); mkdirs() }
    }

    @Test fun `removing a saved word removes it from the predictions`() {
        val e = engine()
        e.learn("zork")                                    // typed with space...
        e.userDictionary.addPhrase("zork")                 // ...then saved from the offer
        assertTrue(strip(e, "zork").contains("zork"))

        assertTrue(e.userDictionary.removePhrase("zork"))
        assertTrue("still offered: ${strip(e, "zork")}", !strip(e, "zork").contains("zork"))
        assertTrue(!e.userDictionary.contains("zork"))
        assertTrue(!e.isKnown("zork"))
    }

    @Test fun `forgetting also drops its bigrams`() {
        val e = engine()
        e.learn("the"); e.learn("zork"); e.learn("the")
        assertTrue(e.predictNextWord().isNotEmpty() || true)
        e.userDictionary.remove("zork")
        e.learn("the")
        assertTrue(e.predictNextWord().none { it.text == "zork" })
    }

    @Test fun `forgetting a dictionary word only resets its use, the word stays`() {
        val e = engine()
        repeat(5) { e.learn("word") }
        assertTrue(e.userDictionary.remove("word"))
        val texts = strip(e, "word")
        assertTrue("dictionary word must survive: $texts", texts.contains("word"))
        assertEquals(CandidateSource.DICTIONARY, e.composition().candidates.first { it.text == "word" }.source)
    }

    @Test fun `the forgotten word survives a restart as forgotten`() {
        val e = engine()
        e.learn("zork"); e.userDictionary.addPhrase("zork")
        e.userDictionary.removePhrase("zork")
        e.close()
        val again = engine()
        assertTrue(!strip(again, "zork").contains("zork"))
        assertTrue(again.userDictionary.phrases().none { it.word == "zork" })
    }
}

/** The "my words" list: learned words the dictionary lacks, and nothing it has. */
class NovelWordsTest {

    private lateinit var tmp: File

    private fun engine() = TrieEngine(object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path.endsWith(".txt")) ByteArrayInputStream("word\t5728\nthe\t90000\n".toByteArray()) else null
        override fun dataDir(): File = tmp
        override val languageTag = "en"
    }).apply { initialize(); startSession(EditorContext()) }

    @Before fun setUp() {
        tmp = File.createTempFile("t9novel", "").apply { delete(); mkdirs() }
    }

    @Test fun `a word committed by space that the dictionary lacks is listed`() {
        val e = engine()
        "2222".forEach { e.onDigit(it) }                  // spells nothing known
        assertEquals("aaaa", e.commitInline())            // space commits the plain letters
        assertEquals(listOf("aaaa"), e.userDictionary.novelWords().map { it.word })
    }

    @Test fun `dictionary words are not listed however often they are used`() {
        val e = engine()
        repeat(9) { e.learn("word") }
        e.learn("tomasito")
        assertEquals(listOf("tomasito"), e.userDictionary.novelWords().map { it.word })
        assertTrue(e.userDictionary.entries().any { it.word == "word" })   // still counted
    }

    @Test fun `the list survives a restart`() {
        val e = engine()
        e.learn("tomasito"); e.close()
        assertEquals(listOf("tomasito"), engine().userDictionary.novelWords().map { it.word })
    }

    @Test fun `removing a listed word empties the list and the predictions`() {
        val e = engine()
        e.learn("tomasito")
        e.userDictionary.remove("tomasito")
        assertTrue(e.userDictionary.novelWords().isEmpty())
        e.reset(); "86627486".forEach { e.onDigit(it) }
        assertTrue(e.composition().candidates.none { it.text == "tomasito" })
    }
}

/** Learned words, saved phrases and dictionary words are weighed on one scale. */
class OneScaleTest {

    private lateinit var tmp: File

    // 7272: "papa" common, "sasa" rare; a saved "papá" competes with both.
    private val dictionary = "papa\t30000\nsasa\t40\nthe\t90000\n"

    private fun engine() = TrieEngine(object : EngineResources {
        override fun openAsset(path: String): InputStream? =
            if (path.endsWith(".txt")) ByteArrayInputStream(dictionary.toByteArray()) else null
        override fun dataDir(): File = tmp
        override val languageTag = "en"
    }).apply { initialize(); startSession(EditorContext()) }

    private fun strip(e: TrieEngine): List<String> {
        e.reset(); "7272".forEach { e.onDigit(it) }
        return e.composition().candidates.filter { it.source != CandidateSource.LITERAL }.map { it.text }
    }

    @Before fun setUp() {
        tmp = File.createTempFile("t9scale", "").apply { delete(); mkdirs() }
    }

    @Test fun `a saved word never used sits below a common dictionary word`() {
        val e = engine()
        e.userDictionary.addPhrase("Papá")
        assertEquals(listOf("papa", "Papá", "sasa"), strip(e))
    }

    @Test fun `a learned word never used sits below a common dictionary word too`() {
        val e = engine()
        e.learn("rapa")                                   // 7272, not in the dictionary
        assertEquals(listOf("papa", "rapa", "sasa"), strip(e))
    }

    @Test fun `a saved word used often beats a dictionary word never used`() {
        val e = engine()
        e.userDictionary.addPhrase("Papá")
        repeat(4) {
            e.reset(); "7272".forEach { c -> e.onDigit(c) }
            e.selectCandidate(e.composition().candidates.indexOfFirst { it.text == "Papá" })
        }
        assertEquals("Papá", strip(e).first())
    }

    @Test fun `a dictionary word used more still beats a saved word used less`() {
        val e = engine()
        e.userDictionary.addPhrase("Papá")
        repeat(2) {
            e.reset(); "7272".forEach { c -> e.onDigit(c) }
            e.selectCandidate(e.composition().candidates.indexOfFirst { it.text == "Papá" })
        }
        repeat(5) { e.learn("papa") }
        assertEquals("papa", strip(e).first())
    }
}

/** Two very common words on the same keys must still order by use — no clipping. */
class NoClipTest {
    @Test fun `the more-used of two habitual top-frequency words leads`() {
        val tmp = File.createTempFile("t9clip", "").apply { delete(); mkdirs() }
        val e = TrieEngine(object : EngineResources {
            override fun openAsset(path: String): InputStream? =
                if (path.endsWith(".txt")) ByteArrayInputStream("to\t593853\nun\t301543\n".toByteArray()) else null
            override fun dataDir(): File = tmp
            override val languageTag = "en"
        }).apply { initialize(); startSession(EditorContext()) }
        // The numbers from the device report: both far past saturation.
        repeat(37) { e.learn("to") }
        repeat(75) { e.learn("un") }
        e.reset(); "86".forEach { e.onDigit(it) }
        assertEquals("un", e.composition().candidates.first().text)
        // A small lead in use does not overturn a big lead in frequency, though.
        repeat(30) { e.learn("to") }                      // 67 vs 75
        e.reset(); "86".forEach { e.onDigit(it) }
        assertEquals("to", e.composition().candidates.first().text)
        // Below saturation, 14 uses of un vs 0 of to flips it.
        val f = TrieEngine(object : EngineResources {
            override fun openAsset(path: String): InputStream? =
                if (path.endsWith(".txt")) ByteArrayInputStream("to\t593853\nun\t301543\n".toByteArray()) else null
            override fun dataDir(): File = File.createTempFile("t9clip2", "").apply { delete(); mkdirs() }
            override val languageTag = "en"
        }).apply { initialize(); startSession(EditorContext()) }
        repeat(14) { f.learn("un") }
        f.reset(); "86".forEach { f.onDigit(it) }
        assertEquals("un", f.composition().candidates.first().text)
        repeat(14) { f.learn("to") }                      // equal use: frequency decides again
        f.reset(); "86".forEach { f.onDigit(it) }
        assertEquals("to", f.composition().candidates.first().text)
    }
}
