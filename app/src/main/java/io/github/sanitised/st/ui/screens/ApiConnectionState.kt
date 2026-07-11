package io.github.sanitised.st.ui.screens

import io.github.sanitised.st.api.SecretProviderState
import java.util.Locale

internal enum class ConnectionVerifyStatus {
    NOT_VERIFIED,
    TESTING,
    SUCCESS,
    FAILED
}

internal data class ApiConnectionProviderDefinition(
    val id: String,
    val label: String,
    val icon: String,
    val mode: String,
    val mainApi: String,
    val sourceValue: String? = null,
    val secretKeys: List<String> = emptyList(),
    val modelSettingsGroup: String? = null,
    val modelKey: String? = null,
    val requiresSecret: Boolean = true
)

internal data class ApiConnectionProviderState(
    val definition: ApiConnectionProviderDefinition,
    val configuredEntryCount: Int,
    val activeSecretLabel: String? = null
) {
    val id: String = definition.id
    val label: String = definition.label
    val icon: String = definition.icon
    val mode: String = definition.mode
    val secretKeys: List<String> = definition.secretKeys
    val requiresSecret: Boolean = definition.requiresSecret
    val hasConfiguredSecret: Boolean = configuredEntryCount > 0 || !requiresSecret
    val secretStatusLabel: String = when {
        !requiresSecret -> "无需密钥"
        configuredEntryCount > 0 -> "已配置"
        else -> "未配置"
    }
}

internal data class ApiConnectionUiState(
    val activeMode: String,
    val activeProvider: ApiConnectionProviderState,
    val visibleProviders: List<ApiConnectionProviderState>,
    val configuredProviderCount: Int,
    val activeModel: String,
    val serviceStatusText: String,
    val connectionStatusText: String,
    val connectionStatusOk: Boolean
)

internal fun buildApiConnectionUiState(
    settings: Map<String, Any?>,
    secrets: List<SecretProviderState>,
    serviceRunning: Boolean,
    selectedProviderId: String? = null,
    selectedMode: String? = null
): ApiConnectionUiState {
    val settingsProvider = activeApiConnectionProvider(settings)
    val selectedProvider = selectedProviderId
        ?.let(::apiConnectionProviderForId)
        ?.takeIf { selectedMode == null || it.mode == selectedMode }
    val visibleMode = selectedMode ?: selectedProvider?.mode ?: settingsProvider.mode
    val fallbackProvider = apiConnectionProvidersForMode(visibleMode).firstOrNull() ?: settingsProvider
    val activeProviderDefinition = selectedProvider
        ?: settingsProvider.takeIf { it.mode == visibleMode }
        ?: fallbackProvider
    val visibleProviders = apiConnectionProvidersForMode(visibleMode)
        .map { it.withSecretState(secrets) }
    val activeProvider = visibleProviders.firstOrNull { it.id == activeProviderDefinition.id }
        ?: activeProviderDefinition.withSecretState(secrets)
    val configuredCount = configuredApiConnectionProviderCount(secrets)
    val activeModel = modelForProvider(settings, activeProviderDefinition)
        .ifBlank { if (serviceRunning) "未选择模型" else "服务未启动" }
    val serviceStatusText = if (serviceRunning) "服务运行中" else "服务未启动"
    val connectionStatusText = when {
        !serviceRunning -> "服务未启动"
        activeProvider.hasConfiguredSecret && activeProvider.requiresSecret -> "密钥已配置，尚未验证"
        activeProvider.hasConfiguredSecret -> "无需密钥，尚未验证"
        else -> "未配置密钥"
    }
    val connectionStatusOk = serviceRunning && activeProvider.hasConfiguredSecret

    return ApiConnectionUiState(
        activeMode = visibleMode,
        activeProvider = activeProvider,
        visibleProviders = visibleProviders,
        configuredProviderCount = configuredCount,
        activeModel = activeModel,
        serviceStatusText = serviceStatusText,
        connectionStatusText = connectionStatusText,
        connectionStatusOk = connectionStatusOk
    )
}

