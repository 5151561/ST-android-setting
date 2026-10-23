package io.github.sanitised.st.api

internal suspend fun TauriTavernJniClient.doFetchSettingsEnvelope(): Pair<Map<String, Any?>, Map<String, Any?>> {
    val response = invokeAsMap("settings_get")
    val settingsField = response["settings"]
    val parsedSettings: Map<String, Any?> = when (settingsField) {
        is String -> (StJson.parse(settingsField) as? Map<*, *>)?.toStringKeyMap() ?: emptyMap()
        is Map<*, *> -> settingsField.toStringKeyMap()
        else -> response
    }
    return response to parsedSettings
}

internal suspend fun TauriTavernJniClient.doGetSettings(): Map<String, Any?> {
    return doFetchSettingsEnvelope().second
}

internal suspend fun TauriTavernJniClient.doSaveSettings(settings: Map<String, Any?>) {
    invokeRaw("settings_save", mapOf("settings" to settings))
}

internal suspend fun TauriTavernJniClient.doGetTagSettings(): STTagSettings {
    val (response, rawSettings) = doFetchSettingsEnvelope()
    val worldNames = response.stringListValue("world_names").ifEmpty {
        runCatching { invokeAsListOfStrings("worldinfo_list") }.getOrDefault(emptyList())
    }
    return STTagSettings(
        tags = rawSettings.listMapValue("tags").map { it.toSTTag() },
        tagMap = rawSettings.anyMapValue("tag_map").mapValues { (_, value) -> value.toStringList() },
        worldNames = worldNames,
        rawSettings = rawSettings
    )
}

internal suspend fun TauriTavernJniClient.doSaveTagSettings(settings: STTagSettings) {
    val merged = settings.rawSettings.ifEmpty { doGetSettings() }.toMutableMap()
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
    doSaveSettings(merged)
}

