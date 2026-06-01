package io.github.sanitised.st.chat.prompt

import io.github.sanitised.st.api.WorldInfoEntry

/** Result of a world-info scan, split by insertion position relative to char defs. */
data class WorldInfoInjection(
    val before: String,
    val after: String,
)

/**
 * Minimal world-info (lorebook) activation, replicating the core of SillyTavern's
 * world-info.js scan: an entry activates if it is `constant`, or if any primary key
 * appears in the recent chat text; `selective` entries additionally require a
 * secondary key match. Active entries are split by `position` (0 = before char defs,
 * 1 = after char defs) and concatenated by `order`.
 *
 * Out of scope for now (kept on the WebView fallback): depth/AN-positioned entries,
 * recursion, probability, inclusion groups, regex keys, scan-depth per entry.
 *
 * Pure Kotlin so it can be unit-tested directly.
 */
object WorldInfoScanner {

    private const val POSITION_BEFORE = 0
    private const val POSITION_AFTER = 1

    fun scan(entries: List<WorldInfoEntry>, scanText: String): WorldInfoInjection {
        if (entries.isEmpty()) return WorldInfoInjection("", "")
        val haystack = scanText.lowercase()

        val active = entries.filter { entry ->
            if (entry.disabled || entry.content.isBlank()) return@filter false
            val primaryMatch = entry.constant || entry.keys.anyKeyIn(haystack)
            if (!primaryMatch) return@filter false
            if (entry.selective && entry.secondaryKeys.isNotEmpty()) {
                entry.secondaryKeys.anyKeyIn(haystack)
            } else {
                true
            }
        }.sortedBy { it.order }

        val before = active.filter { it.position == POSITION_BEFORE }.joinContent()
        val after = active.filter { it.position == POSITION_AFTER }.joinContent()
        return WorldInfoInjection(before = before, after = after)
    }

    private fun List<String>.anyKeyIn(haystack: String): Boolean =
        any { key -> key.isNotBlank() && haystack.contains(key.lowercase()) }

    private fun List<WorldInfoEntry>.joinContent(): String =
        joinToString("\n") { it.content.trim() }.trim()
}