internal fun configuredApiConnectionProviderCount(secrets: List<SecretProviderState>): Int {
    val configuredSecretKeys = secrets
        .filter { it.entries.isNotEmpty() }
        .map { it.key }
        .toSet()
    return allApiConnectionProviders()
        .mapNotNull { provider -> provider.secretKeys.firstOrNull { it in configuredSecretKeys } }
        .distinct()
        .count()
}

internal fun apiConnectionProvidersForMode(mode: String): List<ApiConnectionProviderDefinition> {
    return allApiConnectionProviders().filter { it.mode == mode }
}

internal fun apiConnectionSecretProviderDefinitions(): List<ApiConnectionProviderDefinition> {
    return allApiConnectionProviders()
        .filter { it.secretKeys.isNotEmpty() }
        .distinctBy { it.secretKeys.first() }
}

internal fun apiConnectionProviderForId(idOrAlias: String): ApiConnectionProviderDefinition? {
    val normalized = idOrAlias.normalizedProviderId()
    val aliased = providerAliases[normalized] ?: normalized
    return allApiConnectionProviders().firstOrNull { provider ->
        val candidates = listOfNotNull(
            provider.id,
            provider.label,
            provider.sourceValue,
            provider.mainApi
        ) + provider.secretKeys
        candidates.any { it.normalizedProviderId() == aliased }
    }
}

internal fun activeApiConnectionProvider(settings: Map<String, Any?>): ApiConnectionProviderDefinition {
    val mainApi = settings.stringAnyValue("main_api").ifBlank { "openai" }
    return when (mainApi) {
        "openai" -> {
            val source = settings.mapAnyValue("oai_settings")
                .stringAnyValue("chat_completion_source")
                .ifBlank { "openai" }
            apiConnectionProviderForId(source) ?: apiConnectionProviderForId("openai")!!
        }
        "textgenerationwebui" -> {
            val type = settings.mapAnyValue("textgenerationwebui_settings")
                .stringAnyValue("type")
                .ifBlank { "ooba" }
            apiConnectionProviderForId(type) ?: apiConnectionProviderForId("ooba")!!
        }
        "kobold", "koboldhorde" -> allApiConnectionProviders().first { it.id == mainApi }
        "novel" -> apiConnectionProviderForId("novelai")!!
        else -> apiConnectionProviderForId(mainApi) ?: apiConnectionProviderForId("openai")!!
    }
}

internal fun settingsWithSelectedApiProvider(
    settings: Map<String, Any?>,
    provider: ApiConnectionProviderDefinition
): Map<String, Any?> {
    val updated = settings.toMutableMap()
    updated.remove("api_type")
    updated["main_api"] = provider.mainApi

    when {
        provider.mode == "cc" && provider.sourceValue != null -> {
            val oaiSettings = updated.mapAnyValue("oai_settings").toMutableMap()
            oaiSettings["chat_completion_source"] = provider.sourceValue
            updated["oai_settings"] = oaiSettings
        }
        provider.mainApi == "textgenerationwebui" && provider.sourceValue != null -> {
            val textGenSettings = updated.mapAnyValue("textgenerationwebui_settings").toMutableMap()
            textGenSettings["type"] = provider.sourceValue
            updated["textgenerationwebui_settings"] = textGenSettings
        }
    }

    return updated
}

internal fun modelForProvider(
    settings: Map<String, Any?>,
    provider: ApiConnectionProviderDefinition
): String {
    val group = provider.modelSettingsGroup ?: return ""
    val key = provider.modelKey ?: return ""
    return settings.mapAnyValue(group).stringAnyValue(key)
}

private fun ApiConnectionProviderDefinition.withSecretState(
    secrets: List<SecretProviderState>
): ApiConnectionProviderState {
    val entries = secretKeys
        .flatMap { secretKey ->
            secrets.firstOrNull { it.key == secretKey }?.entries.orEmpty()
        }
    return ApiConnectionProviderState(
        definition = this,
        configuredEntryCount = entries.size,
        activeSecretLabel = entries.firstOrNull { it.active }?.label ?: entries.firstOrNull()?.label
    )
}

