package io.github.sanitised.st.chat.prompt

import io.github.sanitised.st.api.WorldInfoEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldInfoScannerTest {

    private fun entry(
        uid: Int,
        keys: List<String> = emptyList(),
        content: String,
        position: Int = 0,
        order: Int = 0,
        depth: Int = 4,
        constant: Boolean = false,
        selective: Boolean = false,
        secondary: List<String> = emptyList(),
        disabled: Boolean = false,
        raw: Map<String, Any?> = emptyMap(),
    ) = WorldInfoEntry(
        uid = uid, keys = keys, secondaryKeys = secondary, content = content,
        order = order, depth = depth, position = position, constant = constant,
        selective = selective, disabled = disabled, raw = raw
    )

    @Test
    fun activatesByKeywordAndSplitsByPosition() {
        val entries = listOf(
            entry(1, keys = listOf("dragon"), content = "Dragons breathe fire.", position = 0),
            entry(2, keys = listOf("castle"), content = "The castle is old.", position = 1),
            entry(3, keys = listOf("unicorn"), content = "Never mentioned.", position = 0),
        )
        val result = WorldInfoScanner.scan(entries, "We saw a DRAGON near the castle.")

        assertEquals("Dragons breathe fire.", result.before)
        assertEquals("The castle is old.", result.after)
        assertTrue(!result.before.contains("Never"))
    }

    @Test
    fun constantAlwaysActivatesAndDisabledNeverDoes() {
        val entries = listOf(
            entry(1, content = "Always here.", constant = true, position = 0),
            entry(2, keys = listOf("dragon"), content = "Off.", disabled = true, position = 0),
        )
        val result = WorldInfoScanner.scan(entries, "dragon")
        assertEquals("Always here.", result.before)
    }

    @Test
    fun selectiveRequiresSecondaryKey() {
        val base = entry(
            1, keys = listOf("king"), content = "Royal lore.", position = 0,
            selective = true, secondary = listOf("crown")
        )
        assertEquals("", WorldInfoScanner.scan(listOf(base), "the king walked").before)
        assertEquals("Royal lore.", WorldInfoScanner.scan(listOf(base), "the king wore a crown").before)
    }

    @Test
    fun ordersByOrderField() {
        val entries = listOf(
            entry(1, keys = listOf("a"), content = "second", position = 0, order = 10),
            entry(2, keys = listOf("a"), content = "first", position = 0, order = 1),
        )
        assertEquals("first\nsecond", WorldInfoScanner.scan(entries, "a").before)
    }

    @Test
    fun honorsPhase2KeyMatchingOptions() {
        val entries = listOf(
            entry(
                1,
                keys = listOf("Drake"),
                content = "case sensitive",
                raw = mapOf("caseSensitive" to true),
            ),
            entry(
                2,
                keys = listOf("art"),
                content = "whole word",
                raw = mapOf("matchWholeWords" to true),
            ),
            entry(3, keys = listOf("/sig-?nal/"), content = "regex"),
        )

        val result = WorldInfoScanner.scan(
            entries,
            "the drake circled the particle accelerator while a signal flashed",
        )

        assertEquals("regex", result.before)
    }

    @Test
    fun honorsSelectiveNotAnyAndDeterministicProbabilityRolls() {
        val entries = listOf(
            entry(
                1,
                keys = listOf("station"),
                secondary = listOf("abandoned"),
                selective = true,
                content = "not any",
                raw = mapOf("selectiveLogic" to 2),
            ),
            entry(
                2,
                keys = listOf("signal"),
                content = "low probability",
                raw = mapOf("useProbability" to true, "probability" to 1),
            ),
            entry(
                3,
                keys = listOf("signal"),
                content = "certain probability",
                raw = mapOf("useProbability" to true, "probability" to 100),
            ),
        )

        val result = WorldInfoScanner.scan(entries, "an abandoned station emitted a signal")

        assertEquals("certain probability", result.before)
    }

    @Test
    fun worldInfoEngineHonorsDepthRecursionAndInclusionGroups() {
        val entries = listOf(
            entry(
                1,
                keys = listOf("ancient"),
                content = "too old",
                depth = 1,
                raw = mapOf("scanDepth" to 1),
            ),
            entry(
                2,
                keys = listOf("map"),
                content = "The ruins contain a beacon.",
                order = 1,
            ),
            entry(
                3,
                keys = listOf("beacon"),
                content = "Recursive beacon lore.",
                order = 2,
            ),
            entry(
                4,
                keys = listOf("map"),
                content = "Low group entry.",
                order = 1,
                raw = mapOf("group" to "guild"),
            ),
            entry(
                5,
                keys = listOf("map"),
                content = "High group entry.",
                order = 10,
                raw = mapOf("group" to "guild"),
            ),
        )

        val result = WorldInfoEngine.scan(
            entries = entries,
            history = listOf("Alex: ancient seal", "Alex: recent map"),
            recursive = true,
        )

        assertTrue(!result.before.contains("too old"))
        assertTrue(result.before.contains("The ruins contain a beacon."))
        assertTrue(result.before.contains("Recursive beacon lore."))
        assertTrue(!result.before.contains("Low group entry."))
        assertTrue(result.before.contains("High group entry."))
    }

    @Test
    fun usesScanDepthForMatchingAndKeepsInsertionDepthSeparate() {
        val entries = listOf(
            entry(
                1,
                keys = listOf("ancient"),
                content = "Ancient lore.",
                depth = 1,
                raw = mapOf("scanDepth" to 2),
            )
        )

        val result = WorldInfoEngine.scan(
            entries = entries,
            history = listOf("Alex: ancient seal", "Seraphina: recent reply"),
            defaultScanDepth = 1,
        )

        assertEquals("Ancient lore.", result.before)
    }
}
