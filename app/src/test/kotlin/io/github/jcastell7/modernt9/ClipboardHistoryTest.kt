package io.github.jcastell7.modernt9

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The store itself is testable on the JVM; only [ClipboardHistory.start] needs Android,
 * so these drive the list through the same paths the listener uses.
 */
class ClipboardHistoryTest {

    @Before fun setUp() = ClipboardHistory.clear()

    private fun push(vararg texts: String) = texts.forEach { ClipboardHistory.pushForTest(it) }

    @Test fun `starts empty`() {
        assertTrue(ClipboardHistory.items().isEmpty())
    }

    @Test fun `most recent comes first`() {
        push("one", "two", "three")
        assertEquals(listOf("three", "two", "one"), ClipboardHistory.items())
    }

    @Test fun `re-copying moves an entry to the top without duplicating`() {
        push("one", "two", "one")
        assertEquals(listOf("one", "two"), ClipboardHistory.items())
    }

    @Test fun `blank entries are ignored`() {
        push("", "   ", "real")
        assertEquals(listOf("real"), ClipboardHistory.items())
    }

    @Test fun `entries are trimmed`() {
        push("  padded  ")
        assertEquals(listOf("padded"), ClipboardHistory.items())
    }

    @Test fun `the list is capped`() {
        repeat(40) { ClipboardHistory.pushForTest("entry $it") }
        assertTrue(ClipboardHistory.items().size <= 25)
        assertEquals("entry 39", ClipboardHistory.items().first())
    }

    @Test fun `remove drops one entry`() {
        push("one", "two")
        ClipboardHistory.remove("one")
        assertEquals(listOf("two"), ClipboardHistory.items())
    }

    @Test fun `clear empties the list`() {
        push("one", "two")
        ClipboardHistory.clear()
        assertTrue(ClipboardHistory.items().isEmpty())
    }

    @Test fun `absurdly long clips are not stored`() {
        ClipboardHistory.pushForTest("x".repeat(5_000))
        assertTrue(ClipboardHistory.items().isEmpty())
    }
}
