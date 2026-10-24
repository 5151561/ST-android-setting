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
    val requiresSecret: Boolean = true,
    val endpointHint: String = "https://api.openai.com/v1",
    val supportsReverseProxy: Boolean = false,
    val endpointSettingKey: String? = null,
    val endpointOptions: List<Pair<String, String>> = emptyList()
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
    val connectionStatusOk: Boolean,
    val verificationStatusText: String = "尚未测试",
    val verificationStatusTone: String? = null
)

internal object ConnectionVerificationRegistry {
    private val statusByProviderId = java.util.concurrent.ConcurrentHashMap<String, ConnectionVerifyStatus>()

    fun record(providerId: String, status: ConnectionVerifyStatus) {
        statusByProviderId[providerId] = status
    }

    fun get(providerId: String): ConnectionVerifyStatus =
        statusByProviderId[providerId] ?: ConnectionVerifyStatus.NOT_VERIFIED
}

internal fun buildApiConnectionUiState(
    settings: Map<String, Any?>,
    secrets: List<SecretProviderState>,
    serviceRunning: Boolean,
    selectedProviderId: String? = null,
    selectedMode: String? = null,
    verificationStatus: ConnectionVerifyStatus? = null
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
    val effectiveVerifyStatus = verificationStatus ?: ConnectionVerificationRegistry.get(activeProvider.id)
    val connectionStatusText = when {
        !serviceRunning -> "服务未启动"
        effectiveVerifyStatus == ConnectionVerifyStatus.SUCCESS -> "连接正常"
        effectiveVerifyStatus == ConnectionVerifyStatus.FAILED -> "验证失败"
        activeProvider.hasConfiguredSecret && activeProvider.requiresSecret -> "密钥已配置，尚未验证"
        activeProvider.hasConfiguredSecret -> "无需密钥，尚未验证"
        else -> "未配置密钥"
    }
    val connectionStatusOk = serviceRunning &&
        activeProvider.hasConfiguredSecret &&
        effectiveVerifyStatus != ConnectionVerifyStatus.FAILED
    val verificationStatusText = when {
        !serviceRunning -> "服务未启动"
        effectiveVerifyStatus == ConnectionVerifyStatus.SUCCESS -> "已验证可用"
        effectiveVerifyStatus == ConnectionVerifyStatus.FAILED -> "验证失败"
        effectiveVerifyStatus == ConnectionVerifyStatus.TESTING -> "测试中…"
        else -> "尚未测试"
    }
    val verificationStatusTone = when (effectiveVerifyStatus) {
        ConnectionVerifyStatus.SUCCESS -> "ok"
        ConnectionVerifyStatus.FAILED -> "error"
        else -> null
    }

    return ApiConnectionUiState(
        activeMode = visibleMode,
        activeProvider = activeProvider,
        visibleProviders = visibleProviders,
        configuredProviderCount = configuredCount,
        activeModel = activeModel,
        serviceStatusText = serviceStatusText,
        connectionStatusText = connectionStatusText,
        connectionStatusOk = connectionStatusOk,
        verificationStatusText = verificationStatusText,
        verificationStatusTone = verificationStatusTone
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
            val src = provider.sourceValue
            oaiSettings["chat_completion_source"] = src
            val supportsProxy = provider.supportsReverseProxy ||
                src in setOf("openai", "claude", "mistralai", "makersuite", "vertexai", "deepseek", "xai", "zai", "moonshot")
            if (supportsProxy) {
                oaiSettings["reverse_proxy"] = oaiSettings.stringAnyValue("reverse_proxy_$src")
                oaiSettings["proxy_password"] = oaiSettings.stringAnyValue("proxy_password_$src")
            } else {
                oaiSettings["reverse_proxy"] = ""
                oaiSettings["proxy_password"] = ""
            }
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

private val legacyPlaceholderModels = setOf(
    "gpt-5.6-terra",
    "claude-sonnet-5",
    "gemini-3.7-flash",
    "OR_Website"
)

internal fun modelForProvider(
    settings: Map<String, Any?>,
    provider: ApiConnectionProviderDefinition
): String {
    val group = provider.modelSettingsGroup ?: return ""
    val key = provider.modelKey ?: return ""
    val raw = settings.mapAnyValue(group).stringAnyValue(key).trim()
    return if (raw in legacyPlaceholderModels) "" else raw
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
        modelKey = "openai_model",
        endpointHint = "https://api.openai.com/v1",
        supportsReverseProxy = true
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
        modelKey = "claude_model",
        endpointHint = "https://api.anthropic.com/v1",
        supportsReverseProxy = true
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
        modelKey = "google_model",
        endpointHint = "https://generativelanguage.googleapis.com",
        supportsReverseProxy = true
    ),
    ApiConnectionProviderDefinition(
        id = "vertexai",
        label = "Google Vertex AI",
        icon = "V",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "vertexai",
        secretKeys = listOf("api_key_vertexai", "vertexai_service_account_json"),
        modelSettingsGroup = "oai_settings",
        modelKey = "vertexai_model",
        endpointHint = "https://aiplatform.googleapis.com",
        supportsReverseProxy = true
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
        modelKey = "deepseek_model",
        endpointHint = "https://api.deepseek.com/v1",
        supportsReverseProxy = true
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
        modelKey = "openrouter_model",
        endpointHint = "https://openrouter.ai/api/v1"
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
        modelKey = "xai_model",
        endpointHint = "https://api.x.ai/v1",
        supportsReverseProxy = true
    ),
    ApiConnectionProviderDefinition(
        id = "groq",
        label = "Groq",
        icon = "Q",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "groq",
        secretKeys = listOf("api_key_groq"),
        modelSettingsGroup = "oai_settings",
        modelKey = "groq_model",
        endpointHint = "https://api.groq.com/openai/v1"
    ),
    ApiConnectionProviderDefinition(
        id = "moonshot",
        label = "Moonshot (Kimi)",
        icon = "K",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "moonshot",
        secretKeys = listOf("api_key_moonshot"),
        modelSettingsGroup = "oai_settings",
        modelKey = "moonshot_model",
        endpointHint = "https://api.moonshot.ai/v1",
        supportsReverseProxy = true,
        endpointSettingKey = "moonshot_endpoint",
        endpointOptions = listOf(
            "global" to "国际站 (moonshot.ai)",
            "cn" to "中国站 (moonshot.cn)"
        )
    ),
    ApiConnectionProviderDefinition(
        id = "siliconflow",
        label = "硅基流动",
        icon = "S",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "siliconflow",
        secretKeys = listOf("api_key_siliconflow"),
        modelSettingsGroup = "oai_settings",
        modelKey = "siliconflow_model",
        endpointHint = "https://api.siliconflow.cn/v1",
        endpointSettingKey = "siliconflow_endpoint",
        endpointOptions = listOf(
            "global" to "国际站 (siliconflow.com)",
            "cn" to "中国站 (siliconflow.cn)"
        )
    ),
    ApiConnectionProviderDefinition(
        id = "zai",
        label = "智谱 GLM",
        icon = "Z",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "zai",
        secretKeys = listOf("api_key_zai"),
        modelSettingsGroup = "oai_settings",
        modelKey = "zai_model",
        endpointHint = "https://api.z.ai/api/paas/v4",
        supportsReverseProxy = true,
        endpointSettingKey = "zai_endpoint",
        endpointOptions = listOf(
            "common" to "标准端点 (paas/v4)",
            "coding" to "Coding 端点 (coding/paas/v4)"
        )
    ),
    ApiConnectionProviderDefinition(
        id = "minimax",
        label = "MiniMax",
        icon = "M",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "minimax",
        secretKeys = listOf("api_key_minimax"),
        modelSettingsGroup = "oai_settings",
        modelKey = "minimax_model",
        endpointHint = "https://api.minimax.io/v1",
        endpointSettingKey = "minimax_endpoint",
        endpointOptions = listOf(
            "global" to "国际站 (minimax.io)",
            "cn" to "中国站 (minimaxi.com)"
        )
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
        modelKey = "cohere_model",
        endpointHint = "https://api.cohere.ai/v2"
    ),
    ApiConnectionProviderDefinition(
        id = "aws_bedrock",
        label = "AWS Bedrock",
        icon = "B",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "aws_bedrock",
        secretKeys = listOf("api_key_aws_bedrock"),
        modelSettingsGroup = "oai_settings",
        modelKey = "aws_bedrock_model",
        endpointHint = "https://bedrock-runtime.us-east-1.amazonaws.com"
    ),
    ApiConnectionProviderDefinition(
        id = "workers_ai",
        label = "Workers AI",
        icon = "W",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "workers_ai",
        secretKeys = listOf("api_key_workers_ai"),
        modelSettingsGroup = "oai_settings",
        modelKey = "workers_ai_model",
        endpointHint = "https://api.cloudflare.com/client/v4/accounts"
    ),
    ApiConnectionProviderDefinition(
        id = "opencode",
        label = "OpenCode",
        icon = "O",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "opencode",
        secretKeys = listOf("api_key_opencode"),
        modelSettingsGroup = "oai_settings",
        modelKey = "opencode_model",
        endpointHint = "https://opencode.ai/zen/v1"
    ),
    ApiConnectionProviderDefinition(
        id = "nanogpt",
        label = "NanoGPT",
        icon = "N",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "nanogpt",
        secretKeys = listOf("api_key_nanogpt"),
        modelSettingsGroup = "oai_settings",
        modelKey = "nanogpt_model",
        endpointHint = "https://nano-gpt.com/api/v1"
    ),
    ApiConnectionProviderDefinition(
        id = "chutes",
        label = "Chutes",
        icon = "C",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "chutes",
        secretKeys = listOf("api_key_chutes"),
        modelSettingsGroup = "oai_settings",
        modelKey = "chutes_model",
        endpointHint = "https://llm.chutes.ai/v1"
    ),
    ApiConnectionProviderDefinition(
        id = "pollinations",
        label = "Pollinations",
        icon = "P",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "pollinations",
        secretKeys = listOf("api_key_pollinations"),
        modelSettingsGroup = "oai_settings",
        modelKey = "pollinations_model",
        requiresSecret = false,
        endpointHint = "https://gen.pollinations.ai/v1",
        endpointSettingKey = "pollinations_endpoint",
        endpointOptions = listOf(
            "default" to "默认 (认证 gen.pollinations.ai)",
            "anonymous" to "匿名免密 (text.pollinations.ai)"
        )
    ),
    ApiConnectionProviderDefinition(
        id = "custom",
        label = "自定义兼容",
        icon = "U",
        mode = "cc",
        mainApi = "openai",
        sourceValue = "custom",
        secretKeys = listOf("api_key_custom"),
        modelSettingsGroup = "oai_settings",
        modelKey = "custom_model",
        requiresSecret = false,
        endpointHint = "https://your-oneapi.example.com/v1"
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
        id = "vllm",
        label = "vLLM",
        icon = "V",
        mode = "tc",
        mainApi = "textgenerationwebui",
        sourceValue = "vllm",
        secretKeys = listOf("api_key_vllm"),
        modelSettingsGroup = "textgenerationwebui_settings",
        modelKey = "vllm_model"
    ),
    ApiConnectionProviderDefinition(
        id = "huggingface",
        label = "HuggingFace",
        icon = "H",
        mode = "tc",
        mainApi = "textgenerationwebui",
        sourceValue = "huggingface",
        secretKeys = emptyList(),
        modelSettingsGroup = "textgenerationwebui_settings",
        modelKey = "huggingface_model",
        requiresSecret = false
    ),
    ApiConnectionProviderDefinition(
        id = "generic",
        label = "Generic OpenAI",
        icon = "G",
        mode = "tc",
        mainApi = "textgenerationwebui",
        sourceValue = "generic",
        secretKeys = listOf("api_key_generic"),
        modelSettingsGroup = "textgenerationwebui_settings",
        modelKey = "generic_model"
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
    "vertexai" to "vertexai",
    "vertex" to "vertexai",
    "googlevertexai" to "vertexai",
    "vertexaiserviceaccountjson" to "vertexai",
    "textgenwebui" to "ooba",
    "textgenerationwebui" to "ooba",
    "novel" to "novelai",
    "novelai" to "novelai",
    "xai" to "xai",
    "grok" to "xai",
    "kimi" to "moonshot",
    "moonshot" to "moonshot",
    "glm" to "zai",
    "zhipu" to "zai",
    "zai" to "zai",
    "siliconflow" to "siliconflow",
    "groq" to "groq",
    "minimax" to "minimax",
    "minimaxgroupid" to "minimax",
    "cohere" to "cohere",
    "awsbedrock" to "awsbedrock",
    "bedrock" to "awsbedrock",
    "workersai" to "workersai",
    "cloudflare" to "workersai",
    "cloudflareworkersai" to "workersai",
    "opencode" to "opencode",
    "nanogpt" to "nanogpt",
    "chutes" to "chutes",
    "pollinations" to "pollinations",
    "perplexity" to "perplexity",
    "ai21" to "ai21",
    "cometapi" to "cometapi",
    "vllm" to "vllm",
    "huggingface" to "huggingface",
    "generic" to "generic",
    "genericopenai" to "generic",
    "custom" to "custom",
    "openaicompatible" to "custom",
    "mistralai" to "custom"
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
