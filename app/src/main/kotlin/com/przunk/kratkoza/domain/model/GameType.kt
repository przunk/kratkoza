package com.przunk.kratkoza.domain.model

/**
 * What kind of game a puzzle belongs to (project.md section 6).
 *
 * Only [SOLO] is playable today. [DAILY] and [CHALLENGE] exist so that saves, history and statistics
 * are keyed by game type from the first database version — retrofitting that column later would mean
 * a migration over rows whose type can no longer be recovered.
 */
enum class GameType {
    SOLO,
    DAILY,
    CHALLENGE,
    ;

    companion object {
        fun fromStorageName(value: String): GameType? = entries.firstOrNull { it.name == value }
    }
}
