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
    fun turnJoiningDiffersOnlyAsRegistered() {
        val result = buildAlpaca()
        val stTurns = golden.strList("st_turns")
        val nativeChatSegment = stTurns.joinToString("\n")
        assertTrue(
            "native prompt 应包含按 \\n 连接的轮次（实现变化请同步契约）",
            result.prompt.contains(nativeChatSegment),
        )
        ContractDiffs.assertContract("tc.full-prompt.turn-join", stTurns.joinToString(""), nativeChatSegment)
    }

    @Test
    fun instructStoryStringSuffixHandlingDiffersOnlyAsRegistered() {
        // 上游 Alpaca instruct 预设携带 story_string_suffix="\n\n"（新版 ST 放在 instruct 字段），
        // native 只检查 power_user.context 下的同名字段，因此该后缀被静默忽略而非 fallback。
        ContractDiffs.assertContract(
            "tc.instruct.story-string-prefix-suffix",
            "ST 应用 instruct 预设中的 story_string_prefix/suffix（如 ChatML 的 <|im_start|>system 包装）",
            "native 忽略 instruct 预设中的 story_string_prefix/suffix，仅检查 power_user.context",
        )
    }

    @Test
    fun mesExamplesInjectionDiffersOnlyAsRegistered() {
        val result = buildAlpaca()
        assertTrue(
            "fixture story_string 不含 {{mesExamples}}，native 应当丢弃示例对话（实现变化请同步契约）",
            !result.prompt.contains("Who guards this grove"),
        )
        ContractDiffs.assertContract(
            "tc.examples-not-injected",
            "ST 通过 formatInstructModeExamples 独立注入 mes_example（不依赖 story_string 占位符）",
            "native 仅在 story_string 含 {{mesExamples}} 时注入，否则丢弃示例对话",
        )
    }

    @Test
    fun upstreamChatmlPresetIsExplicitFallbackNotSilentDivergence() {
        val settings = ContractFixtures.json("settings/text-completion-chatml-ollama.json")
        val result = TextPromptBuilder.build(
            character = ContractFixtures.character("seraphina.json"),
            userName = "Alex",
            history = ContractFixtures.chatHistory("seraphina-main.jsonl").take(3),
            settings = settings,
        )
        val reason = (result as? TextPromptBuildResult.Unsupported)?.reason
            ?: error("上游 ChatML context 预设含 {{#if}}，native 必须显式 fallback 而不是静默组装：$result")
        ContractDiffs.assertContract(
            "tc.chatml.explicit-fallback",
            golden.str("st_chatml_expected_reason"),
            reason,
        )
    }
}
