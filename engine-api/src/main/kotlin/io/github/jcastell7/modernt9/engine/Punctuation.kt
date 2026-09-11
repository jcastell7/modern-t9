package io.github.jcastell7.modernt9.engine

/**
 * What the punctuation key offers, and in what order.
 *
 * Punctuation is first-class on a phone keypad: an address bar needs `.` and `/`, an
 * email needs `@`, Spanish needs `¿` and `¡` *before* the clause rather than after. The
 * order below is by real typing frequency, so the common marks are one or two taps away.
 */
object Punctuation {

    /** General-purpose cycle, most frequent first. */
    private val DEFAULT = listOf(
        ".", ",", "?", "!", "'", "\"", "@", ":", ";", "-", "/",
        "(", ")", "_", "+", "&", "%", "#", "*", "=", "~",
    )

    /** Spanish adds inverted marks, which open a question or exclamation. */
    private val SPANISH = listOf(
        ".", ",", "¿", "?", "¡", "!", "'", "\"", "@", ":", ";", "-", "/",
        "(", ")", "_", "+", "&", "%", "#", "*", "=", "~", "ñ",
    )

    /** In an email field, `@` and `.` are what you actually need. */
    private val EMAIL = listOf(
        "@", ".", "_", "-", "+", ",", "'", ":", "/", "?", "!",
    )

    /** In a URL field, so are `.` `/` and `:`. */
    private val URI = listOf(
        ".", "/", ":", "-", "_", "?", "=", "&", "@", "#", "+", "%", ",", "!",
    )

    /**
     * The cycle for a given context. Field type wins over language: an email address is
     * an email address in any language.
     */
    fun cycleFor(fieldType: FieldType, languageTag: String): List<String> = when (fieldType) {
        FieldType.EMAIL -> EMAIL
        FieldType.URI -> URI
        else -> if (languageTag.startsWith("es")) SPANISH else DEFAULT
    }

    /** Marks that end a sentence — after these, the next letter is capitalised. */
    val SENTENCE_ENDING = setOf(".", "?", "!", "…")

    /** Marks that should not be preceded by a space when committed. */
    val ATTACHES_LEFT = setOf(
        ".", ",", "?", "!", ":", ";", ")", "'", "\"", "@", "/", "-", "_", "%", "…",
    )

    /** Marks that open a clause and should not be followed by a space. */
    val ATTACHES_RIGHT = setOf("(", "¿", "¡", "@", "/", "-", "_", "#", "\"", "'")
}
