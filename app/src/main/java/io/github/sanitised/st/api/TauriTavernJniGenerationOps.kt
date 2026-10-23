package io.github.sanitised.st.api

import io.github.sanitised.st.chat.prompt.GenerationDeltaParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val activeChatStreams = ConcurrentHashMap<String, String>()

internal suspend fun TauriTavernJniClient.doFetchModels(
    mode: String,
    sourceValue: String,
    apiServer: String
): List<String> {
    return runCatching {
        val trimmedServer = apiServer.trim()
        val payload = linkedMapOf<String, Any?>(
            "chat_completion_source" to sourceValue,
            "textgenerationwebui_type" to sourceValue,
            "api_type" to sourceValue,
            "api_server" to trimmedServer,
            "reverse_proxy" to if (sourceValue == "custom") "" else trimmedServer,
            "proxy_password" to "",
            "custom_url" to if (sourceValue == "custom") trimmedServer else ""
        )
        val data = invokeRaw("chat_completion_status", payload + mapOf("request" to payload))
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
        val trimmedServer = apiServer.trim()
        val payload = linkedMapOf<String, Any?>(
            "chat_completion_source" to sourceValue,
            "textgenerationwebui_type" to sourceValue,
            "api_type" to sourceValue,
            "api_server" to trimmedServer,
            "reverse_proxy" to if (sourceValue == "custom") "" else trimmedServer,
            "proxy_password" to "",
            "custom_url" to if (sourceValue == "custom") trimmedServer else ""
        )
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
            val probeModel = when (sourceValue) {
                "minimax" -> "MiniMax-M2.7"
                "vertexai" -> "gemini-2.5-flash"
                else -> ""
            }
            if (probeModel.isNotBlank()) {
                val probePayload = linkedMapOf<String, Any?>(
                    "chat_completion_source" to sourceValue,
                    "model" to probeModel,
                    "messages" to listOf(mapOf("role" to "user", "content" to "Hi")),
                    "max_tokens" to 1,
                    "stream" to false,
                    "reverse_proxy" to if (sourceValue == "custom") "" else trimmedServer,
                    "proxy_password" to "",
                    "custom_url" to if (sourceValue == "custom") trimmedServer else ""
                )
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
