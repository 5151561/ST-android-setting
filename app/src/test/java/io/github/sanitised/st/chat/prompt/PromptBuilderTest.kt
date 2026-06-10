package io.github.sanitised.st.chat.prompt

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.chat.ChatMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    private fun settings(model: String = "gpt-4o", maxContext: Int = 4096, maxTokens: Int = 300) =
        mapOf<String, Any?>(
            "main_api" to "openai",
            "username" to "Alex",
            "oai_settings" to mapOf(
                "chat_completion_source" to "openai",
                "openai_model" to model,
                "temp_openai" to 0.7,
                "openai_max_tokens" to maxTokens,
                "openai_max_context" to maxContext,
            )
        )

    private fun character() = CharacterDetail(
        id = "Alice.png",
        name = "Alice",
        description = "{{char}} is a curious explorer talking to {{user}}.",
        personality = "Brave and witty",
        scenario = "A dusty library at midnight",
    )

    private fun msg(id: Int, text: String, isUser: Boolean) = ChatMessage(
        id = id, name = if (isUser) "Alex" else "Alice", mes = text,
        isUser = isUser, isSystem = false, sendDate = "", swipeId = 0,
        swipes = emptyList(), extra = JSONObject()
    )

    @Test
    fun buildsSystemThenHistoryWithResolvedModelAndSource() {
        val history = listOf(msg(0, "Hello", true), msg(1, "Hi there", false), msg(2, "Who are you?", true))
        val payload = PromptBuilder.build(character(), "Alex", history, settings())

        assertEquals("gpt-4o", payload["model"])
        assertEquals("openai", payload["chat_completion_source"])
        assertEquals(false, payload["stream"])
        assertEquals("Alice", payload["char_name"])
        assertEquals("Alex", payload["user_name"])

        @Suppress("UNCHECKED_CAST")
        val messages = payload["messages"] as List<Map<String, Any?>>
        assertEquals("system", messages.first()["role"])
        val system = messages.filter { it["role"] == "system" }.joinToString("\n") { it["content"] as String }
        // Macros substituted, persona/scenario folded in.
        assertTrue(system.contains("Alice is a curious explorer talking to Alex."))
        assertTrue(system.contains("Brave and witty"))
        assertTrue(system.contains("A dusty library at midnight"))

        // History preserved in order with correct roles.
        val turns = messages.dropWhile { it["role"] == "system" }
        assertEquals(listOf("user", "assistant", "user"), turns.map { it["role"] })
        assertEquals("Who are you?", turns.last()["content"])
    }

    @Test
    fun includesPersonaDescriptionAndMessageExamplesInSystem() {
        val character = character().copy(messageExample = "<START>\n{{user}}: hi\n{{char}}: hello {{user}}!")
        val payload = PromptBuilder.build(
            character, "Alex", listOf(msg(0, "Hello", true)), settings(),
            personaDescription = "Alex is a tired night-shift nurse.",
        )

        @Suppress("UNCHECKED_CAST")
        val messages = payload["messages"] as List<Map<String, Any?>>
        val system = messages.filter { it["role"] == "system" }.joinToString("\n") { it["content"] as String }
        assertTrue(system.contains("Alex is a tired night-shift nurse."))
        assertTrue(system.contains("Start a new Chat"))
        assertTrue(system.contains("hello Alex!")) // macro substituted in examples
    }

    @Test
    fun injectsWorldInfoIntoSystemAndAuthorsNoteAtDepth() {
        val history = (1..6).map { msg(it, "turn $it", isUser = it % 2 == 1) }
        val payload = PromptBuilder.build(
            character(), "Alex", history, settings(),
            worldInfoBefore = "LORE-BEFORE about {{char}}",
            worldInfoAfter = "LORE-AFTER",
            authorsNote = "Stay in character.",
            authorsNoteDepth = 2,
        )

        @Suppress("UNCHECKED_CAST")
        val messages = payload["messages"] as List<Map<String, Any?>>
        val systemMessages = messages.filter { it["role"] == "system" }.map { it["content"] as String }
        val beforeIndex = systemMessages.indexOfFirst { it.contains("LORE-BEFORE about Alice") }
        val descriptionIndex = systemMessages.indexOfFirst { it.contains("curious explorer") }
        val afterIndex = systemMessages.indexOfFirst { it.contains("LORE-AFTER") }
        assertTrue(beforeIndex >= 0)
        assertTrue(afterIndex >= 0)
        assertTrue(beforeIndex < descriptionIndex)
        assertTrue(afterIndex > descriptionIndex)

        // Author's note injected as a system turn 2 positions from the end.
        val anIndex = messages.indexOfFirst { it["content"] == "Stay in character." }
        assertEquals("system", messages[anIndex]["role"])
        assertEquals(messages.size - 2 - 1, anIndex) // depth 2 from end, +1 trailing turn after it
    }

    @Test
    fun trimsOldestHistoryWhenContextBudgetExceeded() {
        // Tiny context budget so only the most recent turn(s) survive.
        val long = "x".repeat(400) // ~100 tokens each
        val history = (0 until 10).map { msg(it, "$long #$it", isUser = it % 2 == 0) }
        val payload = PromptBuilder.build(character(), "Alex", history, settings(maxContext = 400, maxTokens = 100))

        @Suppress("UNCHECKED_CAST")
        val messages = payload["messages"] as List<Map<String, Any?>>
        val turns = messages.filter { it["role"] != "system" }
        // Budget is small, so far fewer than 10 turns are kept, and the newest is retained.
        assertTrue(turns.size < 10)
        assertTrue((turns.last()["content"] as String).contains("#9"))
    }
}
