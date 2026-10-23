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
    val raw = runCatching {
        invokeRaw("presets_list", mapOf("preset_type" to apiId, "api_id" to apiId))
    }.getOrNull() ?: return embeddedCategory

    val docs: List<PresetSummary> = when (raw) {
        is Map<*, *> -> {
            val presetsObj = (raw["presets"] as? Map<*, *>) ?: raw
            val orderedNames = (raw["preset_names"] as? List<*>)
                ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
                ?: presetsObj.keys.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.sorted()
            orderedNames.mapNotNull { name ->
                val content = presetsObj[name] ?: return@mapNotNull null
                PresetSummary(
                    apiId = apiId,
                    name = name,
                    content = jsonValue(content),
                    selected = name == selectedName
                )
            }
        }
        is List<*> -> {
            raw.mapNotNull { entry ->
                val item = (entry as? Map<*, *>)?.toStringKeyMap() ?: return@mapNotNull null
                val name = item.stringAnyValue("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val content = item["content"] ?: item["data"] ?: item
                PresetSummary(
                    apiId = apiId,
                    name = name,
                    content = jsonValue(content),
                    selected = name == selectedName
                )
            }
        }
        else -> emptyList()
    }
    if (docs.isEmpty()) return embeddedCategory
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
                ?: runCatching {
                    val presetData = invokeAsMap(
                        "presets_list",
                        mapOf("preset_type" to apiId, "apiId" to apiId, "api_id" to apiId)
                    )
                    presetData.anyMapValue("presets").anyMapValue(name)
                }.getOrNull()?.takeIf { it.isNotEmpty() }
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

private val reverseProxySupportedSources = setOf(
    "openai", "claude", "mistralai", "makersuite", "vertexai", "deepseek", "xai", "zai", "moonshot"
)

internal fun connectionModelKeyForSource(source: String): String = when (source) {
    "openai" -> "openai_model"
    "claude" -> "claude_model"
    "makersuite" -> "google_model"
    "vertexai" -> "vertexai_model"
    "openrouter" -> "openrouter_model"
    "ai21" -> "ai21_model"
    "mistralai" -> "mistralai_model"
    "custom" -> "custom_model"
    "cohere" -> "cohere_model"
    "perplexity" -> "perplexity_model"
    "groq" -> "groq_model"
    "siliconflow" -> "siliconflow_model"
    "nanogpt" -> "nanogpt_model"
    "deepseek" -> "deepseek_model"
    "xai" -> "xai_model"
    "pollinations" -> "pollinations_model"
    "moonshot" -> "moonshot_model"
    "fireworks" -> "fireworks_model"
    "cometapi" -> "cometapi_model"
    "azure_openai" -> "azure_openai_model"
    "zai" -> "zai_model"
    "chutes" -> "chutes_model"
    "minimax" -> "minimax_model"
    "opencode" -> "opencode_model"
    "aws_bedrock" -> "aws_bedrock_model"
    "workers_ai" -> "workers_ai_model"
    else -> "${source}_model"
}

internal fun listConnectionProfilesFromSettings(settings: Map<String, Any?>): List<ConnectionProfile> {
    val result = mutableListOf<ConnectionProfile>()
    val seenNames = mutableSetOf<String>()

    settings.listMapValue("connection_profiles").forEach { item ->
        val name = item.stringAnyValue("name").ifBlank { item.stringAnyValue("label") }.trim()
        if (name.isNotBlank() && seenNames.add(name)) {
            val source = item.stringAnyValue("source")
            val model = item.stringAnyValue("model")
            val rawUrl = item.stringAnyValue("url")
                .ifBlank { item.stringAnyValue("custom_url") }
                .ifBlank { item.stringAnyValue("reverse_proxy") }
                .ifBlank {
                    listOf(source, model).filter { it.isNotBlank() }.joinToString(" · ")
                }
            result.add(
                ConnectionProfile(
                    label = name,
                    url = rawUrl,
                    lastConnection = item.longAnyValue("lastConnection")
                )
            )
        }
    }

    settings.anyMapValue("power_user").listMapValue("servers").forEach { item ->
        val label = item.stringAnyValue("label").trim()
        val url = item.stringAnyValue("url").trim()
        val effectiveName = label.ifBlank { url }
        if (effectiveName.isNotBlank() && url.isNotBlank() && seenNames.add(effectiveName)) {
            result.add(
                ConnectionProfile(
                    label = effectiveName,
                    url = url,
                    lastConnection = item.longAnyValue("lastConnection")
                )
            )
        }
    }

    return result.sortedByDescending { it.lastConnection }
}

internal fun saveConnectionProfileToSettings(
    settings: Map<String, Any?>,
    profile: ConnectionProfile
): Map<String, Any?> {
    val merged = settings.toMutableMap()
    val mainApi = merged.stringAnyValue("main_api").ifBlank { "openai" }
    val oai = merged.anyMapValue("oai_settings")
    val textGen = merged.anyMapValue("textgenerationwebui_settings")
    val source = when (mainApi) {
        "openai" -> oai.stringAnyValue("chat_completion_source").ifBlank { "openai" }
        "textgenerationwebui" -> textGen.stringAnyValue("type").ifBlank { "ooba" }
        else -> mainApi
    }
    val model = when (mainApi) {
        "openai" -> oai.stringAnyValue(connectionModelKeyForSource(source))
        "textgenerationwebui" -> {
            val key = if (source == "ooba") "custom_model" else "${source}_model"
            textGen.stringAnyValue(key)
        }
        else -> ""
    }
    val reverseProxy = if (mainApi == "openai" && source in reverseProxySupportedSources) {
        oai.stringAnyValue("reverse_proxy_$source").ifBlank { oai.stringAnyValue("reverse_proxy") }
    } else {
        ""
    }
    val proxyPassword = if (mainApi == "openai" && source in reverseProxySupportedSources) {
        oai.stringAnyValue("proxy_password_$source").ifBlank { oai.stringAnyValue("proxy_password") }
    } else {
        ""
    }
    val customUrl = if (mainApi == "openai" && source == "custom") {
        oai.stringAnyValue("custom_url")
    } else {
        ""
    }
    val customApiFormat = oai.stringAnyValue("custom_api_format").ifBlank { "openai_compat" }
    val vertexAuthMode = oai.stringAnyValue("vertexai_auth_mode").ifBlank { "express" }
    val vertexRegion = oai.stringAnyValue("vertexai_region").ifBlank { "us-central1" }
    val vertexProjectId = oai.stringAnyValue("vertexai_express_project_id")

    val resolvedUrl = profile.url.trim().ifBlank {
        when {
            mainApi == "openai" && source == "custom" -> customUrl
            mainApi == "openai" && reverseProxy.isNotBlank() -> reverseProxy
            mainApi == "textgenerationwebui" -> merged.stringAnyValue("api_server_textgenerationwebui")
            else -> listOf(source, model).filter { it.isNotBlank() }.joinToString(" · ")
        }
    }
    val now = profile.lastConnection.takeIf { it > 0 } ?: System.currentTimeMillis()
    val profileName = profile.label.trim().ifBlank { resolvedUrl.ifBlank { source } }

    val profilesList = merged.listMapValue("connection_profiles").map { it.toMutableMap() }.toMutableList()
    val snapshotEntry = linkedMapOf<String, Any?>(
        "name" to profileName,
        "label" to profileName,
        "url" to resolvedUrl,
        "lastConnection" to now,
        "main_api" to mainApi,
        "source" to source,
        "model" to model,
        "reverse_proxy" to reverseProxy,
        "proxy_password" to proxyPassword,
        "custom_url" to customUrl,
        "custom_api_format" to customApiFormat,
        "vertexai_auth_mode" to vertexAuthMode,
        "vertexai_region" to vertexRegion,
        "vertexai_express_project_id" to vertexProjectId
    )
    val existingProfileIdx = profilesList.indexOfFirst { item ->
        val itemName = item.stringAnyValue("name").ifBlank { item.stringAnyValue("label") }
        itemName == profileName
    }
    if (existingProfileIdx >= 0) {
        profilesList[existingProfileIdx] = snapshotEntry
    } else {
        profilesList.add(snapshotEntry)
    }
    merged["connection_profiles"] = profilesList

    val powerUser = merged.anyMapValue("power_user").toMutableMap()
    val servers = powerUser.listMapValue("servers").toMutableList()
    val existingServerIdx = servers.indexOfFirst { item ->
        item.stringAnyValue("label") == profileName
    }
    val serverEntry = linkedMapOf<String, Any?>(
        "label" to profileName,
        "url" to resolvedUrl,
        "lastConnection" to now
    )
    if (existingServerIdx >= 0) {
        servers[existingServerIdx] = serverEntry
    } else {
        servers.add(serverEntry)
    }
    powerUser["servers"] = servers
    powerUser["last_connection"] = profileName
    merged["power_user"] = powerUser
    return merged
}

internal fun deleteConnectionProfileFromSettings(
    settings: Map<String, Any?>,
    name: String
): Map<String, Any?> {
    val target = name.trim()
    val merged = settings.toMutableMap()
    val profilesList = merged.listMapValue("connection_profiles").filterNot { item ->
        val itemName = item.stringAnyValue("name").ifBlank { item.stringAnyValue("label") }.trim()
        itemName == target
    }
    merged["connection_profiles"] = profilesList

    val powerUser = merged.anyMapValue("power_user").toMutableMap()
    val servers = powerUser.listMapValue("servers").filterNot { item ->
        val label = item.stringAnyValue("label").trim()
        val url = item.stringAnyValue("url").trim()
        label == target || (label.isBlank() && url == target)
    }
    powerUser["servers"] = servers
    if (powerUser.stringAnyValue("last_connection") == target) {
        powerUser.remove("last_connection")
    }
    merged["power_user"] = powerUser
    return merged
}

internal fun applyConnectionProfileToSettings(
    settings: Map<String, Any?>,
    name: String
): Map<String, Any?>? {
    val target = name.trim()
    val merged = settings.toMutableMap()
    val profilesList = merged.listMapValue("connection_profiles").map { it.toMutableMap() }.toMutableList()
    val profileIdx = profilesList.indexOfFirst { item ->
        val itemName = item.stringAnyValue("name").ifBlank { item.stringAnyValue("label") }.trim()
        itemName == target
    }
    val now = System.currentTimeMillis()
    if (profileIdx >= 0) {
        val prof = profilesList[profileIdx]
        prof["lastConnection"] = now
        profilesList[profileIdx] = prof
        merged["connection_profiles"] = profilesList

        val mainApi = prof.stringAnyValue("main_api").ifBlank { "openai" }
        val source = prof.stringAnyValue("source")
        val model = prof.stringAnyValue("model")
        val url = prof.stringAnyValue("url")
        val reverseProxy = prof.stringAnyValue("reverse_proxy")
        val proxyPassword = prof.stringAnyValue("proxy_password")
        val customUrl = prof.stringAnyValue("custom_url")
        val customApiFormat = prof.stringAnyValue("custom_api_format")
        val vertexAuthMode = prof.stringAnyValue("vertexai_auth_mode")
        val vertexRegion = prof.stringAnyValue("vertexai_region")
        val vertexProjectId = prof.stringAnyValue("vertexai_express_project_id")

        merged.remove("api_type")
        merged["main_api"] = mainApi

        when (mainApi) {
            "openai" -> {
                val oai = merged.anyMapValue("oai_settings").toMutableMap()
                val effectiveSource = source.ifBlank { oai.stringAnyValue("chat_completion_source").ifBlank { "openai" } }
                oai["chat_completion_source"] = effectiveSource
                if (model.isNotBlank()) {
                    oai[connectionModelKeyForSource(effectiveSource)] = model
                }
                if (effectiveSource == "custom") {
                    oai["custom_url"] = customUrl.ifBlank { url }
                    if (customApiFormat.isNotBlank()) {
                        oai["custom_api_format"] = customApiFormat
                    }
                    oai["reverse_proxy"] = ""
                    oai["proxy_password"] = ""
                } else if (effectiveSource in reverseProxySupportedSources) {
                    oai["reverse_proxy"] = reverseProxy
                    oai["reverse_proxy_$effectiveSource"] = reverseProxy
                    oai["proxy_password"] = proxyPassword
                    oai["proxy_password_$effectiveSource"] = proxyPassword
                } else {
                    oai["reverse_proxy"] = ""
                    oai["proxy_password"] = ""
                }
                if (effectiveSource == "vertexai") {
                    if (vertexAuthMode.isNotBlank()) {
                        oai["vertexai_auth_mode"] = vertexAuthMode
                    }
                    if (vertexRegion.isNotBlank()) {
                        oai["vertexai_region"] = vertexRegion
                    }
                    oai["vertexai_express_project_id"] = vertexProjectId
                }
                merged["oai_settings"] = oai
            }
            "textgenerationwebui" -> {
                val textGen = merged.anyMapValue("textgenerationwebui_settings").toMutableMap()
                val effectiveType = source.ifBlank { textGen.stringAnyValue("type").ifBlank { "ooba" } }
                textGen["type"] = effectiveType
                if (model.isNotBlank()) {
                    val key = if (effectiveType == "ooba") "custom_model" else "${effectiveType}_model"
                    textGen[key] = model
                }
                if (url.isNotBlank()) {
                    merged["api_server_textgenerationwebui"] = url
                }
                merged["textgenerationwebui_settings"] = textGen
            }
        }

        val powerUser = merged.anyMapValue("power_user").toMutableMap()
        powerUser["last_connection"] = target
        val servers = powerUser.listMapValue("servers").map { it.toMutableMap() }.toMutableList()
        val serverIdx = servers.indexOfFirst { it.stringAnyValue("label").trim() == target }
        if (serverIdx >= 0) {
            servers[serverIdx]["lastConnection"] = now
            powerUser["servers"] = servers
        }
        merged["power_user"] = powerUser
        return merged
    }

    val powerUser = merged.anyMapValue("power_user").toMutableMap()
    val servers = powerUser.listMapValue("servers").map { it.toMutableMap() }.toMutableList()
    val serverIdx = servers.indexOfFirst { item ->
        val label = item.stringAnyValue("label").trim()
        val url = item.stringAnyValue("url").trim()
        label == target || (label.isBlank() && url == target)
    }
    if (serverIdx >= 0) {
        val srv = servers[serverIdx]
        srv["lastConnection"] = now
        servers[serverIdx] = srv
        powerUser["servers"] = servers
        powerUser["last_connection"] = target
        merged["power_user"] = powerUser
        val url = srv.stringAnyValue("url").trim()
        if (url.isNotBlank() && merged.stringAnyValue("main_api").ifBlank { "openai" } == "openai") {
            val oai = merged.anyMapValue("oai_settings").toMutableMap()
            val src = oai.stringAnyValue("chat_completion_source").ifBlank { "openai" }
            if (src == "custom") {
                oai["custom_url"] = url
            } else if (src in reverseProxySupportedSources) {
                oai["reverse_proxy"] = url
                oai["reverse_proxy_$src"] = url
            }
            merged["oai_settings"] = oai
        }
        return merged
    }

    return null
}

internal suspend fun TauriTavernJniClient.doListConnectionProfiles(): List<ConnectionProfile> {
    val settings = doGetSettings()
    return listConnectionProfilesFromSettings(settings)
}

internal suspend fun TauriTavernJniClient.doSaveConnectionProfile(profile: ConnectionProfile) {
    val updated = saveConnectionProfileToSettings(doGetSettings(), profile)
    doSaveSettings(updated)
}

internal suspend fun TauriTavernJniClient.doSaveConnectionProfile(name: String, url: String = "") {
    doSaveConnectionProfile(
        ConnectionProfile(
            label = name,
            url = url,
            lastConnection = System.currentTimeMillis()
        )
    )
}

internal suspend fun TauriTavernJniClient.doDeleteConnectionProfile(name: String) {
    val updated = deleteConnectionProfileFromSettings(doGetSettings(), name)
    doSaveSettings(updated)
}

internal suspend fun TauriTavernJniClient.doApplyConnectionProfile(name: String): Boolean {
    val updated = applyConnectionProfileToSettings(doGetSettings(), name) ?: return false
    doSaveSettings(updated)
    return true
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
