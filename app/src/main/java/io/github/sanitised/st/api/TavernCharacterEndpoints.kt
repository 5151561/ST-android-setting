package io.github.sanitised.st.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

internal suspend fun TavernCoreClient.listCharactersImpl(): List<CharacterSummary> {
    return withContext(Dispatchers.IO) {
        val body = postJson("api/characters/all", "{}")
        val items = StJson.parse(body) as? List<*> ?: emptyList<Any?>()
        items.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            map.toCharacterSummary()
        }
    }
}

internal suspend fun TavernCoreClient.getCharacterImpl(avatar: String): CharacterDetail {
    return withContext(Dispatchers.IO) {
        val body = postJson(
            path = "api/characters/get",
            json = jsonObject("avatar_url" to avatar)
        )
        val map = StJson.parse(body) as? Map<*, *>
            ?: throw IllegalStateException("Invalid character response")
        map.toCharacterDetail(fallbackAvatar = avatar)
    }
}

internal suspend fun TavernCoreClient.createCharacterImpl(
    request: CharacterSaveRequest,
    avatarUpload: CharacterUpload?
): String {
    return withContext(Dispatchers.IO) {
        postMultipart(
            path = "api/characters/create",
            body = request.toMultipartBody(avatar = null, avatarUpload = avatarUpload)
        ).trim()
    }
}

internal suspend fun TavernCoreClient.updateCharacterImpl(
    request: CharacterSaveRequest,
    avatarUpload: CharacterUpload?
) {
    val avatar = request.avatar?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("avatar is required")
    withContext(Dispatchers.IO) {
        postMultipart(
            path = "api/characters/edit",
            body = request.toMultipartBody(avatar = avatar, avatarUpload = avatarUpload)
        )
    }
}

internal suspend fun TavernCoreClient.mergeCharacterAttributesImpl(
    avatar: String,
    isFavorite: Boolean?,
    embeddedTags: List<String>?
) {
    withContext(Dispatchers.IO) {
        val extensions = linkedMapOf<String, Any?>()
        if (isFavorite != null) extensions["fav"] = isFavorite
        val data = linkedMapOf<String, Any?>()
        if (embeddedTags != null) data["tags"] = embeddedTags
        if (extensions.isNotEmpty()) data["extensions"] = extensions

        postJson(
            path = "api/characters/merge-attributes",
            json = jsonObject(
                "avatar" to avatar,
                "fav" to isFavorite,
                "tags" to embeddedTags,
                "data" to data
            )
        )
    }
}

internal suspend fun TavernCoreClient.renameCharacterImpl(avatar: String, newName: String): String {
    return withContext(Dispatchers.IO) {
        val body = postJson(
            path = "api/characters/rename",
            json = jsonObject(
                "avatar_url" to avatar,
                "new_name" to newName
            )
        )
        val map = StJson.parse(body) as? Map<*, *> ?: emptyMap<Any, Any>()
        map.stringValue("avatar")
    }
}

internal suspend fun TavernCoreClient.duplicateCharacterImpl(avatar: String): String {
    return withContext(Dispatchers.IO) {
        val body = postJson(
            path = "api/characters/duplicate",
            json = jsonObject("avatar_url" to avatar)
        )
        val map = StJson.parse(body) as? Map<*, *> ?: emptyMap<Any, Any>()
        map.stringValue("path")
    }
}

internal suspend fun TavernCoreClient.deleteCharacterImpl(avatar: String, deleteChats: Boolean) {
    withContext(Dispatchers.IO) {
        postJson(
            path = "api/characters/delete",
            json = jsonObject(
                "avatar_url" to avatar,
                "delete_chats" to deleteChats
            )
        )
    }
}

internal suspend fun TavernCoreClient.listCharacterChatsImpl(avatar: String): List<CharacterChatSummary> {
    return withContext(Dispatchers.IO) {
        val body = postJson(
            path = "api/characters/chats",
            json = jsonObject(
                "avatar_url" to avatar,
                "metadata" to true
            )
        )
        val items = StJson.parse(body) as? List<*> ?: emptyList<Any?>()
        items.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            map.toCharacterChatSummary()
        }
    }
}

