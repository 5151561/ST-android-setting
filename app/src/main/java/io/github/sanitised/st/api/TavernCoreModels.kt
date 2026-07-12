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

data class StUserView(
    val handle: String,
    val name: String,
    val avatar: String? = null,
    val hasPassword: Boolean = false,
    val created: Long = 0L,
)

data class StCurrentUser(
    val handle: String,
    val name: String,
    val admin: Boolean = false,
    val created: Long = 0L,
    val avatar: String? = null,
)

data class WorldInfoBook(
    val name: String,
    val entries: List<WorldInfoEntry> = emptyList(),
    val rawData: Map<String, Any?> = emptyMap(),
    // 文件标识（无扩展名的文件名 = /api/worldinfo/list 的 file_id）。后端 get/edit/delete
    // 都按这个值定位文件；name 仅是 JSON 内的显示名，二者可能不同。
    val fileId: String = ""
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
    val generationModeJoinSuffix: String = "",
    // 服务端 /api/groups/all 不返回消息内容,由本地 group chats/*.jsonl 补齐
    val lastMessage: String? = null
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
    suspend fun listBackgrounds(): List<String>
    suspend fun deleteBackground(bg: String)
    suspend fun renameBackground(oldBg: String, newBg: String)
    suspend fun listUsers(): List<StUserView>
    suspend fun loginUser(handle: String, password: String): String
    suspend fun recoverPasswordStep1(handle: String)
    suspend fun recoverPasswordStep2(handle: String, code: String, newPassword: String)
    suspend fun getCurrentUser(): StCurrentUser
    suspend fun changeUserPassword(handle: String, oldPassword: String, newPassword: String)
    suspend fun logoutUser()
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
