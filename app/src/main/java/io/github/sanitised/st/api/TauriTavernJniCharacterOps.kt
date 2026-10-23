package io.github.sanitised.st.api

import java.util.Locale

internal suspend fun TauriTavernJniClient.doHealthCheck(): CoreHealth {
    return runCatching {
        val data = invokeRaw("ping", emptyMap<String, Any?>())
        val map = data as? Map<*, *>
        val version = map?.get("version")?.toString()
            ?: map?.get("service")?.toString()
            ?: "TauriTavern"
        CoreHealth(ok = true, version = version)
    }.getOrElse {
        CoreHealth(ok = false, version = null)
    }
}

internal suspend fun TauriTavernJniClient.doListCharacters(): List<CharacterSummary> {
    val items = invokeAsListOfMaps("characters_list")
    return items.map { it.toCharacterSummary() }
}

internal suspend fun TauriTavernJniClient.doGetCharacter(avatar: String): CharacterDetail {
    val item = invokeAsMap(
        "character_get",
        mapOf(
            "avatar" to avatar,
            "name" to avatar.removeSuffix(".png"),
            "avatarUrl" to avatar,
            "avatar_url" to avatar
        )
    )
    val base = item.toCharacterDetail(fallbackAvatar = avatar)
    val topExt = item.mapValue("extensions")
    val topDepth = topExt.mapValue("depth_prompt")
    return base.copy(
        systemPrompt = base.systemPrompt.ifBlank { item.stringValue("system_prompt") },
        postHistoryInstructions = base.postHistoryInstructions.ifBlank {
            item.stringValue("post_history_instructions")
        },
        creator = base.creator.ifBlank { item.stringValue("creator") },
        characterVersion = base.characterVersion.ifBlank { item.stringValue("character_version") },
        world = base.world.ifBlank { topExt.stringValue("world") }.ifBlank { item.stringValue("world") },
        talkativeness = if (topExt.containsKey("talkativeness")) {
            topExt.doubleValue("talkativeness", base.talkativeness)
        } else {
            base.talkativeness
        },
        isFavorite = base.isFavorite || topExt.booleanValue("fav"),
        alternateGreetings = base.alternateGreetings.ifEmpty {
            item.stringListValue("alternate_greetings")
        },
        depthPrompt = base.depthPrompt.ifBlank { topDepth.stringValue("prompt") }
            .ifBlank { item.stringValue("depth_prompt_prompt") },
        depthPromptDepth = if (topDepth.containsKey("depth")) {
            topDepth.intValue("depth", 4)
        } else {
            base.depthPromptDepth
        },
        depthPromptRole = base.depthPromptRole.takeIf { it != "system" }
            ?: topDepth.stringValue("role").ifBlank { "system" }
    )
}

internal fun CharacterSaveRequest.toRustRequestMap(
    resolvedAvatar: String?,
    avatarUpload: CharacterUpload? = null
): Map<String, Any?> {
    val map = linkedMapOf<String, Any?>(
        "avatar" to resolvedAvatar,
        "avatar_url" to resolvedAvatar,
        "name" to name.trim(),
        "ch_name" to name.trim(),
        "description" to description,
        "personality" to personality,
        "scenario" to scenario,
        "first_mes" to firstMessage,
        "mes_example" to messageExample,
        "creatorcomment" to creatorNotes,
        "creator_notes" to creatorNotes,
        "system_prompt" to systemPrompt,
        "post_history_instructions" to postHistoryInstructions,
        "creator" to creator,
        "character_version" to characterVersion,
        "tags" to tags,
        "alternate_greetings" to alternateGreetings,
        "talkativeness" to talkativeness,
        "fav" to isFavorite,
        "world" to world,
        "chat" to chat.ifBlank { null },
        "create_date" to createDate.ifBlank { null },
        "depth_prompt_prompt" to depthPrompt,
        "depth_prompt_depth" to depthPromptDepth,
        "depth_prompt_role" to depthPromptRole,
        "json_data" to rawJsonData.ifBlank { null },
        "extensions" to mapOf(
            "source_url" to sourceUrl,
            "fav" to isFavorite,
            "world" to world,
            "talkativeness" to talkativeness,
            "depth_prompt" to mapOf(
                "prompt" to depthPrompt,
                "depth" to depthPromptDepth,
                "role" to depthPromptRole
            )
        )
    )
    if (avatarUpload != null) {
        map["avatarBase64"] = java.util.Base64.getEncoder().encodeToString(avatarUpload.bytes)
        map["avatarFileName"] = avatarUpload.fileName
        map["file_type"] = avatarUpload.fileName.substringAfterLast('.', "png").lowercase(Locale.ROOT)
    }
    return map
}

