package io.github.jcastell7.modernt9.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spanish must be typeable without ever thinking about diacritics. */
class SpanishEncodingTest {

    @Test fun `enye folds to n and lands on key 6`() {
        assertEquals("senor", Keypad.foldToAscii("señor"))
        assertEquals(Keypad.encode("senor"), Keypad.encode("señor"))
        assertEquals("73667", Keypad.encode("señor"))
    }

    @Test fun `accented vowels fold to their base letter`() {
        mapOf(
            "café" to "cafe", "mañana" to "manana", "niño" to "nino",
            "corazón" to "corazon", "así" to "asi", "más" to "mas",
            "número" to "numero", "aquí" to "aqui", "también" to "tambien",
            "adiós" to "adios", "pingüino" to "pinguino",
        ).forEach { (accented, plain) ->
            assertEquals(plain, Keypad.foldToAscii(accented))
            assertEquals(Keypad.encode(plain), Keypad.encode(accented))
        }
    }

    @Test fun `common spanish words encode`() {
        listOf("hola", "gracias", "ustedes", "computadora", "años")
            .forEach { assertNotNull("$it should encode", Keypad.encode(it)) }
    }

    @Test fun `spanish shows enye as a hint on key 6`() {
        assertEquals("mnoñ", Keypad.letterHints('6', "es"))
        assertEquals("mno", Keypad.letterHints('6', "en"))
        assertEquals("abc", Keypad.letterHints('2', "es"))
    }

    @Test fun `spanish punctuation cycle includes inverted marks`() {
        val es = Punctuation.cycleFor(FieldType.TEXT, "es")
        assertTrue(es.contains("¿"))
        assertTrue(es.contains("¡"))
        val en = Punctuation.cycleFor(FieldType.TEXT, "en")
        assertTrue(!en.contains("¿"))
    }
}
