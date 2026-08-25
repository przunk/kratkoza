package com.przunk.kratkoza.domain.settings

/**
 * The language the app shows itself in (project.md section 4).
 *
 * [SYSTEM] means "whatever the phone is set to", which is the default and what most people want.
 * The explicit choices exist because a phone in one language and a game in another is a perfectly
 * normal thing to want, and Android only offers that picker on API 33 and above.
 */
enum class AppLanguage(
    /** BCP 47 tag, or `null` to follow the system. Must match a folder in `res/`. */
    val tag: String?,
) {
    SYSTEM(null),
    ENGLISH("en"),
    POLISH("pl"),
    ;

    companion object {
        fun fromStorageName(value: String): AppLanguage? = entries.firstOrNull { it.name == value }
    }
}
