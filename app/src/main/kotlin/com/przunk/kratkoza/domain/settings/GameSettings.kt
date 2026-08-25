package com.przunk.kratkoza.domain.settings

/**
 * The user's preferences (project.md section 35).
 *
 * Defaults matter here: project.md section 15 fixes automatic candidate removal at ON, and the
 * theme follows the system until the player says otherwise.
 *
 * The list is deliberately short. The remaining options of section 35 — palette, high contrast, the
 * 8/9 hint — arrive with the screens that use them; adding a field to a preferences store costs
 * nothing and needs no migration.
 */
data class GameSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** project.md section 4; follows the phone unless the player says otherwise. */
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** project.md section 15, default ON. */
    val autoRemoveCandidates: Boolean = true,
    /** project.md section 10. */
    val highlightSameDigits: Boolean = true,
    /** project.md section 19 — how many of each digit are still missing. */
    val showRemainingCounts: Boolean = true,
    /**
     * project.md section 17 — fill in the last gap of a row, column or block as a faint hint.
     * Off by default, as the section requires: it is help, and help is opt-in.
     */
    val showEightOfNineHint: Boolean = false,
) {
    companion object {
        val DEFAULT = GameSettings()
    }
}
