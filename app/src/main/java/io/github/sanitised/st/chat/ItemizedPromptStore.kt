package io.github.sanitised.st.chat

import io.github.sanitised.st.chat.engine.NativeEngineMode
import io.github.sanitised.st.chat.engine.NativeGenerationRoute
import java.util.concurrent.ConcurrentHashMap

class ItemizedPromptStore {
    private val prompts = ConcurrentHashMap<Int, ItemizedPrompt>()

    fun record(
        messageId: Int,
        payload: Map<String, Any?>,
        route: NativeGenerationRoute,
    ): ItemizedPrompt {
        val components = componentsFromPayload(payload, route.mode)
        val prompt = ItemizedPrompt(
            mesId = messageId,
            total = components.sumOf { it.tokens },
            components = components,
            presetName = "",
            modelUsed = payload["model"]?.toString().orEmpty(),
            apiUsed = route.api.ifBlank { route.source },
            tokenizer = if (route.mode == NativeEngineMode.TEXT_COMPLETION) "text" else "chat",
        )
        prompts[messageId] = prompt
        return prompt
    }

    fun get(messageId: Int): ItemizedPrompt? = prompts[messageId]

    fun clear() {
        prompts.clear()
    }

    private fun componentsFromPayload(
        payload: Map<String, Any?>,
        mode: NativeEngineMode,
    ): List<ItemizedPromptComponent> {
        if (mode == NativeEngineMode.TEXT_COMPLETION) {
            val prompt = payload["prompt"]?.toString().orEmpty()
            return if (prompt.isBlank()) emptyList() else listOf(
                ItemizedPromptComponent("prompt", estimateTokens(prompt))
            )
        }
        val messages = payload["messages"] as? List<*> ?: return emptyList()
        return messages.mapIndexedNotNull { index, row ->
            val map = row as? Map<*, *> ?: return@mapIndexedNotNull null
            val role = map["role"]?.toString().orEmpty().ifBlank { "message" }
            val content = map["content"]?.toString().orEmpty()
            ItemizedPromptComponent("$role[$index]", estimateTokens(content))
        }
    }

    private fun estimateTokens(text: String): Int =
        (text.length / CHARS_PER_TOKEN) + 1

    companion object {
        val Global = ItemizedPromptStore()
        private const val CHARS_PER_TOKEN = 4
    }
}
