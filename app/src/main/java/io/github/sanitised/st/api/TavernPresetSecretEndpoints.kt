package io.github.sanitised.st.api

import io.github.sanitised.st.chat.prompt.GenerationDeltaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal suspend fun TavernCoreClient.getTagSettingsImpl(): STTagSettings {
    return withContext(Dispatchers.IO) {
        val body = postJson("api/settings/get", "{}")
        val map = StJson.parse(body) as? Map<*, *> ?: emptyMap<Any, Any>()
        val rawSettings = (StJson.parse(map.stringValue("settings")) as? Map<*, *>)
            ?.toStringKeyMap()
            ?: emptyMap()
        STTagSettings(
            tags = rawSettings.listMapValue("tags").map { it.toSTTag() },
            tagMap = rawSettings.anyMapValue("tag_map").mapValues { (_, value) -> value.toStringList() },
            worldNames = map.stringListValue("world_names"),
            rawSettings = rawSettings
        )
    }
}

internal suspend fun TavernCoreClient.saveTagSettingsImpl(settings: STTagSettings) {
    withContext(Dispatchers.IO) {
        val merged = settings.rawSettings.toMutableMap()
        merged["tags"] = settings.tags.map { tag ->
            linkedMapOf(
                "id" to tag.id,
                "name" to tag.name,
                "color" to tag.color,
                "folder_type" to if (tag.isFolder) "character" else "",
                "sort_order" to tag.sortOrder
            )
        }
        merged["tag_map"] = settings.tagMap
        postJson(
            path = "api/settings/save",
            json = jsonValue(merged)
        )
    }
}

internal suspend fun TavernCoreClient.listSettingsSnapshotsImpl(): List<SettingsSnapshot> {
    return withContext(Dispatchers.IO) {
        val body = postJson("api/settings/get-snapshots", "{}")
        val items = StJson.parse(body) as? List<*> ?: emptyList<Any?>()
        items.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            SettingsSnapshot(
                name = map.stringValue("name"),
                date = map.longValue("date"),
                size = map.longValue("size")
            )
        }
    }
}

internal suspend fun TavernCoreClient.makeSettingsSnapshotImpl() {
    withContext(Dispatchers.IO) {
        postJson("api/settings/make-snapshot", "{}")
    }
}

internal suspend fun TavernCoreClient.loadSettingsSnapshotImpl(name: String): String {
    return withContext(Dispatchers.IO) {
        postJson(
            path = "api/settings/load-snapshot",
            json = jsonObject("name" to name)
        )
    }
}

internal suspend fun TavernCoreClient.restoreSettingsSnapshotImpl(name: String) {
    withContext(Dispatchers.IO) {
        postJson(
            path = "api/settings/restore-snapshot",
            json = jsonObject("name" to name)
        )
    }
}

internal suspend fun TavernCoreClient.uploadFileImpl(name: String, base64Data: String): String {
    return withContext(Dispatchers.IO) {
        val body = postJson(
            path = "api/files/upload",
            json = jsonObject("name" to name, "data" to base64Data)
        )
        val map = (StJson.parse(body) as? Map<*, *>) ?: emptyMap<Any?, Any?>()
        map.stringValue("path")
            .ifBlank { map.stringValue("url") }
            .ifBlank { map.stringValue("file") }
    }
}

internal suspend fun TavernCoreClient.deleteFileImpl(path: String) {
    withContext(Dispatchers.IO) {
        postJson(
            path = "api/files/delete",
            json = jsonObject("path" to path)
        )
    }
}

internal suspend fun TavernCoreClient.listBackgroundsImpl(): List<String> {
    return withContext(Dispatchers.IO) {
        val body = postJson("api/backgrounds/all", "{}")
        val loaded = StJson.parse(body)
        val images = when (loaded) {
            is Map<*, *> -> loaded["images"] as? List<*>
            is List<*> -> loaded
            else -> null
        } ?: emptyList<Any?>()
        images.mapNotNull { item ->
            when (item) {
                is String -> item.takeIf { it.isNotBlank() }
                is Map<*, *> -> item.stringValue("filename").takeIf { it.isNotBlank() }
                else -> null
            }
        }
    }
}

