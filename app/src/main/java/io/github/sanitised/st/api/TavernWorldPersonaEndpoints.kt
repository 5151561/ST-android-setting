package io.github.sanitised.st.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

internal suspend fun TavernCoreClient.listWorldInfosImpl(): List<WorldInfoSummary> {
    return withContext(Dispatchers.IO) {
        val body = postJson("api/worldinfo/list", "{}")
        val items = StJson.parse(body) as? List<*> ?: emptyList<Any?>()
        items.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            WorldInfoSummary(
                id = map.stringValue("file_id").ifBlank { map.stringValue("name") },
                name = map.stringValue("name").ifBlank { map.stringValue("file_id") },
                extensions = map.mapValue("extensions").toStringKeyMap()
            )
        }
    }
}

internal suspend fun TavernCoreClient.getWorldInfoImpl(name: String): WorldInfoBook {
    return withContext(Dispatchers.IO) {
        val body = postJson(
            path = "api/worldinfo/get",
            json = jsonObject("name" to name)
        )
        val map = (StJson.parse(body) as? Map<*, *>)
            ?.toStringKeyMap()
            ?: emptyMap()
        // 传入的 name 即 file_id（文件名）；记到 fileId 上，保存/删除时按它定位文件。
        map.toWorldInfoBook(fallbackName = name).copy(fileId = name)
    }
}

internal suspend fun TavernCoreClient.saveWorldInfoImpl(book: WorldInfoBook) {
    withContext(Dispatchers.IO) {
        postJson(
            path = "api/worldinfo/edit",
            json = jsonObject(
                "name" to book.fileId.ifBlank { book.name },
                "data" to book.toApiData()
            )
        )
    }
}

internal suspend fun TavernCoreClient.deleteWorldInfoImpl(name: String) {
    withContext(Dispatchers.IO) {
        postJson(
            path = "api/worldinfo/delete",
            json = jsonObject("name" to name)
        )
    }
}

internal suspend fun TavernCoreClient.listPersonasImpl(): List<PersonaProfile> {
    return withContext(Dispatchers.IO) {
        val avatarBody = postJson("api/avatars/get", "{}")
        val avatars = (StJson.parse(avatarBody) as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()
        val settings = fetchSettings()
        val powerUser = settings.settings.anyMapValue("power_user")
        val personas = powerUser.anyMapValue("personas")
        val descriptions = powerUser.anyMapValue("persona_descriptions")
        val defaultPersona = powerUser.stringAnyValue("default_persona")
        val personaIds = (avatars + personas.keys).distinct().sortedBy { personas[it]?.toString() ?: it }
        personaIds.map { avatar ->
            val descriptor = descriptions.anyMapValue(avatar)
            PersonaProfile(
                avatar = avatar,
                name = personas[avatar]?.toString()?.ifBlank { null } ?: avatar.substringBeforeLast('.'),
                title = descriptor.stringAnyValue("title"),
                description = descriptor.stringAnyValue("description"),
                isDefault = avatar == defaultPersona,
                hasAvatar = avatar in avatars
            )
        }
    }
}

internal suspend fun TavernCoreClient.savePersonaImpl(request: PersonaSaveRequest) {
    withContext(Dispatchers.IO) {
        val settings = fetchSettings()
        val merged = settings.settings.toMutableMap()
        val powerUser = merged.anyMapValue("power_user").toMutableMap()
        val personas = powerUser.anyMapValue("personas").toMutableMap()
        val descriptions = powerUser.anyMapValue("persona_descriptions").toMutableMap()
        val existingDescriptor = descriptions.anyMapValue(request.avatar).toMutableMap()

        personas[request.avatar] = request.name
        existingDescriptor["title"] = request.title
        existingDescriptor["description"] = request.description
        descriptions[request.avatar] = existingDescriptor
        powerUser["personas"] = personas
        powerUser["persona_descriptions"] = descriptions
        if (request.makeDefault) {
            powerUser["default_persona"] = request.avatar
        }
        merged["power_user"] = powerUser
        saveSettingsInternal(merged)
    }
}

internal suspend fun TavernCoreClient.uploadPersonaAvatarImpl(
    fileName: String,
    bytes: ByteArray,
    overwriteName: String?
): String {
    return withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("avatar", fileName, bytes.toRequestBody(TavernCoreClient.binaryMediaType))
            .apply {
                overwriteName?.takeIf { it.isNotBlank() }?.let { name ->
                    addFormDataPart("overwrite_name", name)
                }
            }
            .build()
        val responseBody = postMultipart("api/avatars/upload", body)
        val map = StJson.parse(responseBody) as? Map<*, *> ?: emptyMap<Any, Any>()
        map.stringValue("path").ifBlank { overwriteName.orEmpty() }
    }
}