internal suspend fun TavernCoreClient.importCharacterImpl(
    fileName: String,
    bytes: ByteArray,
    preservedName: String?
): String {
    return withContext(Dispatchers.IO) {
        val fileType = fileName.substringAfterLast('.', "").lowercase()
        require(fileType in setOf("json", "png", "yaml", "yml", "charx", "byaf")) {
            "Unsupported character file type"
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("avatar", fileName, bytes.toRequestBody(TavernCoreClient.binaryMediaType))
            .addFormDataPart("file_type", fileType)
            .addFormDataPart("user_name", "User")
            .apply {
                preservedName?.takeIf { it.isNotBlank() }?.let { name ->
                    addFormDataPart("preserved_name", name)
                }
            }
            .build()
        val responseBody = postMultipart("api/characters/import", body)
        val map = StJson.parse(responseBody) as? Map<*, *> ?: emptyMap<Any, Any>()
        if (map.booleanValue("error")) {
            throw IllegalStateException("SillyTavern failed to import character")
        }
        val imported = map.stringValue("file_name")
            .ifBlank { throw IllegalStateException("Invalid character import response") }
        if (imported.endsWith(".png", ignoreCase = true)) imported else "$imported.png"
    }
}

internal suspend fun TavernCoreClient.importExternalCharacterImpl(
    urlOrUuid: String,
    preservedName: String?
): String {
    val downloaded = withContext(Dispatchers.IO) {
        val path = if (urlOrUuid.startsWith("http://") || urlOrUuid.startsWith("https://")) {
            "api/content/importURL"
        } else {
            "api/content/importUUID"
        }
        postJsonFile(
            path = path,
            json = jsonObject("url" to urlOrUuid),
            fallbackFileName = "character.png"
        )
    }
    return importCharacterImpl(downloaded.fileName, downloaded.bytes, preservedName)
}

internal suspend fun TavernCoreClient.exportCharacterImpl(
    avatar: String,
    format: CharacterExportFormat
): CharacterExportFile {
    return withContext(Dispatchers.IO) {
        postJsonFile(
            path = "api/characters/export",
            json = jsonObject(
                "avatar_url" to avatar,
                "format" to format.apiValue
            ),
            fallbackFileName = avatar.removeSuffix(".png") + "." + format.fileExtension
        )
    }
}

internal suspend fun TavernCoreClient.updateCharacterAvatarImpl(
    avatar: String,
    fileName: String,
    bytes: ByteArray
) {
    withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("avatar", fileName, bytes.toRequestBody(TavernCoreClient.binaryMediaType))
            .addFormDataPart("avatar_url", avatar)
            .build()
        postMultipart("api/characters/edit-avatar", body)
    }
}

internal suspend fun TavernCoreClient.renameCharacterChatImpl(
    avatar: String,
    originalFile: String,
    renamedFile: String
): String {
    return withContext(Dispatchers.IO) {
        val body = postJson(
            path = "api/chats/rename",
            json = jsonObject(
                "avatar_url" to avatar,
                "original_file" to originalFile,
                "renamed_file" to renamedFile,
                "is_group" to false
            )
        )
        val map = StJson.parse(body) as? Map<*, *> ?: emptyMap<Any, Any>()
        map.stringValue("sanitizedFileName").ifBlank { renamedFile.removeSuffix(".jsonl") }
    }
}

internal suspend fun TavernCoreClient.deleteCharacterChatImpl(avatar: String, chatFile: String) {
    withContext(Dispatchers.IO) {
        postJson(
            path = "api/chats/delete",
            json = jsonObject(
                "avatar_url" to avatar,
                "chatfile" to chatFile
            )
        )
    }
}

internal suspend fun TavernCoreClient.importCharacterChatImpl(
    avatar: String,
    characterName: String,
    fileName: String,
    bytes: ByteArray
): List<String> {
    return withContext(Dispatchers.IO) {
        val fileType = fileName.substringAfterLast('.', "").lowercase()
        require(fileType in setOf("json", "jsonl")) {
            "Unsupported chat file type"
        }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("avatar", fileName, bytes.toRequestBody(TavernCoreClient.binaryMediaType))
            .addFormDataPart("file_type", fileType)
            .addFormDataPart("avatar_url", avatar)
            .addFormDataPart("character_name", characterName)
            .addFormDataPart("user_name", "User")
            .build()
        val responseBody = postMultipart("api/chats/import", body)
        val map = StJson.parse(responseBody) as? Map<*, *> ?: emptyMap<Any, Any>()
        if (map.booleanValue("error")) {
            throw IllegalStateException("SillyTavern failed to import chat")
        }
        map.stringListValue("fileNames")
    }
}