internal suspend fun TauriTavernJniClient.doCreateCharacter(
    request: CharacterSaveRequest,
    avatarUpload: CharacterUpload?
): String {
    val reqMap = request.toRustRequestMap(resolvedAvatar = null, avatarUpload = avatarUpload)
    val data = if (avatarUpload != null && avatarUpload.bytes.isNotEmpty()) {
        invokeBinaryRaw("character_create_with_avatar", reqMap, avatarUpload.bytes)
    } else {
        invokeRaw("character_create", reqMap + mapOf("request" to reqMap))
    }
    return when (data) {
        is String -> data.trim()
        is Map<*, *> -> data.stringValue("avatar").ifBlank { data.stringValue("file_name") }
        else -> ""
    }
}

internal suspend fun TauriTavernJniClient.doUpdateCharacter(
    request: CharacterSaveRequest,
    avatarUpload: CharacterUpload?
) {
    val avatar = request.avatar?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("avatar is required")
    val reqMap = request.toRustRequestMap(resolvedAvatar = avatar, avatarUpload = avatarUpload)
    invokeRaw("character_edit", reqMap + mapOf("request" to reqMap))
    if (avatarUpload != null && avatarUpload.bytes.isNotEmpty()) {
        runCatching {
            invokeBinaryRaw(
                "character_update_avatar",
                mapOf(
                    "avatar" to avatar,
                    "avatar_url" to avatar,
                    "name" to avatar.removeSuffix(".png")
                ),
                avatarUpload.bytes
            )
        }
    }
}

internal suspend fun TauriTavernJniClient.doMergeCharacterAttributes(
    avatar: String,
    isFavorite: Boolean?,
    embeddedTags: List<String>?
) {
    val extensions = linkedMapOf<String, Any?>()
    if (isFavorite != null) extensions["fav"] = isFavorite
    val data = linkedMapOf<String, Any?>()
    if (embeddedTags != null) data["tags"] = embeddedTags
    if (extensions.isNotEmpty()) data["extensions"] = extensions

    val req = linkedMapOf<String, Any?>(
        "avatar" to avatar,
        "avatar_url" to avatar,
        "name" to avatar.removeSuffix(".png"),
        "fav" to isFavorite,
        "tags" to embeddedTags,
        "extensions" to extensions.takeIf { it.isNotEmpty() },
        "data" to data
    )
    invokeRaw("character_merge", req + mapOf("request" to req))
}

internal suspend fun TauriTavernJniClient.doRenameCharacter(avatar: String, newName: String): String {
    val data = invokeRaw(
        "character_rename",
        mapOf(
            "avatar" to avatar,
            "avatarUrl" to avatar,
            "avatar_url" to avatar,
            "old_name" to avatar,
            "newName" to newName,
            "new_name" to newName
        )
    )
    return when (data) {
        is String -> data
        is Map<*, *> -> data.stringValue("avatar").ifBlank { avatar }
        else -> avatar
    }
}

internal suspend fun TauriTavernJniClient.doDuplicateCharacter(avatar: String): String {
    val data = invokeRaw(
        "character_duplicate",
        mapOf("avatar" to avatar, "avatarUrl" to avatar, "avatar_url" to avatar)
    )
    return when (data) {
        is String -> data
        is Map<*, *> -> data.stringValue("path").ifBlank { data.stringValue("avatar") }
        else -> ""
    }
}

