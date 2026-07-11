package io.github.sanitised.st.chat

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.TavernCoreApi
import org.json.JSONArray
import org.json.JSONObject

class DataBankRepository(
    private val clientProvider: () -> TavernCoreApi,
) {
    suspend fun load(avatar: String, chatFile: String): DataBankAttachments {
        val client = clientProvider()
        val settings = client.getSettings()
        val character = client.getCharacter(avatar)
        val chat = client.getChatJsonl(avatar, chatFile)
        return collect(settings, character, chat)
    }

    companion object {
        fun collect(
            settings: Map<String, Any?>,
            character: CharacterDetail,
            chat: List<Any?>,
        ): DataBankAttachments {
            val characterRoot = character.rawJsonData
                .takeIf { it.isNotBlank() }
                ?.let { raw -> runCatching { JSONObject(raw).toMap() }.getOrNull() }
                .orEmpty()
            val chatMetadata = chat.firstOrNull()
                .asMap()
                .mapValue("chat_metadata")
            val extensionSettings = settings.mapValue("extension_settings")
            val disabledUrls = extensionSettings.stringSet("disabled_attachments")
            return DataBankAttachments(
                global = (extensionSettings.attachmentList() + settings.attachments("global"))
                    .filterNot { it.url in disabledUrls },
                character = (characterRoot
                    .mapValue("data")
                    .mapValue("extensions")
                    .attachments("character") +
                    characterRoot.attachments("character") +
                    extensionSettings.characterAttachments(character.id, character.avatarUrl.orEmpty()))
                    .filterNot { it.url in disabledUrls },
                chat = (chatMetadata.attachmentList() + chatMetadata.attachments("chat"))
                    .filterNot { it.url in disabledUrls },
            )
        }

        private fun Map<String, Any?>.attachmentList(): List<DataBankAttachment> =
            listValue("attachments").mapNotNull { row ->
                row.asMap().toAttachment()
            }

        private fun Map<String, Any?>.characterAttachments(vararg avatarKeys: String): List<DataBankAttachment> {
            val byAvatar = mapValue("character_attachments")
            return avatarKeys
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .firstNotNullOfOrNull { avatar ->
                    byAvatar.listValue(avatar)
                        .mapNotNull { row -> row.asMap().toAttachment() }
                        .takeIf { it.isNotEmpty() }
                }
                .orEmpty()
        }

        private fun Map<String, Any?>.attachments(scope: String): List<DataBankAttachment> {
            val bank = mapValue("dataBank").ifEmpty { mapValue("data_bank") }
            return bank.listValue(scope).mapNotNull { row ->
                row.asMap().toAttachment()
            }
        }

        private fun Map<String, Any?>.toAttachment(): DataBankAttachment? {
            val url = stringValue("url").ifBlank { stringValue("path") }
            val name = stringValue("name").ifBlank { url.substringAfterLast('/') }
            if (url.isBlank() || name.isBlank()) return null
            return DataBankAttachment(
                url = url,
                name = name,
                size = longValue("size"),
                created = longValue("created"),
            )
        }

        private fun Any?.asMap(): Map<String, Any?> =
            (this as? Map<*, *>)?.entries?.associate { (key, value) -> key.toString() to value } ?: emptyMap()

        private fun Map<String, Any?>.mapValue(key: String): Map<String, Any?> =
            (this[key] as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()

        private fun Map<String, Any?>.listValue(key: String): List<Any?> =
            when (val value = this[key]) {
                is List<*> -> value.toList()
                is Array<*> -> value.toList()
                else -> emptyList()
            }

        private fun Map<String, Any?>.stringSet(key: String): Set<String> =
            listValue(key).mapNotNull { it?.toString() }.toSet()

        private fun Map<String, Any?>.stringValue(key: String): String =
            this[key]?.toString().orEmpty()

        private fun Map<String, Any?>.longValue(key: String): Long =
            when (val value = this[key]) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull() ?: 0L
                else -> 0L
            }

        private fun JSONObject.toMap(): Map<String, Any?> =
            keys().asSequence().associateWith { key -> get(key).toNativeValue() }

        private fun JSONArray.toListValue(): List<Any?> =
            (0 until length()).map { index -> get(index).toNativeValue() }

        private fun Any?.toNativeValue(): Any? =
            when (this) {
                JSONObject.NULL -> null
                is JSONObject -> toMap()
                is JSONArray -> toListValue()
                else -> this
            }
    }
}
