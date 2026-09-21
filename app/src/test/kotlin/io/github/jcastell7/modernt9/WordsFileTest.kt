package io.github.jcastell7.modernt9

import io.github.jcastell7.modernt9.engine.UserWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordsFileTest {

    private val words = listOf(UserWord("spotify", 2, 1_700_000_000_000L, 2), UserWord("karcher", 1, 0L, 1))
    private val phrases = listOf(UserWord("Tomasito", 10_050, 1_690_000_000_000L, 1), UserWord("me@x.com", 10_000, 0L, 0))

    @Test fun `round trip keeps every field`() {
        val parsed = WordsFile.parse(WordsFile.write(words, phrases))
        assertEquals(0, parsed.skippedLines)
        val byText = parsed.entries.associateBy { it.word.word }
        assertEquals(4, byText.size)
        assertTrue(byText.getValue("Tomasito").isPhrase)
        assertEquals(1, byText.getValue("Tomasito").word.uses)
        assertEquals(1_690_000_000_000L, byText.getValue("Tomasito").word.addedAt)
        assertTrue(!byText.getValue("spotify").isPhrase)
        assertEquals(2, byText.getValue("spotify").word.uses)
        assertEquals(1_700_000_000_000L, byText.getValue("spotify").word.addedAt)
        assertEquals(0L, byText.getValue("karcher").word.addedAt)
    }

    @Test fun `dates are written as readable UTC instants`() {
        val text = WordsFile.write(words, emptyList())
        assertTrue(text, text.contains("spotify\tword\t2\t2023-11-14T22:13:20Z"))
    }

    @Test fun `a hand-written list of bare words imports`() {
        val parsed = WordsFile.parse("hola\nme@example.com\n\n# a comment\nWindows-11\n")
        assertEquals(listOf("hola", "me@example.com", "Windows-11"), parsed.entries.map { it.word.word })
        assertEquals(listOf(false, true, true), parsed.entries.map { it.isPhrase })
    }

    @Test fun `windows line endings and epoch millis are accepted`() {
        val parsed = WordsFile.parse("hola\tword\t3\t1700000000000\r\n")
        assertEquals(3, parsed.entries.single().word.uses)
        assertEquals(1_700_000_000_000L, parsed.entries.single().word.addedAt)
    }

    @Test fun `junk is skipped and counted, not fatal`() {
        val parsed = WordsFile.parse("hola\tword\tmany\tyesterday\n\tword\n x\tthing\n")
        assertEquals(1, parsed.entries.size)          // hola: bad uses -> 0, bad date -> 0
        assertEquals(0, parsed.entries.single().word.uses)
        assertEquals(0L, parsed.entries.single().word.addedAt)
        assertEquals(2, parsed.skippedLines)
    }
}
