package io.github.sanitised.st.chat.prompt

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.resolveTextGenServer
import io.github.sanitised.st.chat.ChatMessage

sealed class TextPromptBuildResult {
    data class Ready(
        val payload: Map<String, Any?>,
        val prompt: String,
        val stopStrings: List<String>,
    ) : TextPromptBuildResult()

    data class Unsupported(val reason: String) : TextPromptBuildResult()
}

object TextPromptBuilder {
    private const val CHARS_PER_TOKEN = 4
    private const val PER_TURN_OVERHEAD_TOKENS = 4
    private const val DEFAULT_MAX_CONTEXT = 2048
    private const val DEFAULT_AUTHORS_NOTE_DEPTH = 4

    private val supportedApiTypes = setOf("ooba", "koboldcpp", "llamacpp", "ollama")
    private val allowedStoryPlaceholders = setOf(
        "system",
        "description",
        "personality",
        "scenario",
        "persona",
        "wiBefore",
        "wiAfter",
        "loreBefore",
        "loreAfter",
        "anchorBefore",
        "anchorAfter",
        "char",
        "user",
        "mesExamples",
        "mesExamplesRaw",
        "trim",
    )

    fun build(
        character: CharacterDetail,
        userName: String,
        history: List<ChatMessage>,
        settings: Map<String, Any?>,
        personaDescription: String = "",
        worldInfoBefore: String = "",
        worldInfoAfter: String = "",
        authorsNote: String = "",
    ): TextPromptBuildResult {
        unsupportedReason(settings)?.let { return TextPromptBuildResult.Unsupported(it) }
        val textGen = settings.mapValue("textgenerationwebui_settings")
        val apiType = textGen.stringValue("type").ifBlank { "ooba" }
        val maxTokens = settings.intValue("amount_gen", 300)
        val maxContext = textGen.intValue("max_context", DEFAULT_MAX_CONTEXT)
        val model = modelForApiType(textGen, apiType)

        val powerUser = settings.mapValue("power_user")
        val context = ContextTemplateSettings.fromMap(powerUser.mapValue("context"))
        val instruct = InstructSettings.fromMap(powerUser.mapValue("instruct"))
        val template = InstructTemplate(instruct, userName, character.name)

        val prompt = buildPrompt(
            character = character,
            userName = userName,
            history = history,
            maxContext = maxContext,
            maxTokens = maxTokens,
            personaDescription = personaDescription,
            worldInfoBefore = worldInfoBefore,
            worldInfoAfter = worldInfoAfter,
            authorsNote = authorsNote,
            context = context,
            instruct = instruct,
            template = template,
        )
        val stopStrings = StopStringBuilder.build(instruct, context, userName, character.name)
        val payload = linkedMapOf<String, Any?>(
            "prompt" to prompt,
            "model" to model,
            "api_type" to apiType,
            "api_server" to resolveTextGenServer(settings, apiType),
            "stream" to false,
            "max_new_tokens" to maxTokens,
            "max_tokens" to maxTokens,
            "truncation_length" to maxContext,
            "temperature" to textGen.doubleValue("temp", 0.7),
            "top_p" to textGen.doubleValue("top_p", 1.0),
            "top_k" to textGen.intValue("top_k", 0),
            "repetition_penalty" to textGen.doubleValue("rep_pen", 1.0),
            "rep_pen" to textGen.doubleValue("rep_pen", 1.0),
            "stopping_strings" to stopStrings,
            "stop" to stopStrings,
        )
        if (apiType == "ollama" || apiType == "llamacpp") {
            payload["num_ctx"] = maxContext
        }
        textGen.intListValue("sampler_order")?.let { payload["sampler_order"] = it }
        textGen.stringListValue("sampler_priority")?.let { payload["sampler_priority"] = it }
        return TextPromptBuildResult.Ready(payload, prompt, stopStrings)
    }

    @Suppress("UNUSED_PARAMETER")
    fun supports(settings: Map<String, Any?>, authorsNote: String = ""): Boolean =
        unsupportedReason(settings) == null

    private fun unsupportedReason(settings: Map<String, Any?>): String? {
        if (settings["main_api"] != "textgenerationwebui") {
            return "main_api is not textgenerationwebui"
        }
        val textGen = settings.mapValue("textgenerationwebui_settings")
        val apiType = textGen.stringValue("type").ifBlank { "ooba" }
        if (apiType !in supportedApiTypes) {
            return "unsupported api_type: $apiType"
        }
        val powerUser = settings.mapValue("power_user")
        val context = ContextTemplateSettings.fromMap(powerUser.mapValue("context"))
        if (context.storyStringPosition != 0) {
            return "unsupported story_string_position: ${context.storyStringPosition}"
        }
        unknownStoryPlaceholder(context.storyString)?.let {
            return "unsupported story_string placeholder: $it"
        }
        return null
    }