internal suspend fun TavernCoreClient.deleteBackgroundImpl(bg: String) {
    withContext(Dispatchers.IO) {
        postJson(path = "api/backgrounds/delete", json = jsonObject("bg" to bg))
    }
}

internal suspend fun TavernCoreClient.renameBackgroundImpl(oldBg: String, newBg: String) {
    withContext(Dispatchers.IO) {
        postJson(path = "api/backgrounds/rename", json = jsonObject("old_bg" to oldBg, "new_bg" to newBg))
    }
}

internal suspend fun TavernCoreClient.listUsersImpl(): List<StUserView> {
    return withContext(Dispatchers.IO) {
        val body = postJson("api/users/list", "{}")
        val items = StJson.parse(body) as? List<*> ?: emptyList<Any?>()
        items.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            StUserView(
                handle = m.stringValue("handle"),
                name = m.stringValue("name").ifBlank { m.stringValue("handle") },
                avatar = m.stringValue("avatar").takeIf { it.isNotBlank() },
                hasPassword = (m["password"] as? Boolean) ?: false,
                created = (m["created"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

internal suspend fun TavernCoreClient.loginUserImpl(handle: String, password: String): String {
    return withContext(Dispatchers.IO) {
        val body = postJson("api/users/login", jsonObject("handle" to handle, "password" to password))
        (StJson.parse(body) as? Map<*, *>)?.stringValue("handle")?.ifBlank { handle } ?: handle
    }
}

internal suspend fun TavernCoreClient.recoverPasswordStep1Impl(handle: String) {
    withContext(Dispatchers.IO) {
        postJson("api/users/recover-step1", jsonObject("handle" to handle))
    }
}

internal suspend fun TavernCoreClient.recoverPasswordStep2Impl(
    handle: String,
    code: String,
    newPassword: String
) {
    withContext(Dispatchers.IO) {
        postJson(
            "api/users/recover-step2",
            jsonObject("handle" to handle, "code" to code, "newPassword" to newPassword)
        )
    }
}

internal suspend fun TavernCoreClient.getCurrentUserImpl(): StCurrentUser {
    return withContext(Dispatchers.IO) {
        val body = getJson("api/users/me")
        val m = (StJson.parse(body) as? Map<*, *>) ?: emptyMap<Any?, Any?>()
        StCurrentUser(
            handle = m.stringValue("handle"),
            name = m.stringValue("name").ifBlank { m.stringValue("handle") },
            admin = (m["admin"] as? Boolean) ?: false,
            created = (m["created"] as? Number)?.toLong() ?: 0L,
            avatar = m.stringValue("avatar").takeIf { it.isNotBlank() }
        )
    }
}

internal suspend fun TavernCoreClient.changeUserPasswordImpl(
    handle: String,
    oldPassword: String,
    newPassword: String
) {
    withContext(Dispatchers.IO) {
        postJson(
            "api/users/change-password",
            jsonObject("handle" to handle, "oldPassword" to oldPassword, "newPassword" to newPassword)
        )
    }
}

internal suspend fun TavernCoreClient.logoutUserImpl() {
    withContext(Dispatchers.IO) {
        runCatching { postJson("api/users/logout", "{}") }
        // 无论后端是否成功，都清掉本地会话 cookie 与缓存的 CSRF，避免沿用旧账户身份。
        TavernCoreClient.clearSessionCookies()
        cachedCsrfToken = null
    }
}

internal suspend fun TavernCoreClient.listSecretsImpl(): List<SecretProviderState> {
    return withContext(Dispatchers.IO) {
        val body = postJson("api/secrets/read", "{}")
        val map = StJson.parse(body) as? Map<*, *> ?: emptyMap<Any, Any>()
        val keys = (TavernCoreClient.secretProviderLabels.keys + map.keys.map { it.toString() }).distinct()
        keys.map { key ->
            val entries = (map[key] as? List<*>)
                ?.mapNotNull { item ->
                    val itemMap = item as? Map<*, *> ?: return@mapNotNull null
                    SecretEntry(
                        id = itemMap.stringValue("id"),
                        value = itemMap.stringValue("value"),
                        label = itemMap.stringValue("label"),
                        active = itemMap.booleanValue("active")
                    )
                }
                ?: emptyList()
            SecretProviderState(
                key = key,
                label = TavernCoreClient.secretProviderLabels[key] ?: key,
                entries = entries
            )
        }.sortedBy { it.label }
    }
}

internal suspend fun TavernCoreClient.writeSecretImpl(
    key: String,
    value: String,
    label: String
): String {
    return withContext(Dispatchers.IO) {
        val body = postJson(
            path = "api/secrets/write",
            json = jsonObject(
                "key" to key,
                "value" to value,
                "label" to label
            )
        )
        val map = StJson.parse(body) as? Map<*, *> ?: emptyMap<Any, Any>()
        map.stringValue("id")
    }
}

internal suspend fun TavernCoreClient.rotateSecretImpl(key: String, id: String) {
    withContext(Dispatchers.IO) {
        postJson(
            path = "api/secrets/rotate",
            json = jsonObject("key" to key, "id" to id)
        )
    }
}

internal suspend fun TavernCoreClient.renameSecretImpl(key: String, id: String, label: String) {
    withContext(Dispatchers.IO) {
        postJson(
            path = "api/secrets/rename",
            json = jsonObject("key" to key, "id" to id, "label" to label)
        )
    }
}

internal suspend fun TavernCoreClient.deleteSecretImpl(key: String, id: String?) {
    withContext(Dispatchers.IO) {
        postJson(
            path = "api/secrets/delete",
            json = jsonObject("key" to key, "id" to id)
        )
    }
}

internal suspend fun TavernCoreClient.getSettingsImpl(): Map<String, Any?> {
    return withContext(Dispatchers.IO) {
        fetchSettings().settings
    }
}

internal suspend fun TavernCoreClient.saveSettingsImpl(settings: Map<String, Any?>) {
    withContext(Dispatchers.IO) {
        saveSettingsInternal(settings)
    }
}

internal suspend fun TavernCoreClient.fetchModelsImpl(
    mode: String,
    sourceValue: String,
    apiServer: String
): List<String> {
    return withContext(Dispatchers.IO) {
        runCatching {
            val body = postStatusCheck(mode, sourceValue, apiServer)
            parseModelList(body)
        }.getOrElse { emptyList() }
    }
}

internal suspend fun TavernCoreClient.testConnectionImpl(
    mode: String,
    sourceValue: String,
    apiServer: String
): ConnectionTestResult {
    return withContext(Dispatchers.IO) {
        try {
            val body = postStatusCheck(mode, sourceValue, apiServer)
            val parsed = StJson.parse(body)
            val hasError = parsed is Map<*, *> && parsed["error"] == true
            if (hasError) {
                val msg = parsed["message"]?.toString()
                return@withContext ConnectionTestResult(
                    success = false,
                    errorMessage = msg ?: "API 返回错误，请检查密钥"
                )
            }
            val models = parseModelList(body)
            ConnectionTestResult(success = true, models = models)
        } catch (e: Exception) {
            val msg = e.message?.let { raw ->
                when {
                    "400" in raw -> "请求参数错误，请检查密钥是否已保存"
                    "401" in raw || "403" in raw -> "密钥无效或权限不足"
                    "timeout" in raw.lowercase() -> "连接超时，请检查网络"
                    "connect" in raw.lowercase() -> "无法连接到服务端点"
                    else -> raw.take(120)
                }
            } ?: "连接失败"
            ConnectionTestResult(success = false, errorMessage = msg)
        }
    }
}

internal fun TavernCoreClient.postStatusCheck(
    mode: String,
    sourceValue: String,
    apiServer: String
): String {
    return when (mode) {
        "cc" -> postJson(
            "api/backends/chat-completions/status",
            jsonObject(
                "chat_completion_source" to sourceValue,
                "reverse_proxy" to apiServer.ifBlank { null },
                "proxy_password" to if (apiServer.isNotBlank()) "" else null
            )
        )
        "tc" -> {
            val server = apiServer.ifBlank {
                resolveTextGenServer(fetchSettings().settings, sourceValue)
            }
            postJson(
                "api/backends/text-completions/status",
                jsonObject(
                    "api_server" to server,
                    "api_type" to sourceValue
                )
            )
        }
        else -> postJson(
            "api/backends/chat-completions/status",
            jsonObject("chat_completion_source" to sourceValue)
        )
    }
}

internal fun parseModelList(responseBody: String): List<String> {
    val parsed = StJson.parse(responseBody)
    val list = when (parsed) {
        is List<*> -> parsed
        is Map<*, *> -> parsed["data"] as? List<*> ?: emptyList<Any?>()
        else -> emptyList<Any?>()
    }
    return list.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        map.stringValue("id").takeIf { it.isNotBlank() }
            ?: map.stringValue("name").takeIf { it.isNotBlank() }
    }.distinct().sorted()
}

internal suspend fun TavernCoreClient.getPresetLibraryImpl(): PresetLibrary {
    return withContext(Dispatchers.IO) {
        val settings = fetchSettings()
        val powerUser = settings.settings.anyMapValue("power_user")
        val categories = mutableListOf<PresetCategory>()
        categories += pairedPresetCategory(
            apiId = "openai",
            title = "Chat Completion",
            names = settings.response.stringListValue("openai_setting_names"),
            contents = settings.response.stringListValue("openai_settings"),
            selectedName = settings.settings.stringAnyValue("openai_settings")
        )
        val textGenSettings = settings.settings.anyMapValue("textgenerationwebui_settings")
        categories += pairedPresetCategory(
            apiId = "textgenerationwebui",
            title = "Text Completion",
            names = settings.response.stringListValue("textgenerationwebui_preset_names"),
            contents = settings.response.stringListValue("textgenerationwebui_presets"),
            selectedName = textGenSettings.stringAnyValue("preset")
        )
        categories += objectPresetCategory(
            apiId = "instruct",
            title = "Instruct",
            value = settings.response["instruct"],
            selectedName = powerUser.anyMapValue("instruct").stringAnyValue("preset")
        )
        categories += objectPresetCategory(
            apiId = "context",
            title = "Context",
            value = settings.response["context"],
            selectedName = powerUser.anyMapValue("context").stringAnyValue("preset")
        )
        categories += objectPresetCategory(
            apiId = "sysprompt",
            title = "System Prompt",
            value = settings.response["sysprompt"],
            selectedName = powerUser.anyMapValue("sysprompt").stringAnyValue("name")
        )
        categories += objectPresetCategory(
            apiId = "reasoning",
            title = "Reasoning",
            value = settings.response["reasoning"],
            selectedName = powerUser.anyMapValue("reasoning").stringAnyValue("name")
        )
        PresetLibrary(categories = categories.filter { it.presets.isNotEmpty() })
    }
}

internal suspend fun TavernCoreClient.savePresetImpl(
    apiId: String,
    name: String,
    presetJson: String
) {
    withContext(Dispatchers.IO) {
        val preset = StJson.parse(presetJson) ?: emptyMap<String, Any?>()
        postJson(
            path = "api/presets/save",
            json = jsonObject(
                "apiId" to apiId,
                "name" to name,
                "preset" to normalizeJsonValue(preset)
            )
        )
    }
}

internal suspend fun TavernCoreClient.selectPresetImpl(apiId: String, name: String) {
    withContext(Dispatchers.IO) {
        val settings = fetchSettings()
        val merged = settings.settings.toMutableMap()
        when (apiId) {
            "openai" -> merged["openai_settings"] = name
            "textgenerationwebui" -> {
                val textGenSettings = merged.anyMapValue("textgenerationwebui_settings").toMutableMap()
                textGenSettings["preset"] = name
                merged["textgenerationwebui_settings"] = textGenSettings
            }
            "instruct", "context", "sysprompt", "reasoning" -> {
                val powerUser = merged.anyMapValue("power_user").toMutableMap()
                val key = if (apiId == "sysprompt" || apiId == "reasoning") "name" else "preset"
                val preset = settings.response
                    .listMapValue(apiId)
                    .firstOrNull { it.stringAnyValue("name") == name }
                val current = powerUser.anyMapValue(apiId).toMutableMap()
                preset?.forEach { (presetKey, presetValue) ->
                    current[presetKey] = presetValue
                }
                current[key] = name
                if (apiId == "instruct" || apiId == "sysprompt") {
                    current["enabled"] = true
                }
                powerUser[apiId] = current
                merged["power_user"] = powerUser
            }
            else -> return@withContext
        }
        saveSettingsInternal(merged)
    }
}

internal suspend fun TavernCoreClient.deletePresetImpl(apiId: String, name: String) {
    withContext(Dispatchers.IO) {
        postJson(
            path = "api/presets/delete",
            json = jsonObject("apiId" to apiId, "name" to name)
        )
    }
}

internal suspend fun TavernCoreClient.restorePresetImpl(apiId: String, name: String): String {
    return withContext(Dispatchers.IO) {
        postJson(
            path = "api/presets/restore",
            json = jsonObject("apiId" to apiId, "name" to name)
        )
    }
}

internal fun pairedPresetCategory(
    apiId: String,
    title: String,
    names: List<String>,
    contents: List<String>,
    selectedName: String
): PresetCategory {
    val presets = names.mapIndexed { index, name ->
        PresetSummary(
            apiId = apiId,
            name = name,
            content = contents.getOrNull(index).orEmpty(),
            selected = selectedName == name
        )
    }
    return PresetCategory(apiId = apiId, title = title, presets = presets)
}

internal fun objectPresetCategory(
    apiId: String,
    title: String,
    value: Any?,
    selectedName: String = ""
): PresetCategory {
    val presets = (value as? List<*>)
        ?.mapIndexedNotNull { index, item ->
            val map = (item as? Map<*, *>)?.toStringKeyMap() ?: return@mapIndexedNotNull null
            val name = map.stringAnyValue("name").ifBlank { "$title ${index + 1}" }
            PresetSummary(
                apiId = apiId,
                name = name,
                content = jsonValue(map),
                selected = selectedName == name
            )
        }
        ?: emptyList()
    return PresetCategory(apiId = apiId, title = title, presets = presets)
}

internal suspend fun TavernCoreClient.listConnectionProfilesImpl(): List<ConnectionProfile> {
    return withContext(Dispatchers.IO) {
        val settings = fetchSettings()
        settings.settings
            .anyMapValue("power_user")
            .listMapValue("servers")
            .map {
                ConnectionProfile(
                    label = it.stringAnyValue("label"),
                    url = it.stringAnyValue("url"),
                    lastConnection = it.longAnyValue("lastConnection")
                )
            }
            .filter { it.url.isNotBlank() }
            .sortedByDescending { it.lastConnection }
    }
}

internal suspend fun TavernCoreClient.saveConnectionProfileImpl(profile: ConnectionProfile) {
    withContext(Dispatchers.IO) {
        val settings = fetchSettings()
        val merged = settings.settings.toMutableMap()
        val powerUser = merged.anyMapValue("power_user").toMutableMap()
        val servers = powerUser.listMapValue("servers").toMutableList()
        val existingIndex = servers.indexOfFirst { item ->
            item.stringAnyValue("label") == profile.label && item.stringAnyValue("url") == profile.url
        }
        val saved = linkedMapOf(
            "label" to profile.label,
            "url" to profile.url,
            "lastConnection" to (profile.lastConnection.takeIf { it > 0 } ?: System.currentTimeMillis())
        )
        if (existingIndex >= 0) {
            servers[existingIndex] = saved
        } else {
            servers.add(saved)
        }
        powerUser["servers"] = servers
        merged["power_user"] = powerUser
        saveSettingsInternal(merged)
    }
}

internal suspend fun TavernCoreClient.listChatBackupsImpl(): List<ChatBackupSummary> {
    return withContext(Dispatchers.IO) {
        val body = postJson("api/backups/chat/get", "{}")
        val items = StJson.parse(body) as? List<*> ?: emptyList<Any?>()
        items.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            ChatBackupSummary(
                fileName = map.stringValue("file_name"),
                fileSize = map.stringValue("file_size"),
                messageCount = map.intValue("chat_items", 0),
                lastMessage = map.stringValue("mes"),
                lastMessageAt = when (val value = map["last_mes"]) {
                    is String -> value
                    is Number -> value.toLong().toString()
                    else -> ""
                }
            )
        }
    }
}

internal suspend fun TavernCoreClient.downloadChatBackupImpl(name: String): CharacterExportFile {
    return withContext(Dispatchers.IO) {
        postJsonFile(
            path = "api/backups/chat/download",
            json = jsonObject("name" to name),
            fallbackFileName = name
        )
    }
}

internal suspend fun TavernCoreClient.deleteChatBackupImpl(name: String) {
    withContext(Dispatchers.IO) {
        postJson(
            path = "api/backups/chat/delete",
            json = jsonObject("name" to name)
        )
    }
}

internal suspend fun TavernCoreClient.listGroupsImpl(): List<GroupSummary> {
    return withContext(Dispatchers.IO) {
        val body = postJson("api/groups/all", "{}")
        val items = StJson.parse(body) as? List<*> ?: emptyList<Any?>()
        items.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            map.toGroupSummary()
        }
    }
}

internal suspend fun TavernCoreClient.createGroupImpl(request: GroupCreateRequest): GroupSummary {
    return withContext(Dispatchers.IO) {
        val payload = linkedMapOf<String, Any?>(
            "name" to request.name,
            "members" to request.members,
            "avatar_url" to request.avatarUrl,
            "allow_self_responses" to request.allowSelfResponses,
            "activation_strategy" to request.activationStrategy,
            "generation_mode" to request.generationMode,
            "disabled_members" to request.disabledMembers,
            "fav" to request.isFavorite,
            "auto_mode_delay" to request.autoModeDelay
        )
        request.chatId.takeIf { it.isNotBlank() }?.let { chatId ->
            payload["chat_id"] = chatId
            payload["chats"] = listOf(chatId)
        }
        val body = postJson(
            path = "api/groups/create",
            json = jsonValue(payload)
        )
        val map = StJson.parse(body) as? Map<*, *>
            ?: throw IllegalStateException("Invalid group response")
        map.toGroupSummary()
    }
}

internal suspend fun TavernCoreClient.getGroupChatJsonlImpl(chatId: String): MutableList<Any?> =
    withContext(Dispatchers.IO) {
        val body = postJson(
            "api/chats/group/get",
            jsonObject("id" to chatId.removeSuffix(".jsonl"))
        )
        // Found chats return a JSON array [header, ...messages]; a missing file returns {} or [].
        when (val parsed = StJson.parse(body)) {
            is List<*> -> parsed.map { normalizeJsonValue(it) }.toMutableList()
            else -> mutableListOf()
        }
    }

internal suspend fun TavernCoreClient.saveGroupChatJsonlImpl(chatId: String, chat: List<Any?>) {
    withContext(Dispatchers.IO) {
        postJson(
            "api/chats/group/save",
            jsonObject(
                "id" to chatId.removeSuffix(".jsonl"),
                "chat" to chat,
                "force" to false
            )
        )
    }
}

internal suspend fun TavernCoreClient.generateChatCompletionImpl(payload: Map<String, Any?>): String =
    withContext(Dispatchers.IO) {
        val body = postJsonForGeneration("api/backends/chat-completions/generate", jsonValue(payload))
        val map = StJson.parse(body) as? Map<*, *>
            ?: throw IllegalStateException("生成响应无法解析")
        map["error"]?.takeIf { it != false }?.let {
            val message = (it as? Map<*, *>)?.get("message")?.toString()
                ?: map["message"]?.toString()
                ?: "生成失败"
            throw IllegalStateException(message)
        }
        // The backend normalizes all sources to choices[0].message.content for non-stream.
        val choices = map["choices"] as? List<*>
        val first = choices?.firstOrNull() as? Map<*, *>
        val message = first?.get("message") as? Map<*, *>
        (message?.get("content") as? String)
            ?: (first?.get("text") as? String)
            ?: throw IllegalStateException("生成响应为空")
    }

internal fun TavernCoreClient.generateChatCompletionStreamImpl(payload: Map<String, Any?>): Flow<String> =
    callbackFlow {
        val streamPayload = payload.toMutableMap().apply { put("stream", true) }
        val builder = Request.Builder()
            .url(normalizedBaseUrl + "api/backends/chat-completions/generate")
            .header("Accept", "text/event-stream")
            .post(jsonValue(streamPayload).toRequestBody(TavernCoreClient.jsonMediaType))
        csrfToken().takeIf { it.isNotBlank() }?.let { builder.header("x-csrf-token", it) }
        val call = generationHttpClient.newCall(builder.build())

        val worker = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val err = response.body.string().orEmpty()
                        throw IllegalStateException("SillyTavern API ${response.code}: $err")
                    }
                    val source = response.body.source()
                    while (isActive && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isEmpty() || data == "[DONE]") continue
                        GenerationDeltaParser.extract(data)?.takeIf { it.isNotEmpty() }?.let { trySend(it) }
                    }
                }
                close()
            } catch (e: Throwable) {
                close(e)
            }
        }
        awaitClose {
            call.cancel()
            worker.cancel()
        }
    }

