package io.github.jcastell7.modernt9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ABC mode: letter-at-a-time entry used when prediction is switched off. */
class MultiTapTest {

    private val abc = "abc"
    private val pqrs = "pqrs"

    @Test fun `the first tap gives the first letter`() {
        val m = MultiTap()
        val r = m.tap('2', abc, 0)
        assertEquals("a", r.letter)
        assertTrue("a new letter begins", r.append)
    }

    @Test fun `tapping the same key cycles in place`() {
        val m = MultiTap()
        assertEquals("a", m.tap('2', abc, 0).letter)
        val second = m.tap('2', abc, 100)
        assertEquals("b", second.letter)
        assertFalse("it replaces, rather than adding a character", second.append)
        assertEquals("c", m.tap('2', abc, 200).letter)
    }

    @Test fun `the cycle wraps`() {
        val m = MultiTap()
        listOf(0L, 100L, 200L).forEach { m.tap('2', abc, it) }
        assertEquals("a", m.tap('2', abc, 300).letter)
    }

    @Test fun `a four-letter key cycles through all of them`() {
        val m = MultiTap()
        val letters = (0..3).map { m.tap('7', pqrs, it * 100L).letter }
        assertEquals(listOf("p", "q", "r", "s"), letters)
    }

    @Test fun `pausing settles the letter and starts a new one`() {
        val m = MultiTap()
        m.tap('2', abc, 0)
        val afterPause = m.tap('2', abc, MultiTap.TIMEOUT_MS + 1)
        assertEquals("a", afterPause.letter)
        assertTrue("the previous letter is finished", afterPause.append)
    }

    @Test fun `a different key starts a new letter immediately`() {
        val m = MultiTap()
        m.tap('2', abc, 0)
        val next = m.tap('7', pqrs, 50)
        assertEquals("p", next.letter)
        assertTrue(next.append)
    }

    @Test fun `finish clears what is pending`() {
        val m = MultiTap()
        m.tap('2', abc, 0)
        assertEquals("a", m.pending)
        m.finish()
        assertNull(m.pending)
    }

    @Test fun `expiry is reported once the window closes`() {
        val m = MultiTap()
        m.tap('2', abc, 0)
        assertFalse(m.hasExpired(100))
        assertTrue(m.hasExpired(MultiTap.TIMEOUT_MS + 1))
    }

    @Test fun `nothing pending never counts as expired`() {
        assertFalse(MultiTap().hasExpired(Long.MAX_VALUE))
    }

    @Test fun `continues reports whether the next tap would cycle`() {
        val m = MultiTap()
        m.tap('2', abc, 0)
        assertTrue(m.continues('2', 100))
        assertFalse("a different key", m.continues('7', 100))
        assertFalse("too late", m.continues('2', MultiTap.TIMEOUT_MS + 1))
    }

    @Test fun `a key with no letters falls back to the digit`() {
        val m = MultiTap()
        assertEquals("5", m.tap('5', "", 0).letter)
    }

    @Test fun `spanish key 6 includes the enye`() {
        val letters = MultiTap.lettersFor('6', "es")
        assertEquals("mnoñ", letters)
        val m = MultiTap()
        val typed = (0..3).map { m.tap('6', letters, it * 100L).letter }
        assertEquals(listOf("m", "n", "o", "ñ"), typed)
    }
}
