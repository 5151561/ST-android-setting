package io.github.sanitised.st.chat.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeEngineModeTest {

    @Test
    fun routesOpenAiMainApiToChatCompletion() {
        assertEquals(
            NativeEngineMode.CHAT_COMPLETION,
            engineMode(mapOf("main_api" to "openai"))
        )
    }

    @Test
    fun routesFirstBatchTextCompletionTypesToNativeTextCompletion() {
        listOf("ooba", "koboldcpp", "llamacpp", "ollama").forEach { apiType ->
            assertEquals(
                apiType,
                NativeEngineMode.TEXT_COMPLETION,
                engineMode(settings(apiType = apiType))
            )
        }
    }

    @Test
    fun fallsBackForUnsupportedTextCompletionTypesButAcceptsPhase2StoryStrings() {
        assertEquals(
            NativeEngineMode.FALLBACK,
            engineMode(settings(apiType = "tabby"))
        )
        assertEquals(
            NativeEngineMode.TEXT_COMPLETION,
            engineMode(settings(storyString = "{{#if description}}{{description}}{{/if}}"))
        )
        assertEquals(
            NativeEngineMode.FALLBACK,
            engineMode(settings(storyStringPosition = 1))
        )
    }

    @Test
    fun authorsNoteIsSupportedByNativeTextCompletion() {
        assertEquals(
            NativeEngineMode.TEXT_COMPLETION,
            engineMode(settings(), authorsNote = "Keep the secret tone.")
        )
    }

    private fun settings(
        apiType: String = "ooba",
        storyString: String = "{{description}}",
        storyStringPosition: Int = 0,
    ): Map<String, Any?> =
        mapOf(
            "main_api" to "textgenerationwebui",
            "textgenerationwebui_settings" to mapOf("type" to apiType),
            "power_user" to mapOf(
                "context" to mapOf(
                    "story_string" to storyString,
                    "story_string_position" to storyStringPosition,
                )
            )
        )
}
