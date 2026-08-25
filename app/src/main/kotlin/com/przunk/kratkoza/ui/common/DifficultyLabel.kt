package com.przunk.kratkoza.ui.common

import androidx.annotation.StringRes
import com.przunk.kratkoza.R
import com.przunk.kratkoza.domain.model.Difficulty

/**
 * The user-facing name of a level.
 *
 * Kept apart from [Difficulty] itself so the domain never holds a resource id — and so the names,
 * which project.md section 39 leaves open, can change without touching game logic.
 */
@StringRes
fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.NORMAL -> R.string.difficulty_normal
    Difficulty.HARD -> R.string.difficulty_hard
    Difficulty.EXPERT -> R.string.difficulty_expert
    Difficulty.MASTER -> R.string.difficulty_master
}