internal suspend fun TauriTavernJniClient.doDeleteCharacter(avatar: String, deleteChats: Boolean) {
    invokeRaw(
        "character_delete",
        mapOf(
            "avatar" to avatar,
            "avatarUrl" to avatar,
            "avatar_url" to avatar,
            "deleteChats" to deleteChats,
            "delete_chats" to deleteChats
        )
    )
}

internal suspend fun TauriTavernJniClient.doListCharacterChats(avatar: String): List<CharacterChatSummary> {
    val items = invokeAsListOfMaps(
        "chats_for_character",
        mapOf("avatar" to avatar, "avatarUrl" to avatar, "avatar_url" to avatar)
    )
    return items.map { it.toCharacterChatSummary() }
}

internal suspend fun TauriTavernJniClient.doImportCharacter(
    fileName: String,
    bytes: ByteArray,
    preservedName: String?
): String {
    val fileType = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    require(fileType in setOf("json", "png", "yaml", "yml", "charx", "byaf")) {
        "Unsupported character file type"
    }
    val data = invokeBinaryRaw(
        "character_import",
        mapOf(
            "fileName" to fileName,
            "file_name" to fileName,
            "fileFormat" to fileType,
            "file_format" to fileType,
            "preserveFileName" to preservedName,
            "preserve_file_name" to preservedName,
            "preservedName" to preservedName,
            "preserved_name" to preservedName
        ),
        bytes
    )
    val imported = when (data) {
        is String -> data
        is Map<*, *> -> data.stringValue("file_name")
            .ifBlank { data.stringValue("fileName") }
            .ifBlank { data.stringValue("avatar") }
        else -> ""
    }.ifBlank { throw IllegalStateException("Invalid character import response") }
    return if (imported.endsWith(".png", ignoreCase = true)) imported else "$imported.png"
}

internal suspend fun TauriTavernJniClient.doImportExternalCharacter(
    urlOrUuid: String,
    preservedName: String?
): String {
    val isUrl = urlOrUuid.startsWith("http://", ignoreCase = true) ||
        urlOrUuid.startsWith("https://", ignoreCase = true)
    val data = if (isUrl) {
        invokeRaw(
            "character_import_url",
            mapOf("url" to urlOrUuid, "preservedName" to preservedName, "preserved_name" to preservedName)
        )
    } else {
        invokeRaw(
            "character_import_uuid",
            mapOf("uuid" to urlOrUuid, "url" to urlOrUuid, "preservedName" to preservedName, "preserved_name" to preservedName)
        )
    }
    val imported = when (data) {
        is String -> data
        is Map<*, *> -> data.stringValue("file_name")
            .ifBlank { data.stringValue("fileName") }
            .ifBlank { data.stringValue("avatar") }
        else -> ""
    }.ifBlank { throw IllegalStateException("Invalid character import response") }
    return if (imported.endsWith(".png", ignoreCase = true)) imported else "$imported.png"
}

internal suspend fun TauriTavernJniClient.doExportCharacter(
    avatar: String,
    format: CharacterExportFormat
): CharacterExportFile {
    val meta = mapOf(
        "avatar" to avatar,
        "avatarUrl" to avatar,
        "avatar_url" to avatar,
        "name" to avatar.removeSuffix(".png"),
        "format" to format.apiValue
    )
    val defaultFileName = avatar.removeSuffix(".png") + "." + format.fileExtension
    val defaultContentType = if (format == CharacterExportFormat.JSON) "application/json" else "image/png"
    val directBytes = invokeExportCharacterBytes(meta)
    if (directBytes != null && directBytes.isNotEmpty()) {
        return CharacterExportFile(
            fileName = defaultFileName,
            contentType = defaultContentType,
            bytes = directBytes
        )
    }
    val map = invokeAsMap("character_export", meta)
    val fileName = map.stringAnyValue("fileName")
        .ifBlank { map.stringAnyValue("file_name") }
        .ifBlank { defaultFileName }
    val contentType = map.stringAnyValue("mimeType")
        .ifBlank { map.stringAnyValue("contentType") }
        .ifBlank { defaultContentType }
    val bytes = extractBytesFromExportMap(map)
    return CharacterExportFile(
        fileName = fileName,
        contentType = contentType,
        bytes = bytes
    )
}

