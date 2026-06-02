package io.github.sanitised.st.api

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.sanitised.st.chat.prompt.GenerationDeltaParser
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

data class ConnectionTestResult(
    val success: Boolean,
    val models: List<String> = emptyList(),
    val errorMessage: String? = null
)

@Immutable
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

@Immutable
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
    val rawJsonData: String = "",
    val sourceUrl: String = ""
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

@Immutable
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

@Immutable
data class SettingsSnapshot(
    val name: String,
    val date: Long,
    val size: Long
)

data class WorldInfoSummary(
    val id: String,
    val name: String,
    val extensions: Map<String, Any?> = emptyMap()
)

data class WorldInfoEntry(
    val uid: Int,
    val keys: List<String> = emptyList(),
    val secondaryKeys: List<String> = emptyList(),
    val comment: String = "",
    val content: String = "",
    val order: Int = 0,
    val depth: Int = 4,
    val position: Int = 0,
    val constant: Boolean = false,
    val selective: Boolean = false,
    val disabled: Boolean = false,
    val raw: Map<String, Any?> = emptyMap()
)

data class WorldInfoBook(
    val name: String,
    val entries: List<WorldInfoEntry> = emptyList(),
    val rawData: Map<String, Any?> = emptyMap()
)

data class PersonaProfile(
    val avatar: String,
    val name: String,
    val title: String = "",
    val description: String = "",
    val isDefault: Boolean = false,
    val hasAvatar: Boolean = true
)

data class PersonaSaveRequest(
    val avatar: String,
    val name: String,
    val title: String = "",
    val description: String = "",
    val makeDefault: Boolean = false
)

data class SecretEntry(
    val id: String,
    val value: String,
    val label: String,
    val active: Boolean
)

data class SecretProviderState(
    val key: String,
    val label: String,
    val entries: List<SecretEntry> = emptyList()
)

data class PresetSummary(
    val apiId: String,
    val name: String,
    val content: String,
    val selected: Boolean = false
)

data class PresetCategory(
    val apiId: String,
    val title: String,
    val presets: List<PresetSummary> = emptyList()
)

data class PresetLibrary(
    val categories: List<PresetCategory> = emptyList()
)

data class ConnectionProfile(
    val label: String,
    val url: String,
    val lastConnection: Long = 0
)

data class ChatBackupSummary(
    val fileName: String,
    val fileSize: String = "",
    val messageCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageAt: String = ""
)

@Immutable
data class ChatSummary(
    val id: String,
    val characterId: String,
    val characterName: String,
    val avatarUrl: String? = null,
    val lastMessage: String? = null,
    val lastUpdated: Long = 0,
    val isPinned: Boolean = false
)

@Immutable
data class GroupSummary(
    val id: String,
    val name: String,
    val members: List<String> = emptyList(),
    val chatId: String = "",
    val chats: List<String> = emptyList(),
    val lastUpdated: Long = 0,
    val chatSize: Long = 0,
    val avatarUrl: String = "",
    val allowSelfResponses: Boolean = false,
    val activationStrategy: Int = 0,
    val generationMode: Int = 0,
    val isFavorite: Boolean = false,
    val disabledMembers: List<String> = emptyList(),
    val autoModeDelay: Int = 5,
    val generationModeJoinPrefix: String = "",
    val generationModeJoinSuffix: String = ""
)

