package io.github.sanitised.st.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import org.yaml.snakeyaml.Yaml

data class CoreHealth(
    val ok: Boolean,
    val version: String? = null
)

data class CharacterSummary(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val tags: List<String> = emptyList(),
    val creatorNotes: String = "",
    val isFavorite: Boolean = false,
    val lastChatAt: Long = 0,
    val createDate: String = "",
    val chatSize: Long = 0,
    val dataSize: Long = 0,
    val characterVersion: String = ""
)

data class CharacterDetail(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val messageExample: String = "",
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val tags: List<String> = emptyList(),
    val creator: String = "",
    val characterVersion: String = "",
    val world: String = "",
    val talkativeness: Double = 0.5,
    val isFavorite: Boolean = false,
    val alternateGreetings: List<String> = emptyList(),
    val depthPrompt: String = "",
    val depthPromptDepth: Int = 4,
    val depthPromptRole: String = "system",
    val chat: String = "",
    val createDate: String = "",
    val rawJsonData: String = "",
    val sourceUrl: String = ""
)

data class CharacterSaveRequest(
    val avatar: String? = null,
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val messageExample: String = "",
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val tags: List<String> = emptyList(),
    val creator: String = "",
    val characterVersion: String = "",
    val world: String = "",
    val talkativeness: Double = 0.5,
    val isFavorite: Boolean = false,
    val alternateGreetings: List<String> = emptyList(),
    val depthPrompt: String = "",
    val depthPromptDepth: Int = 4,
    val depthPromptRole: String = "system",
    val chat: String = "",
    val createDate: String = "",
    val rawJsonData: String = ""
)

data class CharacterUpload(
    val fileName: String,
    val bytes: ByteArray
)

data class CharacterChatSummary(
    val id: String,
    val fileName: String,
    val fileSize: String = "",
    val messageCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageAt: String = ""
)

enum class CharacterExportFormat(val apiValue: String, val fileExtension: String) {
    PNG("png", "png"),
    JSON("json", "json")
}

enum class ChatExportFormat(val apiValue: String, val fileExtension: String, val contentType: String) {
    JSONL("jsonl", "jsonl", "application/jsonl"),
    TXT("txt", "txt", "text/plain")
}

data class CharacterExportFile(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
)

data class STTag(
    val id: String,
    val name: String,
    val color: String = "",
    val isFolder: Boolean = false,
    val sortOrder: Int = 0
)

data class STTagSettings(
    val tags: List<STTag> = emptyList(),
    val tagMap: Map<String, List<String>> = emptyMap(),
    val worldNames: List<String> = emptyList(),
    val rawSettings: Map<String, Any?> = emptyMap()
)

data class ChatSummary(
    val id: String,
    val characterId: String,
    val characterName: String,
    val lastMessage: String? = null,
    val lastUpdated: Long = 0,
    val isPinned: Boolean = false
)

data class GenerationChunk(
    val text: String,
    val isFinal: Boolean = false
)

interface TavernCoreApi {
    suspend fun healthCheck(): CoreHealth
    suspend fun listCharacters(): List<CharacterSummary>
    suspend fun getCharacter(avatar: String): CharacterDetail
    suspend fun createCharacter(request: CharacterSaveRequest, avatarUpload: CharacterUpload? = null): String
    suspend fun updateCharacter(request: CharacterSaveRequest, avatarUpload: CharacterUpload? = null)
    suspend fun mergeCharacterAttributes(
        avatar: String,
        isFavorite: Boolean? = null,
        embeddedTags: List<String>? = null
    )
    suspend fun getTagSettings(): STTagSettings
    suspend fun saveTagSettings(settings: STTagSettings)
    suspend fun renameCharacter(avatar: String, newName: String): String
    suspend fun duplicateCharacter(avatar: String): String
    suspend fun deleteCharacter(avatar: String, deleteChats: Boolean = false)
    suspend fun listCharacterChats(avatar: String): List<CharacterChatSummary>
    suspend fun importCharacter(fileName: String, bytes: ByteArray, preservedName: String? = null): String
    suspend fun importExternalCharacter(urlOrUuid: String, preservedName: String? = null): String
    suspend fun exportCharacter(avatar: String, format: CharacterExportFormat): CharacterExportFile
    suspend fun updateCharacterAvatar(avatar: String, fileName: String, bytes: ByteArray)
    suspend fun renameCharacterChat(avatar: String, originalFile: String, renamedFile: String): String
    suspend fun deleteCharacterChat(avatar: String, chatFile: String)
    suspend fun exportCharacterChat(
        avatar: String,
        chatFile: String,
        format: ChatExportFormat
    ): CharacterExportFile
    suspend fun listRecentChats(): List<ChatSummary>
    suspend fun sendMessage(chatId: String, text: String): Flow<GenerationChunk>
    suspend fun stopGeneration(chatId: String)
}

