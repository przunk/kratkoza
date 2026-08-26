package com.przunk.kratkoza.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
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
 */
@Composable
fun NumberPad(
    remainingByDigit: List<Int>,
    onDigitClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    candidateMode: Boolean = false,
    showRemainingCounts: Boolean = true,
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
                onClick = onDigitClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DigitKey(
    digit: Int,
    remaining: Int,
    showRemaining: Boolean,
    candidateMode: Boolean,
    enabled: Boolean,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exhausted = remaining == 0
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.game_digit, digit)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(KEY_CORNER_DP.dp))
            .background(if (candidateMode) colors.secondaryContainer else colors.surfaceContainerHigh)
            .clickable(enabled = enabled && !exhausted) { onClick(digit) }
            .alpha(if (exhausted) EXHAUSTED_ALPHA else 1f)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                if (!enabled || exhausted) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit.toString(),
                fontSize = KEY_DIGIT_SP.sp,
                fontWeight = FontWeight.Medium,
                color = if (candidateMode) colors.onSecondaryContainer else colors.onSurface,
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

private const val PAD_HEIGHT_DP = 64
private const val KEY_GAP_DP = 4
private const val KEY_CORNER_DP = 10
private const val KEY_DIGIT_SP = 24
private const val KEY_REMAINING_SP = 10
private const val EXHAUSTED_ALPHA = 0.20f
