package io.github.sanitised.st.ui.screens

import io.github.sanitised.st.api.SecretEntry
import io.github.sanitised.st.api.SecretProviderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConnectionStateTest {
    @Test
    fun googleAiSecretUsesSillyTavernMakersuiteKeyWithoutFakingOpenAiOrClaude() {
        val state = buildApiConnectionUiState(
            settings = mapOf(
                "main_api" to "openai",
                "oai_settings" to mapOf(
                    "chat_completion_source" to "makersuite",
                    "openai_model" to "gpt-4-turbo",
                    "claude_model" to "claude-sonnet-4-5",
                    "google_model" to "gemini-2.5-pro"
                )
            ),
            secrets = listOf(
                SecretProviderState("api_key_openai", "OpenAI"),
                SecretProviderState("api_key_claude", "Claude"),
                SecretProviderState(
                    key = "api_key_makersuite",
                    label = "Google AI Studio",
                    entries = listOf(
                        SecretEntry(
                            id = "google-primary",
                            value = "*******abc",
                            label = "mobile",
                            active = true
                        )
                    )
                )
            ),
            serviceRunning = true
        )

        assertEquals("cc", state.activeMode)
        assertEquals("google", state.activeProvider.id)
        assertEquals("Google AI", state.activeProvider.label)
        assertEquals("gemini-2.5-pro", state.activeModel)
        assertEquals(1, state.configuredProviderCount)
        assertTrue(state.activeProvider.hasConfiguredSecret)
        assertEquals("密钥已配置，尚未验证", state.connectionStatusText)

        val providersById = state.visibleProviders.associateBy { it.id }
        assertFalse(providersById.getValue("openai").hasConfiguredSecret)
        assertFalse(providersById.getValue("anthropic").hasConfiguredSecret)
        assertTrue(providersById.getValue("google").hasConfiguredSecret)
    }

    @Test
    fun runningServiceWithoutSecretDoesNotReportSuccessfulConnection() {
        val state = buildApiConnectionUiState(
            settings = mapOf(
                "main_api" to "openai",
                "oai_settings" to mapOf(
                    "chat_completion_source" to "claude",
                    "claude_model" to "claude-sonnet-4-5"
                )
            ),
            secrets = emptyList(),
            serviceRunning = true
        )

        assertEquals("anthropic", state.activeProvider.id)
        assertEquals("Claude", state.activeProvider.label)
        assertEquals("claude-sonnet-4-5", state.activeModel)
        assertEquals(0, state.configuredProviderCount)
        assertFalse(state.activeProvider.hasConfiguredSecret)
        assertEquals("未配置密钥", state.connectionStatusText)
    }

    @Test
    fun selectingProviderWritesRealSillyTavernSettingsFields() {
        val original = mapOf(
            "main_api" to "openai",
            "api_type" to "legacy-do-not-touch",
            "oai_settings" to mapOf(
                "chat_completion_source" to "openai",
                "openai_model" to "gpt-4-turbo"
            )
        )

        val googleSettings = settingsWithSelectedApiProvider(
            settings = original,
            provider = apiConnectionProviderForId("google")!!
        )

        assertEquals("openai", googleSettings["main_api"])
        assertEquals(
            "makersuite",
            (googleSettings["oai_settings"] as Map<*, *>)["chat_completion_source"]
        )
        assertFalse(googleSettings.containsKey("api_type"))

        val koboldSettings = settingsWithSelectedApiProvider(
            settings = googleSettings,
            provider = apiConnectionProviderForId("koboldcpp")!!
        )

        assertEquals("textgenerationwebui", koboldSettings["main_api"])
        assertEquals(
            "koboldcpp",
            (koboldSettings["textgenerationwebui_settings"] as Map<*, *>)["type"]
        )
    }

    @Test
    fun providerAliasesResolveBackendSecretKeys() {
        assertEquals(listOf("api_key_claude"), apiConnectionProviderForId("claude")?.secretKeys)
        assertEquals(listOf("api_key_makersuite"), apiConnectionProviderForId("makersuite")?.secretKeys)
        assertEquals(listOf("api_key_makersuite"), apiConnectionProviderForId("googleAi")?.secretKeys)
        assertEquals(listOf("api_key_groq"), apiConnectionProviderForId("groq")?.secretKeys)
        assertEquals(listOf("api_key_moonshot"), apiConnectionProviderForId("kimi")?.secretKeys)
        assertEquals(listOf("api_key_siliconflow"), apiConnectionProviderForId("siliconflow")?.secretKeys)
        assertEquals(listOf("api_key_zai"), apiConnectionProviderForId("glm")?.secretKeys)
        assertEquals(listOf("api_key_minimax"), apiConnectionProviderForId("minimax")?.secretKeys)
        assertEquals(listOf("api_key_custom"), apiConnectionProviderForId("custom")?.secretKeys)
        assertNull(apiConnectionProviderForId("missing-provider"))
    }

    @Test
    fun legacyFictionalPlaceholderModelsAreFilteredOutAndScopedReverseProxyIsIsolated() {
        val settingsWithFictionalModel = mapOf(
            "main_api" to "openai",
            "oai_settings" to mapOf(
                "chat_completion_source" to "makersuite",
                "google_model" to "gemini-3.7-flash",
                "reverse_proxy" to "https://openai-proxy.example.com/v1",
                "reverse_proxy_openai" to "https://openai-proxy.example.com/v1",
                "reverse_proxy_makersuite" to ""
            )
        )
        val googleProvider = apiConnectionProviderForId("google")!!
        assertEquals("", modelForProvider(settingsWithFictionalModel, googleProvider))

        val switchedToGoogle = settingsWithSelectedApiProvider(settingsWithFictionalModel, googleProvider)
        val oaiAfterGoogle = switchedToGoogle["oai_settings"] as Map<*, *>
        assertEquals("", oaiAfterGoogle["reverse_proxy"])

        val openaiProvider = apiConnectionProviderForId("openai")!!
        val switchedBackToOpenAi = settingsWithSelectedApiProvider(switchedToGoogle, openaiProvider)
        val oaiAfterOpenAi = switchedBackToOpenAi["oai_settings"] as Map<*, *>
        assertEquals("https://openai-proxy.example.com/v1", oaiAfterOpenAi["reverse_proxy"])
    }
}