private fun ApiConnectionProviderDefinition.configuredEntryCount(
    secrets: List<SecretProviderState>
): Int {
    return secretKeys.sumOf { secretKey ->
        secrets.firstOrNull { it.key == secretKey }?.entries.orEmpty().size
    }
}

private fun allApiConnectionProviders(): List<ApiConnectionProviderDefinition> = apiConnectionProviders

private val apiConnectionProviders = listOf(
    ApiConnectionProviderDefinition(
        id = "openai",
        label = "OpenAI",
        icon = "O",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "openai",
        secretKeys = listOf("api_key_openai"),
        modelSettingsGroup = "oai_settings",
        modelKey = "openai_model"
    ),
    ApiConnectionProviderDefinition(
        id = "anthropic",
        label = "Claude",
        icon = "C",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "claude",
        secretKeys = listOf("api_key_claude"),
        modelSettingsGroup = "oai_settings",
        modelKey = "claude_model"
    ),
    ApiConnectionProviderDefinition(
        id = "google",
        label = "Google AI",
        icon = "G",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "makersuite",
        secretKeys = listOf("api_key_makersuite"),
        modelSettingsGroup = "oai_settings",
        modelKey = "google_model"
    ),
    ApiConnectionProviderDefinition(
        id = "mistralai",
        label = "Mistral",
        icon = "M",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "mistralai",
        secretKeys = listOf("api_key_mistralai"),
        modelSettingsGroup = "oai_settings",
        modelKey = "mistralai_model"
    ),
    ApiConnectionProviderDefinition(
        id = "openrouter",
        label = "OpenRouter",
        icon = "R",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "openrouter",
        secretKeys = listOf("api_key_openrouter"),
        modelSettingsGroup = "oai_settings",
        modelKey = "openrouter_model"
    ),
    ApiConnectionProviderDefinition(
        id = "deepseek",
        label = "DeepSeek",
        icon = "D",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "deepseek",
        secretKeys = listOf("api_key_deepseek"),
        modelSettingsGroup = "oai_settings",
        modelKey = "deepseek_model"
    ),
    ApiConnectionProviderDefinition(
        id = "xai",
        label = "xAI Grok",
        icon = "X",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "xai",
        secretKeys = listOf("api_key_xai"),
        modelSettingsGroup = "oai_settings",
        modelKey = "xai_model"
    ),
    ApiConnectionProviderDefinition(
        id = "cohere",
        label = "Cohere",
        icon = "C",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "cohere",
        secretKeys = listOf("api_key_cohere"),
        modelSettingsGroup = "oai_settings",
        modelKey = "cohere_model"
    ),
    ApiConnectionProviderDefinition(
        id = "perplexity",
        label = "Perplexity",
        icon = "P",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "perplexity",
        secretKeys = listOf("api_key_perplexity"),
        modelSettingsGroup = "oai_settings",
        modelKey = "perplexity_model"
    ),
    ApiConnectionProviderDefinition(
        id = "koboldcpp",
        label = "KoboldCpp",
        icon = "K",
        mode = "tc",
        mainApi = "textgenerationwebui",
        sourceValue = "koboldcpp",
        secretKeys = listOf("api_key_koboldcpp"),
        modelSettingsGroup = "textgenerationwebui_settings"
    ),
    ApiConnectionProviderDefinition(
        id = "ooba",
        label = "Text-Gen WebUI",
        icon = "T",
        mode = "tc",
        mainApi = "textgenerationwebui",
        sourceValue = "ooba",
        secretKeys = listOf("api_key_ooba"),
        modelSettingsGroup = "textgenerationwebui_settings",
        modelKey = "custom_model"
    ),
    ApiConnectionProviderDefinition(
        id = "tabby",
        label = "TabbyAPI",
        icon = "T",
        mode = "tc",
        mainApi = "textgenerationwebui",
        sourceValue = "tabby",
        secretKeys = listOf("api_key_tabby"),
        modelSettingsGroup = "textgenerationwebui_settings",
        modelKey = "tabby_model"
    ),
    ApiConnectionProviderDefinition(
        id = "aphrodite",
        label = "Aphrodite",
        icon = "A",
        mode = "tc",
        mainApi = "textgenerationwebui",
        sourceValue = "aphrodite",
        secretKeys = listOf("api_key_aphrodite"),
        modelSettingsGroup = "textgenerationwebui_settings",
        modelKey = "aphrodite_model"
    ),
    ApiConnectionProviderDefinition(
        id = "mancer",
        label = "Mancer",
        icon = "M",
        mode = "tc",
        mainApi = "textgenerationwebui",
        sourceValue = "mancer",
        secretKeys = listOf("api_key_mancer"),
        modelSettingsGroup = "textgenerationwebui_settings",
        modelKey = "mancer_model"
    ),
    ApiConnectionProviderDefinition(
        id = "featherless",
        label = "Featherless",
        icon = "F",
        mode = "tc",
        mainApi = "textgenerationwebui",
        sourceValue = "featherless",
        secretKeys = listOf("api_key_featherless"),
        modelSettingsGroup = "textgenerationwebui_settings",
        modelKey = "featherless_model"
    ),
    ApiConnectionProviderDefinition(
        id = "horde",
        label = "Horde (文本)",
        icon = "H",
        mode = "tc",
        mainApi = "koboldhorde",
        secretKeys = listOf("api_key_horde")
    ),
    ApiConnectionProviderDefinition(
        id = "llamacpp",
        label = "llama.cpp",
        icon = "L",
        mode = "tc",
        mainApi = "textgenerationwebui",
        sourceValue = "llamacpp",
        secretKeys = listOf("api_key_llamacpp"),
        modelSettingsGroup = "textgenerationwebui_settings",
        modelKey = "llamacpp_model"
    ),
    ApiConnectionProviderDefinition(
        id = "ollama",
        label = "Ollama",
        icon = "O",
        mode = "tc",
        mainApi = "textgenerationwebui",
        sourceValue = "ollama",
        secretKeys = emptyList(),
        modelSettingsGroup = "textgenerationwebui_settings",
        modelKey = "ollama_model",
        requiresSecret = false
    ),
    ApiConnectionProviderDefinition(
        id = "koboldhorde",
        label = "AI Horde",
        icon = "H",
        mode = "kobold",
        mainApi = "koboldhorde",
        secretKeys = listOf("api_key_horde")
    ),
    ApiConnectionProviderDefinition(
        id = "kobold",
        label = "KoboldAI Classic",
        icon = "K",
        mode = "kobold",
        mainApi = "kobold",
        secretKeys = emptyList(),
        requiresSecret = false
    ),
    ApiConnectionProviderDefinition(
        id = "novelai",
        label = "NovelAI Official",
        icon = "N",
        mode = "novel",
        mainApi = "novel",
        secretKeys = listOf("api_key_novel")
    )
)

