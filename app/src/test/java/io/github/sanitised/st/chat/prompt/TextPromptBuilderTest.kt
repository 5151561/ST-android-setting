package io.github.sanitised.st.chat.prompt

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.chat.ChatMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPromptBuilderTest {

    @Test
    fun buildsTextCompletionPayloadFromContextInstructAndHistory() {
        val result = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = listOf(
                message(0, "Found a key", isUser = true),
                message(1, "Keep it safe", isUser = false),
            ),
            settings = settings(),
            personaDescription = "Alex is careful.",
            worldInfoBefore = "Before lore for {{char}}.",
            worldInfoAfter = "After lore.",
        ) as TextPromptBuildResult.Ready

        val payload = result.payload
        assertEquals("ooba", payload["api_type"])
        assertEquals("http://127.0.0.1:7860", payload["api_server"])
        assertEquals("model-a", payload["model"])
        assertEquals(120, payload["max_new_tokens"])
        assertEquals(120, payload["max_tokens"])
        assertEquals(0.8, payload["temperature"])
        assertEquals(0.9, payload["top_p"])
        assertEquals(50, payload["top_k"])
        assertEquals(1.1, payload["repetition_penalty"])
        assertEquals(1.1, payload["rep_pen"])
        assertEquals(2048, payload["truncation_length"])

        val prompt = payload["prompt"] as String
        assertTrue(prompt.contains("System=Rules for Alice"))
        assertTrue(prompt.contains("Desc=Alice explores with Alex."))
        assertTrue(prompt.contains("Persona=Alex is careful."))
        assertTrue(prompt.contains("WI=Before lore for Alice./After lore."))
        assertTrue(prompt.contains("Examples=Alex: hi\nAlice: hello"))
        assertTrue(prompt.contains("<START Alice>"))
        assertTrue(prompt.contains("<U Alex>\nAlex: Found a key</U>"))
        assertTrue(prompt.contains("<A Alice>\nAlice: Keep it safe</A>"))
        assertTrue(prompt.endsWith("\n<A Alice>\nAlice:"))

        @Suppress("UNCHECKED_CAST")
        val stops = payload["stop"] as List<String>
        assertEquals(stops, payload["stopping_strings"])
        assertTrue(stops.contains("\n<U Alex>"))
        assertTrue(stops.contains("\n<A Alice>"))
        assertTrue(stops.contains("\n<START Alice>"))
    }

    @Test
    fun rendersSimpleHandlebarsIfBlocksInStoryString() {
        val result = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = emptyList(),
            settings = settings(
                storyString = "{{#if system}}System={{system}}\n{{/if}}{{#if wiBefore}}WI={{wiBefore}}\n{{/if}}{{trim}}"
            ),
            worldInfoBefore = "Lore",
        ) as TextPromptBuildResult.Ready

        assertTrue(result.prompt.startsWith("System=Rules for Alice\nWI=Lore"))
    }

    @Test
    fun supportsKnownStoryAliasesAndRejectsUnknownSimplePlaceholders() {
        val ready = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = emptyList(),
            settings = settings(
                storyString = "Lore={{loreBefore}}/{{loreAfter}}\nRaw={{mesExamplesRaw}}\n"
            ),
            worldInfoBefore = "before",
            worldInfoAfter = "after",
        ) as TextPromptBuildResult.Ready

        val prompt = ready.payload["prompt"] as String
        assertTrue(prompt.contains("Lore=before/after"))
        assertTrue(prompt.contains("Raw=Alex: hi\nAlice: hello"))

        val unsupported = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = emptyList(),
            settings = settings(storyString = "{{unknownField}}"),
        )

        assertEquals(
            TextPromptBuildResult.Unsupported("unsupported story_string placeholder: unknownField"),
            unsupported
        )
    }

    @Test
    fun reportsUnsupportedForInChatStoryStringPosition() {
        val result = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = emptyList(),
            settings = settings(storyStringPosition = 1),
        )

        assertEquals(
            TextPromptBuildResult.Unsupported("unsupported story_string_position: 1"),
            result
        )
    }

    @Test
    fun supportsInstructDisabledStoryStringAffixesAndAuthorsNote() {
        val disabled = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = listOf(message(0, "hello", isUser = true)),
            settings = settings(instructEnabled = false),
        ) as TextPromptBuildResult.Ready
        assertTrue(disabled.prompt.contains("Alex: hello"))
        assertTrue(disabled.prompt.endsWith("Alice:"))

        val affixed = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = emptyList(),
            settings = settings(storyStringPrefix = "<SYS>", storyStringSuffix = "</SYS>"),
        ) as TextPromptBuildResult.Ready
        assertTrue(affixed.prompt.startsWith("<SYS>\n"))
        assertTrue(affixed.prompt.contains("</SYS>"))

        val disabledAffix = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = emptyList(),
            settings = settings(
                instructEnabled = false,
                storyStringPrefix = "<SYS>",
                storyStringSuffix = "</SYS>",
            ),
        ) as TextPromptBuildResult.Ready
        assertTrue(!disabledAffix.prompt.contains("<SYS>"))
        assertTrue(!disabledAffix.prompt.contains("</SYS>"))

        val withAuthorsNote = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = listOf(
                message(0, "one", isUser = true),
                message(1, "two", isUser = false),
            ),
            settings = settings(),
            authorsNote = "Keep it tense.",
        ) as TextPromptBuildResult.Ready
        assertTrue(withAuthorsNote.prompt.contains("Keep it tense."))
    }

    @Test
    fun authorsNoteIsInsertedAtInChatDepthForTextCompletion() {
        val history = (1..6).map { message(it, "turn $it", isUser = it % 2 == 1) }
        val result = TextPromptBuilder.build(
            character = character().copy(messageExample = ""),
            userName = "Alex",
            history = history,
            settings = settings(storyString = "{{description}}\n"),
            authorsNote = "Keep it tense.",
        ) as TextPromptBuildResult.Ready

        val prompt = result.prompt
        val beforeNote = prompt.indexOf("turn 2")
        val note = prompt.indexOf("Keep it tense.")
        val afterNote = prompt.indexOf("turn 3")
        assertTrue(beforeNote >= 0)
        assertTrue(note > beforeNote)
        assertTrue(afterNote > note)
    }

    @Test
    fun trimsOldHistoryToContextBudgetAndCarriesTextContextFields() {
        val result = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = listOf(
                message(0, "old " + "x".repeat(200), isUser = true),
                message(1, "recent", isUser = false),
                message(2, "latest", isUser = true),
            ),
            settings = settings(maxContext = 90, amountGen = 20),
        ) as TextPromptBuildResult.Ready

        val prompt = result.payload["prompt"] as String
        assertTrue(prompt.contains("recent"))
        assertTrue(prompt.contains("latest"))
        assertTrue(!prompt.contains("old "))
        assertEquals(90, result.payload["truncation_length"])
    }

    @Test
    fun carriesOllamaContextSizeAsNumCtx() {
        val result = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = emptyList(),
            settings = settings(apiType = "ollama", maxContext = 8192),
        ) as TextPromptBuildResult.Ready

        assertEquals(8192, result.payload["truncation_length"])
        assertEquals(8192, result.payload["num_ctx"])
    }

    @Test
    fun ignoresSystemMessagesWhenChoosingLastVisibleTurnSequence() {
        val result = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = listOf(
                message(0, "Found a key", isUser = true),
                message(1, "Keep it safe", isUser = false),
                message(2, "hidden", isUser = false, isSystem = true),
            ),
            settings = settings(lastOutputSequence = "<LAST {{name}}>"),
        ) as TextPromptBuildResult.Ready

        val prompt = result.payload["prompt"] as String
        assertTrue(prompt.contains("<LAST Alice>\nAlice: Keep it safe</A>"))
    }

    @Test
    fun reportsUnsupportedForTextGenerationTypesOutsideFirstBatch() {
        val result = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = emptyList(),
            settings = settings(apiType = "tabby"),
        )

        assertEquals(
            TextPromptBuildResult.Unsupported("unsupported api_type: tabby"),
            result
        )
    }

    @Test
    fun activationRegexDoesNotDisableTheCurrentlySelectedInstructPresetAndSamplerFieldsAreCarried() {
        val result = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = listOf(message(0, "hello", isUser = true)),
            settings = settings(
                activationRegex = "qwen",
                customModel = "llama-local",
                samplerOrder = listOf(6, 0, 1),
                samplerPriority = listOf("temperature", "top_p"),
            ),
        ) as TextPromptBuildResult.Ready

        assertTrue(result.prompt.contains("Alex: hello"))
        assertTrue(result.prompt.contains("<U Alex>"))
        assertEquals(listOf(6, 0, 1), result.payload["sampler_order"])
        assertEquals(listOf("temperature", "top_p"), result.payload["sampler_priority"])
    }

    private fun settings(
        apiType: String = "ooba",
        lastOutputSequence: String = "<A {{name}}>",
        instructEnabled: Boolean = true,
        storyStringPosition: Int = 0,
        storyStringPrefix: String = "",
        storyStringSuffix: String = "",
        maxContext: Int = 2048,
        amountGen: Int = 120,
        activationRegex: String = "",
        customModel: String = "model-a",
        samplerOrder: List<Int>? = null,
        samplerPriority: List<String>? = null,
        storyString: String = (
            "System={{system}}\n" +
                "Desc={{description}}\n" +
                "Personality={{personality}}\n" +
                "Scenario={{scenario}}\n" +
                "Persona={{persona}}\n" +
                "WI={{wiBefore}}/{{wiAfter}}\n" +
                "Examples={{mesExamples}}\n"
            ),
    ): Map<String, Any?> =
        mapOf(
            "main_api" to "textgenerationwebui",
            "username" to "Alex",
            "amount_gen" to amountGen,
            "textgenerationwebui_settings" to (mapOf(
                "type" to apiType,
                "server_urls" to mapOf(apiType to "http://localhost:7860"),
                "custom_model" to customModel,
                "ollama_model" to "ollama-a",
                "max_context" to maxContext,
                "temp" to 0.8,
                "top_p" to 0.9,
                "top_k" to 50,
                "rep_pen" to 1.1,
            ) +
                (samplerOrder?.let { mapOf("sampler_order" to it) } ?: emptyMap()) +
                (samplerPriority?.let { mapOf("sampler_priority" to it) } ?: emptyMap())),
            "power_user" to mapOf(
                "context" to mapOf(
                    "story_string" to storyString,
                    "story_string_position" to storyStringPosition,
                    "story_string_prefix" to storyStringPrefix,
                    "story_string_suffix" to storyStringSuffix,
                    "chat_start" to "<START {{char}}>",
                    "example_separator" to "***",
                    "use_stop_strings" to true,
                    "names_as_stop_strings" to true,
                ),
                "instruct" to mapOf(
                    "enabled" to instructEnabled,
                    "wrap" to true,
                    "macro" to true,
                    "input_sequence" to "<U {{name}}>",
                    "output_sequence" to "<A {{name}}>",
                    "last_output_sequence" to lastOutputSequence,
                    "input_suffix" to "</U>",
                    "output_suffix" to "</A>",
                    "activation_regex" to activationRegex,
                    "names_behavior" to "always",
                    "sequences_as_stop_strings" to true,
                ),
            )
        )

    private fun character() = CharacterDetail(
        id = "Alice.png",
        name = "Alice",
        description = "{{char}} explores with {{user}}.",
        personality = "Bright",
        scenario = "Library",
        systemPrompt = "Rules for {{char}}",
        messageExample = "{{user}}: hi\n{{char}}: hello",
    )

    private fun message(id: Int, text: String, isUser: Boolean, isSystem: Boolean = false) = ChatMessage(
        id = id,
        name = if (isUser) "Alex" else "Alice",
        mes = text,
        isUser = isUser,
        isSystem = isSystem,
        sendDate = "",
        swipeId = 0,
        swipes = emptyList(),
        extra = JSONObject(),
    )
}
