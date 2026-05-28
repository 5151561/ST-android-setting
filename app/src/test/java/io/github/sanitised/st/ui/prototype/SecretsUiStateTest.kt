package io.github.sanitised.st.ui.prototype

import io.github.sanitised.st.api.SecretEntry
import io.github.sanitised.st.api.SecretProviderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretsUiStateTest {
    @Test
    fun emptyBackendSecretsDoNotCreateSimulatedRows() {
        assertTrue(configuredSecretRows(emptyList()).isEmpty())
    }

    @Test
    fun configuredRowsComeOnlyFromBackendSecrets() {
        val rows = configuredSecretRows(
            listOf(
                SecretProviderState("api_key_makersuite", "Google AI Studio"),
                SecretProviderState(
                    key = "api_key_openrouter",
                    label = "OpenRouter",
                    entries = listOf(
                        SecretEntry(
                            id = "or-main",
                            value = "********router",
                            label = "main",
                            active = true
                        )
                    )
                )
            )
        )

        assertEquals(listOf("api_key_openrouter"), rows.map { it.providerKey })
        assertFalse(rows.any { it.providerKey == "api_key_openai" })
        assertFalse(rows.any { it.providerKey == "api_key_claude" })
        assertFalse(rows.any { it.providerKey == "api_key_makersuite" })
    }

    @Test
    fun addProviderOptionsUseSillyTavernSecretKeys() {
        val keys = secretProviderOptions(emptyList()).map { it.key }

        assertTrue("api_key_openai" in keys)
        assertTrue("api_key_claude" in keys)
        assertTrue("api_key_makersuite" in keys)
        assertFalse("openai" in keys)
        assertFalse("anthropic" in keys)
        assertFalse("google" in keys)
    }
}
