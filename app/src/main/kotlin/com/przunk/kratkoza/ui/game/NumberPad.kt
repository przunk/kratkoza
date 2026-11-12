package com.przunk.kratkoza.ui.game

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.sudoku.Sudoku

/**
 * The 1–9 pad — the primary way digits get onto the board (project.md section 11).
 *
 * One row of nine. Each key is roughly 38dp wide on a narrow phone, under the 48dp guideline, but
 * the keys are flush against one another and 64dp tall: the touch area per key comes out larger than
 * a 48x48 square and there is no dead space between them to miss into. A 3x3 pad would clear the
 * guideline literally, at the cost of a third of the board's height in portrait.
 *
 * A key whose digit is finished — all nine correctly placed — is strongly dimmed and refuses the
 * press. Pressing it could only ever be a mistake, so offering it is offering a trap.
 *
 * [showRemainingCounts] governs the small count under each key, which project.md section 19 makes
 * an option. It does not govern the refusal: whether a move is possible is not a matter of taste,
 * and a key that silently did nothing when the count was hidden would be worse than either.
 *
 * A long press pins the key's digit ([insertDigit]): tapping cells then places it. The pinned key
 * is told apart by a thick outline and a filled container, not by its colour alone, and reports
 * itself as selected to TalkBack. [enabled] governs the short press, [pinEnabled] the long one —
 * pinning needs no selected cell, which is the reason it exists.
 */
@Composable
fun NumberPad(
    remainingByDigit: List<Int>,
    onDigitClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    candidateMode: Boolean = false,
    showRemainingCounts: Boolean = true,
    insertDigit: Int? = null,
    pinEnabled: Boolean = false,
    onDigitLongClick: (Int) -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().height(PAD_HEIGHT_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP_DP.dp),
    ) {
        for (digit in Sudoku.DIGITS) {
            DigitKey(
                digit = digit,
                remaining = remainingByDigit.getOrElse(digit) { 0 },
                showRemaining = showRemainingCounts,
                candidateMode = candidateMode,
                enabled = enabled,
                pinned = digit == insertDigit,
                pinEnabled = pinEnabled,
                onClick = onDigitClick,
                onLongClick = onDigitLongClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DigitKey(
    digit: Int,
    remaining: Int,
    showRemaining: Boolean,
    candidateMode: Boolean,
    enabled: Boolean,
    pinned: Boolean,
    pinEnabled: Boolean,
    onClick: (Int) -> Unit,
    onLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exhausted = remaining == 0
    val canClick = enabled && !exhausted
    val canPin = pinEnabled && !exhausted
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.game_digit, digit)
    val pinLabel = stringResource(R.string.game_pin_digit)
    val pinnedState = stringResource(R.string.game_digit_pinned)
    val availableBackground = when {
        pinned -> colors.primaryContainer
        candidateMode -> colors.secondaryContainer
        else -> colors.surfaceContainerHigh
    }
    // Keeping the container opaque preserves its silhouette; fading the entire key made the
    // exhausted state blend into the screen instead of looking deliberately unavailable.
    val background = if (exhausted) {
        lerp(availableBackground, Color.Black, UNAVAILABLE_BACKGROUND_DARKENING)
    } else {
        availableBackground
    }

    // Two nodes on purpose. clearAndSetSemantics only clears what lies *below* its node, so on the
    // same node the gesture handler would keep announcing a tap even when the tap does nothing —
    // which is exactly the case before a cell is chosen. The outer node says what the key really
    // offers; the inner one only receives the touch.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (pinned) {
                    selected = true
                    stateDescription = pinnedState
                }
                if (canClick) {
                    onClick { onClick(digit); true }
                }
                if (canPin) {
                    onLongClick(label = pinLabel) { onLongClick(digit); true }
                }
                if (!canClick && !canPin) disabled()
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(KEY_CORNER_DP.dp))
                .background(background)
                .then(
                    if (pinned) {
                        Modifier.border(
                            width = PINNED_BORDER_DP.dp,
                            color = colors.primary,
                            shape = RoundedCornerShape(KEY_CORNER_DP.dp),
                        )
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(
                    enabled = canClick || canPin,
                    onClick = { if (canClick) onClick(digit) },
                    onLongClick = if (canPin) ({ onLongClick(digit) }) else null,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(if (exhausted) UNAVAILABLE_CONTENT_ALPHA else 1f),
            ) {
                Text(
                    text = digit.toString(),
                    fontSize = KEY_DIGIT_SP.sp,
                    fontWeight = if (pinned) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        pinned -> colors.onPrimaryContainer
                        candidateMode -> colors.onSecondaryContainer
                        else -> colors.onSurface
                    },
                )
                if (showRemaining) {
                    Text(
                        text = remaining.toString(),
                        fontSize = KEY_REMAINING_SP.sp,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private const val PAD_HEIGHT_DP = 64
private const val KEY_GAP_DP = 4
private const val KEY_CORNER_DP = 10
private const val PINNED_BORDER_DP = 3
private const val KEY_DIGIT_SP = 24
private const val KEY_REMAINING_SP = 10
private const val UNAVAILABLE_CONTENT_ALPHA = 0.12f
private const val UNAVAILABLE_BACKGROUND_DARKENING = 0.32f
