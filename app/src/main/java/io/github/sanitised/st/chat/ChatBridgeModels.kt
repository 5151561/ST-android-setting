package io.github.sanitised.st.chat

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class BridgeMessage(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val name: String,
    val payload: JSONObject = JSONObject(),
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = JSONObject()
        .put("id", id)
        .put("kind", kind)
        .put("name", name)
        .put("payload", payload)
        .put("timestamp", timestamp)
        .toString()
}

data class ChatMessage(
    val id: Int,
    val name: String,
    val mes: String,
    val isUser: Boolean,
    val isSystem: Boolean,
    val sendDate: String,
    val swipeId: Int,
    val swipes: List<String>,
    val extra: JSONObject
) {
    companion object {
        fun fromJson(json: JSONObject): ChatMessage = ChatMessage(
            id = json.optInt("id", -1),
            name = json.optString("name", ""),
            mes = json.optString("mes", ""),
            isUser = json.optBoolean("is_user", false),
            isSystem = json.optBoolean("is_system", false),
            sendDate = json.optString("send_date", ""),
            swipeId = json.optInt("swipe_id", 0),
            swipes = json.optJSONArray("swipes")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it, "") }
            } ?: emptyList(),
            extra = json.optJSONObject("extra") ?: JSONObject()
        )
    }
}

data class MediaAttachment(
    val url: String,
    val type: String,
    val title: String
)

data class FileAttachment(
    val url: String,
    val name: String,
    val size: Long
)

val ChatMessage.mediaAttachments: List<MediaAttachment>
    get() = extra.optJSONArray("media").parseObjectsNotNull { item ->
        val url = item.attachmentUrl()
        if (url.isBlank()) return@parseObjectsNotNull null
        MediaAttachment(
            url = url,
            type = item.optString("type", ""),
            title = item.optString("title").ifBlank { item.optString("name") }
        )
    }

val ChatMessage.fileAttachments: List<FileAttachment>
    get() = extra.optJSONArray("files").parseObjectsNotNull { item ->
        val url = item.attachmentUrl()
        if (url.isBlank()) return@parseObjectsNotNull null
        FileAttachment(
            url = url,
            name = item.optString("name").ifBlank { item.optString("title") },
            size = item.optLong("size", 0L)
        )
    }

private fun JSONObject.attachmentUrl(): String =
    optString("url").ifBlank { optString("path") }

private inline fun <T> JSONArray?.parseObjectsNotNull(transform: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.let(transform)
    }
}

data class ChatSnapshot(
    val mode: String,
    val avatarUrl: String,
    val characterName: String,
    val chatFile: String,
    val isGenerating: Boolean,
    val messages: List<ChatMessage>,
    val metadata: JSONObject
) {
    companion object {
        fun fromJson(json: JSONObject): ChatSnapshot = ChatSnapshot(
            mode = json.optString("mode", "character"),
            avatarUrl = json.optString("avatarUrl", ""),
            characterName = json.optString("characterName", ""),
            chatFile = json.optString("chatFile", ""),
            isGenerating = json.optBoolean("isGenerating", false),
            messages = json.optJSONArray("messages")?.let { arr ->
                (0 until arr.length()).map { ChatMessage.fromJson(arr.getJSONObject(it)) }
            } ?: emptyList(),
            metadata = json.optJSONObject("metadata") ?: JSONObject()
        )
    }
}

sealed class BridgeEvent {
    data class RuntimeReady(val raw: JSONObject) : BridgeEvent()
    data class RuntimeError(val message: String, val raw: JSONObject) : BridgeEvent()
    data class ChatLoaded(val snapshot: ChatSnapshot) : BridgeEvent()
    data class ChatChanged(val raw: JSONObject) : BridgeEvent()
    data class MessageAdded(val message: ChatMessage) : BridgeEvent()
    data class MessageUpdated(val message: ChatMessage) : BridgeEvent()
    data class MessageDeleted(val messageId: Int) : BridgeEvent()
    data class GenerationStarted(val raw: JSONObject) : BridgeEvent()
    data class GenerationEnded(val raw: JSONObject) : BridgeEvent()
    data class GenerationStopped(val raw: JSONObject) : BridgeEvent()
    data class GenerationError(val message: String, val raw: JSONObject) : BridgeEvent()
    data class StreamToken(val messageId: Int, val token: String, val fullText: String) : BridgeEvent()
    data class SaveError(val message: String, val raw: JSONObject) : BridgeEvent()
    data class CommandResult(val commandId: String, val payload: JSONObject) : BridgeEvent()
    data class CommandError(val commandId: String, val message: String) : BridgeEvent()

    companion object {
        fun parse(json: String): BridgeEvent? {
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val name = obj.optString("name", "")
            val payload = obj.optJSONObject("payload") ?: JSONObject()
            return when (name) {
                "runtime.ready" -> RuntimeReady(payload)
                "runtime.error" -> RuntimeError(payload.optString("message", "unknown"), payload)
                "chat.loaded" -> ChatLoaded(ChatSnapshot.fromJson(payload))
                "chat.changed" -> ChatChanged(payload)
                "message.added" -> MessageAdded(ChatMessage.fromJson(payload))
                "message.updated" -> MessageUpdated(ChatMessage.fromJson(payload))
                "message.deleted" -> MessageDeleted(payload.optInt("id", -1))
                "generation.started" -> GenerationStarted(payload)
                "generation.ended" -> GenerationEnded(payload)
                "generation.stopped" -> GenerationStopped(payload)
                "generation.error" -> GenerationError(payload.optString("message", "unknown"), payload)
                "save.error" -> SaveError(payload.optString("message", "unknown"), payload)
                "stream.token" -> StreamToken(
                    messageId = payload.optInt("id", -1),
                    token = payload.optString("token", ""),
                    fullText = payload.optString("fullText", "")
                )
                "bridge.result" -> CommandResult(
                    commandId = obj.optString("id", ""),
                    payload = payload
                )
                "bridge.error" -> CommandError(
                    commandId = obj.optString("id", ""),
                    message = payload.optString("message", "unknown")
                )
                else -> null
            }
        }
    }
}
