package io.github.jcastell7.modernt9.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {

    @Test fun `empty composition reports empty`() {
        assertTrue(Composition.Empty.isEmpty)
        assertEquals("", Composition.Empty.digits)
        assertEquals("", Composition.Empty.composing)
        assertTrue(Composition.Empty.candidates.isEmpty())
    }

    @Test fun `a composition with digits is not empty`() {
        val c = Composition(digits = "4663", composing = "good")
        assertFalse(c.isEmpty)
    }

    @Test fun `candidate defaults are sensible`() {
        val c = Candidate("hello")
        assertEquals(0, c.score)
        assertEquals(CandidateSource.DICTIONARY, c.source)
        assertTrue(c.isExactLength)
    }

    @Test fun `editor context defaults to a learnable text field`() {
        val ctx = EditorContext()
        assertEquals(FieldType.TEXT, ctx.fieldType)
        assertEquals("en", ctx.languageTag)
        assertFalse(ctx.incognito)
        assertEquals("", ctx.precedingText)
    }

    @Test fun `engine descriptor carries capability flags`() {
        val d = EngineDescriptor(
            id = "x", displayName = "X", version = "1",
            supportedLanguages = listOf("en"),
            supportsLearning = false,
            supportsNextWordPrediction = false,
        )
        assertFalse(d.supportsLearning)
        assertFalse(d.supportsNextWordPrediction)
        assertEquals(listOf("en"), d.supportedLanguages)
    }

    @Test fun `all candidate sources are distinct`() {
        assertEquals(
            CandidateSource.entries.size,
            CandidateSource.entries.map { it.name }.toSet().size,
        )
    }
}
