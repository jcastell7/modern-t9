package io.github.jcastell7.modernt9.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * TouchPal's dark palette and metrics, sampled from the reference screenshots in
 * `touchpal-layout/`.
 *
 * Kept as plain values rather than a MaterialTheme: the keyboard is a fixed, dense
 * layout, and Material's dynamic colour would fight the reference design.
 */
@Immutable
object T9Theme {
    /** Keyboard backdrop — the gaps between keys. */
    val background = Color(0xFF16191C)

    /** Standard key face. */
    val key = Color(0xFF2A2F33)

    /** A key while held, and the selected item in a long-press popup. */
    val keyPressed = Color(0xFF4A5054)

    /** Keys that sit on the backdrop rather than being raised (right column). */
    val keyFlat = Color(0xFF1E2225)

    /** Teal accent: function labels, icons, the active candidate. */
    val accent = Color(0xFF26D0C4)

    val textPrimary = Color(0xFFECEFF1)

    /** Digit corner labels and secondary glyphs. */
    val textSecondary = Color(0xFF8B9296)

    /** Separators inside the left symbol strip. */
    val divider = Color(0xFF3A4045)

    /** Background of the shift key while armed or locked. */
    val shiftActive = Color(0xFF12433F)

    /** Long-press popup body. */
    val popup = Color(0xFF3C4247)

    // ---- metrics ----
    val keyGap = 3.dp
    val keyCorner = 6.dp
    val stripHeight = 44.dp

    /** Row height at scale 1.0. [KeyboardMetrics] scales it. */
    val baseRowHeight = 60.dp

    /** Clearance kept below the keys so the gesture bar never sits on the bottom row. */
    val minBottomInset = 28.dp

    // Column weights measured from basic-layout.png (901 px wide):
    //   symbol strip 132 · three T9 columns 206 each · right column 134
    const val WEIGHT_SIDE = 1.0f
    const val WEIGHT_LETTER = 1.55f

    // Bottom row: 12# 132 · "," 85 · space 445 · "." 85 · Search 134
    const val WEIGHT_SYM_KEY = 1.55f
    const val WEIGHT_COMMA = 1.0f
    const val WEIGHT_SPACE = 5.2f
    const val WEIGHT_SEARCH = 1.57f
}

/**
 * Live keyboard dimensions.
 *
 * Height is user-adjustable at runtime (the resize bar), so it cannot live in the
 * constants above — every pane takes this instead.
 */
@Immutable
data class KeyboardMetrics(
    /** 1.0 is the reference height; the resize bar clamps to [MIN_SCALE]..[MAX_SCALE]. */
    val scale: Float = 1f,
    /** Extra clearance below the keys, on top of the system navigation inset. */
    val bottomInset: Dp = 0.dp,
) {
    val rowHeight: Dp get() = T9Theme.baseRowHeight * scale
    /**
     * The function row. Deliberately shorter than a key row — it holds labels rather
     * than letters, and at full row height it dominated the keyboard.
     */
    val bottomRowHeight: Dp get() = T9Theme.baseRowHeight * 0.85f * scale

    /**
     * Height of the key area, excluding the top strip.
     *
     * Every pane uses this, so switching to symbols, editing or emoji never moves the
     * keyboard or changes its size.
     */
    val paneHeight: Dp get() = rowHeight * 3 + bottomRowHeight

    /**
     * The short navigation strip on panes whose body is content rather than keys —
     * emoji, clipboard, editing.
     *
     * A full [bottomRowHeight] is right under three rows of keys, but looks enormous
     * under a dense emoji grid, so those panes get a compact bar and give the reclaimed
     * space to their content. Total pane height is unchanged either way.
     */
    val toolbarHeight: Dp get() = 44.dp * scale

    /** What a content pane has left for its body once the toolbar is taken. */
    val contentHeight: Dp get() = paneHeight - toolbarHeight

    companion object {
        const val MIN_SCALE = 0.72f
        const val MAX_SCALE = 1.45f
    }
}
