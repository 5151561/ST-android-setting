package io.github.sanitised.st.chat.contract

import io.github.sanitised.st.chat.contract.ContractFixtures.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 0 交付 3：`chat-contract-fixtures` 样本集存在且形状可用。
 * 这组断言冻结夹具的最低形状，后续 Phase 2 的逐项 diff 直接复用同一批输入。
 */
class ChatContractFixturesTest {

    @Test
    fun characterCardsLoad() {
        val seraphina = ContractFixtures.character("seraphina.json")
        assertEquals("Seraphina", seraphina.name)
        assertTrue(seraphina.systemPrompt.isNotBlank())
        assertTrue(seraphina.messageExample.isNotBlank())
        assertEquals("eldoria", seraphina.world)

        val plain = ContractFixtures.character("plain-bot.json")
        assertEquals("Plain Bot", plain.name)
        assertTrue(plain.systemPrompt.isBlank())

        val macro = ContractFixtures.character("macro-weaver.json")
        assertTrue(macro.description.contains("{{user}}"))
        assertTrue(macro.scenario.contains("{{char}}"))
    }

    @Test
    fun personaFixtureLoads() {
        val persona = ContractFixtures.json("persona/persona.json")
        assertEquals("Alex", persona.str("name"))
        assertTrue(persona.str("persona_description").isNotBlank())
    }

    @Test
    fun settingsFixturesLoad() {
        val cc = ContractFixtures.json("settings/chat-completion-openai.json")
        assertEquals("openai", cc["main_api"])

        val alpaca = ContractFixtures.json("settings/text-completion-alpaca-ooba.json")
        assertEquals("textgenerationwebui", alpaca["main_api"])
        val alpacaInstruct = (alpaca["power_user"] as Map<*, *>)["instruct"] as Map<*, *>
        assertEquals("### Instruction:", alpacaInstruct["input_sequence"])

        val chatml = ContractFixtures.json("settings/text-completion-chatml-ollama.json")
        val chatmlContext = (chatml["power_user"] as Map<*, *>)["context"] as Map<*, *>
        // 上游 ChatML context 预设原文带 {{#if}} Handlebars 块。
        assertTrue((chatmlContext["story_string"] as String).contains("{{#if"))
    }

    @Test
    fun worldBooksLoad() {
        val eldoria = ContractFixtures.worldBook("eldoria.json")
        assertEquals(6, eldoria.size)
        assertTrue(eldoria.any { it.constant })
        assertTrue(eldoria.any { it.selective })
        assertTrue(eldoria.any { it.disabled })

        val frontier = ContractFixtures.worldBook("frontier-station.json")
        assertEquals(5, frontier.size)
        assertTrue(frontier.any { it.raw["caseSensitive"] == true })
        assertTrue(frontier.any { it.raw["matchWholeWords"] == true })
        assertTrue(frontier.any { it.raw["useProbability"] == true })
    }

    @Test
    fun chatJsonlFixturesLoad() {
        val single = ContractFixtures.jsonl("chats/seraphina-main.jsonl")
        assertEquals(5, single.size) // header + 4 messages
        val history = ContractFixtures.chatHistory("seraphina-main.jsonl")
        assertEquals(4, history.size)
        assertTrue(history[1].swipes.size >= 2)
        assertTrue(history[1].extra.has("reasoning"))
        assertTrue(history[2].extra.has("files"))

        val group = ContractFixtures.jsonl("chats/group-roundtable.jsonl")
        assertTrue(group.size >= 3)
    }

    @Test
    fun extensionSettingsFixtureLoads() {
        val extensions = ContractFixtures.json("extensions/extension-settings.json")
        assertTrue(extensions.containsKey("regex"))
        assertTrue(extensions.containsKey("quickReply"))
    }

    @Test
    fun knownDiffMatrixEntriesAreDocumented() {
        ContractDiffs.assertAllEntriesDocumented()
    }

    @Test
    fun knownDiffMatrixHasNoOrphanEntries() {
        // 与各契约测试实际行使的 known-diff id 一一对应；新增/删除差异时同步这份清单。
        ContractDiffs.assertMatrixIdsAreExactly(
            setOf(
                "cc.messages.structure",
                "wi.probability.randomness",
                "wi.inclusion-group.selection",
                "wi.position.non-prompt-injections",
                "ops.swipe.wrap-around",
                "ops.create-swipe.switch-and-info",
            )
        )
    }
}
