package io.github.sanitised.st.chat

import io.github.sanitised.st.chat.engine.NativeEngineMode
import io.github.sanitised.st.chat.engine.NativeGenerationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemizedPromptStoreTest {
    @Test
    fun recordsChatCompletionMessageComponents() {
        val store = ItemizedPromptStore()
        val prompt = store.record(
            messageId = 7,
            payload = mapOf(
                "model" to "gpt-test",
                "messages" to listOf(
                    mapOf("role" to "system", "content" to "System prompt"),
                    mapOf("role" to "user", "content" to "Hello there"),
                )
            ),
            route = NativeGenerationRoute(
                mode = NativeEngineMode.CHAT_COMPLETION,
                api = "openai",
                source = "openai",
                settings = emptyMap()
            )
        )

        assertEquals(prompt, store.get(7))
        assertEquals(7, prompt.mesId)
        assertEquals("gpt-test", prompt.modelUsed)
        assertEquals("openai", prompt.apiUsed)
        assertEquals(listOf("system[0]", "user[1]"), prompt.components.map { it.name })
        assertTrue(prompt.total > 0)
    }

    @Test
    fun returnsNullForMessagesWithoutNativePromptRecord() {
        assertNull(ItemizedPromptStore().get(99))
    }
}