data class GroupCreateRequest(
    val name: String,
    val members: List<String>,
    val avatarUrl: String = "img/ai4.png",
    val allowSelfResponses: Boolean = false,
    val activationStrategy: Int = 0,
    val generationMode: Int = 0,
    val disabledMembers: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val chatId: String = "",
    val autoModeDelay: Int = 5
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
    suspend fun listSettingsSnapshots(): List<SettingsSnapshot>
    suspend fun makeSettingsSnapshot()
    suspend fun loadSettingsSnapshot(name: String): String
    suspend fun restoreSettingsSnapshot(name: String)
    suspend fun listWorldInfos(): List<WorldInfoSummary>
    suspend fun getWorldInfo(name: String): WorldInfoBook
    suspend fun saveWorldInfo(book: WorldInfoBook)
    suspend fun deleteWorldInfo(name: String)
    suspend fun uploadFile(name: String, base64Data: String): String
    suspend fun deleteFile(path: String)
    suspend fun listPersonas(): List<PersonaProfile>
    suspend fun savePersona(request: PersonaSaveRequest)
    suspend fun uploadPersonaAvatar(fileName: String, bytes: ByteArray, overwriteName: String? = null): String
    suspend fun deletePersona(avatar: String)
    suspend fun listSecrets(): List<SecretProviderState>
    suspend fun writeSecret(key: String, value: String, label: String): String
    suspend fun rotateSecret(key: String, id: String)
    suspend fun renameSecret(key: String, id: String, label: String)
    suspend fun deleteSecret(key: String, id: String? = null)
    suspend fun getSettings(): Map<String, Any?>
    suspend fun saveSettings(settings: Map<String, Any?>)
    suspend fun fetchModels(mode: String, sourceValue: String, apiServer: String = ""): List<String>
    suspend fun testConnection(mode: String, sourceValue: String, apiServer: String = ""): ConnectionTestResult
    suspend fun getPresetLibrary(): PresetLibrary
    suspend fun savePreset(apiId: String, name: String, presetJson: String)
    suspend fun selectPreset(apiId: String, name: String)
    suspend fun deletePreset(apiId: String, name: String)
    suspend fun restorePreset(apiId: String, name: String): String
    suspend fun listConnectionProfiles(): List<ConnectionProfile>
    suspend fun saveConnectionProfile(profile: ConnectionProfile)
    suspend fun listChatBackups(): List<ChatBackupSummary>
    suspend fun downloadChatBackup(name: String): CharacterExportFile
    suspend fun deleteChatBackup(name: String)
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
    suspend fun importCharacterChat(
        avatar: String,
        characterName: String,
        fileName: String,
        bytes: ByteArray
    ): List<String>
    suspend fun exportCharacterChat(
        avatar: String,
        chatFile: String,
        format: ChatExportFormat
    ): CharacterExportFile
    suspend fun listRecentChats(): List<ChatSummary>
    suspend fun listGroups(): List<GroupSummary>
    suspend fun createGroup(request: GroupCreateRequest): GroupSummary
    /** Overwrites a group's metadata (`POST /api/groups/edit`); sends the full object. */
    suspend fun editGroup(group: GroupSummary)
    /** Deletes a group by id (`POST /api/groups/delete`). */
    suspend fun deleteGroup(groupId: String)
    suspend fun sendMessage(chatId: String, text: String): Flow<GenerationChunk>
    suspend fun stopGeneration(chatId: String)
    /** Reads a group chat JSONL (`[header, ...messages]`) by its chat id; empty list if absent. */
    suspend fun getGroupChatJsonl(chatId: String): MutableList<Any?>
    /** Saves a group chat JSONL (`[header, ...messages]`) back to disk by its chat id. */
    suspend fun saveGroupChatJsonl(chatId: String, chat: List<Any?>)

    // --- Native generation pipeline (Chat Completion) ---
    /** Reads the raw chat JSONL as `[header, ...messages]`; empty list if the file does not exist. */
    suspend fun getChatJsonl(avatar: String, chatFile: String): MutableList<Any?>
    /** Saves the raw chat JSONL (`[header, ...messages]`) back to disk. */
    suspend fun saveChatJsonl(avatar: String, chatFile: String, chat: List<Any?>)
    /** Posts an already-assembled chat-completion payload and returns the assistant reply text. */
    suspend fun generateChatCompletion(payload: Map<String, Any?>): String
    /** Streams a chat-completion (SSE), emitting incremental text deltas. Forces `stream=true`. */
    fun generateChatCompletionStream(payload: Map<String, Any?>): Flow<String>
    /** Posts an already-assembled text-completion payload and returns the generated text. */
    suspend fun generateTextCompletion(payload: Map<String, Any?>): String
    /** Streams a text-completion (SSE), emitting incremental text deltas. Forces `stream=true`. */
    fun generateTextCompletionStream(payload: Map<String, Any?>): Flow<String>
}