internal suspend fun TavernCoreClient.exportCharacterChatImpl(
    avatar: String,
    chatFile: String,
    format: ChatExportFormat
): CharacterExportFile {
    return withContext(Dispatchers.IO) {
        val body = postJson(
            path = "api/chats/export",
            json = jsonObject(
                "avatar_url" to avatar,
                "file" to chatFile,
                "format" to format.apiValue,
                "exportfilename" to chatFile.removeSuffix(".jsonl") + "." + format.fileExtension,
                "is_group" to false
            )
        )
        val map = StJson.parse(body) as? Map<*, *> ?: emptyMap<Any, Any>()
        val fileName = chatFile.removeSuffix(".jsonl") + "." + format.fileExtension
        CharacterExportFile(
            fileName = fileName,
            contentType = format.contentType,
            bytes = map.stringValue("result").toByteArray()
        )
    }
}

internal suspend fun TavernCoreClient.getChatJsonlImpl(avatar: String, chatFile: String): MutableList<Any?> =
    withContext(Dispatchers.IO) {
        val body = postJson(
            "api/chats/get",
            jsonObject(
                "avatar_url" to avatar,
                "file_name" to chatFile.removeSuffix(".jsonl")
            )
        )
        // Found chats return a JSON array [header, ...messages]; a missing file returns {}.
        when (val parsed = StJson.parse(body)) {
            is List<*> -> parsed.map { normalizeJsonValue(it) }.toMutableList()
            else -> mutableListOf()
        }
    }

internal suspend fun TavernCoreClient.saveChatJsonlImpl(
    avatar: String,
    chatFile: String,
    chat: List<Any?>
) {
    withContext(Dispatchers.IO) {
        postJson(
            "api/chats/save",
            jsonObject(
                "avatar_url" to avatar,
                "file_name" to chatFile.removeSuffix(".jsonl"),
                "chat" to chat,
                "force" to false
            )
        )
    }
}

internal fun Map<*, *>.toCharacterSummary(): CharacterSummary {
    val data = mapValue("data")
    val extensions = data.mapValue("extensions")
    val avatar = stringValue("avatar").ifBlank { stringValue("id") }
    val name = stringValue("name")
        .ifBlank { data.stringValue("name") }
        .ifBlank { avatar.removeSuffix(".png") }
    val tags = data.stringListValue("tags").ifEmpty { stringListValue("tags") }
    return CharacterSummary(
        id = avatar,
        name = name,
        avatarUrl = avatar.ifBlank { null },
        tags = tags,
        creatorNotes = data.stringValue("creator_notes"),
        isFavorite = extensions.booleanValue("fav") || booleanValue("fav"),
        lastChatAt = longValue("date_last_chat"),
        createDate = stringValue("create_date"),
        chatSize = longValue("chat_size"),
        dataSize = longValue("data_size"),
        characterVersion = data.stringValue("character_version")
    )
}

internal fun Map<*, *>.toCharacterDetail(fallbackAvatar: String): CharacterDetail {
    val data = mapValue("data")
    val extensions = data.mapValue("extensions")
    val depthPrompt = extensions.mapValue("depth_prompt")
    val avatar = stringValue("avatar").ifBlank { fallbackAvatar }
    val name = stringValue("name")
        .ifBlank { data.stringValue("name") }
        .ifBlank { avatar.removeSuffix(".png") }
    return CharacterDetail(
        id = avatar,
        name = name,
        avatarUrl = avatar.ifBlank { null },
        description = stringValue("description").ifBlank { data.stringValue("description") },
        personality = stringValue("personality").ifBlank { data.stringValue("personality") },
        scenario = stringValue("scenario").ifBlank { data.stringValue("scenario") },
        firstMessage = stringValue("first_mes").ifBlank { data.stringValue("first_mes") },
        messageExample = stringValue("mes_example").ifBlank { data.stringValue("mes_example") },
        creatorNotes = data.stringValue("creator_notes").ifBlank { stringValue("creatorcomment") },
        systemPrompt = data.stringValue("system_prompt"),
        postHistoryInstructions = data.stringValue("post_history_instructions"),
        tags = data.stringListValue("tags").ifEmpty { stringListValue("tags") },
        creator = data.stringValue("creator"),
        characterVersion = data.stringValue("character_version"),
        world = extensions.stringValue("world"),
        talkativeness = extensions.doubleValue("talkativeness", doubleValue("talkativeness", 0.5)),
        isFavorite = extensions.booleanValue("fav") || booleanValue("fav"),
        alternateGreetings = data.stringListValue("alternate_greetings"),
        depthPrompt = depthPrompt.stringValue("prompt"),
        depthPromptDepth = depthPrompt.intValue("depth", 4),
        depthPromptRole = depthPrompt.stringValue("role").ifBlank { "system" },
        chat = stringValue("chat"),
        createDate = stringValue("create_date"),
        rawJsonData = stringValue("json_data"),
        sourceUrl = extensions.characterSourceUrl()
    )
}