    private fun buildPrompt(
        character: CharacterDetail,
        userName: String,
        history: List<ChatMessage>,
        maxContext: Int,
        maxTokens: Int,
        personaDescription: String,
        worldInfoBefore: String,
        worldInfoAfter: String,
        authorsNote: String,
        context: ContextTemplateSettings,
        instruct: InstructSettings,
        template: InstructTemplate,
    ): String {
        val values = mapOf(
            "system" to character.systemPrompt,
            "description" to character.description,
            "personality" to character.personality,
            "scenario" to character.scenario,
            "persona" to personaDescription,
            "wiBefore" to worldInfoBefore,
            "wiAfter" to worldInfoAfter,
            "loreBefore" to worldInfoBefore,
            "loreAfter" to worldInfoAfter,
            "anchorBefore" to "",
            "anchorAfter" to "",
            "char" to character.name,
            "user" to userName,
            "mesExamples" to character.messageExample,
            "mesExamplesRaw" to character.messageExample,
            "trim" to "",
        ).mapValues { (_, value) -> substituteMacros(value, userName, character.name) }

        val renderedStory = renderStoryString(context.storyString, values).trimEnd()
        val header = if (instruct.enabled) {
            val storyPrefix = context.storyStringPrefix.ifBlank { instruct.storyStringPrefix }
            val storySuffix = context.storyStringSuffix.ifBlank { instruct.storyStringSuffix }
            val separator = if (instruct.wrap) "\n" else ""
            buildString {
                if (storyPrefix.isNotBlank()) {
                    append(substituteStoryAffix(storyPrefix, userName, character.name))
                    append(separator)
                }
                append(renderedStory)
                if (storySuffix.isNotBlank()) {
                    append(substituteStoryAffix(storySuffix, userName, character.name))
                }
            }.trimEnd()
        } else {
            renderedStory
        }

        val promptTail = if (instruct.enabled) {
            template.formatPrompt(name = character.name)
        } else {
            "${character.name}:"
        }

        val turnBudget = (maxContext - maxTokens - estimateTokens(header) - estimateTokens(promptTail))
            .coerceAtLeast(0)
        val turns = trimTurnsToBudget(history.filter { !it.isSystem }, turnBudget)

        val blocks = mutableListOf<PromptPart>()
        header.takeIf { it.isNotBlank() }?.let { blocks += PromptPart(it) }
        formatExamples(character.messageExample, context, instruct, template, userName, character.name)
            .takeIf { it.isNotBlank() }
            ?.let { blocks += PromptPart(it) }
        context.chatStart.takeIf { it.isNotBlank() }?.let {
            blocks += PromptPart(substituteMacros(it, userName, character.name))
        }
        val turnParts = mutableListOf<PromptPart>()
        turns.forEachIndexed { index, message ->
            turnParts += PromptPart(
                text = if (instruct.enabled) {
                    template.formatChat(
                        name = message.name.ifBlank { if (message.isUser) userName else character.name },
                        message = message.mes,
                        isUser = message.isUser,
                        position = when (index) {
                            0 -> InstructTurnPosition.FIRST
                            turns.lastIndex -> InstructTurnPosition.LAST
                            else -> InstructTurnPosition.NORMAL
                        }
                    )
                } else {
                    "${message.name.ifBlank { if (message.isUser) userName else character.name }}: ${message.mes}\n"
                }
            )
        }
        if (authorsNote.isNotBlank()) {
            val note = if (instruct.enabled) {
                template.formatChat("System", authorsNote, isUser = false, isNarrator = true)
            } else {
                authorsNote
            }
            val insertAt = (turnParts.size - DEFAULT_AUTHORS_NOTE_DEPTH).coerceIn(0, turnParts.size)
            turnParts.add(insertAt, PromptPart(note))
        }
        blocks += turnParts
        blocks += PromptPart(promptTail)
        return joinPromptParts(blocks)
    }

    private fun trimTurnsToBudget(history: List<ChatMessage>, budgetTokens: Int): List<ChatMessage> {
        val kept = ArrayDeque<ChatMessage>()
        var used = 0
        for (message in history.asReversed()) {
            val cost = estimateTokens(message.mes) + PER_TURN_OVERHEAD_TOKENS
            if (used + cost > budgetTokens && kept.isNotEmpty()) break
            kept.addFirst(message)
            used += cost
        }
        return kept.toList()
    }

    private fun estimateTokens(text: String): Int = (text.length / CHARS_PER_TOKEN) + 1

    private fun renderStoryString(storyString: String, values: Map<String, String>): String {
        var rendered = renderSimpleConditionals(storyString, values)
        allowedStoryPlaceholders.forEach { key ->
            rendered = rendered.replace(
                Regex("\\{\\{\\s*${Regex.escape(key)}\\s*\\}\\}", RegexOption.IGNORE_CASE),
                values[key].orEmpty()
            )
        }
        return rendered
    }