internal fun TauriTavernJniClient.extractBytesFromExportMap(map: Map<String, Any?>): ByteArray {
    (map["bytes"] as? List<*>)?.let { rawList ->
        val out = ByteArray(rawList.size)
        for (i in rawList.indices) {
            out[i] = ((rawList[i] as? Number)?.toInt() ?: 0).toByte()
        }
        return out
    }
    (map["dataBase64"] as? String ?: map["base64"] as? String)?.let { b64 ->
        return decodeBase64(b64)
    }
    (map["result"] as? String ?: map["content"] as? String)?.let { text ->
        return text.toByteArray(Charsets.UTF_8)
    }
    return ByteArray(0)
}

internal suspend fun TauriTavernJniClient.doUpdateCharacterAvatar(
    avatar: String,
    fileName: String,
    bytes: ByteArray
) {
    invokeBinaryRaw(
        "character_update_avatar",
        mapOf(
            "avatar" to avatar,
            "avatarUrl" to avatar,
            "avatar_url" to avatar,
            "name" to avatar.removeSuffix(".png"),
            "fileName" to fileName,
            "file_name" to fileName
        ),
        bytes
    )
}

internal suspend fun TauriTavernJniClient.doRenameCharacterChat(
    avatar: String,
    originalFile: String,
    renamedFile: String
): String {
    val data = invokeRaw(
        "chat_rename",
        mapOf(
            "avatar" to avatar,
            "avatarUrl" to avatar,
            "avatar_url" to avatar,
            "originalFile" to originalFile,
            "original_file" to originalFile,
            "renamedFile" to renamedFile,
            "renamed_file" to renamedFile
        )
    )
    return when (data) {
        is String -> data.ifBlank { renamedFile.removeSuffix(".jsonl") }
        is Map<*, *> -> data.stringValue("sanitizedFileName")
            .ifBlank { data.stringValue("file_name") }
            .ifBlank { data.stringValue("fileName") }
            .ifBlank { renamedFile.removeSuffix(".jsonl") }
        else -> renamedFile.removeSuffix(".jsonl")
    }
}

internal suspend fun TauriTavernJniClient.doDeleteCharacterChat(avatar: String, chatFile: String) {
    invokeRaw(
        "chat_delete",
        mapOf(
            "avatar" to avatar,
            "avatarUrl" to avatar,
            "avatar_url" to avatar,
            "fileName" to chatFile.removeSuffix(".jsonl"),
            "file_name" to chatFile.removeSuffix(".jsonl")
        )
    )
}

internal suspend fun TauriTavernJniClient.doImportCharacterChat(
    avatar: String,
    characterName: String,
    fileName: String,
    bytes: ByteArray
): List<String> {
    val fileType = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    require(fileType in setOf("json", "jsonl")) {
        "Unsupported chat file type"
    }
    val data = invokeBinaryRaw(
        "chat_import",
        mapOf(
            "avatar" to avatar,
            "avatarUrl" to avatar,
            "avatar_url" to avatar,
            "characterName" to characterName,
            "character_name" to characterName,
            "character_display_name" to characterName,
            "fileName" to fileName,
            "file_name" to fileName,
            "fileType" to fileType,
            "file_type" to fileType
        ),
        bytes
    )
    return when (data) {
        is List<*> -> data.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
        is Map<*, *> -> data.stringListValue("fileNames")
            .ifEmpty { data.stringListValue("file_names") }
        is String -> listOfNotNull(data.takeIf { it.isNotBlank() })
        else -> emptyList()
    }
}

internal suspend fun TauriTavernJniClient.doExportCharacterChat(
    avatar: String,
    chatFile: String,
    format: ChatExportFormat
): CharacterExportFile {
    val fileName = chatFile.removeSuffix(".jsonl") + "." + format.fileExtension
    val messages = runCatching { doGetChatJsonl(avatar, chatFile) }.getOrDefault(mutableListOf())
    val serialized = if (format == ChatExportFormat.JSONL) {
        messages.joinToString("\n") { jsonValue(it) }.toByteArray(Charsets.UTF_8)
    } else {
        messages.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val name = m["name"]?.toString().orEmpty()
            val mes = m["mes"]?.toString().orEmpty()
            if (mes.isBlank()) null else "$name: $mes"
        }.joinToString("\n\n").toByteArray(Charsets.UTF_8)
    }
    return CharacterExportFile(
        fileName = fileName,
        contentType = format.contentType,
        bytes = serialized
    )
}

