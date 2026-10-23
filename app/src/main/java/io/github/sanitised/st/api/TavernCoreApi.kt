package io.github.sanitised.st.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class TavernCoreClient(
    baseUrl: String = "http://127.0.0.1:8000",
    internal val httpClient: OkHttpClient = defaultHttpClient
) : TavernCoreApi {
    internal val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    internal var cachedCsrfToken: String? = null

    // Generations (streaming or a slow non-stream reply) can run far longer than the
    // shared client's 15s call / 10s read timeouts allow, so derive a long-timeout client
    // (shares cookie jar + pool). No overall call timeout; a 120s read gap bounds hangs.
    internal val generationHttpClient: OkHttpClient by lazy {
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

    override suspend fun listCharacters(): List<CharacterSummary> = listCharactersImpl()

    override suspend fun getCharacter(avatar: String): CharacterDetail = getCharacterImpl(avatar)

    override suspend fun createCharacter(
        request: CharacterSaveRequest,
        avatarUpload: CharacterUpload?
    ): String = createCharacterImpl(request, avatarUpload)

    override suspend fun updateCharacter(
        request: CharacterSaveRequest,
        avatarUpload: CharacterUpload?
    ) = updateCharacterImpl(request, avatarUpload)

    override suspend fun mergeCharacterAttributes(
        avatar: String,
        isFavorite: Boolean?,
        embeddedTags: List<String>?
    ) = mergeCharacterAttributesImpl(avatar, isFavorite, embeddedTags)

    override suspend fun getTagSettings(): STTagSettings = getTagSettingsImpl()

    override suspend fun saveTagSettings(settings: STTagSettings) = saveTagSettingsImpl(settings)

    override suspend fun listSettingsSnapshots(): List<SettingsSnapshot> = listSettingsSnapshotsImpl()

    override suspend fun makeSettingsSnapshot() = makeSettingsSnapshotImpl()

    override suspend fun loadSettingsSnapshot(name: String): String = loadSettingsSnapshotImpl(name)

    override suspend fun restoreSettingsSnapshot(name: String) = restoreSettingsSnapshotImpl(name)

    override suspend fun listWorldInfos(): List<WorldInfoSummary> = listWorldInfosImpl()

    override suspend fun getWorldInfo(name: String): WorldInfoBook = getWorldInfoImpl(name)

    override suspend fun saveWorldInfo(book: WorldInfoBook) = saveWorldInfoImpl(book)

    override suspend fun deleteWorldInfo(name: String) = deleteWorldInfoImpl(name)

    override suspend fun uploadFile(name: String, base64Data: String): String =
        uploadFileImpl(name, base64Data)

    override suspend fun deleteFile(path: String) = deleteFileImpl(path)

    override suspend fun listBackgrounds(): List<String> = listBackgroundsImpl()

    override suspend fun deleteBackground(bg: String) = deleteBackgroundImpl(bg)

    override suspend fun renameBackground(oldBg: String, newBg: String) =
        renameBackgroundImpl(oldBg, newBg)

    override suspend fun listUsers(): List<StUserView> = listUsersImpl()

    override suspend fun loginUser(handle: String, password: String): String =
        loginUserImpl(handle, password)

    override suspend fun recoverPasswordStep1(handle: String) = recoverPasswordStep1Impl(handle)

    override suspend fun recoverPasswordStep2(
        handle: String,
        code: String,
        newPassword: String
    ) = recoverPasswordStep2Impl(handle, code, newPassword)

    override suspend fun getCurrentUser(): StCurrentUser = getCurrentUserImpl()

    override suspend fun changeUserPassword(
        handle: String,
        oldPassword: String,
        newPassword: String
    ) = changeUserPasswordImpl(handle, oldPassword, newPassword)

    override suspend fun logoutUser() = logoutUserImpl()

    override suspend fun listPersonas(): List<PersonaProfile> = listPersonasImpl()

    override suspend fun savePersona(request: PersonaSaveRequest) = savePersonaImpl(request)

    override suspend fun uploadPersonaAvatar(
        fileName: String,
        bytes: ByteArray,
        overwriteName: String?
    ): String = uploadPersonaAvatarImpl(fileName, bytes, overwriteName)

    override suspend fun deletePersona(avatar: String) = deletePersonaImpl(avatar)

    override suspend fun listSecrets(): List<SecretProviderState> = listSecretsImpl()

    override suspend fun writeSecret(key: String, value: String, label: String): String =
        writeSecretImpl(key, value, label)

    override suspend fun rotateSecret(key: String, id: String) = rotateSecretImpl(key, id)

    override suspend fun renameSecret(key: String, id: String, label: String) =
        renameSecretImpl(key, id, label)

    override suspend fun deleteSecret(key: String, id: String?) = deleteSecretImpl(key, id)

    override suspend fun getSettings(): Map<String, Any?> = getSettingsImpl()

    override suspend fun saveSettings(settings: Map<String, Any?>) = saveSettingsImpl(settings)

    override suspend fun fetchModels(
        mode: String,
        sourceValue: String,
        apiServer: String
    ): List<String> = fetchModelsImpl(mode, sourceValue, apiServer)

    override suspend fun testConnection(
        mode: String,
        sourceValue: String,
        apiServer: String
    ): ConnectionTestResult = testConnectionImpl(mode, sourceValue, apiServer)

    override suspend fun getPresetLibrary(): PresetLibrary = getPresetLibraryImpl()

    override suspend fun savePreset(apiId: String, name: String, presetJson: String) =
        savePresetImpl(apiId, name, presetJson)

    override suspend fun selectPreset(apiId: String, name: String) = selectPresetImpl(apiId, name)

    override suspend fun deletePreset(apiId: String, name: String) = deletePresetImpl(apiId, name)

    override suspend fun restorePreset(apiId: String, name: String): String =
        restorePresetImpl(apiId, name)

    override suspend fun listConnectionProfiles(): List<ConnectionProfile> =
        listConnectionProfilesImpl()

    override suspend fun saveConnectionProfile(profile: ConnectionProfile) =
        saveConnectionProfileImpl(profile)

    override suspend fun listChatBackups(): List<ChatBackupSummary> = listChatBackupsImpl()

    override suspend fun downloadChatBackup(name: String): CharacterExportFile =
        downloadChatBackupImpl(name)

    override suspend fun deleteChatBackup(name: String) = deleteChatBackupImpl(name)

    override suspend fun renameCharacter(avatar: String, newName: String): String =
        renameCharacterImpl(avatar, newName)

    override suspend fun duplicateCharacter(avatar: String): String =
        duplicateCharacterImpl(avatar)

    override suspend fun deleteCharacter(avatar: String, deleteChats: Boolean) =
        deleteCharacterImpl(avatar, deleteChats)

    override suspend fun listCharacterChats(avatar: String): List<CharacterChatSummary> =
        listCharacterChatsImpl(avatar)

    override suspend fun importCharacter(
        fileName: String,
        bytes: ByteArray,
        preservedName: String?
    ): String = importCharacterImpl(fileName, bytes, preservedName)

    override suspend fun importExternalCharacter(
        urlOrUuid: String,
        preservedName: String?
    ): String = importExternalCharacterImpl(urlOrUuid, preservedName)

    override suspend fun exportCharacter(
        avatar: String,
        format: CharacterExportFormat
    ): CharacterExportFile = exportCharacterImpl(avatar, format)

    override suspend fun updateCharacterAvatar(
        avatar: String,
        fileName: String,
        bytes: ByteArray
    ) = updateCharacterAvatarImpl(avatar, fileName, bytes)

    override suspend fun renameCharacterChat(
        avatar: String,
        originalFile: String,
        renamedFile: String
    ): String = renameCharacterChatImpl(avatar, originalFile, renamedFile)

    override suspend fun deleteCharacterChat(avatar: String, chatFile: String) =
        deleteCharacterChatImpl(avatar, chatFile)

    override suspend fun importCharacterChat(
        avatar: String,
        characterName: String,
        fileName: String,
        bytes: ByteArray
    ): List<String> = importCharacterChatImpl(avatar, characterName, fileName, bytes)

    override suspend fun exportCharacterChat(
        avatar: String,
        chatFile: String,
        format: ChatExportFormat
    ): CharacterExportFile = exportCharacterChatImpl(avatar, chatFile, format)

    override suspend fun listRecentChats(): List<ChatSummary> {
        // TODO: GET /api/chats
        return emptyList()
    }

    override suspend fun listGroups(): List<GroupSummary> = listGroupsImpl()

    override suspend fun createGroup(request: GroupCreateRequest): GroupSummary =
        createGroupImpl(request)

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
        // TODO: POST /api/chats/{chatId}/messages with SSE streaming ( also supports sendGroupMessage( )
        return kotlinx.coroutines.flow.emptyFlow()
    }

    override suspend fun stopGeneration(chatId: String) {
        // TODO: POST /api/chats/{chatId}/stop
    }

    override suspend fun getChatJsonl(avatar: String, chatFile: String): MutableList<Any?> =
        getChatJsonlImpl(avatar, chatFile)

    override suspend fun saveChatJsonl(avatar: String, chatFile: String, chat: List<Any?>) =
        saveChatJsonlImpl(avatar, chatFile, chat)

    override suspend fun getGroupChatJsonl(chatId: String): MutableList<Any?> =
        getGroupChatJsonlImpl(chatId)

    override suspend fun saveGroupChatJsonl(chatId: String, chat: List<Any?>) =
        saveGroupChatJsonlImpl(chatId, chat)

    override suspend fun generateChatCompletion(payload: Map<String, Any?>): String =
        generateChatCompletionImpl(payload)

    override fun generateChatCompletionStream(payload: Map<String, Any?>): Flow<String> =
        generateChatCompletionStreamImpl(payload)

    override suspend fun generateTextCompletion(payload: Map<String, Any?>): String =
        generateTextCompletionImpl(payload)

    override fun generateTextCompletionStream(payload: Map<String, Any?>): Flow<String> =
        generateTextCompletionStreamImpl(payload)

    internal companion object {
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
            "api_key_groq" to "Groq",
            "api_key_moonshot" to "Moonshot (Kimi)",
            "api_key_siliconflow" to "SiliconFlow (硅基流动)",
            "api_key_zai" to "Z.AI (智谱 GLM)",
            "api_key_minimax" to "MiniMax",
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

        private val sharedCookieJar = InMemoryCookieJar()

        val defaultHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(750, TimeUnit.MILLISECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .cookieJar(sharedCookieJar)
            .followRedirects(true)
            .build()

        /** 清空共享会话 cookie（退出登录后调用，避免沿用旧账户会话）。 */
        fun clearSessionCookies() = sharedCookieJar.clear()

        fun normalizeBaseUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim()
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}