    private fun renderSimpleConditionals(storyString: String, values: Map<String, String>): String {
        var rendered = storyString
        val block = Regex(
            "\\{\\{#if\\s+([A-Za-z0-9_]+)\\s*}}(.*?)\\{\\{/if}}",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        var changed: Boolean
        do {
            changed = false
            rendered = block.replace(rendered) { match ->
                changed = true
                val key = match.groupValues[1]
                if (values[key].orEmpty().isNotBlank()) match.groupValues[2] else ""
            }
        } while (changed)
        return rendered.replace(Regex("\\{\\{\\s*trim\\s*}}", RegexOption.IGNORE_CASE), "")
    }

    private fun formatExamples(
        messageExample: String,
        context: ContextTemplateSettings,
        instruct: InstructSettings,
        template: InstructTemplate,
        userName: String,
        charName: String,
    ): String {
        if (messageExample.isBlank()) return ""
        val examples = messageExample
            .replace("\r", "")
            .replace(Regex("<START>\\s*", RegexOption.IGNORE_CASE), "")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@mapNotNull null
                val rawName = line.substring(0, separator).trim()
                val message = substituteMacros(line.substring(separator + 1).trim(), userName, charName)
                val isUser = rawName.equals("{{user}}", ignoreCase = true) ||
                    rawName.equals(userName, ignoreCase = true)
                val name = if (isUser) userName else charName
                if (instruct.enabled && !instruct.skipExamples) {
                    template.formatChat(name = name, message = message, isUser = isUser)
                } else {
                    "$name: $message\n"
                }
            }
            .toList()
        if (examples.isEmpty()) return ""
        val heading = context.exampleSeparator
            .takeIf { it.isNotBlank() }
            ?.let { substituteMacros(it, userName, charName) + "\n" }
            .orEmpty()
        return heading + examples.joinToString("")
    }

    private data class PromptPart(val text: String)

    private fun joinPromptParts(parts: List<PromptPart>): String = buildString {
        parts.filter { it.text.isNotBlank() }.forEachIndexed { index, part ->
            if (index > 0 && isNotEmpty() && !endsWith("\n")) append('\n')
            append(part.text)
        }
    }

    private fun unknownStoryPlaceholder(storyString: String): String? =
        Regex("\\{\\{([^}]+)\\}\\}").findAll(storyString)
            .map { it.groupValues[1].trim() }
            .firstOrNull { body ->
                when {
                    body.startsWith("#if ", ignoreCase = true) -> {
                        val key = body.removePrefix("#if").trim()
                        allowedStoryPlaceholders.none { allowed -> allowed.equals(key, ignoreCase = true) }
                    }
                    body.equals("/if", ignoreCase = true) -> false
                    body.equals("trim", ignoreCase = true) -> false
                    body.any { it.isWhitespace() } -> true
                    else -> allowedStoryPlaceholders.none { allowed -> allowed.equals(body, ignoreCase = true) }
                }
            }

    private fun modelForApiType(textGen: Map<String, Any?>, apiType: String): String =
        when (apiType) {
            "ooba" -> textGen.stringValue("custom_model")
            "llamacpp" -> textGen.stringValue("llamacpp_model")
            "ollama" -> textGen.stringValue("ollama_model")
            "koboldcpp" -> textGen.stringValue("koboldcpp_model")
            else -> ""
        }

    private fun substituteMacros(text: String, userName: String, charName: String): String =
        text
            .replace(Regex("\\{\\{char\\}\\}", RegexOption.IGNORE_CASE), charName)
            .replace(Regex("\\{\\{user\\}\\}", RegexOption.IGNORE_CASE), userName)

    private fun substituteStoryAffix(text: String, userName: String, charName: String): String =
        substituteMacros(text, userName, charName)
            .replace(Regex("\\{\\{name\\}\\}", RegexOption.IGNORE_CASE), "System")

    private fun Map<String, Any?>.mapValue(key: String): Map<String, Any?> =
        (this[key] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()

    private fun Map<String, Any?>.intValue(key: String, default: Int): Int =
        when (val value = this[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }

    private fun Map<String, Any?>.doubleValue(key: String, default: Double): Double =
        when (val value = this[key]) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: default
            else -> default
        }

    private fun Map<String, Any?>.intListValue(key: String): List<Int>? =
        (this[key] as? List<*>)?.mapNotNull { value ->
            when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        }?.takeIf { it.isNotEmpty() }

    private fun Map<String, Any?>.stringListValue(key: String): List<String>? =
        (this[key] as? List<*>)?.map { it.toString() }?.takeIf { it.isNotEmpty() }
}
