package io.github.sanitised.st.chat.contract

import io.github.sanitised.st.chat.contract.ContractFixtures.asStringKeyMap
import io.github.sanitised.st.chat.contract.ContractFixtures.str
import io.github.sanitised.st.chat.contract.ContractFixtures.strList
import io.github.sanitised.st.chat.prompt.PromptBuilder
import io.github.sanitised.st.chat.prompt.WorldInfoScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        ContractDiffs.assertContract(
            "cc.payload.field-set",
            stFields.sorted(),
            payload.keys.sorted(),
        )
        // ST 独有字段必须确实不在 native payload 里，防止矩阵记录过期。
        golden.strList("st_only_fields").forEach { field ->
            assertFalse("字段 '$field' 已出现在 native payload，请更新 golden", payload.containsKey(field))
        }
    }

    @Test
    fun messageStructureDiffersOnlyAsRegistered() {
        val payload = buildNativePayload()

        @Suppress("UNCHECKED_CAST")
        val messages = payload["messages"] as List<Map<String, Any?>>
        val systemMessages = messages.filter { it["role"] == "system" }

        // native 当前把所有 prompt-manager 条目压成单条 system 消息：登记为 known-diff。
        assertEquals(1, systemMessages.size)
        ContractDiffs.assertContract(
            "cc.messages.structure",
            golden.strList("st_system_identifier_order"),
            listOf("native-single-squashed-system-message"),
        )

        // 历史轮次的角色序列必须与 ST 一致（无 known-diff 余地）。
        val historyRoles = messages.dropWhile { it["role"] == "system" }.map { it["role"] }
        ContractDiffs.assertContract("cc.messages.history-roles", golden.strList("history_roles"), historyRoles)
    }
}