internal suspend fun TavernCoreClient.deletePersonaImpl(avatar: String) {
    withContext(Dispatchers.IO) {
        runCatching {
            postJson(
                path = "api/avatars/delete",
                json = jsonObject("avatar" to avatar)
            )
        }.onFailure { error ->
            if (!error.message.orEmpty().contains("404")) throw error
        }
        val settings = fetchSettings()
        val merged = settings.settings.toMutableMap()
        val powerUser = merged.anyMapValue("power_user").toMutableMap()
        val personas = powerUser.anyMapValue("personas").toMutableMap()
        val descriptions = powerUser.anyMapValue("persona_descriptions").toMutableMap()
        personas.remove(avatar)
        descriptions.remove(avatar)
        if (powerUser.stringAnyValue("default_persona") == avatar) {
            powerUser.remove("default_persona")
        }
        powerUser["personas"] = personas
        powerUser["persona_descriptions"] = descriptions
        merged["power_user"] = powerUser
        saveSettingsInternal(merged)
    }
}

internal fun Map<String, Any?>.toWorldInfoBook(fallbackName: String): WorldInfoBook {
    val entriesMap = anyMapValue("entries")
    val entries = entriesMap.entries.mapNotNull { (key, value) ->
        val entryMap = (value as? Map<*, *>)?.toStringKeyMap()
            ?: return@mapNotNull null
        val uid = entryMap.intAnyValue("uid", key.toIntOrNull() ?: 0)
        WorldInfoEntry(
            uid = uid,
            keys = entryMap["key"].toStringList(),
            secondaryKeys = entryMap["keysecondary"].toStringList(),
            comment = entryMap.stringAnyValue("comment"),
            content = entryMap.stringAnyValue("content"),
            order = entryMap.intAnyValue("order", 0),
            depth = entryMap.intAnyValue("depth", 4),
            position = entryMap.intAnyValue("position", 0),
            constant = entryMap.booleanAnyValue("constant"),
            selective = entryMap.booleanAnyValue("selective"),
            disabled = entryMap.booleanAnyValue("disable") || entryMap.booleanAnyValue("disabled"),
            raw = entryMap
        )
    }.sortedWith(compareBy<WorldInfoEntry> { it.order }.thenBy { it.uid })
    return WorldInfoBook(
        name = stringAnyValue("name").ifBlank { fallbackName },
        entries = entries,
        rawData = this
    )
}

internal fun WorldInfoBook.toApiData(): Map<String, Any?> {
    val data = rawData.toMutableMap()
    data["name"] = name
    data["entries"] = entries.associate { entry ->
        entry.uid.toString() to entry.toApiData()
    }
    return data
}

internal fun WorldInfoEntry.toApiData(): Map<String, Any?> {
    val data = raw.toMutableMap()
    data["uid"] = uid
    data["key"] = keys
    data["keysecondary"] = secondaryKeys
    data["comment"] = comment
    data["content"] = content
    data["order"] = order
    data["depth"] = depth
    data["position"] = position
    data["constant"] = constant
    data["selective"] = selective
    data["disable"] = disabled
    data.remove("disabled")
    return data
}
