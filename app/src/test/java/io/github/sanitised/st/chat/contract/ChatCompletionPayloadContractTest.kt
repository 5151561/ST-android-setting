package io.github.sanitised.st.chat.contract

import io.github.sanitised.st.chat.contract.ContractFixtures.asStringKeyMap
import io.github.sanitised.st.chat.contract.ContractFixtures.str
import io.github.sanitised.st.chat.contract.ContractFixtures.strList
import io.github.sanitised.st.chat.prompt.PromptBuilder
import io.github.sanitised.st.chat.prompt.WorldInfoScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 0 交付 4 的 Chat Completion 部分：同一输入下对照最终 generate payload。
 *
 * ST 侧产物推导依据：
 * - 顶层字段：`SillyTavern/public/scripts/openai.js` `sendOpenAIRequest` 的 `generate_data`。
 * - 消息结构与顺序：`preparePromptsForChatCompletion` +
 *   `SillyTavern/public/scripts/PromptManager.js` `promptManagerDefaultPromptOrder`。
 */
class ChatCompletionPayloadContractTest {

    private val golden = ContractFixtures.json("goldens/cc-payload-openai.json")

    private fun buildNativePayload(): Map<String, Any?> {
        val settings = ContractFixtures.json("settings/chat-completion-openai.json")
        val character = ContractFixtures.character("seraphina.json")
        val history = ContractFixtures.chatHistory("seraphina-main.jsonl").take(3)
        val persona = ContractFixtures.json("persona/persona.json").str("persona_description")
        val wi = WorldInfoScanner.scan(
            ContractFixtures.worldBook("eldoria.json"),
            ContractFixtures.json("goldens/worldinfo-eldoria.json").str("scan_text"),
        )
        return PromptBuilder.build(
            character = character,
            userName = "Alex",
            history = history,
            settings = settings,
            personaDescription = persona,
            worldInfoBefore = wi.before,
            worldInfoAfter = wi.after,
        )
    }

    @Test
    fun scalarFieldsMatchStGenerateData() {
        val payload = buildNativePayload()
        golden["matching_fields"].asStringKeyMap().forEach { (field, stValue) ->
            ContractDiffs.assertContract("cc.payload.value.$field", stValue, payload[field])
        }
    }

    @Test
    fun payloadFieldSetDiffersOnlyAsRegistered() {
        val payload = buildNativePayload()
        val stFields = golden["matching_fields"].asStringKeyMap().keys +
            golden.strList("st_only_fields") + "messages"
        assertEquals(stFields.sorted(), payload.keys.sorted())
    }

    @Test
    fun systemMessagesFollowPromptManagerOrder() {
        val payload = buildNativePayload()

        @Suppress("UNCHECKED_CAST")
        val messages = payload["messages"] as List<Map<String, Any?>>
        val systemMessages = messages.filter { it["role"] == "system" }

        assertTrue(systemMessages.size > 1)
        val systemContents = systemMessages.map { it["content"].toString() }
        val expectedOrder = listOf(
            "Write Seraphina's next reply",
            "Eldoria is an enchanted forest realm",
            "Alex is a wandering cartographer",
            "Seraphina is a gentle forest guardian",
            "kind, protective",
            "Alex wakes up wounded",
            "The ruins hide a sealed gate",
            "Start a new Chat",
        )
        var lastIndex = -1
        expectedOrder.forEach { needle ->
            val nextIndex = systemContents.indexOfFirst { it.contains(needle) }
            assertTrue("缺少 system prompt 片段：$needle\n$systemContents", nextIndex >= 0)
            assertTrue("system prompt 片段顺序错误：$needle\n$systemContents", nextIndex > lastIndex)
            lastIndex = nextIndex
        }

        // 历史轮次的角色序列必须与 ST 一致（无 known-diff 余地）。
        val historyRoles = messages.dropWhile { it["role"] == "system" }.map { it["role"] }
        ContractDiffs.assertContract("cc.messages.history-roles", golden.strList("history_roles"), historyRoles)
    }

    @Test
    fun openAiExampleMessageStructureDiffersOnlyAsRegistered() {
        val payload = buildNativePayload()

        @Suppress("UNCHECKED_CAST")
        val messages = payload["messages"] as List<Map<String, Any?>>
        val nativeExampleShape = messages
            .filter { it["content"].toString().contains("Start a new Chat") }
            .map { "${it["role"]}:${it["content"]}" }

        ContractDiffs.assertContract(
            "cc.messages.structure",
            listOf(
                "system:[Start a new Chat]",
                "user:Who guards this grove?",
                "assistant:I do, little wanderer.",
            ),
            nativeExampleShape,
        )
    }
}
