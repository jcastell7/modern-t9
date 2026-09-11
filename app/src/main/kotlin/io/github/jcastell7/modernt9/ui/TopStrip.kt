package io.github.jcastell7.modernt9.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jcastell7.modernt9.engine.Candidate
import io.github.jcastell7.modernt9.engine.CandidateSource

/**
 * The bar above the keys. It has three states, all visible in the reference shots:
 *
 *  * idle          — the icon bar (`basic-layout.png`)
 *  * composing     — the candidate list (`normal-words.png`, `custom-word-appears.png`)
 *  * unknown word  — the add-to-dictionary offer (`new-word.png`)
 */
@Composable
fun TopStrip(
    candidates: List<Candidate>,
    newWord: String?,
    /** True when these are next-word guesses rather than the current composition. */
    isPrediction: Boolean,
    onAction: (KeyAction) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(T9Theme.stripHeight)
            .background(T9Theme.background),
    ) {
        when {
            newWord != null -> NewWordBar(newWord, onAction)
            candidates.isNotEmpty() -> CandidateRow(candidates, isPrediction, onAction)
            else -> IconBar(onAction)
        }
    }
}

/**
 * `new-word.png` — a word the dictionary does not know, offered for saving.
 * ⊕ adds it, ✕ dismisses it.
 */
@Composable
private fun NewWordBar(word: String, onAction: (KeyAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "⊕",
            color = T9Theme.accent,
            fontSize = 22.sp,
            modifier = Modifier
                .clickable { onAction(KeyAction.SaveNewWord(word)) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            text = word,
            color = T9Theme.textPrimary,
            fontSize = 21.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            "✕",
            color = T9Theme.textSecondary,
            fontSize = 20.sp,
            modifier = Modifier
                .clickable { onAction(KeyAction.DismissNewWord) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** The candidate list. The first entry is teal — it is what space will accept. */
@Composable
private fun CandidateRow(
    candidates: List<Candidate>,
    isPrediction: Boolean,
    onAction: (KeyAction) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) {
            itemsIndexed(candidates) { index, candidate ->
                Text(
                    text = candidate.text,
                    color = when {
                        index == 0 -> T9Theme.accent
                        candidate.source == CandidateSource.PHRASE -> T9Theme.accent
                        candidate.source == CandidateSource.EDITING -> T9Theme.accent
                        candidate.source == CandidateSource.LETTER -> T9Theme.textPrimary
                        candidate.source == CandidateSource.LITERAL -> T9Theme.textSecondary
                        else -> T9Theme.textPrimary
                    },
                    fontSize = 21.sp,
                    fontWeight = if (index == 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier
                        .clickable {
                            // A prediction is not part of any composition, so it cannot
                            // be selected by index — it is committed directly.
                            if (isPrediction) onAction(KeyAction.NextWord(candidate))
                            else onAction(KeyAction.SelectCandidate(index))
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        // The chevron TouchPal shows when the list overflows.
        if (candidates.size > 3) {
            Text(
                "›",
                color = T9Theme.textSecondary,
                fontSize = 24.sp,
                modifier = Modifier
                    .clickable { onAction(KeyAction.ExpandCandidates) }
                    .padding(horizontal = 10.dp),
            )
        }
    }
}

/** `basic-layout.png` top bar: cursor tools, gesture hint, theme, emoji. */
@Composable
private fun IconBar(onAction: (KeyAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StripIcon("\u2336", T9Theme.textPrimary) { onAction(KeyAction.ShowLayer(KeyboardLayer.EDIT)) }
        StripIcon("\u223F", T9Theme.textPrimary) { onAction(KeyAction.ToggleResize) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StripIcon("\u2756", T9Theme.textPrimary) { onAction(KeyAction.OpenSettings) }
            StripIcon("\u263A\uFE0E", T9Theme.textPrimary) { onAction(KeyAction.ShowLayer(KeyboardLayer.EMOJI)) }
        }
    }
}

@Composable
private fun StripIcon(glyph: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text = glyph,
        color = color,
        fontSize = 20.sp,
        maxLines = 1,
        // No fixed box: a 20sp glyph inside a 38dp box with 9dp padding was clipped.
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