class TavernCoreClient(
    baseUrl: String = "http://127.0.0.1:8000",
    private val httpClient: OkHttpClient = defaultHttpClient
) : TavernCoreApi {
    private val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    private val yaml = Yaml()
    private var csrfToken: String? = null

    // Generations (streaming or a slow non-stream reply) can run far longer than the
    // shared client's 15s call / 10s read timeouts allow, so derive a long-timeout client
    // (shares cookie jar + pool). No overall call timeout; a 120s read gap bounds hangs.
    private val generationHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

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

    override suspend fun listSettingsSnapshots(): List<SettingsSnapshot> {
        return withContext(Dispatchers.IO) {
            val body = postJson("api/settings/get-snapshots", "{}")
            val items = yaml.load<Any?>(body) as? List<*> ?: emptyList<Any?>()
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

    override suspend fun makeSettingsSnapshot() {
        withContext(Dispatchers.IO) {
            postJson("api/settings/make-snapshot", "{}")
        }
    }

    override suspend fun loadSettingsSnapshot(name: String): String {
        return withContext(Dispatchers.IO) {
            postJson(
                path = "api/settings/load-snapshot",
                json = jsonObject("name" to name)
            )
        }
    }

    override suspend fun restoreSettingsSnapshot(name: String) {
        withContext(Dispatchers.IO) {
            postJson(
                path = "api/settings/restore-snapshot",
                json = jsonObject("name" to name)
            )
        }
    }

    override suspend fun listWorldInfos(): List<WorldInfoSummary> {
        return withContext(Dispatchers.IO) {
            val body = postJson("api/worldinfo/list", "{}")
            val items = yaml.load<Any?>(body) as? List<*> ?: emptyList<Any?>()
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

    override suspend fun getWorldInfo(name: String): WorldInfoBook {
        return withContext(Dispatchers.IO) {
            val body = postJson(
                path = "api/worldinfo/get",
                json = jsonObject("name" to name)
            )
            val map = (yaml.load<Any?>(body) as? Map<*, *>)
                ?.toStringKeyMap()
                ?: emptyMap()
            map.toWorldInfoBook(fallbackName = name)
        }
    }

    override suspend fun saveWorldInfo(book: WorldInfoBook) {
        withContext(Dispatchers.IO) {
            postJson(
                path = "api/worldinfo/edit",
                json = jsonObject(
                    "name" to book.name,
                    "data" to book.toApiData()
                )
            )
        }
    }

    override suspend fun deleteWorldInfo(name: String) {
        withContext(Dispatchers.IO) {
            postJson(
                path = "api/worldinfo/delete",
                json = jsonObject("name" to name)
            )
        }
    }

    override suspend fun uploadFile(name: String, base64Data: String): String {
        return withContext(Dispatchers.IO) {
            val body = postJson(
                path = "api/files/upload",
                json = jsonObject("name" to name, "data" to base64Data)
            )
            val map = (yaml.load<Any?>(body) as? Map<*, *>) ?: emptyMap<Any?, Any?>()
            map.stringValue("path")
                .ifBlank { map.stringValue("url") }
                .ifBlank { map.stringValue("file") }
        }
    }

    override suspend fun deleteFile(path: String) {
        withContext(Dispatchers.IO) {
            postJson(
                path = "api/files/delete",
                json = jsonObject("path" to path)
            )
        }
    }

    override suspend fun listPersonas(): List<PersonaProfile> {
        return withContext(Dispatchers.IO) {
            val avatarBody = postJson("api/avatars/get", "{}")
            val avatars = (yaml.load<Any?>(avatarBody) as? List<*>)
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

    override suspend fun savePersona(request: PersonaSaveRequest) {
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

    override suspend fun uploadPersonaAvatar(fileName: String, bytes: ByteArray, overwriteName: String?): String {
        return withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("avatar", fileName, bytes.toRequestBody(binaryMediaType))
                .apply {
                    overwriteName?.takeIf { it.isNotBlank() }?.let { name ->
                        addFormDataPart("overwrite_name", name)
                    }
                }
                .build()
            val responseBody = postMultipart("api/avatars/upload", body)
            val map = yaml.load<Any?>(responseBody) as? Map<*, *> ?: emptyMap<Any, Any>()
            map.stringValue("path").ifBlank { overwriteName.orEmpty() }
        }
    }

    override suspend fun deletePersona(avatar: String) {
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

    override suspend fun listSecrets(): List<SecretProviderState> {
        return withContext(Dispatchers.IO) {
            val body = postJson("api/secrets/read", "{}")
            val map = yaml.load<Any?>(body) as? Map<*, *> ?: emptyMap<Any, Any>()
            val keys = (secretProviderLabels.keys + map.keys.map { it.toString() }).distinct()
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
                    label = secretProviderLabels[key] ?: key,
                    entries = entries
                )
            }.sortedBy { it.label }
        }
    }

    override suspend fun writeSecret(key: String, value: String, label: String): String {
        return withContext(Dispatchers.IO) {
            val body = postJson(
                path = "api/secrets/write",
                json = jsonObject(
                    "key" to key,
                    "value" to value,
                    "label" to label
                )
            )
            val map = yaml.load<Any?>(body) as? Map<*, *> ?: emptyMap<Any, Any>()
            map.stringValue("id")
        }
    }

    override suspend fun rotateSecret(key: String, id: String) {
        withContext(Dispatchers.IO) {
            postJson(
                path = "api/secrets/rotate",
                json = jsonObject("key" to key, "id" to id)
            )
        }
    }

    override suspend fun renameSecret(key: String, id: String, label: String) {
        withContext(Dispatchers.IO) {
            postJson(
                path = "api/secrets/rename",
                json = jsonObject("key" to key, "id" to id, "label" to label)
            )
        }
    }

    override suspend fun deleteSecret(key: String, id: String?) {
        withContext(Dispatchers.IO) {
            postJson(
                path = "api/secrets/delete",
                json = jsonObject("key" to key, "id" to id)
            )
        }
    }

    override suspend fun getSettings(): Map<String, Any?> {
        return withContext(Dispatchers.IO) {
            fetchSettings().settings
        }
    }

    override suspend fun saveSettings(settings: Map<String, Any?>) {
        withContext(Dispatchers.IO) {
            saveSettingsInternal(settings)
        }
    }

    override suspend fun fetchModels(mode: String, sourceValue: String, apiServer: String): List<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = postStatusCheck(mode, sourceValue, apiServer)
                parseModelList(body)
            }.getOrElse { emptyList() }
        }
    }

    override suspend fun testConnection(mode: String, sourceValue: String, apiServer: String): ConnectionTestResult {
        return withContext(Dispatchers.IO) {
            try {
                val body = postStatusCheck(mode, sourceValue, apiServer)
                val parsed = yaml.load<Any?>(body)
                val hasError = parsed is Map<*, *> && parsed["error"] == true
                if (hasError) {
                    val msg = (parsed as? Map<*, *>)?.get("message")?.toString()
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

    private fun postStatusCheck(mode: String, sourceValue: String, apiServer: String): String {
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

    private fun parseModelList(responseBody: String): List<String> {
        val parsed = yaml.load<Any?>(responseBody)
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

    override suspend fun getPresetLibrary(): PresetLibrary {
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

    override suspend fun savePreset(apiId: String, name: String, presetJson: String) {
        withContext(Dispatchers.IO) {
            val preset = yaml.load<Any?>(presetJson) ?: emptyMap<String, Any?>()
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

    override suspend fun selectPreset(apiId: String, name: String) {
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

    override suspend fun deletePreset(apiId: String, name: String) {
        withContext(Dispatchers.IO) {
            postJson(
                path = "api/presets/delete",
                json = jsonObject("apiId" to apiId, "name" to name)
            )
        }
    }

    override suspend fun restorePreset(apiId: String, name: String): String {
        return withContext(Dispatchers.IO) {
            postJson(
                path = "api/presets/restore",
                json = jsonObject("apiId" to apiId, "name" to name)
            )
        }
    }

    override suspend fun listConnectionProfiles(): List<ConnectionProfile> {
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

    override suspend fun saveConnectionProfile(profile: ConnectionProfile) {
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

    override suspend fun listChatBackups(): List<ChatBackupSummary> {
        return withContext(Dispatchers.IO) {
            val body = postJson("api/backups/chat/get", "{}")
            val items = yaml.load<Any?>(body) as? List<*> ?: emptyList<Any?>()
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

    override suspend fun downloadChatBackup(name: String): CharacterExportFile {
        return withContext(Dispatchers.IO) {
            postJsonFile(
                path = "api/backups/chat/download",
                json = jsonObject("name" to name),
                fallbackFileName = name
            )
        }
    }

    override suspend fun deleteChatBackup(name: String) {
        withContext(Dispatchers.IO) {
            postJson(
                path = "api/backups/chat/delete",
                json = jsonObject("name" to name)
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

    override suspend fun importCharacterChat(
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
                .addFormDataPart("avatar", fileName, bytes.toRequestBody(binaryMediaType))
                .addFormDataPart("file_type", fileType)
                .addFormDataPart("avatar_url", avatar)
                .addFormDataPart("character_name", characterName)
                .addFormDataPart("user_name", "User")
                .build()
            val responseBody = postMultipart("api/chats/import", body)
            val map = yaml.load<Any?>(responseBody) as? Map<*, *> ?: emptyMap<Any, Any>()
            if (map.booleanValue("error")) {
                throw IllegalStateException("SillyTavern failed to import chat")
            }
            map.stringListValue("fileNames")
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

    override suspend fun listGroups(): List<GroupSummary> {
        return withContext(Dispatchers.IO) {
            val body = postJson("api/groups/all", "{}")
            val items = yaml.load<Any?>(body) as? List<*> ?: emptyList<Any?>()
            items.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                map.toGroupSummary()
            }
        }
    }

    override suspend fun createGroup(request: GroupCreateRequest): GroupSummary {
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
            val map = yaml.load<Any?>(body) as? Map<*, *>
                ?: throw IllegalStateException("Invalid group response")
            map.toGroupSummary()
        }
    }

    override suspend fun editGroup(group: GroupSummary) {
        withContext(Dispatchers.IO) {
            val payload = linkedMapOf<String, Any?>(
                "id" to group.id,
                "name" to group.name,
                "members" to group.members,
                "avatar_url" to group.avatarUrl.ifBlank { "img/ai4.png" },
                "allow_self_responses" to group.allowSelfResponses,
                "activation_strategy" to group.activationStrategy,
                "generation_mode" to group.generationMode,
                "disabled_members" to group.disabledMembers,
                "fav" to group.isFavorite,
                "chat_id" to group.chatId.ifBlank { group.id },
                "chats" to group.chats.ifEmpty { listOf(group.chatId.ifBlank { group.id }) },
                "auto_mode_delay" to group.autoModeDelay,
                "generation_mode_join_prefix" to group.generationModeJoinPrefix,
                "generation_mode_join_suffix" to group.generationModeJoinSuffix
            )
            postJson(path = "api/groups/edit", json = jsonValue(payload))
        }
    }

    override suspend fun deleteGroup(groupId: String) {
        withContext(Dispatchers.IO) {
            postJson(path = "api/groups/delete", json = jsonObject("id" to groupId))
        }
    }

    override suspend fun sendMessage(chatId: String, text: String): Flow<GenerationChunk> {
        // TODO: POST /api/chats/{chatId}/messages with SSE streaming
        return kotlinx.coroutines.flow.emptyFlow()
    }

    override suspend fun stopGeneration(chatId: String) {
        // TODO: POST /api/chats/{chatId}/stop
    }

    override suspend fun getChatJsonl(avatar: String, chatFile: String): MutableList<Any?> =
        withContext(Dispatchers.IO) {
            val body = postJson(
                "api/chats/get",
                jsonObject(
                    "avatar_url" to avatar,
                    "file_name" to chatFile.removeSuffix(".jsonl")
                )
            )
            // Found chats return a JSON array [header, ...messages]; a missing file returns {}.
            when (val parsed = yaml.load<Any?>(body)) {
                is List<*> -> parsed.map { normalizeJsonValue(it) }.toMutableList()
                else -> mutableListOf()
            }
        }

    override suspend fun saveChatJsonl(avatar: String, chatFile: String, chat: List<Any?>) {
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

    override suspend fun getGroupChatJsonl(chatId: String): MutableList<Any?> =
        withContext(Dispatchers.IO) {
            val body = postJson(
                "api/chats/group/get",
                jsonObject("id" to chatId.removeSuffix(".jsonl"))
            )
            // Found chats return a JSON array [header, ...messages]; a missing file returns {} or [].
            when (val parsed = yaml.load<Any?>(body)) {
                is List<*> -> parsed.map { normalizeJsonValue(it) }.toMutableList()
                else -> mutableListOf()
            }
        }

    override suspend fun saveGroupChatJsonl(chatId: String, chat: List<Any?>) {
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

    override suspend fun generateChatCompletion(payload: Map<String, Any?>): String =
        withContext(Dispatchers.IO) {
            val body = postJsonForGeneration("api/backends/chat-completions/generate", jsonValue(payload))
            val map = yaml.load<Any?>(body) as? Map<*, *>
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

    override fun generateChatCompletionStream(payload: Map<String, Any?>): Flow<String> = callbackFlow {
        val streamPayload = payload.toMutableMap().apply { put("stream", true) }
        val builder = Request.Builder()
            .url(normalizedBaseUrl + "api/backends/chat-completions/generate")
            .header("Accept", "text/event-stream")
            .post(jsonValue(streamPayload).toRequestBody(jsonMediaType))
        csrfToken().takeIf { it.isNotBlank() }?.let { builder.header("x-csrf-token", it) }
        val call = generationHttpClient.newCall(builder.build())

        val worker = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val err = response.body?.string().orEmpty()
                        throw IllegalStateException("SillyTavern API ${response.code}: $err")
                    }
                    val source = response.body?.source() ?: throw IllegalStateException("生成响应为空")
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

    override suspend fun generateTextCompletion(payload: Map<String, Any?>): String =
        withContext(Dispatchers.IO) {
            val body = postJsonForGeneration("api/backends/text-completions/generate", jsonValue(payload))
            extractTextCompletionResponse(body)
        }

    private fun extractTextCompletionResponse(body: String): String {
        val map = yaml.load<Any?>(body) as? Map<*, *>
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

    override fun generateTextCompletionStream(payload: Map<String, Any?>): Flow<String> = callbackFlow {
        val streamPayload = payload.toMutableMap().apply { put("stream", true) }
        val builder = Request.Builder()
            .url(normalizedBaseUrl + "api/backends/text-completions/generate")
            .header("Accept", "text/event-stream")
            .post(jsonValue(streamPayload).toRequestBody(jsonMediaType))
        csrfToken().takeIf { it.isNotBlank() }?.let { builder.header("x-csrf-token", it) }
        val call = generationHttpClient.newCall(builder.build())

        val worker = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val err = response.body?.string().orEmpty()
                        throw IllegalStateException("SillyTavern API ${response.code}: $err")
                    }
                    val source = response.body?.source() ?: throw IllegalStateException("生成响应为空")
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

    private companion object {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val binaryMediaType = "application/octet-stream".toMediaType()
        val secretProviderLabels = linkedMapOf(
            "api_key_openai" to "OpenAI",
            "api_key_openrouter" to "OpenRouter",
            "api_key_makersuite" to "Google AI Studio",
            "api_key_vertexai" to "Google Vertex AI",
            "api_key_custom" to "OpenAI-compatible",
            "api_key_horde" to "AI Horde",
            "api_key_koboldcpp" to "KoboldCpp",
            "api_key_ooba" to "Text Generation WebUI",
            "api_key_claude" to "Claude",
            "api_key_mistralai" to "Mistral",
            "api_key_deepseek" to "DeepSeek",
            "api_key_xai" to "xAI",
            "api_key_cohere" to "Cohere",
            "api_key_perplexity" to "Perplexity",
            "api_key_tabby" to "TabbyAPI",
            "api_key_aphrodite" to "Aphrodite",
            "api_key_mancer" to "Mancer",
            "api_key_featherless" to "Featherless",
            "api_key_llamacpp" to "llama.cpp",
            "api_key_novel" to "NovelAI",
            "api_key_generic" to "Generic"
        )

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

    /** Like [postJson] but uses the long-timeout generation client (slow model replies). */
    private fun postJsonForGeneration(path: String, json: String): String {
        val builder = Request.Builder()
            .url(normalizedBaseUrl + path.removePrefix("/"))
            .post(json.toRequestBody(jsonMediaType))
        csrfToken().takeIf { it.isNotBlank() }?.let { token ->
            builder.header("x-csrf-token", token)
        }
        generationHttpClient.newCall(builder.build()).execute().use { response ->
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

    private data class SettingsPayload(
        val response: Map<String, Any?>,
        val settings: Map<String, Any?>
    )

    private fun fetchSettings(): SettingsPayload {
        val body = postJson("api/settings/get", "{}")
        val response = (yaml.load<Any?>(body) as? Map<*, *>)
            ?.toStringKeyMap()
            ?: emptyMap()
        val settings = (yaml.load<Any?>(response.stringAnyValue("settings")) as? Map<*, *>)
            ?.toStringKeyMap()
            ?: emptyMap()
        return SettingsPayload(response = response, settings = settings)
    }

    private fun saveSettingsInternal(settings: Map<String, Any?>) {
        postJson(
            path = "api/settings/save",
            json = jsonValue(settings)
        )
    }

    private fun Map<String, Any?>.toWorldInfoBook(fallbackName: String): WorldInfoBook {
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

    private fun WorldInfoBook.toApiData(): Map<String, Any?> {
        val data = rawData.toMutableMap()
        data["name"] = name
        data["entries"] = entries.associate { entry ->
            entry.uid.toString() to entry.toApiData()
        }
        return data
    }

    private fun WorldInfoEntry.toApiData(): Map<String, Any?> {
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

    private fun pairedPresetCategory(
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

    private fun objectPresetCategory(apiId: String, title: String, value: Any?, selectedName: String = ""): PresetCategory {
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
            sourceUrl = extensions.characterSourceUrl()
        )
    }

    private fun Map<*, *>.characterSourceUrl(): String {
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

    private fun Map<*, *>.toGroupSummary(): GroupSummary {
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
                addFormDataPart("extensions", jsonObject("source_url" to sourceUrl))
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

    private fun Map<String, Any?>.longAnyValue(key: String): Long =
        when (val value = get(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
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
