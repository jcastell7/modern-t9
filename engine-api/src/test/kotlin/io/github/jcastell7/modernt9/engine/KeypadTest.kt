package io.github.jcastell7.modernt9.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeypadTest {

    @Test fun `every letter digit maps to the ITU-T E161 letters`() {
        assertEquals("abc", Keypad.letters['2'])
        assertEquals("def", Keypad.letters['3'])
        assertEquals("ghi", Keypad.letters['4'])
        assertEquals("jkl", Keypad.letters['5'])
        assertEquals("mno", Keypad.letters['6'])
        assertEquals("pqrs", Keypad.letters['7'])
        assertEquals("tuv", Keypad.letters['8'])
        assertEquals("wxyz", Keypad.letters['9'])
    }

    @Test fun `the 26 letters are covered exactly once`() {
        val all = Keypad.letters.values.joinToString("")
        assertEquals(26, all.length)
        assertEquals(26, all.toSet().size)
        assertEquals(('a'..'z').toSet(), all.toSet())
    }

    @Test fun `1 and 0 carry no letters`() {
        assertNull(Keypad.letters['1'])
        assertNull(Keypad.letters['0'])
        assertFalse(Keypad.isLetterDigit('1'))
        assertFalse(Keypad.isLetterDigit('0'))
        assertEquals(setOf('2', '3', '4', '5', '6', '7', '8', '9'), Keypad.letterDigits)
    }

    @Test fun `encode maps words to digit sequences`() {
        assertEquals("43556", Keypad.encode("hello"))
        assertEquals("4663", Keypad.encode("good"))
        assertEquals("4663", Keypad.encode("gone"))
        assertEquals("4663", Keypad.encode("home"))
        assertEquals("4653", Keypad.encode("gold"))
        assertEquals("96753", Keypad.encode("world"))
        assertEquals("843", Keypad.encode("the"))
    }

    @Test fun `encode is case insensitive`() {
        assertEquals(Keypad.encode("hello"), Keypad.encode("HELLO"))
        assertEquals(Keypad.encode("hello"), Keypad.encode("Hello"))
    }

    @Test fun `encode rejects characters with no key`() {
        assertNull(Keypad.encode("hi there"))     // space
        // The apostrophe is deliberately silent now, so "it's" encodes like "its" —
        // that is what lets typing "dont" find "don't".
        assertEquals(Keypad.encode("its"), Keypad.encode("it's"))
        assertNull(Keypad.encode("a1"))           // digit
        assertNull(Keypad.encode("e-mail"))       // hyphen
    }

    @Test fun `encode of empty string is empty`() {
        assertEquals("", Keypad.encode(""))
    }

    @Test fun `accent folding normalises before encoding`() {
        assertEquals("cafe", Keypad.foldToAscii("café"))
        assertEquals("naive", Keypad.foldToAscii("naïve"))
        assertEquals("uber", Keypad.foldToAscii("über"))
        assertEquals("senor", Keypad.foldToAscii("señor"))
        assertEquals("2233", Keypad.encode(Keypad.foldToAscii("café")))
    }

    @Test fun `folding leaves unaccented text untouched`() {
        assertEquals("hello", Keypad.foldToAscii("hello"))
        assertEquals("", Keypad.foldToAscii(""))
    }

    @Test fun `words on the same key collide as T9 expects`() {
        // The whole point of T9: one digit sequence, several words.
        val collisions = listOf("good", "gone", "home", "hood", "hone")
        val encoded = collisions.mapNotNull { Keypad.encode(it) }.toSet()
        assertEquals(1, encoded.size)
        assertTrue(encoded.contains("4663"))
    }
}
