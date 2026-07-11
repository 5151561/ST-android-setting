package io.github.sanitised.st.chat.engine

import io.github.sanitised.st.ui.prototype.apiConnectionProviderForId
import io.github.sanitised.st.ui.prototype.settingsWithSelectedApiProvider
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NativeGenerationProviderCoverageTest {
    @Test
    fun everyConnectionPageProviderRoutesToNativeGeneration() {
        val providerIds = listOf(
            "openai",
            "anthropic",
            "google",
            "mistralai",
            "openrouter",
            "deepseek",
            "xai",
            "cohere",
            "perplexity",
            "koboldcpp",
            "ooba",
            "tabby",
            "aphrodite",
            "mancer",
            "featherless",
            "horde",
            "llamacpp",
            "ollama",
            "koboldhorde",
            "kobold",
            "novelai",
        )

        providerIds.forEach { providerId ->
            val provider = requireNotNull(apiConnectionProviderForId(providerId))
            val settings = settingsWithSelectedApiProvider(baseSettings(), provider)

            assertNotEquals(
                "provider=$providerId main_api=${settings["main_api"]}",
                NativeEngineMode.UNSUPPORTED,
                engineMode(settings)
            )
        }
    }

    private fun baseSettings(): Map<String, Any?> =
        mapOf(
            "main_api" to "openai",
            "amount_gen" to 128,
            "oai_settings" to mapOf(
                "chat_completion_source" to "openai",
                "openai_model" to "gpt-test",
                "claude_model" to "claude-test",
                "google_model" to "gemini-test",
                "mistralai_model" to "mistral-test",
                "openrouter_model" to "openrouter/test",
                "deepseek_model" to "deepseek-test",
                "xai_model" to "grok-test",
                "cohere_model" to "cohere-test",
                "perplexity_model" to "sonar-test",
            ),
            "textgenerationwebui_settings" to mapOf(
                "type" to "ooba",
                "custom_model" to "ooba-test",
                "koboldcpp_model" to "koboldcpp-test",
                "tabby_model" to "tabby-test",
                "aphrodite_model" to "aphrodite-test",
                "mancer_model" to "mancer-test",
                "featherless_model" to "featherless-test",
                "llamacpp_model" to "llamacpp-test",
                "ollama_model" to "ollama-test",
                "max_context" to 2048,
            ),
            "power_user" to mapOf(
                "context" to mapOf(
                    "story_string" to "{{description}}",
                    "story_string_position" to 0,
                )
            ),
        )
}
