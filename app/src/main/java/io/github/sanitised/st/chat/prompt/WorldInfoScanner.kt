package io.github.sanitised.st.chat.prompt

import io.github.sanitised.st.api.WorldInfoEntry

/** Result of a world-info scan, split by insertion position relative to char defs. */
data class WorldInfoInjection(
    val before: String,
    val after: String,
)

object WorldInfoScanner {

    fun scan(entries: List<WorldInfoEntry>, scanText: String): WorldInfoInjection =
        WorldInfoEngine.scan(entries, scanText)
}

/**
 * Phase 2 world-info activation. It keeps the old scanner API while covering the
 * ST key-matching options that are represented in contract fixtures.
 */
object WorldInfoEngine {

    private const val POSITION_BEFORE = 0
    private const val POSITION_AFTER = 1
    private const val SELECTIVE_AND_ANY = 0
    private const val SELECTIVE_AND_ALL = 1
    private const val SELECTIVE_NOT_ANY = 2
    private const val SELECTIVE_NOT_ALL = 3

    fun scan(entries: List<WorldInfoEntry>, scanText: String): WorldInfoInjection {
        if (entries.isEmpty()) return WorldInfoInjection("", "")
        return scanInternal(entries, recursive = false) { scanText }
    }

    fun scan(
        entries: List<WorldInfoEntry>,
        history: List<String>,
        recursive: Boolean = false,
        defaultScanDepth: Int = DEFAULT_SCAN_DEPTH,
    ): WorldInfoInjection {
        if (entries.isEmpty()) return WorldInfoInjection("", "")
        return scanInternal(entries, recursive) { entry ->
            val depth = entry.scanDepth(defaultScanDepth).coerceAtLeast(1)
            history.takeLast(depth).joinToString("\n")
        }
    }

    private fun scanInternal(
        entries: List<WorldInfoEntry>,
        recursive: Boolean,
        scanTextFor: (WorldInfoEntry) -> String,
    ): WorldInfoInjection {
        val active = mutableListOf<WorldInfoEntry>()
        do {
            val activeText = if (recursive) active.joinToString("\n") { it.content } else ""
            val newEntries = entries.filter { entry ->
                entry.uid !in active.map { it.uid } &&
                    activates(entry, listOf(scanTextFor(entry), activeText).joinToString("\n"))
            }
            if (newEntries.isNotEmpty()) active += newEntries
        } while (recursive && newEntries.isNotEmpty())

        val grouped = applyInclusionGroups(active)

        val before = grouped.filter { it.position == POSITION_BEFORE }.joinContent()
        val after = grouped.filter { it.position == POSITION_AFTER }.joinContent()
        return WorldInfoInjection(before = before, after = after)
    }

    private fun activates(entry: WorldInfoEntry, scanText: String): Boolean {
        if (entry.disabled || entry.content.isBlank()) return false
        if (!passesProbability(entry)) return false
        val primaryMatch = entry.constant || entry.keys.anyKeyIn(scanText, entry)
        if (!primaryMatch) return false
        return if (entry.selective && entry.secondaryKeys.isNotEmpty()) {
            entry.secondaryKeys.matchesSelective(scanText, entry)
        } else {
            true
        }
    }

    private fun applyInclusionGroups(entries: List<WorldInfoEntry>): List<WorldInfoEntry> {
        val ungrouped = entries.filter { it.groupName().isBlank() || it.groupOverride() }
        val grouped = entries
            .filter { it.groupName().isNotBlank() && !it.groupOverride() }
            .groupBy { it.groupName() }
            .values
            .mapNotNull { group -> group.maxWithOrNull(compareBy<WorldInfoEntry> { it.order }.thenBy { it.uid }) }
        return (ungrouped + grouped).sortedBy { it.order }
    }

    private fun List<String>.matchesSelective(scanText: String, entry: WorldInfoEntry): Boolean {
        val matches = map { key -> keyMatches(key, scanText, entry) }
        return when (entry.raw.intValue("selectiveLogic", SELECTIVE_AND_ANY)) {
            SELECTIVE_AND_ALL -> matches.all { it }
            SELECTIVE_NOT_ANY -> matches.none { it }
            SELECTIVE_NOT_ALL -> !matches.all { it }
            else -> matches.any { it }
        }
    }

    private fun List<String>.anyKeyIn(scanText: String, entry: WorldInfoEntry): Boolean =
        any { key -> keyMatches(key, scanText, entry) }

    private fun keyMatches(key: String, scanText: String, entry: WorldInfoEntry): Boolean {
        if (key.isBlank()) return false
        parseRegexKey(key, entry.caseSensitive())?.let { regex ->
            return regex.containsMatchIn(scanText)
        }
        val needle = if (entry.caseSensitive()) key else key.lowercase()
        val haystack = if (entry.caseSensitive()) scanText else scanText.lowercase()
        return if (entry.matchWholeWords()) {
            val pattern = "(?:^|\\W)${Regex.escape(needle)}(?:$|\\W)"
            Regex(pattern).containsMatchIn(haystack)
        } else {
            haystack.contains(needle)
        }
    }

    private fun parseRegexKey(key: String, caseSensitive: Boolean): Regex? {
        if (!key.startsWith("/") || key.length < 2) return null
        val lastSlash = key.lastIndexOf('/')
        if (lastSlash <= 0) return null
        val pattern = key.substring(1, lastSlash)
        val flags = key.substring(lastSlash + 1)
        val options = buildSet {
            if (!caseSensitive || flags.contains('i')) add(RegexOption.IGNORE_CASE)
            if (flags.contains('s')) add(RegexOption.DOT_MATCHES_ALL)
            if (flags.contains('m')) add(RegexOption.MULTILINE)
        }
        return runCatching { Regex(pattern, options) }.getOrNull()
    }

    private fun passesProbability(entry: WorldInfoEntry): Boolean {
        if (!entry.raw.booleanValue("useProbability", false)) return true
        val probability = entry.raw.intValue("probability", 100).coerceIn(0, 100)
        if (probability <= 0) return false
        if (probability >= 100) return true
        val roll = ((entry.uid * 37) % 100) + 1
        return roll <= probability
    }

    private fun WorldInfoEntry.caseSensitive(): Boolean =
        raw.booleanValue("caseSensitive", false)

    private fun WorldInfoEntry.matchWholeWords(): Boolean =
        raw.booleanValue("matchWholeWords", false)

    private fun WorldInfoEntry.groupName(): String =
        (raw["group"] as? String).orEmpty()

    private fun WorldInfoEntry.groupOverride(): Boolean =
        raw.booleanValue("groupOverride", false)

    private fun WorldInfoEntry.scanDepth(defaultScanDepth: Int): Int =
        raw.intValueOrNull("scanDepth")
            ?: raw.mapValue("extensions").intValueOrNull("scan_depth")
            ?: defaultScanDepth

    private fun List<WorldInfoEntry>.joinContent(): String =
        joinToString("\n") { it.content.trim() }.trim()

    private fun Map<String, Any?>.mapValue(key: String): Map<String, Any?> =
        (this[key] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()

    private fun Map<String, Any?>.booleanValue(key: String, default: Boolean): Boolean =
        when (val value = this[key]) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> default
        }

    private fun Map<String, Any?>.intValue(key: String, default: Int): Int =
        when (val value = this[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }

    private fun Map<String, Any?>.intValueOrNull(key: String): Int? =
        when (val value = this[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }

    private const val DEFAULT_SCAN_DEPTH = 2
}
