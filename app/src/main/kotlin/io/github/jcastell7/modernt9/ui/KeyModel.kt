package io.github.jcastell7.modernt9.ui

/** Which pane the keyboard is showing. */
enum class KeyboardLayer { MAIN, SYMBOLS_1, SYMBOLS_2, EDIT, EMOJI, CLIPBOARD }

/**
 * One key on a symbol pane.
 *
 * @param label the glyph shown large and centred
 * @param sub the smaller glyph in the corner, reachable by long press
 *   (TouchPal puts `_` under `-`, `&` under `@`, `¿` under `?`, and so on)
 */
data class SymKey(val label: String, val sub: String? = null)

/**
 * The two symbol pages, transcribed from `touchpal-layout/symbol-view-1.png`
 * and `symbol-view-2.png`.
 */
object SymbolPages {

    val PAGE_1: List<List<SymKey>> = listOf(
        listOf(
            SymKey("1"), SymKey("2"), SymKey("3"), SymKey("+"), SymKey("-", "_"),
            SymKey("@", "&"), SymKey("$", "€"), SymKey("(", "<"), SymKey(")", ">"),
        ),
        listOf(
            SymKey("4"), SymKey("5"), SymKey("6"), SymKey("*"), SymKey("/"),
            SymKey("'", "‹"), SymKey("\"", "«"), SymKey(":", ";"), SymKey("#", "%"),
        ),
        listOf(
            SymKey("7"), SymKey("8"), SymKey("9"), SymKey(","), SymKey("="),
            SymKey("!", "¡"), SymKey("?", "¿"),
        ),
    )

    val PAGE_2: List<List<SymKey>> = listOf(
        listOf(
            SymKey("["), SymKey("]"), SymKey("{"), SymKey("}"), SymKey("<"),
            SymKey(">"), SymKey("&"), SymKey("_"), SymKey("%"), SymKey("√"),
        ),
        listOf(
            SymKey("|", "¦"), SymKey("\\", "§"), SymKey("~"), SymKey("•", "°"),
            SymKey("`"), SymKey("…"), SymKey("€", "$"), SymKey("¥"), SymKey("£"),
            SymKey("¢"),
        ),
        listOf(
            SymKey("α"), SymKey("β"), SymKey("^", "Δ"), SymKey("®"), SymKey("©"),
            SymKey("™"), SymKey("π", "Π"), SymKey("¤", "Φ"),
        ),
    )

    /** Shown in the left strip when nothing is being composed. Scrolls vertically. */
    val QUICK_SYMBOLS = listOf(
        "!", "?", "'", "~", "-", "_", ":", ";", "\"", "(", ")", "/", "&", "%", "+", "=",
    )
}

/**
 * Characters offered when a letter key is held.
 *
 * The digit comes first (so a held key can still type its number), then the base
 * letters, then their accented forms — matching `touchpal-layout/long-press.png`,
 * where holding `2` gives `2 a b c ä à â á ã …`.
 */
object LongPressAlternates {

    private val ACCENTS: Map<Char, String> = mapOf(
        'a' to "äàâáãåā", 'c' to "çćč", 'e' to "éèêëēėę", 'i' to "íìîïī",
        'n' to "ñń", 'o' to "óòôöõøō", 'u' to "úùûüū", 's' to "śš",
        'y' to "ýÿ", 'z' to "žź", 'g' to "ğ", 'l' to "ł",
    )

    /** For a T9 key: its digit, its letters, then every accented variant. */
    fun forDigit(digit: Char, letters: String): List<String> = buildList {
        add(digit.toString())
        letters.forEach { add(it.toString()) }
        letters.forEach { base -> ACCENTS[base]?.forEach { add(it.toString()) } }
    }

    /** For a symbol key: itself plus its corner glyph. */
    fun forSymbol(key: SymKey): List<String> =
        listOfNotNull(key.label, key.sub)
}
