package io.github.sanitised.st.api

import org.junit.Assert.assertEquals
import org.junit.Test

class TextGenerationServerResolverTest {

    @Test
    fun fixedServersMatchSillyTavernForHostedTypes() {
        val settings = settingsWithServerUrls(
            "featherless" to "http://ignored",
            "mancer" to "http://ignored",
            "togetherai" to "http://ignored",
            "infermaticai" to "http://ignored",
            "dreamgen" to "http://ignored",
            "openrouter" to "http://ignored",
        )

        assertEquals("https://api.featherless.ai/v1", resolveTextGenServer(settings, "featherless"))
        assertEquals("https://neuro.mancer.tech", resolveTextGenServer(settings, "mancer"))
        assertEquals("https://api.together.xyz", resolveTextGenServer(settings, "togetherai"))
        assertEquals("https://api.totalgpt.ai", resolveTextGenServer(settings, "infermaticai"))
        assertEquals("https://dreamgen.com", resolveTextGenServer(settings, "dreamgen"))
        assertEquals("https://openrouter.ai/api", resolveTextGenServer(settings, "openrouter"))
    }

    @Test
    fun localTypesReadConfiguredServerUrlsAndNormalizeLocalhost() {
        val settings = settingsWithServerUrls(
            "ooba" to "http://localhost:7860",
            "koboldcpp" to "http://localhost:5001",
            "llamacpp" to "http://127.0.0.1:8080",
            "ollama" to "http://localhost:11434",
        )

        assertEquals("http://127.0.0.1:7860", resolveTextGenServer(settings, "ooba"))
        assertEquals("http://127.0.0.1:5001", resolveTextGenServer(settings, "koboldcpp"))
        assertEquals("http://127.0.0.1:8080", resolveTextGenServer(settings, "llamacpp"))
        assertEquals("http://127.0.0.1:11434", resolveTextGenServer(settings, "ollama"))
    }

    @Test
    fun localhostNormalizationOnlyRewritesTheUrlHost() {
        val settings = settingsWithServerUrls(
            "ooba" to "http://localhost:7860/v1?label=localhost",
            "koboldcpp" to "http://localhost.example:5001",
        )

        assertEquals("http://127.0.0.1:7860/v1?label=localhost", resolveTextGenServer(settings, "ooba"))
        assertEquals("http://localhost.example:5001", resolveTextGenServer(settings, "koboldcpp"))
    }

    @Test
    fun localTypesDoNotInventDefaultsWhenServerUrlIsMissing() {
        assertEquals("", resolveTextGenServer(settingsWithServerUrls(), "ollama"))
        assertEquals("", resolveTextGenServer(settingsWithServerUrls(), "koboldcpp"))
        assertEquals("", resolveTextGenServer(settingsWithServerUrls(), "llamacpp"))
    }

    private fun settingsWithServerUrls(vararg pairs: Pair<String, String>): Map<String, Any?> =
        mapOf(
            "textgenerationwebui_settings" to mapOf(
                "server_urls" to mapOf(*pairs)
            )
        )
}
