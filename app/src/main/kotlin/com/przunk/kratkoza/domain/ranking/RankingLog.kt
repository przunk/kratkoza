package com.przunk.kratkoza.domain.ranking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The last few things the ranking did, kept so they can be read after the fact.
 *
 * Everything about Play Games happens once, quickly, and often while the player is looking at
 * something else — which made a Toast the wrong instrument for finding out what happened. It is
 * gone before it can be read, it depends on which screen was in front at the time, and its absence
 * is indistinguishable from code that never ran.
 *
 * This is deliberately in memory only. It answers "what just happened", not "what happened last
 * week", and a diagnostic that outlives the process it is diagnosing invites being trusted further
 * than it should be.
 */
class RankingLog(private val limit: Int = 20) {

    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun add(message: String) {
        // Newest first: the answer being looked for is almost always the most recent one.
        _entries.value = (listOf(message) + _entries.value).take(limit)
    }
}
