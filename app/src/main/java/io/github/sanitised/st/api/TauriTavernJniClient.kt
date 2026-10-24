package io.github.sanitised.st.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * Pure-JNI implementation of [TavernCoreApi] backed by [TauriTavernJniBridge].
 *
 * Replaces all localhost HTTP/SSE calls to `127.0.0.1:8088` with direct JNI envelopes
 * (`{"ok":true,"data":...}` / `{"ok":false,"error":{"code":...,"message":...}}`).
 */
class TauriTavernJniClient(
    internal val bridgeInvoker: ((command: String, payloadJson: String) -> String)? = null
) : TavernCoreApi {

    companion object {
        val instance: TauriTavernJniClient by lazy { TauriTavernJniClient() }
    }

    private fun invokeBridgeJson(command: String, payloadJson: String): String {
        bridgeInvoker?.let { return it(command, payloadJson) }
        if (command == "ping") {
            val ok = TauriTavernJniBridge.driverOverride != null || TauriTavernJniBridge.isInitialized()
            return jsonObject(
                "ok" to ok,
                "data" to mapOf(
                    "status" to if (ok) "ok" else "uninitialized",
                    "healthy" to ok,
                    "version" to "TauriTavern"
                )
            )
        }
        val override = TauriTavernJniBridge.driverOverride
        if (override != null) {
            return override.invokeJson(command, payloadJson)
        }
        TauriTavernJniBridge.ensureLibraryLoaded()
        return TauriTavernJniBridge.nativeInvokeJson(command, payloadJson)
    }

    internal suspend fun invokeRaw(
        command: String,
        payload: Any = emptyMap<String, Any?>()
    ): Any? = withContext(Dispatchers.IO) {
        val payloadJson = when (payload) {
            is String -> payload
            else -> jsonValue(payload)
        }
        val rawResponse = invokeBridgeJson(command, payloadJson)
        unwrapEnvelope(command, rawResponse)
    }

    internal suspend fun invokeBinaryRaw(
        command: String,
        meta: Map<String, Any?> = emptyMap(),
        bytes: ByteArray
    ): Any? = withContext(Dispatchers.IO) {
        val metaJson = jsonValue(meta)
        val rawResponse = if (bridgeInvoker != null) {
            val fallbackPayload = meta.toMutableMap().apply {
                put("bytes", bytes.map { it.toInt() and 0xFF })
            }
            bridgeInvoker.invoke(command, jsonValue(fallbackPayload))
        } else {
            val override = TauriTavernJniBridge.driverOverride
            if (override != null) {
                override.invokeBinary(command, metaJson, bytes)
            } else {
                TauriTavernJniBridge.ensureLibraryLoaded()
                TauriTavernJniBridge.nativeInvokeBinary(command, metaJson, bytes)
            }
        }
        unwrapEnvelope(command, rawResponse)
    }

    internal suspend fun invokeExportCharacterBytes(
        meta: Map<String, Any?>
    ): ByteArray? = withContext(Dispatchers.IO) {
        if (bridgeInvoker != null) {
            return@withContext null
        }
        val metaJson = jsonValue(meta)
        val override = TauriTavernJniBridge.driverOverride
        if (override != null) {
            override.exportCharacter(metaJson)
        } else {
            TauriTavernJniBridge.ensureLibraryLoaded()
            TauriTavernJniBridge.nativeExportCharacter(metaJson)
        }
    }

    internal suspend fun invokeStartChatStream(
        payload: Map<String, Any?>
    ): Any? = withContext(Dispatchers.IO) {
        val requestJson = jsonValue(mapOf("request" to payload) + payload)
        val rawResponse = if (bridgeInvoker != null) {
            bridgeInvoker.invoke("chat_completion_stream_start", jsonValue(mapOf("request" to payload)))
        } else {
            val override = TauriTavernJniBridge.driverOverride
            if (override != null) {
                override.startChatStream(requestJson)
            } else {
                TauriTavernJniBridge.ensureLibraryLoaded()
                TauriTavernJniBridge.nativeStartChatStream(requestJson)
            }
        }
        unwrapEnvelope("chat_completion_stream_start", rawResponse)
    }

    internal suspend fun invokeReadChatStream(
        streamId: String,
        afterSeq: Long
    ): Map<String, Any?> = withContext(Dispatchers.IO) {
        val rawResponse = if (bridgeInvoker != null) {
            val payloadJson = jsonValue(
                mapOf(
                    "streamId" to streamId,
                    "stream_id" to streamId,
                    "afterSeq" to afterSeq,
                    "after_seq" to afterSeq
                )
            )
            bridgeInvoker.invoke("chat_completion_stream_read", payloadJson)
        } else {
            val override = TauriTavernJniBridge.driverOverride
            if (override != null) {
                override.readChatStream(streamId)
            } else {
                TauriTavernJniBridge.ensureLibraryLoaded()
                TauriTavernJniBridge.nativeReadChatStream(streamId)
            }
        }
        val data = unwrapEnvelope("chat_completion_stream_read", rawResponse)
        (data as? Map<*, *>)?.toStringKeyMap() ?: emptyMap()
    }

    internal suspend fun invokeCloseChatStream(
        streamId: String
    ): Any? = withContext(Dispatchers.IO) {
        val rawResponse = if (bridgeInvoker != null) {
            val payloadJson = jsonValue(mapOf("streamId" to streamId, "stream_id" to streamId))
            bridgeInvoker.invoke("chat_completion_stream_cancel", payloadJson)
        } else {
            val override = TauriTavernJniBridge.driverOverride
            if (override != null) {
                override.closeChatStream(streamId)
            } else {
                TauriTavernJniBridge.ensureLibraryLoaded()
                TauriTavernJniBridge.nativeCloseChatStream(streamId)
            }
        }
        unwrapEnvelope("chat_completion_stream_cancel", rawResponse)
    }

    internal fun unwrapEnvelope(command: String, rawResponse: String): Any? {
        if (rawResponse.isBlank()) {
            throw IllegalStateException("Empty JNI response for command '$command'")
        }
        val root = try {
            StJson.parse(rawResponse)
        } catch (e: Exception) {
            throw IllegalStateException("Malformed JNI JSON for '$command': ${e.message}", e)
        }
        if (root !is Map<*, *>) {
            return root
        }
        val okField = root["ok"]
        if (okField is Boolean && !okField) {
            val errElem = root["error"]
            val message = when (errElem) {
                null -> "JNI command '$command' failed"
                is Map<*, *> -> {
                    val code = errElem["code"]?.toString()?.takeIf { it.isNotBlank() }
                    val msg = errElem["message"]?.toString()?.takeIf { it.isNotBlank() }
                        ?: errElem.toString()
                    if (!code.isNullOrBlank()) "[$code] $msg" else msg
                }
                else -> errElem.toString()
            }
            throw IllegalStateException(message)
        }
        if (okField is Boolean && okField && root.containsKey("data")) {
            return root["data"]
        }
        return root
    }

    internal suspend fun invokeAsMap(
        command: String,
        payload: Any = emptyMap<String, Any?>()
    ): Map<String, Any?> {
        val data = invokeRaw(command, payload)
        return (data as? Map<*, *>)?.toStringKeyMap() ?: emptyMap()
    }

    internal suspend fun invokeAsListOfMaps(
        command: String,
        payload: Any = emptyMap<String, Any?>()
    ): List<Map<String, Any?>> {
        val data = invokeRaw(command, payload)
        val rawList = when (data) {
            is List<*> -> data
            is Map<*, *> -> data.values.firstOrNull { it is List<*> } as? List<*> ?: emptyList<Any?>()
            else -> emptyList<Any?>()
        }
        return rawList.mapNotNull { (it as? Map<*, *>)?.toStringKeyMap() }
    }

    internal suspend fun invokeAsListOfStrings(
        command: String,
        payload: Any = emptyMap<String, Any?>()
    ): List<String> {
        val data = invokeRaw(command, payload)
        val rawList = when (data) {
            is List<*> -> data
            is Map<*, *> -> data.values.firstOrNull { it is List<*> } as? List<*> ?: emptyList<Any?>()
            else -> emptyList<Any?>()
        }
        return rawList.mapNotNull { item ->
            when (item) {
                is String -> item.takeIf { it.isNotBlank() }
                is Map<*, *> -> item.stringValue("filename")
                    .ifBlank { item.stringValue("name") }
                    .ifBlank { item.stringValue("id") }
                    .takeIf { it.isNotBlank() }
                else -> item?.toString()?.takeIf { it.isNotBlank() }
            }
        }
    }

    internal fun encodeBase64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    internal fun decodeBase64(encoded: String): ByteArray =
        runCatching { Base64.getDecoder().decode(encoded.trim()) }.getOrDefault(ByteArray(0))

    override suspend fun healthCheck(): CoreHealth = doHealthCheck()
    override suspend fun listCharacters(): List<CharacterSummary> = doListCharacters()
    override suspend fun getCharacter(avatar: String): CharacterDetail = doGetCharacter(avatar)
    override suspend fun createCharacter(
        request: CharacterSaveRequest,
        avatarUpload: CharacterUpload?
    ): String = doCreateCharacter(request, avatarUpload)
    override suspend fun updateCharacter(
        request: CharacterSaveRequest,
        avatarUpload: CharacterUpload?
    ) = doUpdateCharacter(request, avatarUpload)
    override suspend fun mergeCharacterAttributes(
        avatar: String,
        isFavorite: Boolean?,
        embeddedTags: List<String>?
    ) = doMergeCharacterAttributes(avatar, isFavorite, embeddedTags)
    override suspend fun getTagSettings(): STTagSettings = doGetTagSettings()
    override suspend fun saveTagSettings(settings: STTagSettings) = doSaveTagSettings(settings)
    override suspend fun listSettingsSnapshots(): List<SettingsSnapshot> = doListSettingsSnapshots()
    override suspend fun makeSettingsSnapshot() = doMakeSettingsSnapshot()
    override suspend fun loadSettingsSnapshot(name: String): String = doLoadSettingsSnapshot(name)
    override suspend fun restoreSettingsSnapshot(name: String) = doRestoreSettingsSnapshot(name)
    override suspend fun listWorldInfos(): List<WorldInfoSummary> = doListWorldInfos()
    override suspend fun getWorldInfo(name: String): WorldInfoBook = doGetWorldInfo(name)
    override suspend fun saveWorldInfo(book: WorldInfoBook) = doSaveWorldInfo(book)
    override suspend fun deleteWorldInfo(name: String) = doDeleteWorldInfo(name)
    override suspend fun uploadFile(name: String, base64Data: String): String =
        doUploadFile(name, base64Data)
    override suspend fun deleteFile(path: String) = doDeleteFile(path)
    override suspend fun listBackgrounds(): List<String> = doListBackgrounds()
    override suspend fun deleteBackground(bg: String) = doDeleteBackground(bg)
    override suspend fun renameBackground(oldBg: String, newBg: String) =
        doRenameBackground(oldBg, newBg)
    override suspend fun listUsers(): List<StUserView> = doListUsers()
    override suspend fun loginUser(handle: String, password: String): String =
        doLoginUser(handle, password)
    override suspend fun recoverPasswordStep1(handle: String) = doRecoverPasswordStep1(handle)
    override suspend fun recoverPasswordStep2(handle: String, code: String, newPassword: String) =
        doRecoverPasswordStep2(handle, code, newPassword)
    override suspend fun getCurrentUser(): StCurrentUser = doGetCurrentUser()
    override suspend fun changeUserPassword(
        handle: String,
        oldPassword: String,
        newPassword: String
    ) = doChangeUserPassword(handle, oldPassword, newPassword)
    override suspend fun logoutUser() = doLogoutUser()
    override suspend fun listPersonas(): List<PersonaProfile> = doListPersonas()
    override suspend fun savePersona(request: PersonaSaveRequest) = doSavePersona(request)
    override suspend fun uploadPersonaAvatar(
        fileName: String,
        bytes: ByteArray,
        overwriteName: String?
    ): String = doUploadPersonaAvatar(fileName, bytes, overwriteName)
    override suspend fun deletePersona(avatar: String) = doDeletePersona(avatar)
    override suspend fun listSecrets(): List<SecretProviderState> = doListSecrets()
    override suspend fun writeSecret(key: String, value: String, label: String): String =
        doWriteSecret(key, value, label)
    override suspend fun rotateSecret(key: String, id: String) = doRotateSecret(key, id)
    override suspend fun renameSecret(key: String, id: String, label: String) =
        doRenameSecret(key, id, label)
    override suspend fun deleteSecret(key: String, id: String?) = doDeleteSecret(key, id)
    override suspend fun getSettings(): Map<String, Any?> = doGetSettings()
    override suspend fun saveSettings(settings: Map<String, Any?>) = doSaveSettings(settings)
    override suspend fun fetchModels(
        mode: String,
        sourceValue: String,
        apiServer: String
    ): List<String> = doFetchModels(mode, sourceValue, apiServer)
    override suspend fun testConnection(
        mode: String,
        sourceValue: String,
        apiServer: String
    ): ConnectionTestResult = doTestConnection(mode, sourceValue, apiServer)
    override suspend fun getPresetLibrary(): PresetLibrary = doGetPresetLibrary()
    override suspend fun savePreset(apiId: String, name: String, presetJson: String) =
        doSavePreset(apiId, name, presetJson)
    override suspend fun selectPreset(apiId: String, name: String) = doSelectPreset(apiId, name)
    override suspend fun deletePreset(apiId: String, name: String) = doDeletePreset(apiId, name)
    override suspend fun restorePreset(apiId: String, name: String): String =
        doRestorePreset(apiId, name)
    override suspend fun listConnectionProfiles(): List<ConnectionProfile> =
        doListConnectionProfiles()
    override suspend fun saveConnectionProfile(profile: ConnectionProfile) =
        doSaveConnectionProfile(profile)
    override suspend fun deleteConnectionProfile(name: String) =
        doDeleteConnectionProfile(name)
    override suspend fun applyConnectionProfile(name: String): Boolean =
        doApplyConnectionProfile(name)
    override suspend fun listChatBackups(): List<ChatBackupSummary> = doListChatBackups()
    override suspend fun downloadChatBackup(name: String): CharacterExportFile =
        doDownloadChatBackup(name)
    override suspend fun deleteChatBackup(name: String) = doDeleteChatBackup(name)
    override suspend fun renameCharacter(avatar: String, newName: String): String =
        doRenameCharacter(avatar, newName)
    override suspend fun duplicateCharacter(avatar: String): String =
        doDuplicateCharacter(avatar)
    override suspend fun deleteCharacter(avatar: String, deleteChats: Boolean) =
        doDeleteCharacter(avatar, deleteChats)
    override suspend fun listCharacterChats(avatar: String): List<CharacterChatSummary> =
        doListCharacterChats(avatar)
    override suspend fun importCharacter(
        fileName: String,
        bytes: ByteArray,
        preservedName: String?
    ): String = doImportCharacter(fileName, bytes, preservedName)
    override suspend fun importExternalCharacter(
        urlOrUuid: String,
        preservedName: String?
    ): String = doImportExternalCharacter(urlOrUuid, preservedName)
    override suspend fun exportCharacter(
        avatar: String,
        format: CharacterExportFormat
    ): CharacterExportFile = doExportCharacter(avatar, format)
    override suspend fun updateCharacterAvatar(
        avatar: String,
        fileName: String,
        bytes: ByteArray
    ) = doUpdateCharacterAvatar(avatar, fileName, bytes)
    override suspend fun renameCharacterChat(
        avatar: String,
        originalFile: String,
        renamedFile: String
    ): String = doRenameCharacterChat(avatar, originalFile, renamedFile)
    override suspend fun deleteCharacterChat(avatar: String, chatFile: String) =
        doDeleteCharacterChat(avatar, chatFile)
    override suspend fun importCharacterChat(
        avatar: String,
        characterName: String,
        fileName: String,
        bytes: ByteArray
    ): List<String> = doImportCharacterChat(avatar, characterName, fileName, bytes)
    override suspend fun exportCharacterChat(
        avatar: String,
        chatFile: String,
        format: ChatExportFormat
    ): CharacterExportFile = doExportCharacterChat(avatar, chatFile, format)
    override suspend fun listRecentChats(): List<ChatSummary> = doListRecentChats()
    override suspend fun listGroups(): List<GroupSummary> = doListGroups()
    override suspend fun createGroup(request: GroupCreateRequest): GroupSummary =
        doCreateGroup(request)
    override suspend fun editGroup(group: GroupSummary) = doEditGroup(group)
    override suspend fun deleteGroup(groupId: String) = doDeleteGroup(groupId)
    override suspend fun sendMessage(chatId: String, text: String): Flow<GenerationChunk> =
        doSendMessage(chatId, text)
    override suspend fun stopGeneration(chatId: String) = doStopGeneration(chatId)
    override suspend fun getGroupChatJsonl(chatId: String): MutableList<Any?> =
        doGetGroupChatJsonl(chatId)
    override suspend fun saveGroupChatJsonl(chatId: String, chat: List<Any?>) =
        doSaveGroupChatJsonl(chatId, chat)
    override suspend fun getChatJsonl(avatar: String, chatFile: String): MutableList<Any?> =
        doGetChatJsonl(avatar, chatFile)
    override suspend fun getChatIntegrity(
        avatar: String,
        chatFile: String,
    ): String? = doGetChatIntegrity(avatar, chatFile)
    override suspend fun saveChatJsonl(avatar: String, chatFile: String, chat: List<Any?>) =
        doSaveChatJsonl(avatar, chatFile, chat)
    override suspend fun appendChatJsonl(
        avatar: String,
        chatFile: String,
        newMessages: List<Any?>,
        headerIfMissing: Map<String, Any?>?,
        expectedIntegrity: String?,
    ): Boolean = doAppendChatJsonl(
        avatar = avatar,
        chatFile = chatFile,
        newMessages = newMessages,
        headerIfMissing = headerIfMissing,
        expectedIntegrity = expectedIntegrity,
    )
    override suspend fun getChatTailJsonl(
        avatar: String,
        chatFile: String,
        maxLines: Int,
    ): ChatPayloadTail = doGetChatTailJsonl(avatar, chatFile, maxLines)
    override suspend fun getChatBeforeJsonl(
        avatar: String,
        chatFile: String,
        cursor: ChatPayloadCursor,
        maxLines: Int,
    ): ChatPayloadChunk = doGetChatBeforeJsonl(avatar, chatFile, cursor, maxLines)
    override suspend fun generateChatCompletion(payload: Map<String, Any?>): String =
        doGenerateChatCompletion(payload)
    override fun generateChatCompletionStream(payload: Map<String, Any?>): Flow<String> =
        doGenerateChatCompletionStream(payload)
    override suspend fun generateTextCompletion(payload: Map<String, Any?>): String =
        doGenerateTextCompletion(payload)
    override fun generateTextCompletionStream(payload: Map<String, Any?>): Flow<String> =
        doGenerateTextCompletionStream(payload)
}