private fun Throwable.isChatNotFoundError(): Boolean {
    val msg = message ?: return false
    return msg.contains("NOT_FOUND", ignoreCase = true) || msg.contains("not found", ignoreCase = true)
}

private fun parseChatLineMaps(rawLines: List<*>?): List<Map<String, Any?>> {
    if (rawLines == null) return emptyList()
    return rawLines.mapNotNull { item ->
        when (item) {
            is String -> item.takeIf { it.isNotBlank() }
                ?.let { StJson.parse(it) as? Map<*, *> }
                ?.toStringKeyMap()
            is Map<*, *> -> item.toStringKeyMap()
            else -> null
        }
    }
}

private fun parseChatPayloadCursor(rawCursor: Any?): ChatPayloadCursor? {
    val cursorMap = rawCursor as? Map<*, *> ?: return null
    val offset = cursorMap.longValue("offset")
    val size = cursorMap.longValue("size")
    val modifiedMillis = if (cursorMap.containsKey("modifiedMillis")) {
        cursorMap.longValue("modifiedMillis")
    } else {
        cursorMap.longValue("modified_millis")
    }
    return ChatPayloadCursor(
        offset = offset,
        size = size,
        modifiedMillis = modifiedMillis
    )
}

internal suspend fun TauriTavernJniClient.doGetChatJsonl(
    avatar: String,
    chatFile: String
): MutableList<Any?> {
    val normalized = chatFile.removeSuffix(".jsonl")
    val data = runCatching {
        invokeRaw(
            "chat_get",
            mapOf(
                "avatar" to avatar,
                "avatarUrl" to avatar,
                "avatar_url" to avatar,
                "character_name" to avatar.removeSuffix(".png"),
                "fileName" to normalized,
                "file_name" to normalized
            )
        )
    }.getOrElse { err ->
        if (err.isChatNotFoundError()) return mutableListOf()
        throw err
    }
    return when (data) {
        is List<*> -> data.map { normalizeJsonValue(it) }.toMutableList()
        is Map<*, *> -> {
            val inner = (data["messages"] as? List<*>) ?: (data["chat"] as? List<*>)
            inner?.map { normalizeJsonValue(it) }?.toMutableList() ?: mutableListOf()
        }
        else -> mutableListOf()
    }
}

internal suspend fun TauriTavernJniClient.doSaveChatJsonl(
    avatar: String,
    chatFile: String,
    chat: List<Any?>
) {
    val normalized = chatFile.removeSuffix(".jsonl")
    val chName = avatar.removeSuffix(".png")
    invokeRaw(
        "chat_save",
        mapOf(
            "avatar" to avatar,
            "avatarUrl" to avatar,
            "avatar_url" to avatar,
            "chName" to chName,
            "ch_name" to chName,
            "fileName" to normalized,
            "file_name" to normalized,
            "chat" to chat,
            "messages" to chat
        )
    )
}

