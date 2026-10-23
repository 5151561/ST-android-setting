package io.github.sanitised.st.api

import io.github.sanitised.st.chat.prompt.GenerationDeltaParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val activeChatStreams = ConcurrentHashMap<String, String>()

private suspend fun TauriTavernJniClient.buildStatusPayload(
    sourceValue: String,
    apiServer: String
): Pair<LinkedHashMap<String, Any?>, Map<String, Any?>> {
    val trimmedServer = apiServer.trim()
    val settings = runCatching { doGetSettings() }.getOrDefault(emptyMap())
    val oai = (settings["oai_settings"] as? Map<*, *>)
        ?.entries
        ?.associate { (k, v) -> k.toString() to v }
        .orEmpty()
    val proxyPassword = ((oai["proxy_password_$sourceValue"] as? String)?.takeIf { it.isNotBlank() }
        ?: (oai["proxy_password"] as? String)).orEmpty()
    val customUrl = if (sourceValue == "custom") {
        trimmedServer.ifBlank { (oai["custom_url"] as? String).orEmpty().trim() }
    } else {
        ""
    }
    val reverseProxy = if (sourceValue == "custom") {
        ""
    } else {
        trimmedServer.ifBlank {
            ((oai["reverse_proxy_$sourceValue"] as? String)?.takeIf { it.isNotBlank() }
                ?: (oai["reverse_proxy"] as? String)).orEmpty().trim()
        }
    }
    val payload = linkedMapOf<String, Any?>(
        "chat_completion_source" to sourceValue,
        "textgenerationwebui_type" to sourceValue,
        "api_type" to sourceValue,
        "api_server" to trimmedServer,
        "reverse_proxy" to reverseProxy,
        "proxy_password" to proxyPassword,
        "custom_url" to customUrl
    )
    val forwardedStringKeys = listOf(
        "secret_id",
        "siliconflow_endpoint",
        "opencode_endpoint",
        "opencode_api_format",
        "minimax_endpoint",
        "moonshot_endpoint",
        "pollinations_endpoint",
        "zai_endpoint",
        "workers_ai_account_id",
        "aws_bedrock_region",
        "custom_include_headers",
        "custom_api_format",
        "vertexai_auth_mode",
        "vertexai_region",
        "vertexai_express_project_id",
        "nanogpt_provider"
    )
    for (key in forwardedStringKeys) {
        (oai[key] as? String)?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            payload[key] = value
        }
    }
    if (sourceValue == "custom") {
        payload["custom_api_format"] = (oai["custom_api_format"] as? String)?.takeIf { it.isNotBlank() } ?: "openai_compat"
        if ((oai["custom_include_headers"] as? String)?.isNotBlank() == true) {
            payload["custom_include_headers"] = oai["custom_include_headers"]
        }
    }
    if (sourceValue == "pollinations") {
        payload["pollinations_endpoint"] = (oai["pollinations_endpoint"] as? String)?.takeIf { it.isNotBlank() } ?: "text"
    }
    if (sourceValue == "vertexai") {
        payload.putIfAbsent("vertexai_auth_mode", "express")
        payload.putIfAbsent("vertexai_region", "us-central1")
        payload.putIfAbsent("vertexai_express_project_id", (oai["vertexai_express_project_id"] as? String).orEmpty())
    }
    return payload to oai
}

