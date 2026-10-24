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
    val keys = (secretProviderLabels.keys + map.keys).distinct()
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
            label = secretProviderLabels[key] ?: key,
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
    "openai", "claude", "makersuite", "vertexai", "deepseek", "xai", "zai", "moonshot"
)

private val sourceSpecificStringKeysBySource = mapOf(
    "opencode" to listOf("opencode_endpoint", "opencode_api_format"),
    "vertexai" to listOf("vertexai_auth_mode", "vertexai_region", "vertexai_express_project_id"),
    "zai" to listOf("zai_endpoint"),
    "siliconflow" to listOf("siliconflow_endpoint"),
    "minimax" to listOf("minimax_endpoint"),
    "moonshot" to listOf("moonshot_endpoint"),
    "pollinations" to listOf("pollinations_endpoint"),
    "workers_ai" to listOf("workers_ai_account_id"),
    "nanogpt" to listOf("nanogpt_provider"),
    "aws_bedrock" to listOf(
        "aws_bedrock_region",
        "aws_bedrock_custom_template",
        "aws_bedrock_custom_response_path",
        "aws_bedrock_custom_stream_path"
    )
)

private val sourceSpecificBooleanKeysBySource = mapOf(
    "nanogpt" to listOf("nanogpt_payg_override"),
    "aws_bedrock" to listOf("aws_bedrock_use_custom_template")
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

private fun expectedSecretKeyForConnectionSource(
    source: String,
    vertexAuthMode: String = "express"
): String = when (source) {
    "vertexai" -> if (vertexAuthMode.equals("full", ignoreCase = true)) {
        "vertexai_service_account_json"
    } else {
        "api_key_vertexai"
    }
    "openai" -> "api_key_openai"
    "opencode" -> "api_key_opencode"
    "openrouter" -> "api_key_openrouter"
    "custom" -> "api_key_custom"
    "claude" -> "api_key_claude"
    "makersuite" -> "api_key_makersuite"
    "deepseek" -> "api_key_deepseek"
    "cohere" -> "api_key_cohere"
    "groq" -> "api_key_groq"
    "moonshot" -> "api_key_moonshot"
    "nanogpt" -> "api_key_nanogpt"
    "chutes" -> "api_key_chutes"
    "siliconflow" -> "api_key_siliconflow"
    "workers_ai" -> "api_key_workers_ai"
    "zai" -> "api_key_zai"
    "minimax" -> "api_key_minimax"
    "aws_bedrock" -> "api_key_aws_bedrock"
    "xai" -> "api_key_xai"
    "pollinations" -> "api_key_pollinations"
    else -> "api_key_$source"
}

private fun slugifyLlmConnectionId(raw: String): String {
    val trimmed = raw.trim()
    val asciiSlug = buildString {
        var prevDash = false
        for (ch in trimmed.lowercase()) {
            when {
                ch in 'a'..'z' || ch in '0'..'9' || ch == '_' -> {
                    append(ch)
                    prevDash = false
                }
                ch == '-' || ch.isWhitespace() || ch == '·' || ch == '.' || ch == '/' -> {
                    if (isNotEmpty() && !prevDash) {
                        append('-')
                        prevDash = true
                    }
                }
            }
        }
    }.trim('-')
    val hashSuffix = Integer.toUnsignedString(trimmed.hashCode(), 16)
    val candidate = when {
        asciiSlug.isEmpty() -> "conn-$hashSuffix"
        asciiSlug.length == trimmed.length && trimmed.all {
            it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_'
        } -> asciiSlug
        else -> "${asciiSlug.take(96)}-$hashSuffix"
    }
    return candidate.take(128).trim('-').ifEmpty { "conn-default" }
}

internal suspend fun TauriTavernJniClient.doListConnectionProfiles(): List<ConnectionProfile> {
    val items = runCatching { invokeAsListOfMaps("list_llm_connections") }.getOrDefault(emptyList())
    return items.mapNotNull { item ->
        val id = item.stringAnyValue("id").trim()
        val displayName = item.stringAnyValue("displayName")
            .ifBlank { item.stringAnyValue("display_name") }
            .ifBlank { id }
            .trim()
        if (displayName.isBlank()) return@mapNotNull null
        val source = item.stringAnyValue("chatCompletionSource")
            .ifBlank { item.stringAnyValue("chat_completion_source") }
        val description = item.stringAnyValue("description")
        val subtitle = description.ifBlank { source }
        ConnectionProfile(
            label = displayName,
            url = subtitle,
            lastConnection = 0L
        )
    }
}

internal suspend fun TauriTavernJniClient.doSaveConnectionProfile(profile: ConnectionProfile) {
    val settings = doGetSettings()
    val oai = settings.anyMapValue("oai_settings")
    val source = oai.stringAnyValue("chat_completion_source").ifBlank { "openai" }
    val model = oai.stringAnyValue(connectionModelKeyForSource(source)).trim()
    val displayName = profile.label.trim().ifBlank { source }
    val id = slugifyLlmConnectionId(displayName)

    val customApiFormat = oai.stringAnyValue("custom_api_format").ifBlank { "openai_compat" }
    val providerMap = linkedMapOf<String, Any?>(
        "chatCompletionSource" to source
    ).apply {
        if (source == "custom") {
            put("customApiFormat", customApiFormat)
        }
    }

    val sourceSpecific = linkedMapOf<String, Any?>()
    sourceSpecificStringKeysBySource[source]?.forEach { key ->
        val value = oai.stringAnyValue(key).trim()
        if (value.isNotEmpty()) {
            sourceSpecific[key] = value
        }
    }
    sourceSpecificBooleanKeysBySource[source]?.forEach { key ->
        if (oai.containsKey(key)) {
            sourceSpecific[key] = oai.booleanAnyValue(key)
        }
    }
    val endpointMap = linkedMapOf<String, Any?>().apply {
        if (source == "custom") {
            val baseUrl = oai.stringAnyValue("custom_url").trim()
                .ifBlank { profile.url.trim() }
                .ifBlank { "https://api.openai.com/v1" }
            put("baseUrl", baseUrl)
        }
        if (sourceSpecific.isNotEmpty()) {
            put("sourceSpecific", sourceSpecific)
        }
    }

    val reverseProxyUrl = if (source in reverseProxySupportedSources) {
        oai.stringAnyValue("reverse_proxy_$source")
            .ifBlank { oai.stringAnyValue("reverse_proxy") }
            .trim()
    } else {
        ""
    }
    val routingMap = linkedMapOf<String, Any?>().apply {
        if (reverseProxyUrl.isNotEmpty()) {
            put("reverseProxy", mapOf("url" to reverseProxyUrl))
        }
    }

    val vertexAuthMode = oai.stringAnyValue("vertexai_auth_mode").ifBlank { "express" }
    val expectedSecretKey = expectedSecretKeyForConnectionSource(source, vertexAuthMode)
    val secretStates = runCatching { doListSecrets() }.getOrDefault(emptyList())
    val matchingEntries = secretStates.firstOrNull { it.key == expectedSecretKey }?.entries.orEmpty()
    val activeSecret = matchingEntries.firstOrNull { it.active } ?: matchingEntries.firstOrNull()
    val authMap = linkedMapOf<String, Any?>().apply {
        if (activeSecret != null) {
            put(
                "secretRef",
                linkedMapOf<String, Any?>(
                    "key" to expectedSecretKey,
                    "id" to activeSecret.id,
                    "labelSnapshot" to activeSecret.label.takeIf { it.isNotBlank() }
                ).filterValues { it != null }
            )
        } else if (reverseProxyUrl.isEmpty()) {
            put(
                "secretRef",
                mapOf(
                    "key" to expectedSecretKey,
                    "id" to "default"
                )
            )
        }
    }

    val description = profile.url.trim().ifBlank {
        listOf(source, model).filter { it.isNotBlank() }.joinToString(" · ")
    }
    val definition = linkedMapOf<String, Any?>(
        "schemaVersion" to 1,
        "kind" to "tauritavern.llmConnection",
        "id" to id,
        "displayName" to displayName,
        "provider" to providerMap,
        "endpoint" to endpointMap,
        "auth" to authMap,
        "routing" to routingMap,
        "adapterHints" to emptyMap<String, Any?>(),
        "capabilities" to emptyMap<String, Any?>()
    ).apply {
        if (description.isNotBlank()) {
            put("description", description)
        }
    }

    invokeRaw("save_llm_connection", mapOf("connection" to definition))

    val merged = settings.toMutableMap()
    val powerUser = merged.anyMapValue("power_user").toMutableMap()
    powerUser["last_connection"] = displayName
    merged["power_user"] = powerUser
    doSaveSettings(merged)
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

private suspend fun TauriTavernJniClient.resolveLlmConnectionId(nameOrId: String): String {
    val target = nameOrId.trim()
    val items = runCatching { invokeAsListOfMaps("list_llm_connections") }.getOrDefault(emptyList())
    val matched = items.firstOrNull { item ->
        val id = item.stringAnyValue("id").trim()
        val displayName = item.stringAnyValue("displayName")
            .ifBlank { item.stringAnyValue("display_name") }
            .trim()
        id == target || displayName == target
    }
    return matched?.stringAnyValue("id")?.trim()?.takeIf { it.isNotEmpty() }
        ?: slugifyLlmConnectionId(target)
}

internal suspend fun TauriTavernJniClient.doDeleteConnectionProfile(name: String) {
    val id = resolveLlmConnectionId(name)
    invokeRaw("delete_llm_connection", mapOf("id" to id))
}

internal suspend fun TauriTavernJniClient.doApplyConnectionProfile(name: String): Boolean {
    val id = resolveLlmConnectionId(name)
    val conn = runCatching { invokeAsMap("load_llm_connection", mapOf("id" to id)) }
        .getOrDefault(emptyMap())
    if (conn.isEmpty()) return false

    val provider = conn.anyMapValue("provider")
    val source = provider.stringAnyValue("chatCompletionSource")
        .ifBlank { provider.stringAnyValue("chat_completion_source") }
        .ifBlank { return false }
    val customApiFormat = provider.stringAnyValue("customApiFormat")
        .ifBlank { provider.stringAnyValue("custom_api_format") }

    val endpoint = conn.anyMapValue("endpoint")
    val baseUrl = endpoint.stringAnyValue("baseUrl")
        .ifBlank { endpoint.stringAnyValue("base_url") }
    val sourceSpecific = endpoint.anyMapValue("sourceSpecific")
        .ifEmpty { endpoint.anyMapValue("source_specific") }

    val routing = conn.anyMapValue("routing")
    val reverseProxy = routing.anyMapValue("reverseProxy")
        .ifEmpty { routing.anyMapValue("reverse_proxy") }
    val reverseProxyUrl = reverseProxy.stringAnyValue("url")

    val settings = doGetSettings().toMutableMap()
    settings.remove("api_type")
    settings["main_api"] = "openai"
    val oai = settings.anyMapValue("oai_settings").toMutableMap()
    oai["chat_completion_source"] = source
    if (source == "custom") {
        if (baseUrl.isNotBlank()) {
            oai["custom_url"] = baseUrl
        }
        if (customApiFormat.isNotBlank()) {
            oai["custom_api_format"] = customApiFormat
        }
        oai["reverse_proxy"] = ""
    } else if (source in reverseProxySupportedSources) {
        oai["reverse_proxy"] = reverseProxyUrl
        oai["reverse_proxy_$source"] = reverseProxyUrl
    } else {
        oai["reverse_proxy"] = ""
    }
    sourceSpecific.forEach { (k, v) ->
        oai[k] = v
    }
    val description = conn.stringAnyValue("description")
    val modelPart = description.substringAfter("·", "").trim()
    if (modelPart.isNotBlank()) {
        oai[connectionModelKeyForSource(source)] = modelPart
    }
    settings["oai_settings"] = oai

    val displayName = conn.stringAnyValue("displayName")
        .ifBlank { conn.stringAnyValue("display_name") }
        .ifBlank { name.trim() }
    val powerUser = settings.anyMapValue("power_user").toMutableMap()
    powerUser["last_connection"] = displayName
    settings["power_user"] = powerUser

    doSaveSettings(settings)
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
