package io.github.jcastell7.modernt9.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The extended encoding is what makes email addresses and URLs typeable. Every character
 * must map to something — it must never return null — or a phrase becomes unreachable.
 */
class ExtendedEncodingTest {

    @Test fun `letters encode as on the normal keypad`() {
        assertEquals("43556", Keypad.encodeExtended("hello"))
        assertEquals(Keypad.encode("hello"), Keypad.encodeExtended("hello"))
    }

    @Test fun `digits encode as themselves`() {
        assertEquals("2024", Keypad.encodeExtended("2024"))
        assertEquals("66820243", Keypad.encodeExtended("nov2024e"))
    }

    @Test fun `symbols collapse onto the punctuation key`() {
        assertEquals("1", Keypad.encodeExtended("@"))
        assertEquals("1", Keypad.encodeExtended("."))
        assertEquals("1", Keypad.encodeExtended(":"))
        assertEquals("111", Keypad.encodeExtended("?!/"))
    }

    @Test fun `an email address encodes end to end`() {
        // j5 u8 a2 n6 @1 g4 m6 a2 i4 l5 .1 c2 o6 m6
        assertEquals("58261462451266", Keypad.encodeExtended("user@gmail.com"))
    }

    @Test fun `a url encodes end to end`() {
        assertEquals("4887711191266", Keypad.encodeExtended("https://x.com"))
        assertTrue(Keypad.encodeExtended("https://example.com/path").isNotEmpty())
    }

    @Test fun `the prefix of a phrase is short and typeable`() {
        // Four taps of the local part is the point: it surfaces the whole address.
        val encoded = Keypad.encodeExtended("user@gmail.com")
        assertTrue(encoded.startsWith("5826"))
    }

    @Test fun `extended encoding never returns null for any input`() {
        listOf("", "!!!", "a1@#$%^&*()", "über@correo.mx", "  spaced  ", "日本")
            .forEach { assertNotNull(Keypad.encodeExtended(it)) }
    }

    private fun assertNotNull(value: Any?) = assertTrue(value != null)

    @Test fun `case is ignored`() {
        assertEquals(
            Keypad.encodeExtended("user@Gmail.COM"),
            Keypad.encodeExtended("user@gmail.com"),
        )
    }

    @Test fun `accents fold before encoding`() {
        assertEquals(
            Keypad.encodeExtended("jose@correo.mx"),
            Keypad.encodeExtended("josé@correo.mx"),
        )
    }

    @Test fun `isComplexToken distinguishes phrases from plain words`() {
        assertFalse(Keypad.isComplexToken("hello"))
        assertFalse(Keypad.isComplexToken("señor"))       // folds to letters
        assertTrue(Keypad.isComplexToken("user@gmail.com"))
        assertTrue(Keypad.isComplexToken("x.com"))
        assertTrue(Keypad.isComplexToken("abc123"))
        assertTrue(Keypad.isComplexToken("two words"))
    }

    @Test fun `strict encoding still rejects complex tokens`() {
        assertNull(Keypad.encode("user@gmail.com"))
        assertNull(Keypad.encode("abc123"))
    }
}
