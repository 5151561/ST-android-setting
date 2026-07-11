package io.github.sanitised.st.chat

import org.json.JSONArray
import org.json.JSONObject

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

data class ToolInvocation(
    val id: String,
    val displayName: String,
    val name: String,
    val parameters: String,
    val result: String
)

data class ItemizedPromptComponent(
    val name: String,
    val tokens: Int
)

data class ItemizedPrompt(
    val mesId: Int,
    val total: Int,
    val components: List<ItemizedPromptComponent>,
    val presetName: String,
    val modelUsed: String,
    val apiUsed: String,
    val tokenizer: String
) {
    companion object {
        fun fromJson(json: JSONObject): ItemizedPrompt = ItemizedPrompt(
            mesId = json.optInt("mesId", -1),
            total = json.optInt("total", 0),
            components = json.optJSONArray("components")?.let { arr ->
                (0 until arr.length()).mapNotNull { idx ->
                    val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                    val name = obj.optString("name")
                    if (name.isBlank()) return@mapNotNull null
                    ItemizedPromptComponent(name = name, tokens = obj.optInt("tokens", 0))
                }
            } ?: emptyList(),
            presetName = json.optString("presetName", ""),
            modelUsed = json.optString("modelUsed", ""),
            apiUsed = json.optString("apiUsed", ""),
            tokenizer = json.optString("tokenizer", "")
        )
    }
}

data class DataBankAttachment(
    val url: String,
    val name: String,
    val size: Long,
    val created: Long
) {
    companion object {
        fun fromJson(json: JSONObject): DataBankAttachment = DataBankAttachment(
            url = json.optString("url"),
            name = json.optString("name"),
            size = json.optLong("size", 0L),
            created = json.optLong("created", 0L)
        )
    }
}

data class DataBankAttachments(
    val global: List<DataBankAttachment>,
    val character: List<DataBankAttachment>,
    val chat: List<DataBankAttachment>
) {
    companion object {
        private fun parseList(json: JSONObject, key: String): List<DataBankAttachment> =
            json.optJSONArray(key)?.let { arr ->
                (0 until arr.length()).mapNotNull { idx ->
                    arr.optJSONObject(idx)?.let { DataBankAttachment.fromJson(it) }
                }
            } ?: emptyList()

        fun fromJson(json: JSONObject): DataBankAttachments = DataBankAttachments(
            global = parseList(json, "global"),
            character = parseList(json, "character"),
            chat = parseList(json, "chat")
        )
    }
}

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

val ChatMessage.reasoning: String?
    get() = extra.optString("reasoning").takeIf { it.isNotBlank() }

val ChatMessage.bookmarkLink: String?
    get() = extra.optString("bookmark_link").takeIf { it.isNotBlank() }

val ChatMessage.branches: List<String>
    get() = extra.optJSONArray("branches")?.let { arr ->
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    } ?: emptyList()

val ChatMessage.toolInvocations: List<ToolInvocation>
    get() = extra.optJSONArray("tool_invocations").parseObjectsNotNull { item ->
        val params = when (val raw = item.opt("parameters")) {
            null, JSONObject.NULL -> ""
            is JSONObject -> raw.toString(2)
            is JSONArray -> raw.toString(2)
            else -> raw.toString()
        }
        val result = when (val raw = item.opt("result")) {
            null, JSONObject.NULL -> ""
            is JSONObject -> raw.toString(2)
            is JSONArray -> raw.toString(2)
            else -> raw.toString()
        }
        ToolInvocation(
            id = item.optString("id"),
            displayName = item.optString("displayName").ifBlank { item.optString("name") },
            name = item.optString("name"),
            parameters = params,
            result = result
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
