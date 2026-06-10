package io.github.sanitised.st.chat

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.TavernCoreApi
import org.json.JSONArray
import org.json.JSONObject

class NativeChatLoader(
    private val store: ChatStore,
    private val clientProvider: () -> TavernCoreApi,
) {
    suspend fun openCharacter(avatar: String, chatFile: String?): Boolean {
        val client = clientProvider()
        val character = client.getCharacter(avatar)
        val selectedChatFile = chatFile
            ?.takeIf { it.isNotBlank() }
            ?: character.chat.takeIf { it.isNotBlank() }
            ?: return false
        val rawChat = client.getChatJsonl(avatar, selectedChatFile)
        if (rawChat.isEmpty()) return false
        store.applySnapshot(
            buildNativeCharacterChatSnapshot(
                avatar = avatar,
                character = character,
                chatFile = selectedChatFile,
                rawChat = rawChat,
            ),
            markRuntimeReady = false,
        )
        return true
    }
}

internal fun buildNativeCharacterChatSnapshot(
    avatar: String,
    character: CharacterDetail,
    chatFile: String,
    rawChat: List<Any?>,
): ChatSnapshot {
    val header = rawChat.firstOrNull().asMap()?.takeIf { it.isChatHeader() }
    val messageRows = if (header != null) rawChat.drop(1) else rawChat
    val metadata = header
        ?.get("chat_metadata")
        .asMap()
        .toSnapshotMetadata()
    return ChatSnapshot(
        mode = "character",
        avatarUrl = avatar,
        characterName = character.name.ifBlank { header?.get("character_name").asString() },
        chatFile = chatFile,
        isGenerating = false,
        messages = messageRows.mapIndexedNotNull { index, row ->
            row.asMap()?.toChatMessage(index)
        },
        metadata = metadata,
    )
}

private fun Map<*, *>.isChatHeader(): Boolean =
    containsKey("chat_metadata") ||
        containsKey("user_name") ||
        containsKey("character_name") ||
        containsKey("create_date")

private fun Map<*, *>?.toSnapshotMetadata(): JSONObject {
    val metadata = this ?: emptyMap<Any?, Any?>()
    return JSONObject()
        .put("integrity", metadata["integrity"].asString())
        .put(
            "authorsNote",
            // 上游 ST 字段 note_prompt 优先；authors_note 为旧 adapter 自造字段，保留兼容读取。
            metadata["note_prompt"].asString(
                metadata["authors_note"].asString(metadata["authorsNote"].asString())
            )
        )
        .put("cfgScale", metadata["cfg_guidance_scale"].asDouble(metadata["cfgScale"].asDouble(1.0)))
        .put("cfgNegativePrompt", metadata["cfg_negative_prompt"].asString(metadata["cfgNegativePrompt"].asString()))
        .put("cfgPositivePrompt", metadata["cfg_positive_prompt"].asString(metadata["cfgPositivePrompt"].asString()))
        .put("worldInfo", metadata["world_info"].asString(metadata["worldInfo"].asString()))
}

private fun Map<*, *>.toChatMessage(id: Int): ChatMessage =
    ChatMessage(
        id = id,
        name = this["name"].asString(),
        mes = this["mes"].asString(),
        isUser = this["is_user"].asBoolean(),
        isSystem = this["is_system"].asBoolean(),
        sendDate = this["send_date"].asString(),
        swipeId = this["swipe_id"].asInt(),
        swipes = this["swipes"].asStringList(),
        extra = this["extra"].asMap().toJsonObject(),
    )

private fun Any?.asMap(): Map<*, *>? = this as? Map<*, *>

private fun Any?.asString(default: String = ""): String =
    when (this) {
        null -> default
        is String -> this
        else -> toString()
    }

private fun Any?.asBoolean(default: Boolean = false): Boolean =
    when (this) {
        is Boolean -> this
        is Number -> toInt() != 0
        is String -> equals("true", ignoreCase = true)
        else -> default
    }

private fun Any?.asInt(default: Int = 0): Int =
    when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: default
        else -> default
    }

private fun Any?.asDouble(default: Double = 0.0): Double =
    when (this) {
        is Number -> toDouble()
        is String -> toDoubleOrNull() ?: default
        else -> default
    }

private fun Any?.asStringList(): List<String> =
    when (this) {
        is Iterable<*> -> mapNotNull { it?.toString() }
        is Array<*> -> mapNotNull { it?.toString() }
        else -> emptyList()
    }

private fun Map<*, *>?.toJsonObject(): JSONObject {
    val obj = JSONObject()
    this?.forEach { (key, value) ->
        if (key != null) obj.put(key.toString(), value.toJsonValue())
    }
    return obj
}

private fun Any?.toJsonValue(): Any =
    when (this) {
        null -> JSONObject.NULL
        is Map<*, *> -> toJsonObject()
        is Iterable<*> -> JSONArray().also { arr -> forEach { arr.put(it.toJsonValue()) } }
        is Array<*> -> JSONArray().also { arr -> forEach { arr.put(it.toJsonValue()) } }
        else -> this
    }