internal suspend fun TauriTavernJniClient.doFetchModels(
    mode: String,
    sourceValue: String,
    apiServer: String
): List<String> {
    return runCatching {
        val (payload, oai) = buildStatusPayload(sourceValue, apiServer)
        val data = invokeRaw("chat_completion_status", payload + mapOf("request" to payload))
        val map = data as? Map<*, *>
        if (map != null && map["bypass"] == true) {
            return@runCatching when (sourceValue) {
                "vertexai" -> {
                    val authMode = (oai["vertexai_auth_mode"] as? String).orEmpty().lowercase()
                    val baseGemini = listOf(
                        "gemini-2.5-flash",
                        "gemini-2.5-pro",
                        "gemini-2.0-flash-001",
                        "gemini-1.5-pro-002",
                        "gemini-1.5-flash-002"
                    )
                    val claudeModels = listOf(
                        "claude-sonnet-4-5@20250929",
                        "claude-opus-4-5@20251101",
                        "claude-3-7-sonnet@20250219",
                        "claude-3-5-sonnet-v2@20241022",
                        "claude-3-5-haiku@20241022"
                    )
                    if (authMode == "full" || (payload["reverse_proxy"] as? String).orEmpty().isNotBlank()) {
                        baseGemini + claudeModels
                    } else {
                        baseGemini
                    }
                }
                "minimax" -> listOf("MiniMax-M2.7", "MiniMax-Text-01", "abab6.5s-chat")
                else -> emptyList()
            }
        }
        val list = when (data) {
            is List<*> -> data
            is Map<*, *> -> (data["data"] as? List<*>)
                ?: (data["models"] as? List<*>)
                ?: (data["result"] as? List<*>)
                ?: emptyList<Any?>()
            else -> emptyList<Any?>()
        }
        list.mapNotNull { item ->
            when (item) {
                is String -> item.takeIf { it.isNotBlank() }
                is Map<*, *> -> item.stringValue("id").takeIf { it.isNotBlank() }
                    ?: item.stringValue("name").takeIf { it.isNotBlank() }
                else -> null
            }
        }.distinct().sorted()
    }.getOrDefault(emptyList())
}

internal suspend fun TauriTavernJniClient.doTestConnection(
    mode: String,
    sourceValue: String,
    apiServer: String
): ConnectionTestResult {
    return try {
        val (payload, oai) = buildStatusPayload(sourceValue, apiServer)
        val data = invokeRaw("chat_completion_status", payload + mapOf("request" to payload))
        val map = data as? Map<*, *>
        val hasError = map != null && map["error"] == true
        if (hasError) {
            val msg = map["message"]?.toString()
            return ConnectionTestResult(
                success = false,
                errorMessage = msg ?: "API 返回错误，请检查密钥"
            )
        }
        if (map != null && map["bypass"] == true) {
            val modelMap = mapOf(
                "openai" to "openai_model",
                "claude" to "claude_model",
                "makersuite" to "google_model",
                "vertexai" to "vertexai_model",
                "openrouter" to "openrouter_model",
                "ai21" to "ai21_model",
                "mistralai" to "mistralai_model",
                "custom" to "custom_model",
                "cohere" to "cohere_model",
                "perplexity" to "perplexity_model",
                "groq" to "groq_model",
                "siliconflow" to "siliconflow_model",
                "nanogpt" to "nanogpt_model",
                "deepseek" to "deepseek_model",
                "xai" to "xai_model",
                "pollinations" to "pollinations_model",
                "moonshot" to "moonshot_model",
                "fireworks" to "fireworks_model",
                "cometapi" to "cometapi_model",
                "azure_openai" to "azure_openai_model",
                "zai" to "zai_model",
                "chutes" to "chutes_model",
                "minimax" to "minimax_model",
                "opencode" to "opencode_model",
                "aws_bedrock" to "aws_bedrock_model",
                "workers_ai" to "workers_ai_model"
            )
            val modelKey = modelMap[sourceValue] ?: "${sourceValue}_model"
            val configuredModel = (oai[modelKey] as? String)?.trim().orEmpty()
            val probeModel = configuredModel.ifBlank {
                when (sourceValue) {
                    "minimax" -> "MiniMax-M2.7"
                    "vertexai" -> "gemini-2.5-flash"
                    else -> ""
                }
            }
            if (probeModel.isNotBlank()) {
                val probePayload = LinkedHashMap<String, Any?>(payload).apply {
                    put("model", probeModel)
                    put("messages", listOf(mapOf("role" to "user", "content" to "Hi")))
                    put("max_tokens", 1)
                    put("stream", false)
                    if (sourceValue == "custom") {
                        put("custom_api_format", (oai["custom_api_format"] as? String)?.takeIf { it.isNotBlank() } ?: "openai_compat")
                        put("custom_url", (payload["custom_url"] as? String).orEmpty())
                        put("custom_include_headers", (oai["custom_include_headers"] as? String).orEmpty())
                        put("custom_include_body", (oai["custom_include_body"] as? String).orEmpty())
                        put("custom_exclude_body", (oai["custom_exclude_body"] as? String).orEmpty())
                        put("custom_claude_prompt_caching", (oai["custom_claude_prompt_caching"] as? Boolean) ?: false)
                        put("custom_openai_responses_websocket", (oai["custom_openai_responses_websocket"] as? Boolean) ?: false)
                    }
                    if (sourceValue == "vertexai") {
                        put("vertexai_auth_mode", (oai["vertexai_auth_mode"] as? String)?.takeIf { it.isNotBlank() } ?: "express")
                        put("vertexai_region", (oai["vertexai_region"] as? String)?.takeIf { it.isNotBlank() } ?: "us-central1")
                        put("vertexai_express_project_id", (oai["vertexai_express_project_id"] as? String).orEmpty())
                    }
                }
                doGenerateChatCompletion(probePayload)
            }
        }
        val models = parseModelList(jsonValue(data)).ifEmpty {
            doFetchModels(mode, sourceValue, apiServer)
        }
        ConnectionTestResult(success = true, models = models)
    } catch (e: Exception) {
        ConnectionTestResult(
            success = false,
            errorMessage = e.message?.take(120) ?: "连接失败"
        )
    }
}