internal suspend fun TauriTavernJniClient.doGetChatIntegrity(
    avatar: String,
    chatFile: String
): String? {
    val avatarName = avatar.removeSuffix(".png")
    val fileName = chatFile.removeSuffix(".jsonl")
    val data = runCatching {
        invokeRaw(
            "chat_integrity",
            mapOf(
                "character_name" to avatarName,
                "avatar" to avatar,
                "avatar_url" to avatar,
                "file_name" to fileName
            )
        )
    }.getOrElse { err ->
        if (err.isChatNotFoundError()) return null
        throw err
    }
    return when (data) {
        is Map<*, *> -> data["integrity"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        is String -> data.takeIf { it.isNotBlank() && it != "null" }
        else -> null
    }
}

internal suspend fun TauriTavernJniClient.doAppendChatJsonl(
    avatar: String,
    chatFile: String,
    newMessages: List<Any?>,
    headerIfMissing: Map<String, Any?>? = null,
    expectedIntegrity: String? = null
): Boolean {
    if (newMessages.isEmpty()) return true
    val avatarName = avatar.removeSuffix(".png")
    val fileName = chatFile.removeSuffix(".jsonl")
    val payload = linkedMapOf<String, Any?>(
        "character_name" to avatarName,
        "avatar" to avatar,
        "avatar_url" to avatar,
        "file_name" to fileName,
        "lines" to newMessages
    )
    if (headerIfMissing != null) {
        payload["header"] = headerIfMissing
    }
    if (!expectedIntegrity.isNullOrBlank()) {
        payload["expected_integrity"] = expectedIntegrity
    }
    val data = invokeRaw("chat_append_lines", payload)
    return when (data) {
        is Map<*, *> -> (data["ok"] as? Boolean) ?: (data["appended"] as? Boolean) ?: true
        is Boolean -> data
        else -> true
    }
}

internal suspend fun TauriTavernJniClient.doGetChatTailJsonl(
    avatar: String,
    chatFile: String,
    maxLines: Int = 100
): ChatPayloadTail {
    val avatarName = avatar.removeSuffix(".png")
    val fileName = chatFile.removeSuffix(".jsonl")
    val data = runCatching {
        invokeRaw(
            "get_chat_tail",
            mapOf(
                "character_name" to avatarName,
                "avatar" to avatar,
                "avatar_url" to avatar,
                "file_name" to fileName,
                "max_lines" to maxLines.coerceAtLeast(1)
            )
        )
    }.getOrElse { err ->
        if (err.isChatNotFoundError()) return ChatPayloadTail()
        throw err
    }
    val map = data as? Map<*, *> ?: return ChatPayloadTail()
    val header = when (val rawHeader = map["header"]) {
        is String -> rawHeader.takeIf { it.isNotBlank() }
            ?.let { StJson.parse(it) as? Map<*, *> }
            ?.toStringKeyMap()
        is Map<*, *> -> rawHeader.toStringKeyMap()
        else -> null
    }
    val rawLines = (map["lines"] as? List<*>) ?: (map["messages"] as? List<*>)
    val messages = parseChatLineMaps(rawLines)
    val hasMoreBefore = if (map.containsKey("hasMoreBefore")) {
        map.booleanValue("hasMoreBefore")
    } else {
        map.booleanValue("has_more_before")
    }
    val cursor = parseChatPayloadCursor(map["cursor"])
    return ChatPayloadTail(
        header = header,
        messages = messages,
        cursor = cursor,
        hasMoreBefore = hasMoreBefore
    )
}

internal suspend fun TauriTavernJniClient.doGetChatBeforeJsonl(
    avatar: String,
    chatFile: String,
    cursor: ChatPayloadCursor,
    maxLines: Int = 100
): ChatPayloadChunk {
    val avatarName = avatar.removeSuffix(".png")
    val fileName = chatFile.removeSuffix(".jsonl")
    val data = runCatching {
        invokeRaw(
            "get_chat_before",
            mapOf(
                "character_name" to avatarName,
                "avatar" to avatar,
                "avatar_url" to avatar,
                "file_name" to fileName,
                "before_offset" to cursor.offset,
                "expected_size" to cursor.size,
                "expected_modified_millis" to cursor.modifiedMillis,
                "cursor" to mapOf(
                    "offset" to cursor.offset,
                    "size" to cursor.size,
                    "modifiedMillis" to cursor.modifiedMillis,
                    "modified_millis" to cursor.modifiedMillis
                ),
                "max_lines" to maxLines.coerceAtLeast(1)
            )
        )
    }.getOrElse { err ->
        if (err.isChatNotFoundError()) return ChatPayloadChunk()
        throw err
    }
    val map = data as? Map<*, *> ?: return ChatPayloadChunk()
    val rawLines = (map["lines"] as? List<*>) ?: (map["messages"] as? List<*>)
    val messages = parseChatLineMaps(rawLines)
    val hasMoreBefore = if (map.containsKey("hasMoreBefore")) {
        map.booleanValue("hasMoreBefore")
    } else {
        map.booleanValue("has_more_before")
    }
    val nextCursor = parseChatPayloadCursor(map["cursor"])
    return ChatPayloadChunk(
        messages = messages,
        cursor = nextCursor,
        hasMoreBefore = hasMoreBefore
    )
}

internal suspend fun TauriTavernJniClient.doListRecentChats(): List<ChatSummary> {
    val items = invokeAsListOfMaps("chats_recent")
    return items.mapNotNull { item ->
        val fileName = item.stringAnyValue("file_name")
            .ifBlank { item.stringAnyValue("fileName") }
            .ifBlank { item.stringAnyValue("id") }
        if (fileName.isBlank()) return@mapNotNull null
        val avatar = item.stringAnyValue("avatar")
        val charName = item.stringAnyValue("character_name")
            .ifBlank { avatar.removeSuffix(".png") }
        ChatSummary(
            id = fileName,
            characterId = avatar,
            characterName = charName,
            avatarUrl = avatar.ifBlank { null },
            lastMessage = item.stringAnyValue("mes").ifBlank { item.stringAnyValue("last_mes") },
            lastUpdated = item.longAnyValue("last_mes")
        )
    }
}

internal suspend fun TauriTavernJniClient.doListGroups(): List<GroupSummary> {
    val items = invokeAsListOfMaps("groups_list")
    return items.map { it.toGroupSummary() }
}

internal suspend fun TauriTavernJniClient.doCreateGroup(request: GroupCreateRequest): GroupSummary {
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
    val map = invokeAsMap("group_create", payload + mapOf("request" to payload))
    return map.toGroupSummary()
}

internal suspend fun TauriTavernJniClient.doEditGroup(group: GroupSummary) {
    val payload = linkedMapOf<String, Any?>(
        "id" to group.id,
        "name" to group.name,
        "members" to group.members,
        "avatar_url" to group.avatarUrl,
        "allow_self_responses" to group.allowSelfResponses,
        "activation_strategy" to group.activationStrategy,
        "generation_mode" to group.generationMode,
        "disabled_members" to group.disabledMembers,
        "chat_metadata" to emptyMap<String, Any?>(),
        "fav" to group.isFavorite,
        "chat_id" to group.chatId,
        "chats" to group.chats,
        "auto_mode_delay" to group.autoModeDelay,
        "generation_mode_join_prefix" to group.generationModeJoinPrefix,
        "generation_mode_join_suffix" to group.generationModeJoinSuffix
    )
    invokeRaw("group_edit", payload + mapOf("request" to payload))
}

internal suspend fun TauriTavernJniClient.doDeleteGroup(groupId: String) {
    invokeRaw("group_delete", mapOf("id" to groupId))
}

internal suspend fun TauriTavernJniClient.doGetGroupChatJsonl(chatId: String): MutableList<Any?> {
    val normalizedId = chatId.removeSuffix(".jsonl")
    val data = runCatching {
        invokeRaw("group_chat_get", mapOf("id" to normalizedId, "chatId" to normalizedId))
    }.getOrElse { err ->
        if (err.isChatNotFoundError()) {
            return mutableListOf()
        }
        throw err
    }
    return when (data) {
        is List<*> -> data.map { normalizeJsonValue(it) }.toMutableList()
        is Map<*, *> -> {
            val inner = (data["messages"] as? List<*>) ?: (data["chat"] as? List<*>)
            inner?.map { normalizeJsonValue(it) }?.toMutableList() ?: mutableListOf()
        }
        else -> mutableListOf()
    }
}

internal suspend fun TauriTavernJniClient.doSaveGroupChatJsonl(chatId: String, chat: List<Any?>) {
    val normalizedId = chatId.removeSuffix(".jsonl")
    invokeRaw(
        "group_chat_save",
        mapOf(
            "id" to normalizedId,
            "chatId" to normalizedId,
            "messages" to chat,
            "chat" to chat
        )
    )
}
