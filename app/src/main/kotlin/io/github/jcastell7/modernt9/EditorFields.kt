package io.github.jcastell7.modernt9

import android.text.InputType
import android.view.inputmethod.EditorInfo
import io.github.jcastell7.modernt9.engine.FieldType

/**
 * Pure translations of Android editor flags into engine-level concepts.
 *
 * Kept free of [EditorInfo] instances (they take plain `Int`s) so they can be unit-tested
 * on the JVM — `InputType`/`EditorInfo` constants are compile-time ints and inline, while
 * the classes themselves are stubs in unit tests.
 */

/** Classify a field so the engine knows whether it may learn from it. */
fun fieldTypeFor(inputType: Int): FieldType {
    val klass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when {
        klass == InputType.TYPE_CLASS_NUMBER -> FieldType.NUMBER
        klass == InputType.TYPE_CLASS_PHONE -> FieldType.PHONE
        klass != InputType.TYPE_CLASS_TEXT -> FieldType.TEXT
        variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> FieldType.PASSWORD
        variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldType.EMAIL
        variation == InputType.TYPE_TEXT_VARIATION_URI -> FieldType.URI
        variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT -> FieldType.TEXT
        variation == InputType.TYPE_TEXT_VARIATION_FILTER -> FieldType.SEARCH
        else -> FieldType.TEXT
    }
}

/** True when the editor asks for sentence capitalisation, so shift starts armed. */
fun startsSentence(inputType: Int): Boolean =
    (inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0

/** True when the editor forbids personalised learning (incognito / private fields). */
fun isIncognito(imeOptions: Int): Boolean =
    (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
