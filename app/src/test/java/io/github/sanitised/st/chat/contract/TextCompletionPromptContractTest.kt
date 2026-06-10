package io.github.sanitised.st.chat.contract

import io.github.sanitised.st.chat.contract.ContractFixtures.asStringKeyMap
import io.github.sanitised.st.chat.contract.ContractFixtures.str
import io.github.sanitised.st.chat.contract.ContractFixtures.strList
import io.github.sanitised.st.chat.prompt.InstructSettings
import io.github.sanitised.st.chat.prompt.InstructTemplate
import io.github.sanitised.st.chat.prompt.InstructTurnPosition
import io.github.sanitised.st.chat.prompt.TextPromptBuildResult
import io.github.sanitised.st.chat.prompt.TextPromptBuilder
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 0 交付 4 的 Text Completion 部分：上游真实预设下对照 prompt 片段与 stop strings 集合。
 *
 * ST 侧产物推导依据：
 * - 轮次格式 / 生成尾部：`SillyTavern/public/scripts/instruct-mode.js`
 *   `formatInstructModeChat` / `formatInstructModePrompt`。
 * - stop strings：同文件 `getInstructStoppingSequences` + `script.js` `getStoppingStrings`。
 * - 轮次拼接：`script.js` `finalMesSend.map(...).join('')`（空串连接）。
 * - 预设原文：`SillyTavern/default/content/presets/instruct|context/{Alpaca,ChatML}.json`。
 */
class TextCompletionPromptContractTest {

    private val golden = ContractFixtures.json("goldens/tc-alpaca.json")

    private fun buildAlpaca(): TextPromptBuildResult.Ready {
        val settings = ContractFixtures.json("settings/text-completion-alpaca-ooba.json")
        val result = TextPromptBuilder.build(
            character = ContractFixtures.character("seraphina.json"),
            userName = "Alex",
            history = ContractFixtures.chatHistory("seraphina-main.jsonl").take(3),
            settings = settings,
            personaDescription = ContractFixtures.json("persona/persona.json").str("persona_description"),
        )
        return result as? TextPromptBuildResult.Ready
            ?: error("Alpaca fixture 应当被 native 接受：$result")
    }

    private fun alpacaTemplate(): InstructTemplate {
        val settings = ContractFixtures.json("settings/text-completion-alpaca-ooba.json")
        val instruct = ((settings["power_user"] as Map<*, *>)["instruct"]).asStringKeyMap()
        return InstructTemplate(InstructSettings.fromMap(instruct), "Alex", "Seraphina")
    }

    @Test
    fun formattedTurnsMatchStInstructMode() {
        val template = alpacaTemplate()
        val history = ContractFixtures.chatHistory("seraphina-main.jsonl").take(3)
        val stTurns = golden.strList("st_turns")

        val nativeTurns = history.mapIndexed { index, message ->
            template.formatChat(
                name = message.name,
                message = message.mes,
                isUser = message.isUser,
                position = when (index) {
                    0 -> InstructTurnPosition.FIRST
                    history.lastIndex -> InstructTurnPosition.LAST
                    else -> InstructTurnPosition.NORMAL
                },
            )
        }
        nativeTurns.forEachIndexed { index, turn ->
            ContractDiffs.assertContract("tc.alpaca.turn-$index", stTurns[index], turn)
        }
    }

    @Test
    fun promptTailMatchesStFormatInstructModePrompt() {
        ContractDiffs.assertContract(
            "tc.alpaca.prompt-tail",
            golden.str("st_prompt_tail"),
            alpacaTemplate().formatPrompt(name = "Seraphina"),
        )
    }

    @Test
    fun stopStringSetMatchesSt() {
        val result = buildAlpaca()
        ContractDiffs.assertContract(
            "tc.alpaca.stop-strings",
            golden.strList("st_stop_strings").toSet(),
            result.stopStrings.toSet(),
        )
    }

    @Test
    fun turnJoiningMatchesStWithoutExtraSeparators() {
        val result = buildAlpaca()
        val stTurns = golden.strList("st_turns")
        assertTrue(
            "native prompt 应包含按 ST 空串拼接的轮次，不额外插入空行",
            result.prompt.contains(stTurns.joinToString("")),
        )
    }

    @Test
    fun instructStoryStringAffixesFromInstructPresetAreApplied() {
        val settings = ContractFixtures.json("settings/text-completion-chatml-ollama.json")
        val result = TextPromptBuilder.build(
            character = ContractFixtures.character("seraphina.json"),
            userName = "Alex",
            history = ContractFixtures.chatHistory("seraphina-main.jsonl").take(1),
            settings = settings,
        ) as? TextPromptBuildResult.Ready
            ?: error("ChatML fixture 应当由 Phase 2 原生 story renderer 接受")

        assertTrue(result.prompt.startsWith("<|im_start|>system"))
        assertTrue(result.prompt.contains("Write Seraphina's next reply"))
        assertTrue(result.prompt.contains("<|im_end|>\n"))
    }

    @Test
    fun mesExamplesAreInjectedIndependentlyOfStoryStringPlaceholder() {
        val result = buildAlpaca()
        assertTrue(
            "ST 通过 formatInstructModeExamples 独立注入 mes_example，native 也必须保留示例对话",
            result.prompt.contains("Who guards this grove"),
        )
        assertTrue(
            "示例对话应使用当前 instruct preset 格式化",
            result.prompt.contains("### Instruction:\nWho guards this grove?"),
        )
    }

    @Test
    fun upstreamChatmlPresetBuildsNativePrompt() {
        val settings = ContractFixtures.json("settings/text-completion-chatml-ollama.json")
        val result = TextPromptBuilder.build(
            character = ContractFixtures.character("seraphina.json"),
            userName = "Alex",
            history = ContractFixtures.chatHistory("seraphina-main.jsonl").take(3),
            settings = settings,
        )
        val ready = result as? TextPromptBuildResult.Ready
            ?: error("Phase 2 应支持 ChatML 常见模板，不再 fallback：$result")
        assertTrue(ready.prompt.contains("<|im_start|>user"))
        assertTrue(ready.prompt.contains("<|im_start|>assistant"))
        assertTrue(ready.stopStrings.any { it.contains("<|im_end|>") })
    }
}