internal fun Map<*, *>.characterSourceUrl(): String {
    val chubPath = when (val value = get("chub")) {
        is Map<*, *> -> value.stringValue("full_path")
        is String -> value
        else -> ""
    }
    if (chubPath.isNotBlank()) return "https://chub.ai/characters/$chubPath"

    val pygmalionId = stringValue("pygmalion_id")
    if (pygmalionId.isNotBlank()) return "https://pygmalion.chat/$pygmalionId"

    val githubRepo = stringValue("github_repo")
    if (githubRepo.isNotBlank()) return "https://github.com/$githubRepo"

    val sourceUrl = stringValue("source_url")
    if (sourceUrl.isNotBlank()) return sourceUrl

    val risuSource = (get("risuai") as? Map<*, *>)
        ?.let { risu -> risu["source"] as? List<*> }
        ?.firstOrNull() as? String
    if (risuSource?.startsWith("risurealm:") == true) {
        return "https://realm.risuai.net/character/${risuSource.substringAfter(':')}"
    }

    val perchanceSlug = (get("perchance_data") as? Map<*, *>)?.stringValue("slug")
    if (!perchanceSlug.isNullOrBlank()) {
        return "https://perchance.org/ai-character-chat?data=$perchanceSlug"
    }

    return ""
}

internal fun Map<*, *>.toCharacterChatSummary(): CharacterChatSummary {
    return CharacterChatSummary(
        id = stringValue("file_id").ifBlank { stringValue("file_name").removeSuffix(".jsonl") },
        fileName = stringValue("file_name"),
        fileSize = stringValue("file_size"),
        messageCount = intValue("chat_items", 0),
        lastMessage = stringValue("mes"),
        lastMessageAt = when (val value = get("last_mes")) {
            is String -> value
            is Number -> value.toLong().toString()
            else -> ""
        }
    )
}

internal fun CharacterSaveRequest.toMultipartBody(
    avatar: String?,
    avatarUpload: CharacterUpload?
): MultipartBody {
    return MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .apply {
            avatar?.takeIf { it.isNotBlank() }?.let { addFormDataPart("avatar_url", it) }
            avatarUpload?.let { upload ->
                addFormDataPart("avatar", upload.fileName, upload.bytes.toRequestBody(TavernCoreClient.binaryMediaType))
            }
            addFormDataPart("ch_name", name)
            addFormDataPart("description", description)
            addFormDataPart("personality", personality)
            addFormDataPart("scenario", scenario)
            addFormDataPart("first_mes", firstMessage)
            addFormDataPart("mes_example", messageExample)
            addFormDataPart("creator_notes", creatorNotes)
            addFormDataPart("system_prompt", systemPrompt)
            addFormDataPart("post_history_instructions", postHistoryInstructions)
            addFormDataPart("tags", tags.joinToString(", "))
            addFormDataPart("creator", creator)
            addFormDataPart("character_version", characterVersion)
            addFormDataPart("world", world)
            addFormDataPart("talkativeness", talkativeness.toString())
            addFormDataPart("fav", if (isFavorite) "true" else "false")
            alternateGreetings.forEach { greeting ->
                addFormDataPart("alternate_greetings", greeting)
            }
            addFormDataPart("depth_prompt_prompt", depthPrompt)
            addFormDataPart("depth_prompt_depth", depthPromptDepth.toString())
            addFormDataPart("depth_prompt_role", depthPromptRole)
            addFormDataPart("chat", chat)
            addFormDataPart("create_date", createDate)
            addFormDataPart("json_data", rawJsonData)
            addFormDataPart("extensions", jsonObject("source_url" to sourceUrl))
        }
        .build()
}