internal suspend fun TauriTavernJniClient.doGenerateChatCompletion(
    payload: Map<String, Any?>
): String {
    val req = payload.toMutableMap().apply { put("stream", false) }
    val data = invokeRaw("chat_completion_generate", req + mapOf("request" to req))
    return extractCompletionReply(data)
}

internal suspend fun TauriTavernJniClient.doGenerateTextCompletion(
    payload: Map<String, Any?>
): String {
    val req = payload.toMutableMap().apply { put("stream", false) }
    val data = invokeRaw("chat_completion_generate", req + mapOf("request" to req))
    return extractCompletionReply(data)
}

private fun extractCompletionReply(data: Any?): String {
    if (data == null) throw IllegalStateException("生成响应为空")
    if (data is String) {
        return GenerationDeltaParser.extract(data) ?: data
    }
    if (data is Map<*, *>) {
        data["error"]?.takeIf { it != false }?.let { err ->
            val message = (err as? Map<*, *>)?.get("message")?.toString()
                ?: data["message"]?.toString()
                ?: "生成失败"
            throw IllegalStateException(message)
        }
        val choices = data["choices"] as? List<*>
        val first = choices?.firstOrNull() as? Map<*, *>
        val message = first?.get("message") as? Map<*, *>
        val extracted = (message?.get("content") as? String)
            ?: (first?.get("text") as? String)
            ?: (data["content"] as? String)
            ?: (data["response"] as? String)
        if (extracted != null) return extracted
    }
    val jsonStr = jsonValue(data)
    return GenerationDeltaParser.extract(jsonStr) ?: jsonStr
}

internal fun TauriTavernJniClient.doGenerateChatCompletionStream(
    payload: Map<String, Any?>
): Flow<String> = streamCompletionViaJni(
    payload = payload
)

internal fun TauriTavernJniClient.doGenerateTextCompletionStream(
    payload: Map<String, Any?>
): Flow<String> = streamCompletionViaJni(
    payload = payload
)

internal fun TauriTavernJniClient.doSendMessage(
    chatId: String,
    text: String
): Flow<GenerationChunk> {
    val streamKey = UUID.randomUUID().toString()
    activeChatStreams[chatId] = streamKey
    val payload = linkedMapOf<String, Any?>(
        "stream" to true,
        "chat_id" to chatId,
        "messages" to listOf(mapOf("role" to "user", "content" to text))
    )
    return streamCompletionViaJni(
        payload = payload,
        chatId = chatId
    ).map { delta ->
        GenerationChunk(text = delta, isFinal = false)
    }
}

