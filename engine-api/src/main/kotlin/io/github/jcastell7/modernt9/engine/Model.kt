package io.github.jcastell7.modernt9.engine

/** Where a candidate came from. Lets the UI style them and the engine rank them. */
enum class CandidateSource {
    /** Exact match from the main dictionary. */
    DICTIONARY,

    /** A word the user typed before, or added explicitly. Outranks DICTIONARY. */
    USER,

    /** Predicted continuation of a longer word from the current prefix. */
    COMPLETION,

    /** Predicted next word, offered when no digits are pending. */
    NEXT_WORD,

    /** The literal digits, offered so numbers are always typeable. */
    LITERAL,

    /** A user-added phrase — email address, URL, handle. Outranks everything. */
    PHRASE,

    /** A punctuation mark offered by the punctuation key. */
    PUNCTUATION,

    /** A bare letter from the pressed key, offered before any word. */
    LETTER,

    /** The word currently being re-edited, pinned to the front of the list. */
    EDITING,
}

/**
 * One suggestion offered to the user.
 *
 * @param score higher is better. Scores are only meaningful *within* one result list;
 *   engines are free to use any scale.
 */
data class Candidate(
    val text: String,
    val score: Int = 0,
    val source: CandidateSource = CandidateSource.DICTIONARY,
    /** True when [text] consumes the whole pending digit sequence. */
    val isExactLength: Boolean = true,
)

/** What kind of field is being edited — engines may suppress learning in some of them. */
enum class FieldType { TEXT, PASSWORD, EMAIL, URI, NUMBER, PHONE, SEARCH }

/**
 * What the engine knows about the editor when a session starts.
 *
 * [precedingText] is what makes next-word prediction possible; pass as much as is cheaply
 * available (a sentence is plenty).
 */
data class EditorContext(
    val fieldType: FieldType = FieldType.TEXT,
    val precedingText: String = "",
    val languageTag: String = "en",
    /** Honour this by not learning from the field's content. */
    val incognito: Boolean = false,
)

/**
 * The engine's view of the current composition, returned after every input event.
 *
 * The IME renders exactly this: [composing] goes into the editor as composing text,
 * [candidates] fill the suggestion strip.
 */
data class Composition(
    /** Pending digit sequence, e.g. "43556". Empty when nothing is being composed. */
    val digits: String = "",
    /** Text to show inline in the editor — usually `candidates.first().text`. */
    val composing: String = "",
    val candidates: List<Candidate> = emptyList(),
    /**
     * Caret position **within** [composing], 0..composing.length.
     *
     * Keys insert here and backspace deletes just before it, so editing a word from the
     * middle behaves the way the user placed the caret rather than always acting on the
     * end.
     */
    val cursor: Int = composing.length,
) {
    val isEmpty: Boolean get() = digits.isEmpty()

    companion object {
        val Empty = Composition()
    }
}

/** Describes an engine so the UI can list and identify it. */
data class EngineDescriptor(
    val id: String,
    val displayName: String,
    val version: String,
    /** BCP-47 tags this engine can serve, e.g. ["en", "es"]. */
    val supportedLanguages: List<String>,
    /** False for engines that cannot learn (a read-only or stateless backend). */
    val supportsLearning: Boolean = true,
    val supportsNextWordPrediction: Boolean = false,
)