private val providerAliases = mapOf(
    "claude" to "anthropic",
    "anthropic" to "anthropic",
    "googleai" to "google",
    "google" to "google",
    "gemini" to "google",
    "makersuite" to "google",
    "palm" to "google",
    "textgenwebui" to "ooba",
    "textgenerationwebui" to "ooba",
    "novel" to "novelai",
    "novelai" to "novelai",
    "xai" to "xai",
    "grok" to "xai"
)

private fun String.normalizedProviderId(): String {
    return lowercase(Locale.US)
        .replace("api_key_", "")
        .replace("_", "")
        .replace("-", "")
        .replace(" ", "")
        .replace(".", "")
        .replace("(", "")
        .replace(")", "")
}

private fun Map<*, *>.toStringKeyMap(): Map<String, Any?> {
    return entries.associate { (key, value) ->
        key.toString() to when (value) {
            is Map<*, *> -> value.toStringKeyMap()
            is List<*> -> value.map { item ->
                if (item is Map<*, *>) item.toStringKeyMap() else item
            }
            else -> value
        }
    }
}

private fun Map<String, Any?>.mapAnyValue(key: String): Map<String, Any?> {
    return (get(key) as? Map<*, *>)?.toStringKeyMap() ?: emptyMap()
}

private fun Map<String, Any?>.stringAnyValue(key: String): String {
    return (get(key) as? String).orEmpty()
}