internal suspend fun TavernCoreClient.generateTextCompletionImpl(payload: Map<String, Any?>): String =
    withContext(Dispatchers.IO) {
        val body = postJsonForGeneration("api/backends/text-completions/generate", jsonValue(payload))
        extractTextCompletionResponse(body)
    }

internal fun extractTextCompletionResponse(body: String): String {
    val map = StJson.parse(body) as? Map<*, *>
        ?: throw IllegalStateException("生成响应无法解析")
    map["error"]?.takeIf { it != false }?.let { error ->
        val message = (error as? Map<*, *>)?.get("message")?.toString()
            ?: map["message"]?.toString()
            ?: "生成失败"
        throw IllegalStateException(message)
    }
    val choices = map["choices"] as? List<*>
    val first = choices?.firstOrNull() as? Map<*, *>
    val message = first?.get("message") as? Map<*, *>
    return (first?.get("text") as? String)
        ?: (message?.get("content") as? String)
        ?: (map["content"] as? String)
        ?: (map["response"] as? String)
        ?: throw IllegalStateException("生成响应为空")
}

internal fun TavernCoreClient.generateTextCompletionStreamImpl(payload: Map<String, Any?>): Flow<String> =
    callbackFlow {
        val streamPayload = payload.toMutableMap().apply { put("stream", true) }
        val builder = Request.Builder()
            .url(normalizedBaseUrl + "api/backends/text-completions/generate")
            .header("Accept", "text/event-stream")
            .post(jsonValue(streamPayload).toRequestBody(TavernCoreClient.jsonMediaType))
        csrfToken().takeIf { it.isNotBlank() }?.let { builder.header("x-csrf-token", it) }
        val call = generationHttpClient.newCall(builder.build())

        val worker = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val err = response.body.string().orEmpty()
                        throw IllegalStateException("SillyTavern API ${response.code}: $err")
                    }
                    val source = response.body.source()
                    while (isActive && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isEmpty() || data == "[DONE]") continue
                        GenerationDeltaParser.extract(data)?.takeIf { it.isNotEmpty() }?.let { trySend(it) }
                    }
                }
                close()
            } catch (e: Throwable) {
                close(e)
            }
        }
        awaitClose {
            call.cancel()
            worker.cancel()
        }
    }

internal fun Map<*, *>.toGroupSummary(): GroupSummary {
    val id = stringValue("id")
    val chatId = stringValue("chat_id")
    return GroupSummary(
        id = id,
        name = stringValue("name").ifBlank { id.ifBlank { "未命名群聊" } },
        members = stringListValue("members"),
        chatId = chatId,
        chats = stringListValue("chats"),
        lastUpdated = longValue("date_last_chat"),
        chatSize = longValue("chat_size"),
        avatarUrl = stringValue("avatar_url"),
        allowSelfResponses = booleanValue("allow_self_responses"),
        activationStrategy = intValue("activation_strategy", 0),
        generationMode = intValue("generation_mode", 0),
        isFavorite = booleanValue("fav"),
        disabledMembers = stringListValue("disabled_members"),
        autoModeDelay = intValue("auto_mode_delay", 5),
        generationModeJoinPrefix = stringValue("generation_mode_join_prefix"),
        generationModeJoinSuffix = stringValue("generation_mode_join_suffix")
    )
}