internal suspend fun TauriTavernJniClient.doListSettingsSnapshots(): List<SettingsSnapshot> {
    val items = invokeAsListOfMaps("settings_snapshots_list")
    return items.mapNotNull { map ->
        val name = map.stringAnyValue("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val dateVal = map.longAnyValue("date").takeIf { it != 0L } ?: map.longAnyValue("modified")
        SettingsSnapshot(
            name = name,
            date = dateVal,
            size = map.longAnyValue("size")
        )
    }
}

internal suspend fun TauriTavernJniClient.doMakeSettingsSnapshot() {
    invokeRaw("settings_snapshot_create")
}

internal suspend fun TauriTavernJniClient.doLoadSettingsSnapshot(name: String): String {
    val data = runCatching {
        invokeRaw("settings_snapshot_restore", mapOf("name" to name))
    }.getOrElse {
        doGetSettings()
    }
    return when (data) {
        is String -> data
        else -> jsonValue(data)
    }
}

internal suspend fun TauriTavernJniClient.doRestoreSettingsSnapshot(name: String) {
    invokeRaw("settings_snapshot_restore", mapOf("name" to name))
}

internal suspend fun TauriTavernJniClient.doListWorldInfos(): List<WorldInfoSummary> {
    val data = invokeRaw("worldinfo_list")
    val rawList = when (data) {
        is List<*> -> data
        is Map<*, *> -> (data["world_names"] as? List<*>)
            ?: data.values.firstOrNull { it is List<*> } as? List<*>
            ?: emptyList<Any?>()
        else -> emptyList<Any?>()
    }
    return rawList.mapNotNull { item ->
        when (item) {
            is String -> item.takeIf { it.isNotBlank() }?.let { WorldInfoSummary(id = it, name = it) }
            is Map<*, *> -> {
                val fileId = item.stringValue("file_id").ifBlank { item.stringValue("name") }
                val name = item.stringValue("name").ifBlank { fileId }
                if (fileId.isBlank()) null
                else WorldInfoSummary(
                    id = fileId,
                    name = name,
                    extensions = item.mapValue("extensions").toStringKeyMap()
                )
            }
            else -> null
        }
    }
}

internal suspend fun TauriTavernJniClient.doGetWorldInfo(name: String): WorldInfoBook {
    val map = invokeAsMap("worldinfo_get", mapOf("name" to name))
    return map.toWorldInfoBook(fallbackName = name).copy(fileId = name)
}

internal suspend fun TauriTavernJniClient.doSaveWorldInfo(book: WorldInfoBook) {
    val targetName = book.fileId.ifBlank { book.name }
    invokeRaw(
        "worldinfo_save",
        mapOf(
            "name" to targetName,
            "data" to book.toApiData()
        )
    )
}

internal suspend fun TauriTavernJniClient.doDeleteWorldInfo(name: String) {
    invokeRaw("worldinfo_delete", mapOf("name" to name))
}

internal suspend fun TauriTavernJniClient.doUploadFile(name: String, base64Data: String): String {
    val bytes = decodeBase64(base64Data)
    val data = invokeBinaryRaw(
        "user_media_upload",
        mapOf("filename" to name, "file_name" to name, "name" to name),
        bytes
    )
    return when (data) {
        is String -> data
        is Map<*, *> -> data.stringValue("path")
            .ifBlank { data.stringValue("url") }
            .ifBlank { data.stringValue("file") }
            .ifBlank { name }
        else -> name
    }
}

internal suspend fun TauriTavernJniClient.doDeleteFile(path: String) {
    invokeRaw("user_media_delete", mapOf("filename" to path, "name" to path, "path" to path))
}

internal suspend fun TauriTavernJniClient.doListBackgrounds(): List<String> {
    return invokeAsListOfStrings("backgrounds_list")
}

internal suspend fun TauriTavernJniClient.doDeleteBackground(bg: String) {
    invokeRaw("background_delete", mapOf("bg" to bg, "filename" to bg, "name" to bg))
}

internal suspend fun TauriTavernJniClient.doRenameBackground(oldBg: String, newBg: String) {
    invokeRaw(
        "background_rename",
        mapOf(
            "oldBg" to oldBg,
            "old_bg" to oldBg,
            "old_name" to oldBg,
            "newBg" to newBg,
            "new_bg" to newBg,
            "new_name" to newBg
        )
    )
}

internal suspend fun TauriTavernJniClient.doListUsers(): List<StUserView> {
    val items = invokeAsListOfMaps("users_list")
    return items.mapNotNull { m ->
        val handle = m.stringAnyValue("handle").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        StUserView(
            handle = handle,
            name = m.stringAnyValue("name").ifBlank { handle },
            avatar = m.stringAnyValue("avatar").takeIf { it.isNotBlank() },
            hasPassword = m.booleanAnyValue("password") || m.booleanAnyValue("hasPassword"),
            created = m.longAnyValue("created")
        )
    }
}

internal suspend fun TauriTavernJniClient.doLoginUser(handle: String, password: String): String {
    val data = runCatching {
        invokeRaw("user_login", mapOf("handle" to handle, "password" to password))
    }.getOrNull()
    return when (data) {
        is String -> data.ifBlank { handle }
        is Map<*, *> -> data.stringValue("handle").ifBlank { handle }
        else -> handle
    }
}

internal suspend fun TauriTavernJniClient.doRecoverPasswordStep1(handle: String) {
    runCatching { invokeRaw("user_recover_step1", mapOf("handle" to handle)) }
}

internal suspend fun TauriTavernJniClient.doRecoverPasswordStep2(
    handle: String,
    code: String,
    newPassword: String
) {
    runCatching {
        invokeRaw(
            "user_recover_step2",
            mapOf(
                "handle" to handle,
                "code" to code,
                "newPassword" to newPassword,
                "new_password" to newPassword
            )
        )
    }
}

internal suspend fun TauriTavernJniClient.doGetCurrentUser(): StCurrentUser {
    val m = runCatching {
        invokeAsListOfMaps("users_list").firstOrNull() ?: emptyMap()
    }.getOrDefault(emptyMap())
    val handle = m.stringAnyValue("handle").ifBlank { "default-user" }
    return StCurrentUser(
        handle = handle,
        name = m.stringAnyValue("name").ifBlank { handle },
        admin = m.booleanAnyValue("admin"),
        created = m.longAnyValue("created"),
        avatar = m.stringAnyValue("avatar").takeIf { it.isNotBlank() }
    )
}

internal suspend fun TauriTavernJniClient.doChangeUserPassword(
    handle: String,
    oldPassword: String,
    newPassword: String
) {
    runCatching {
        invokeRaw(
            "user_change_password",
            mapOf(
                "handle" to handle,
                "oldPassword" to oldPassword,
                "old_password" to oldPassword,
                "newPassword" to newPassword,
                "new_password" to newPassword
            )
        )
    }
}

internal suspend fun TauriTavernJniClient.doLogoutUser() {
    runCatching { invokeRaw("user_logout") }
}

internal suspend fun TauriTavernJniClient.doListPersonas(): List<PersonaProfile> {
    val avatars = runCatching { invokeAsListOfStrings("avatars_list") }.getOrDefault(emptyList())
    val settings = runCatching { doGetSettings() }.getOrDefault(emptyMap())
    val powerUser = settings.anyMapValue("power_user")
    val personas = powerUser.anyMapValue("personas")
    val descriptions = powerUser.anyMapValue("persona_descriptions")
    val defaultPersona = powerUser.stringAnyValue("default_persona")
    val personaIds = (avatars + personas.keys).distinct().sortedBy { personas[it]?.toString() ?: it }
    return personaIds.map { avatar ->
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

internal suspend fun TauriTavernJniClient.doSavePersona(request: PersonaSaveRequest) {
    val merged = doGetSettings().toMutableMap()
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
    doSaveSettings(merged)
}

internal suspend fun TauriTavernJniClient.doUploadPersonaAvatar(
    fileName: String,
    bytes: ByteArray,
    overwriteName: String?
): String {
    val data = invokeBinaryRaw(
        "avatar_upload",
        mapOf(
            "fileName" to fileName,
            "file_name" to fileName,
            "overwriteName" to overwriteName,
            "overwrite_name" to overwriteName
        ),
        bytes
    )
    return when (data) {
        is String -> data.ifBlank { overwriteName.orEmpty() }
        is Map<*, *> -> data.stringValue("path")
            .ifBlank { data.stringValue("avatar") }
            .ifBlank { overwriteName.orEmpty() }
        else -> overwriteName.orEmpty()
    }
}

internal suspend fun TauriTavernJniClient.doDeletePersona(avatar: String) {
    runCatching {
        invokeRaw("avatar_delete", mapOf("avatar" to avatar, "name" to avatar))
    }
    val merged = doGetSettings().toMutableMap()
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
    doSaveSettings(merged)
}

internal suspend fun TauriTavernJniClient.doListSecrets(): List<SecretProviderState> {
    val map = invokeAsMap("secrets_read")
    val keys = (TavernCoreClient.secretProviderLabels.keys + map.keys).distinct()
    return keys.map { key ->
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

internal suspend fun TauriTavernJniClient.doWriteSecret(
    key: String,
    value: String,
    label: String
): String {
    val data = invokeRaw(
        "secrets_write",
        mapOf("key" to key, "value" to value, "label" to label)
    )
    return when (data) {
        is String -> data
        is Map<*, *> -> data.stringValue("id")
        else -> ""
    }
}

internal suspend fun TauriTavernJniClient.doRotateSecret(key: String, id: String) {
    invokeRaw("secrets_rotate", mapOf("key" to key, "id" to id))
}

internal suspend fun TauriTavernJniClient.doRenameSecret(
    key: String,
    id: String,
    label: String
) {
    invokeRaw("secrets_rename", mapOf("key" to key, "id" to id, "label" to label))
}

internal suspend fun TauriTavernJniClient.doDeleteSecret(key: String, id: String?) {
    invokeRaw("secrets_delete", mapOf("key" to key, "id" to id))
}

internal suspend fun TauriTavernJniClient.doGetPresetLibrary(): PresetLibrary {
    val (response, settings) = doFetchSettingsEnvelope()
    val powerUser = settings.anyMapValue("power_user")
    val categories = mutableListOf<PresetCategory>()
    categories += loadPresetCategoryOrFallback(
        apiId = "openai",
        title = "Chat Completion",
        selectedName = settings.stringAnyValue("openai_settings"),
        embeddedCategory = pairedPresetCategory(
            apiId = "openai",
            title = "Chat Completion",
            names = response.stringListValue("openai_setting_names"),
            contents = response.stringListValue("openai_settings"),
            selectedName = settings.stringAnyValue("openai_settings")
        )
    )
    val textGenSettings = settings.anyMapValue("textgenerationwebui_settings")
    categories += loadPresetCategoryOrFallback(
        apiId = "textgenerationwebui",
        title = "Text Completion",
        selectedName = textGenSettings.stringAnyValue("preset"),
        embeddedCategory = pairedPresetCategory(
            apiId = "textgenerationwebui",
            title = "Text Completion",
            names = response.stringListValue("textgenerationwebui_preset_names"),
            contents = response.stringListValue("textgenerationwebui_presets"),
            selectedName = textGenSettings.stringAnyValue("preset")
        )
    )
    categories += loadPresetCategoryOrFallback(
        apiId = "instruct",
        title = "Instruct",
        selectedName = powerUser.anyMapValue("instruct").stringAnyValue("preset"),
        embeddedCategory = objectPresetCategory(
            apiId = "instruct",
            title = "Instruct",
            value = response["instruct"],
            selectedName = powerUser.anyMapValue("instruct").stringAnyValue("preset")
        )
    )
    categories += loadPresetCategoryOrFallback(
        apiId = "context",
        title = "Context",
        selectedName = powerUser.anyMapValue("context").stringAnyValue("preset"),
        embeddedCategory = objectPresetCategory(
            apiId = "context",
            title = "Context",
            value = response["context"],
            selectedName = powerUser.anyMapValue("context").stringAnyValue("preset")
        )
    )
    categories += loadPresetCategoryOrFallback(
        apiId = "sysprompt",
        title = "System Prompt",
        selectedName = powerUser.anyMapValue("sysprompt").stringAnyValue("name"),
        embeddedCategory = objectPresetCategory(
            apiId = "sysprompt",
            title = "System Prompt",
            value = response["sysprompt"],
            selectedName = powerUser.anyMapValue("sysprompt").stringAnyValue("name")
        )
    )
    categories += loadPresetCategoryOrFallback(
        apiId = "reasoning",
        title = "Reasoning",
        selectedName = powerUser.anyMapValue("reasoning").stringAnyValue("name"),
        embeddedCategory = objectPresetCategory(
            apiId = "reasoning",
            title = "Reasoning",
            value = response["reasoning"],
            selectedName = powerUser.anyMapValue("reasoning").stringAnyValue("name")
        )
    )
    return PresetLibrary(categories = categories.filter { it.presets.isNotEmpty() })
}

private suspend fun TauriTavernJniClient.loadPresetCategoryOrFallback(
    apiId: String,
    title: String,
    selectedName: String,
    embeddedCategory: PresetCategory
): PresetCategory {
    if (embeddedCategory.presets.isNotEmpty()) return embeddedCategory
    val list = runCatching {
        invokeAsListOfMaps("presets_list", mapOf("preset_type" to apiId, "api_id" to apiId))
    }.getOrDefault(emptyList())
    if (list.isEmpty()) return embeddedCategory
    val docs = list.mapNotNull { item ->
        val name = item.stringAnyValue("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val content = item["content"] ?: item
        PresetSummary(
            apiId = apiId,
            name = name,
            content = jsonValue(content),
            selected = name == selectedName
        )
    }
    return PresetCategory(apiId = apiId, title = title, presets = docs)
}

internal suspend fun TauriTavernJniClient.doSavePreset(
    apiId: String,
    name: String,
    presetJson: String
) {
    val preset = StJson.parse(presetJson) ?: emptyMap<String, Any?>()
    invokeRaw(
        "preset_save",
        mapOf(
            "preset_type" to apiId,
            "apiId" to apiId,
            "api_id" to apiId,
            "name" to name,
            "preset" to normalizeJsonValue(preset),
            "content" to normalizeJsonValue(preset)
        )
    )
}

internal suspend fun TauriTavernJniClient.doSelectPreset(apiId: String, name: String) {
    val (response, settings) = doFetchSettingsEnvelope()
    val merged = settings.toMutableMap()
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
            val preset = response
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
        else -> return
    }
    doSaveSettings(merged)
}

internal suspend fun TauriTavernJniClient.doDeletePreset(apiId: String, name: String) {
    invokeRaw(
        "preset_delete",
        mapOf("preset_type" to apiId, "apiId" to apiId, "api_id" to apiId, "name" to name)
    )
}

internal suspend fun TauriTavernJniClient.doRestorePreset(apiId: String, name: String): String {
    val data = invokeRaw(
        "preset_restore_default",
        mapOf("preset_type" to apiId, "apiId" to apiId, "api_id" to apiId, "name" to name)
    )
    return when (data) {
        is String -> data
        else -> jsonValue(data)
    }
}

internal suspend fun TauriTavernJniClient.doListConnectionProfiles(): List<ConnectionProfile> {
    val settings = doGetSettings()
    return settings
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

internal suspend fun TauriTavernJniClient.doSaveConnectionProfile(profile: ConnectionProfile) {
    val merged = doGetSettings().toMutableMap()
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
    doSaveSettings(merged)
}

internal suspend fun TauriTavernJniClient.doListChatBackups(): List<ChatBackupSummary> {
    val list = runCatching { invokeAsListOfMaps("chat_backups_list") }.getOrDefault(emptyList())
    return list.mapNotNull { item ->
        val name = item.stringAnyValue("name")
            .ifBlank { item.stringAnyValue("file_name") }
            .ifBlank { item.stringAnyValue("backup_file_name") }
        if (name.isBlank()) null
        else ChatBackupSummary(
            fileName = name,
            fileSize = item.stringAnyValue("file_size").ifBlank { item.longAnyValue("size").toString() },
            messageCount = item.intAnyValue("chat_items", 0),
            lastMessage = item.stringAnyValue("mes"),
            lastMessageAt = item.stringAnyValue("last_mes")
        )
    }
}

internal suspend fun TauriTavernJniClient.doDownloadChatBackup(name: String): CharacterExportFile {
    return CharacterExportFile(
        fileName = name,
        contentType = "application/jsonl",
        bytes = ByteArray(0)
    )
}

internal suspend fun TauriTavernJniClient.doDeleteChatBackup(name: String) {
    runCatching {
        invokeRaw("chat_backup_delete", mapOf("backup_file_name" to name, "name" to name))
    }.onFailure {
        doDeleteFile(name)
    }
}
