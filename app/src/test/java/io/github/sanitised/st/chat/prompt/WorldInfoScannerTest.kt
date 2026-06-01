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
        constant: Boolean = false,
        selective: Boolean = false,
        secondary: List<String> = emptyList(),
        disabled: Boolean = false,
    ) = WorldInfoEntry(
        uid = uid, keys = keys, secondaryKeys = secondary, content = content,
        order = order, position = position, constant = constant,
        selective = selective, disabled = disabled
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
}
