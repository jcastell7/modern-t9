package io.github.jcastell7.modernt9

import io.github.jcastell7.modernt9.engine.FieldType
import io.github.jcastell7.modernt9.engine.Punctuation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Multi-tap on the punctuation key. This is how `.` `,` `?` `@` and `:` are reached
 * without a symbol panel, so the cycling contract matters.
 */
class PunctuationCyclerTest {

    private fun cycler(type: FieldType = FieldType.TEXT, lang: String = "en") =
        PunctuationCycler().apply { configure(type, lang) }

    @Test fun `starts inactive`() {
        val c = cycler()
        assertFalse(c.isActive)
        assertNull(c.current)
    }

    @Test fun `first tap yields a full stop`() {
        val c = cycler()
        assertEquals(".", c.next())
        assertTrue(c.isActive)
        assertEquals(".", c.current)
    }

    @Test fun `successive taps advance through the cycle`() {
        val c = cycler()
        assertEquals(".", c.next())
        assertEquals(",", c.next())
        assertEquals("?", c.next())
        assertEquals("!", c.next())
    }

    @Test fun `the cycle wraps around`() {
        val c = cycler()
        val size = c.options().size
        repeat(size) { c.next() }
        assertEquals("the cycle should wrap to the start", ".", c.next())
    }

    @Test fun `finish deactivates and clears`() {
        val c = cycler()
        c.next()
        c.finish()
        assertFalse(c.isActive)
        assertNull(c.current)
    }

    @Test fun `a fresh cycle restarts from the beginning`() {
        val c = cycler()
        c.next(); c.next(); c.next()
        c.finish()
        assertEquals(".", c.next())
    }

    @Test fun `email fields start at the at-sign`() {
        assertEquals("@", cycler(FieldType.EMAIL).next())
    }

    @Test fun `uri fields start at the full stop then slash`() {
        val c = cycler(FieldType.URI)
        assertEquals(".", c.next())
        assertEquals("/", c.next())
        assertEquals(":", c.next())
    }

    @Test fun `spanish offers the inverted question mark early`() {
        val c = cycler(lang = "es")
        val firstFive = (1..5).map { c.next() }
        assertTrue("¿ should be reachable quickly: $firstFive", firstFive.contains("¿"))
    }

    @Test fun `select jumps straight to a mark`() {
        val c = cycler()
        assertEquals("@", c.select("@"))
        assertEquals("@", c.current)
        // and cycling continues from there
        assertEquals(":", c.next())
    }

    @Test fun `select of an unknown mark still returns it but does not activate`() {
        val c = cycler()
        assertEquals("€", c.select("€"))
        assertFalse(c.isActive)
    }

    @Test fun `reconfiguring resets the cycle`() {
        val c = cycler()
        c.next(); c.next()
        c.configure(FieldType.EMAIL, "en")
        assertFalse(c.isActive)
        assertEquals("@", c.next())
    }

    @Test fun `options match the configured cycle`() {
        assertEquals(Punctuation.cycleFor(FieldType.EMAIL, "en"), cycler(FieldType.EMAIL).options())
    }
}
