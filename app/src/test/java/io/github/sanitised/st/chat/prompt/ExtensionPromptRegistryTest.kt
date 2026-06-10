package io.github.sanitised.st.chat.prompt

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.chat.ChatMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionPromptRegistryTest {

    @Test
    fun collectsEnabledPromptsByPositionOrderAndTrigger() {
        val registry = ExtensionPromptRegistry()
            .register(
                ExtensionPrompt(
                    id = "late",
                    content = "late memory",
                    position = ExtensionPromptPosition.BEFORE_PROMPT,
                    order = 20,
                )
            )
            .register(
                ExtensionPrompt(
                    id = "early",
                    content = "early memory",
                    position = ExtensionPromptPosition.BEFORE_PROMPT,
                    order = 5,
                )
            )
            .register(
                ExtensionPrompt(
                    id = "continue-only",
                    content = "continue note",
                    position = ExtensionPromptPosition.IN_PROMPT,
                    triggers = setOf("continue"),
                )
            )
            .register(
                ExtensionPrompt(
                    id = "off",
                    content = "disabled",
                    position = ExtensionPromptPosition.IN_PROMPT,
                    enabled = false,
                )
            )

        assertEquals(
            listOf("early", "late"),
            registry.collect("normal").map { it.id },
        )
        assertEquals(
            listOf("early", "late", "continue-only"),
            registry.collect("continue").map { it.id },
        )
    }

    @Test
    fun promptBuilderInjectsRegistryPromptsIntoChatPayload() {
        val registry = ExtensionPromptRegistry()
            .register(
                ExtensionPrompt(
                    id = "summary",
                    content = "Earlier summary for {{char}} and {{user}}.",
                    position = ExtensionPromptPosition.BEFORE_PROMPT,
                    role = "system",
                    order = 1,
                )
            )
            .register(
                ExtensionPrompt(
                    id = "bias",
                    content = "Begin with wonder.",
                    position = ExtensionPromptPosition.IN_PROMPT,
                    role = "assistant",
                    order = 1,
                )
            )

        val payload = PromptBuilder.build(
            character = CharacterDetail(id = "Alice.png", name = "Alice", description = "Alice explores."),
            userName = "Alex",
            history = listOf(message(0, "Hello", isUser = true)),
            settings = mapOf(
                "main_api" to "openai",
                "oai_settings" to mapOf("chat_completion_source" to "openai"),
            ),
            extensionPrompts = registry.collect("normal"),
        )

        @Suppress("UNCHECKED_CAST")
        val messages = payload["messages"] as List<Map<String, Any?>>

        assertTrue(messages.any { it["role"] == "system" && it["content"] == "Earlier summary for Alice and Alex." })
        assertTrue(messages.any { it["role"] == "assistant" && it["content"] == "Begin with wonder." })
    }

    @Test
    fun appliesGenerationInterceptorsInOrder() {
        val registry = ExtensionPromptRegistry()
            .registerInterceptor(
                ExtensionPromptInterceptor(id = "second", order = 20) { payload ->
                    payload + ("trace" to "${payload["trace"]}>second")
                }
            )
            .registerInterceptor(
                ExtensionPromptInterceptor(id = "first", order = 10) { payload ->
                    payload + ("trace" to "first")
                }
            )

        assertEquals(
            mapOf("trace" to "first>second"),
            registry.intercept(mapOf("trace" to "start")),
        )
    }

    private fun message(id: Int, text: String, isUser: Boolean) = ChatMessage(
        id = id,
        name = if (isUser) "Alex" else "Alice",
        mes = text,
        isUser = isUser,
        isSystem = false,
        sendDate = "",
        swipeId = 0,
        swipes = emptyList(),
        extra = JSONObject(),
    )
}
