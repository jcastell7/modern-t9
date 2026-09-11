package io.github.jcastell7.modernt9.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctuationTest {

    @Test fun `the most common marks come first`() {
        val cycle = Punctuation.cycleFor(FieldType.TEXT, "en")
        assertEquals(".", cycle[0])
        assertEquals(",", cycle[1])
    }

    @Test fun `email fields put the at-sign first`() {
        val cycle = Punctuation.cycleFor(FieldType.EMAIL, "en")
        assertEquals("@", cycle[0])
        assertEquals(".", cycle[1])
    }

    @Test fun `uri fields prioritise dot slash and colon`() {
        val cycle = Punctuation.cycleFor(FieldType.URI, "en")
        assertEquals(listOf(".", "/", ":"), cycle.take(3))
    }

    @Test fun `field type wins over language`() {
        // An email address is an email address in any language.
        assertEquals("@", Punctuation.cycleFor(FieldType.EMAIL, "es")[0])
    }

    @Test fun `every cycle contains the marks a keyboard must have`() {
        listOf(FieldType.TEXT, FieldType.EMAIL, FieldType.URI).forEach { type ->
            listOf("en", "es").forEach { lang ->
                val cycle = Punctuation.cycleFor(type, lang)
                listOf(".", "?", "@", ":").forEach { mark ->
                    assertTrue("$type/$lang must offer $mark", cycle.contains(mark))
                }
            }
        }
    }

    @Test fun `cycles contain no duplicates`() {
        listOf(FieldType.TEXT, FieldType.EMAIL, FieldType.URI).forEach { type ->
            listOf("en", "es").forEach { lang ->
                val cycle = Punctuation.cycleFor(type, lang)
                assertEquals("$type/$lang has duplicates", cycle.size, cycle.toSet().size)
            }
        }
    }

    @Test fun `sentence-ending marks are identified`() {
        assertTrue(Punctuation.SENTENCE_ENDING.contains("."))
        assertTrue(Punctuation.SENTENCE_ENDING.contains("?"))
        assertTrue(Punctuation.SENTENCE_ENDING.contains("!"))
        assertTrue(!Punctuation.SENTENCE_ENDING.contains(","))
    }

    @Test fun `spacing rules cover the common marks`() {
        assertTrue(Punctuation.ATTACHES_LEFT.contains("."))
        assertTrue(Punctuation.ATTACHES_LEFT.contains(","))
        assertTrue(Punctuation.ATTACHES_RIGHT.contains("¿"))
        assertTrue(Punctuation.ATTACHES_RIGHT.contains("("))
    }
}
