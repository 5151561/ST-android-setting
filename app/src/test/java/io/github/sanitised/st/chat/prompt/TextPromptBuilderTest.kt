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
    fun reportsUnsupportedForComplexHandlebarsStoryString() {
        val result = TextPromptBuilder.build(
            character = character(),
            userName = "Alex",
            history = emptyList(),
            settings = settings(
                storyString = "{{#if description}}{{description}}{{/if}}"
            ),
        )

        assertTrue(result is TextPromptBuildResult.Unsupported)
        assertTrue((result as TextPromptBuildResult.Unsupported).reason.contains("Handlebars"))
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
    fun reportsUnsupportedForInstructDisabledStoryStringAffixesAndAuthorsNote() {
        assertEquals(
            TextPromptBuildResult.Unsupported("unsupported instruct.enabled=false"),
            TextPromptBuilder.build(
                character = character(),
                userName = "Alex",
                history = emptyList(),
                settings = settings(instructEnabled = false),
            )
        )
        assertEquals(
            TextPromptBuildResult.Unsupported("unsupported story_string_prefix/suffix"),
            TextPromptBuilder.build(
                character = character(),
                userName = "Alex",
                history = emptyList(),
                settings = settings(storyStringPrefix = "prefix"),
            )
        )
        assertEquals(
            TextPromptBuildResult.Unsupported("unsupported authors_note"),
            TextPromptBuilder.build(
                character = character(),
                userName = "Alex",
                history = emptyList(),
                settings = settings(),
                authorsNote = "Keep it tense.",
            )
        )
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

    private fun settings(
        apiType: String = "ooba",
        lastOutputSequence: String = "<A {{name}}>",
        instructEnabled: Boolean = true,
        storyStringPosition: Int = 0,
        storyStringPrefix: String = "",
        storyStringSuffix: String = "",
        maxContext: Int = 2048,
        amountGen: Int = 120,
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
            "textgenerationwebui_settings" to mapOf(
                "type" to apiType,
                "server_urls" to mapOf(apiType to "http://localhost:7860"),
                "custom_model" to "model-a",
                "ollama_model" to "ollama-a",
                "max_context" to maxContext,
                "temp" to 0.8,
                "top_p" to 0.9,
                "top_k" to 50,
                "rep_pen" to 1.1,
            ),
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