internal suspend fun TauriTavernJniClient.doStopGeneration(chatId: String) {
    val streamId = activeChatStreams.remove(chatId) ?: chatId
    runCatching {
        invokeCloseChatStream(streamId)
    }
    runCatching {
        invokeRaw(
            "chat_completion_abort",
            mapOf("streamId" to streamId, "stream_id" to streamId, "requestId" to streamId, "request_id" to streamId)
        )
    }
}

private fun TauriTavernJniClient.streamCompletionViaJni(
    payload: Map<String, Any?>,
    chatId: String? = null
): Flow<String> = flow {
    val req = payload.toMutableMap().apply { put("stream", true) }
    val startData = invokeStartChatStream(req)
    val streamId = when (startData) {
        is String -> startData
        is Map<*, *> -> startData.stringValue("streamId")
            .ifBlank { startData.stringValue("stream_id") }
            .ifBlank { startData.stringValue("id") }
        else -> ""
    }
    if (streamId.isBlank()) {
        return@flow
    }
    if (!chatId.isNullOrBlank()) {
        activeChatStreams[chatId] = streamId
    }
    var afterSeq: Long = 0L
    try {
        while (true) {
            val readMap = invokeReadChatStream(streamId, afterSeq)
            val events = readMap["events"] as? List<*> ?: emptyList<Any?>()
            var streamDone = false
            for (rawEvent in events) {
                val ev = rawEvent as? Map<*, *> ?: continue
                val seq = (ev["seq"] as? Number)?.toLong()
                if (seq != null && seq > afterSeq) {
                    afterSeq = seq
                }
                val type = (ev["type"] as? String).orEmpty()
                when (type) {
                    "chunk" -> {
                        val rawChunk = (ev["data"] as? String)
                            ?: (ev["delta"] as? String)
                            ?: (ev["text"] as? String).orEmpty()
                        val delta = extractStreamDelta(rawChunk)
                        if (delta.isNotEmpty()) {
                            emit(delta)
                        }
                    }
                    "error" -> {
                        val errMsg = (ev["message"] as? String)
                            ?: (ev["error"] as? String)
                            ?: "Stream generation error"
                        throw IllegalStateException(errMsg)
                    }
                    "done" -> {
                        streamDone = true
                    }
                }
            }
            if (events.isEmpty()) {
                val chunksList = readMap["chunks"] as? List<*>
                if (chunksList != null) {
                    for (c in chunksList) {
                        val str = c?.toString().orEmpty()
                        val delta = extractStreamDelta(str)
                        if (delta.isNotEmpty()) {
                            emit(delta)
                        }
                    }
                }
            }
            val nextSeq = (readMap["seq"] as? Number)?.toLong()
                ?: (readMap["nextSeq"] as? Number)?.toLong()
                ?: (readMap["next_seq"] as? Number)?.toLong()
                ?: (readMap["lastSeq"] as? Number)?.toLong()
            if (nextSeq != null && nextSeq > afterSeq) {
                afterSeq = nextSeq
            }
            val status = (readMap["status"] as? String).orEmpty()
            val doneFlag = (readMap["done"] as? Boolean) ?: false
            if (streamDone || doneFlag ||
                status.equals("done", ignoreCase = true) ||
                status.equals("completed", ignoreCase = true) ||
                status.equals("cancelled", ignoreCase = true) ||
                status.equals("error", ignoreCase = true)
            ) {
                break
            }
            if (events.isEmpty() && (readMap["chunks"] as? List<*>).isNullOrEmpty()) {
                delay(25L)
            }
        }
    } finally {
        if (!chatId.isNullOrBlank()) {
            activeChatStreams.remove(chatId, streamId)
        }
        runCatching { invokeCloseChatStream(streamId) }
    }
}

private fun extractStreamDelta(rawChunk: String): String {
    val trimmed = rawChunk.trim()
    if (trimmed.isEmpty() || trimmed == "[DONE]") return ""
    if (trimmed.startsWith("data:")) {
        val ssePayload = trimmed.removePrefix("data:").trim()
        if (ssePayload == "[DONE]") return ""
        return GenerationDeltaParser.extract(ssePayload) ?: ssePayload
    }
    if (trimmed.startsWith("{")) {
        return GenerationDeltaParser.extract(trimmed) ?: ""
    }
    return rawChunk
}