class TavernCoreClient(
    baseUrl: String = "http://127.0.0.1:8000",
    private val httpClient: OkHttpClient = defaultHttpClient
) : TavernCoreApi {
    private val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    private val yaml = Yaml()
    private var csrfToken: String? = null

    override suspend fun healthCheck(): CoreHealth {
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(normalizedBaseUrl)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    CoreHealth(
                        ok = response.code in 200..399,
                        version = response.header("X-SillyTavern-Version")
                    )
                }
            }.getOrElse {
                CoreHealth(ok = false)
            }
        }
    }

    override suspend fun listCharacters(): List<CharacterSummary> {
        return withContext(Dispatchers.IO) {
            val body = postJson("api/characters/all", "{}")
            val items = yaml.load<Any?>(body) as? List<*> ?: emptyList<Any?>()
            items.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                map.toCharacterSummary()
            }
        }
    }

    override suspend fun getCharacter(avatar: String): CharacterDetail {
        return withContext(Dispatchers.IO) {
            val body = postJson(
                path = "api/characters/get",
                json = jsonObject("avatar_url" to avatar)
            )
            val map = yaml.load<Any?>(body) as? Map<*, *>
                ?: throw IllegalStateException("Invalid character response")
            map.toCharacterDetail(fallbackAvatar = avatar)
        }
    }

    override suspend fun createCharacter(request: CharacterSaveRequest, avatarUpload: CharacterUpload?): String {
        return withContext(Dispatchers.IO) {
            postMultipart(
                path = "api/characters/create",
                body = request.toMultipartBody(avatar = null, avatarUpload = avatarUpload)
            ).trim()
        }
    }

    override suspend fun updateCharacter(request: CharacterSaveRequest, avatarUpload: CharacterUpload?) {
        val avatar = request.avatar?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("avatar is required")
        withContext(Dispatchers.IO) {
            postMultipart(
                path = "api/characters/edit",
                body = request.toMultipartBody(avatar = avatar, avatarUpload = avatarUpload)
            )
        }
    }

    override suspend fun mergeCharacterAttributes(
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

    override suspend fun getTagSettings(): STTagSettings {
        return withContext(Dispatchers.IO) {
            val body = postJson("api/settings/get", "{}")
            val map = yaml.load<Any?>(body) as? Map<*, *> ?: emptyMap<Any, Any>()
            val rawSettings = (yaml.load<Any?>(map.stringValue("settings")) as? Map<*, *>)
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

    override suspend fun saveTagSettings(settings: STTagSettings) {
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

    override suspend fun renameCharacter(avatar: String, newName: String): String {
        return withContext(Dispatchers.IO) {
            val body = postJson(
                path = "api/characters/rename",
                json = jsonObject(
                    "avatar_url" to avatar,
                    "new_name" to newName
                )
            )
            val map = yaml.load<Any?>(body) as? Map<*, *> ?: emptyMap<Any, Any>()
            map.stringValue("avatar")
        }
    }

    override suspend fun duplicateCharacter(avatar: String): String {
        return withContext(Dispatchers.IO) {
            val body = postJson(
                path = "api/characters/duplicate",
                json = jsonObject("avatar_url" to avatar)
            )
            val map = yaml.load<Any?>(body) as? Map<*, *> ?: emptyMap<Any, Any>()
            map.stringValue("path")
        }
    }

    override suspend fun deleteCharacter(avatar: String, deleteChats: Boolean) {
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

    override suspend fun listCharacterChats(avatar: String): List<CharacterChatSummary> {
        return withContext(Dispatchers.IO) {
            val body = postJson(
                path = "api/characters/chats",
                json = jsonObject(
                    "avatar_url" to avatar,
                    "metadata" to true
                )
            )
            val items = yaml.load<Any?>(body) as? List<*> ?: emptyList<Any?>()
            items.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                map.toCharacterChatSummary()
            }
        }
    }

    override suspend fun importCharacter(fileName: String, bytes: ByteArray, preservedName: String?): String {
        return withContext(Dispatchers.IO) {
            val fileType = fileName.substringAfterLast('.', "").lowercase()
            require(fileType in setOf("json", "png", "yaml", "yml", "charx", "byaf")) {
                "Unsupported character file type"
            }
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("avatar", fileName, bytes.toRequestBody(binaryMediaType))
                .addFormDataPart("file_type", fileType)
                .addFormDataPart("user_name", "User")
                .apply {
                    preservedName?.takeIf { it.isNotBlank() }?.let { name ->
                        addFormDataPart("preserved_name", name)
                    }
                }
                .build()
            val responseBody = postMultipart("api/characters/import", body)
            val map = yaml.load<Any?>(responseBody) as? Map<*, *> ?: emptyMap<Any, Any>()
            if (map.booleanValue("error")) {
                throw IllegalStateException("SillyTavern failed to import character")
            }
            val imported = map.stringValue("file_name")
                .ifBlank { throw IllegalStateException("Invalid character import response") }
            if (imported.endsWith(".png", ignoreCase = true)) imported else "$imported.png"
        }
    }

    override suspend fun importExternalCharacter(urlOrUuid: String, preservedName: String?): String {
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
        return importCharacter(downloaded.fileName, downloaded.bytes, preservedName)
    }

    override suspend fun exportCharacter(avatar: String, format: CharacterExportFormat): CharacterExportFile {
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

    override suspend fun updateCharacterAvatar(avatar: String, fileName: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("avatar", fileName, bytes.toRequestBody(binaryMediaType))
                .addFormDataPart("avatar_url", avatar)
                .build()
            postMultipart("api/characters/edit-avatar", body)
        }
    }

    override suspend fun renameCharacterChat(avatar: String, originalFile: String, renamedFile: String): String {
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
            val map = yaml.load<Any?>(body) as? Map<*, *> ?: emptyMap<Any, Any>()
            map.stringValue("sanitizedFileName").ifBlank { renamedFile.removeSuffix(".jsonl") }
        }
    }

    override suspend fun deleteCharacterChat(avatar: String, chatFile: String) {
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

    override suspend fun exportCharacterChat(
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
            val map = yaml.load<Any?>(body) as? Map<*, *> ?: emptyMap<Any, Any>()
            val fileName = chatFile.removeSuffix(".jsonl") + "." + format.fileExtension
            CharacterExportFile(
                fileName = fileName,
                contentType = format.contentType,
                bytes = map.stringValue("result").toByteArray()
            )
        }
    }

    override suspend fun listRecentChats(): List<ChatSummary> {
        // TODO: GET /api/chats
        return emptyList()
    }

    override suspend fun sendMessage(chatId: String, text: String): Flow<GenerationChunk> {
        // TODO: POST /api/chats/{chatId}/messages with SSE streaming
        return kotlinx.coroutines.flow.emptyFlow()
    }

    override suspend fun stopGeneration(chatId: String) {
        // TODO: POST /api/chats/{chatId}/stop
    }

    private companion object {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val binaryMediaType = "application/octet-stream".toMediaType()

        val defaultHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(750, TimeUnit.MILLISECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .cookieJar(InMemoryCookieJar())
            .followRedirects(true)
            .build()

        fun normalizeBaseUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim()
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }

    private fun postJson(path: String, json: String): String {
        val builder = Request.Builder()
            .url(normalizedBaseUrl + path.removePrefix("/"))
            .post(json.toRequestBody(jsonMediaType))
        csrfToken().takeIf { it.isNotBlank() }?.let { token ->
            builder.header("x-csrf-token", token)
        }
        val request = builder.build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("SillyTavern API ${response.code}: $body")
            }
            return body
        }
    }

    private fun postMultipart(path: String, body: MultipartBody): String {
        val builder = Request.Builder()
            .url(normalizedBaseUrl + path.removePrefix("/"))
            .post(body)
        csrfToken().takeIf { it.isNotBlank() }?.let { token ->
            builder.header("x-csrf-token", token)
        }
        val request = builder.build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("SillyTavern API ${response.code}: $responseBody")
            }
            return responseBody
        }
    }

    private fun postJsonFile(path: String, json: String, fallbackFileName: String): CharacterExportFile {
        val builder = Request.Builder()
            .url(normalizedBaseUrl + path.removePrefix("/"))
            .post(json.toRequestBody(jsonMediaType))
        csrfToken().takeIf { it.isNotBlank() }?.let { token ->
            builder.header("x-csrf-token", token)
        }
        val request = builder.build()
        httpClient.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                throw IllegalStateException("SillyTavern API ${response.code}: ${bytes.toString(Charsets.UTF_8)}")
            }
            return CharacterExportFile(
                fileName = response.header("Content-Disposition")
                    ?.contentDispositionFileName()
                    ?.ifBlank { fallbackFileName }
                    ?: fallbackFileName,
                contentType = response.header("Content-Type")?.substringBefore(';') ?: "application/octet-stream",
                bytes = bytes
            )
        }
    }

    private fun csrfToken(): String {
        csrfToken?.let { return it }
        return runCatching {
            val request = Request.Builder()
                .url(normalizedBaseUrl + "csrf-token")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching ""
                val body = response.body?.string().orEmpty()
                val token = (yaml.load<Any?>(body) as? Map<*, *>)
                    ?.stringValue("token")
                    .orEmpty()
                csrfToken = token
                token
            }
        }.getOrDefault("")
    }

    private fun Map<*, *>.toCharacterSummary(): CharacterSummary {
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

    private fun Map<*, *>.toCharacterDetail(fallbackAvatar: String): CharacterDetail {
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
            sourceUrl = extensions.stringValue("source_url")
                .ifBlank { extensions.stringValue("chub") }
                .ifBlank { extensions.stringValue("pygmalion_id") }
        )
    }

    private fun Map<*, *>.toCharacterChatSummary(): CharacterChatSummary {
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

    private fun CharacterSaveRequest.toMultipartBody(
        avatar: String?,
        avatarUpload: CharacterUpload?
    ): MultipartBody {
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                avatar?.takeIf { it.isNotBlank() }?.let { addFormDataPart("avatar_url", it) }
                avatarUpload?.let { upload ->
                    addFormDataPart("avatar", upload.fileName, upload.bytes.toRequestBody(binaryMediaType))
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
                addFormDataPart("extensions", "{}")
            }
            .build()
    }

    private fun Map<*, *>?.stringValue(key: String): String =
        (this?.get(key) as? String).orEmpty()

    private fun Map<*, *>?.longValue(key: String): Long =
        when (val value = this?.get(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }

    private fun Map<*, *>?.doubleValue(key: String, default: Double): Double =
        when (val value = this?.get(key)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: default
            else -> default
        }

    private fun Map<*, *>?.intValue(key: String, default: Int): Int =
        when (val value = this?.get(key)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }

    private fun Map<*, *>?.booleanValue(key: String): Boolean =
        when (val value = this?.get(key)) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> false
        }

    private fun Map<*, *>?.stringListValue(key: String): List<String> =
        when (val value = this?.get(key)) {
            is List<*> -> value.mapNotNull { it as? String }
            is String -> value.split(',').map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }

    private fun Map<*, *>?.mapValue(key: String): Map<*, *> =
        this?.get(key) as? Map<*, *> ?: emptyMap<Any, Any>()

    private fun Map<String, Any?>.anyMapValue(key: String): Map<String, Any?> =
        (this[key] as? Map<*, *>)?.toStringKeyMap() ?: emptyMap()

    private fun Map<String, Any?>.listMapValue(key: String): List<Map<String, Any?>> =
        (this[key] as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            ?.map { it.toStringKeyMap() }
            ?: emptyList()

    private fun Map<*, *>.toStringKeyMap(): Map<String, Any?> =
        entries.associate { (key, value) -> key.toString() to normalizeJsonValue(value) }

    private fun normalizeJsonValue(value: Any?): Any? =
        when (value) {
            is Map<*, *> -> value.toStringKeyMap()
            is List<*> -> value.map { normalizeJsonValue(it) }
            else -> value
        }

    private fun Map<String, Any?>.toSTTag(): STTag =
        STTag(
            id = stringAnyValue("id"),
            name = stringAnyValue("name"),
            color = stringAnyValue("color"),
            isFolder = stringAnyValue("folder_type").isNotBlank() || booleanAnyValue("is_folder"),
            sortOrder = intAnyValue("sort_order", 0)
        )

    private fun Map<String, Any?>.stringAnyValue(key: String): String =
        (get(key) as? String).orEmpty()

    private fun Map<String, Any?>.booleanAnyValue(key: String): Boolean =
        when (val value = get(key)) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> false
        }

    private fun Map<String, Any?>.intAnyValue(key: String, default: Int): Int =
        when (val value = get(key)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }

    private fun Any?.toStringList(): List<String> =
        when (this) {
            is List<*> -> mapNotNull { it as? String }
            is String -> split(',').map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }

    private fun String.contentDispositionFileName(): String {
        val token = "filename=\""
        val start = indexOf(token)
        if (start < 0) return ""
        return substring(start + token.length).substringBefore('"')
    }

    private fun jsonObject(vararg pairs: Pair<String, Any?>): String =
        pairs.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
            "${quoteJson(key)}:${jsonValue(value)}"
        }

    private fun jsonValue(value: Any?): String =
        when (value) {
            null -> "null"
            is Boolean -> value.toString()
            is Number -> value.toString()
            is String -> quoteJson(value)
            is List<*> -> value.joinToString(separator = ",", prefix = "[", postfix = "]") { jsonValue(it) }
            is Map<*, *> -> value.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { entry ->
                "${quoteJson(entry.key.toString())}:${jsonValue(entry.value)}"
            }
            else -> quoteJson(value.toString())
        }

    private fun quoteJson(value: String): String {
        val escaped = buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
        return "\"$escaped\""
    }
}

private class InMemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(this.cookies) {
            this.cookies.removeAll { existing ->
                cookies.any { it.name == existing.name && it.domain == existing.domain && it.path == existing.path }
            }
            this.cookies.addAll(cookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(cookies) {
            val validCookies = cookies.filter { it.expiresAt > System.currentTimeMillis() }
            cookies.retainAll(validCookies.toSet())
            return validCookies.filter { it.matches(url) }
        }
    }
}
