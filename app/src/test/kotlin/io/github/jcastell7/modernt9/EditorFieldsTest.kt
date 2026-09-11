package io.github.jcastell7.modernt9

import android.text.InputType
import android.view.inputmethod.EditorInfo
import io.github.jcastell7.modernt9.engine.FieldType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These decide whether the engine is allowed to learn from a field, so they are worth
 * testing exhaustively — a mistake here leaks passwords into the user dictionary.
 */
class EditorFieldsTest {

    private fun text(variation: Int = 0, flags: Int = 0) =
        InputType.TYPE_CLASS_TEXT or variation or flags

    @Test fun `plain text is TEXT`() {
        assertEquals(FieldType.TEXT, fieldTypeFor(text()))
        assertEquals(FieldType.TEXT, fieldTypeFor(0))
    }

    @Test fun `all three password variations are PASSWORD`() {
        assertEquals(FieldType.PASSWORD,
            fieldTypeFor(text(InputType.TYPE_TEXT_VARIATION_PASSWORD)))
        assertEquals(FieldType.PASSWORD,
            fieldTypeFor(text(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)))
        assertEquals(FieldType.PASSWORD,
            fieldTypeFor(text(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)))
    }

    @Test fun `numeric and phone classes are detected`() {
        assertEquals(FieldType.NUMBER, fieldTypeFor(InputType.TYPE_CLASS_NUMBER))
        assertEquals(FieldType.PHONE, fieldTypeFor(InputType.TYPE_CLASS_PHONE))
        // A numeric password is still a number field, never learnable.
        assertEquals(FieldType.NUMBER,
            fieldTypeFor(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }

    @Test fun `email variations are EMAIL`() {
        assertEquals(FieldType.EMAIL,
            fieldTypeFor(text(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)))
        assertEquals(FieldType.EMAIL,
            fieldTypeFor(text(InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)))
    }

    @Test fun `uri fields are URI`() {
        assertEquals(FieldType.URI, fieldTypeFor(text(InputType.TYPE_TEXT_VARIATION_URI)))
    }

    @Test fun `filter fields are SEARCH`() {
        assertEquals(FieldType.SEARCH, fieldTypeFor(text(InputType.TYPE_TEXT_VARIATION_FILTER)))
    }

    @Test fun `capitalisation flags do not change the field type`() {
        assertEquals(FieldType.PASSWORD, fieldTypeFor(
            text(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        ))
    }

    @Test fun `sentence capitalisation is detected`() {
        assertTrue(startsSentence(text(flags = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)))
        assertFalse(startsSentence(text()))
        assertFalse(startsSentence(0))
    }

    @Test fun `other capitalisation flags do not arm shift`() {
        assertFalse(startsSentence(text(flags = InputType.TYPE_TEXT_FLAG_CAP_WORDS)))
    }

    @Test fun `incognito flag is detected`() {
        assertTrue(isIncognito(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        assertFalse(isIncognito(0))
        assertTrue(isIncognito(
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_ACTION_DONE
        ))
    }
}
