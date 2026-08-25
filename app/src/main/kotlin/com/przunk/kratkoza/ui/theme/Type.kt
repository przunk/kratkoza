package com.przunk.kratkoza.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 defaults, plus the two board-specific styles. Board text is sized in `sp` so it still
 * follows the system font scale, but the board itself scales its own text from the cell size (see
 * SudokuBoardView) to keep the grid geometry intact, as project.md section 8 requires.
 */
internal val KratkozaTypography = Typography()

internal val DigitTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 28.sp,
)

internal val CandidateTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
)
