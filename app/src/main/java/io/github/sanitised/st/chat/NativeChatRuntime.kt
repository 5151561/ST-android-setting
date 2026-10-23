package io.github.sanitised.st.chat

import io.github.sanitised.st.api.CharacterChatSummary
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.CharacterExportFile
import io.github.sanitised.st.api.ChatExportFormat
import io.github.sanitised.st.api.TavernCoreApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface NativeChatDataSource {
    suspend fun getCharacter(avatar: String): CharacterDetail
    suspend fun getChatJsonl(avatar: String, chatFile: String): MutableList<Any?>
    suspend fun saveChatJsonl(avatar: String, chatFile: String, chat: List<Any?>)
    suspend fun listCharacterChats(avatar: String): List<CharacterChatSummary>

    suspend fun getChatIntegrity(avatar: String, chatFile: String): String? =
        getChatJsonl(avatar, chatFile).nativeChatIntegrity().takeIf { it.isNotBlank() }

    suspend fun appendChatJsonl(
        avatar: String,
        chatFile: String,
        newMessages: List<Any?>,
        headerIfMissing: Map<String, Any?>? = null,
        expectedIntegrity: String? = null,
    ): Boolean {
        val current = getChatJsonl(avatar, chatFile)
        val actualIntegrity = current.nativeChatIntegrity()
        if (!expectedIntegrity.isNullOrBlank() && actualIntegrity.isNotBlank() && actualIntegrity != expectedIntegrity) {
            return false
        }
        val next = if (current.isEmpty()) {
            mutableListOf<Any?>().apply {
                if (headerIfMissing != null) {
                    add(nativeChatDeepCopyValue(headerIfMissing))
                }
                ensureNativeChatIntegrity()
            }
        } else {
            current.nativeChatDeepCopy()
        }
        newMessages.forEach { message ->
            if (message != null) {
                next.add(nativeChatDeepCopyValue(message))
            }
        }
        saveChatJsonl(avatar, chatFile, next)
        return true
    }

    suspend fun renameCharacterChat(avatar: String, originalFile: String, renamedFile: String): String =
        error("renameCharacterChat is not available")

    suspend fun deleteCharacterChat(avatar: String, chatFile: String) {
        error("deleteCharacterChat is not available")
    }

    suspend fun importCharacterChat(
        avatar: String,
        characterName: String,
        fileName: String,
        bytes: ByteArray,
    ): List<String> = error("importCharacterChat is not available")

    suspend fun exportCharacterChat(
        avatar: String,
        chatFile: String,
        format: ChatExportFormat,
    ): CharacterExportFile = error("exportCharacterChat is not available")
}

class TavernNativeChatDataSource(
    private val api: TavernCoreApi,
) : NativeChatDataSource {
    override suspend fun getCharacter(avatar: String): CharacterDetail =
        api.getCharacter(avatar)

    override suspend fun getChatJsonl(avatar: String, chatFile: String): MutableList<Any?> =
        api.getChatJsonl(avatar, chatFile)

    override suspend fun saveChatJsonl(avatar: String, chatFile: String, chat: List<Any?>) {
        api.saveChatJsonl(avatar, chatFile, chat)
    }

    override suspend fun getChatIntegrity(avatar: String, chatFile: String): String? =
        api.getChatIntegrity(avatar, chatFile)

    override suspend fun appendChatJsonl(
        avatar: String,
        chatFile: String,
        newMessages: List<Any?>,
        headerIfMissing: Map<String, Any?>?,
        expectedIntegrity: String?,
    ): Boolean =
        api.appendChatJsonl(
            avatar = avatar,
            chatFile = chatFile,
            newMessages = newMessages,
            headerIfMissing = headerIfMissing,
            expectedIntegrity = expectedIntegrity,
        )

    override suspend fun listCharacterChats(avatar: String): List<CharacterChatSummary> =
        api.listCharacterChats(avatar)

    override suspend fun renameCharacterChat(avatar: String, originalFile: String, renamedFile: String): String =
        api.renameCharacterChat(avatar, originalFile, renamedFile)

    override suspend fun deleteCharacterChat(avatar: String, chatFile: String) {
        api.deleteCharacterChat(avatar, chatFile)
    }

    override suspend fun importCharacterChat(
        avatar: String,
        characterName: String,
        fileName: String,
        bytes: ByteArray,
    ): List<String> =
        api.importCharacterChat(avatar, characterName, fileName, bytes)

    override suspend fun exportCharacterChat(
        avatar: String,
        chatFile: String,
        format: ChatExportFormat,
    ): CharacterExportFile =
        api.exportCharacterChat(avatar, chatFile, format)
}

class NativeChatRepository(
    private val dataSourceProvider: () -> NativeChatDataSource,
    private val backupNameProvider: (avatar: String, chatFile: String) -> String = DEFAULT_BACKUP_NAME_PROVIDER,
    private val backupRetentionCount: Int = DEFAULT_NATIVE_BACKUP_RETENTION_COUNT,
    private val enableLegacyFileBackups: Boolean = backupNameProvider !== DEFAULT_BACKUP_NAME_PROVIDER,
) {
    private data class CachedChatSession(
        val key: String,
        val lines: MutableList<Any?>,
        val integrity: String?,
    )

    @Volatile
    private var cachedSession: CachedChatSession? = null

    suspend fun load(avatar: String, chatFile: String): Pair<CharacterDetail, MutableList<Any?>> {
        val source = dataSourceProvider()
        val character = source.getCharacter(avatar)
        val key = writeLockKey(avatar, chatFile)
        val cached = cachedSession?.takeIf { it.key == key }
        if (cached != null) {
            return character to cached.lines.nativeChatDeepCopy()
        }
        val lines = source.getChatJsonl(avatar, chatFile)
        cachedSession = CachedChatSession(
            key = key,
            lines = lines.nativeChatDeepCopy(),
            integrity = lines.nativeChatIntegrity().takeIf { it.isNotBlank() },
        )
        return character to lines
    }

    suspend fun getCharacter(avatar: String): CharacterDetail =
        dataSourceProvider().getCharacter(avatar)

    suspend fun save(avatar: String, chatFile: String, chat: List<Any?>) {
        val key = writeLockKey(avatar, chatFile)
        writeLocks.getOrPut(key) { Mutex() }.withLock {
            val source = dataSourceProvider()
            val expectedIntegrity = chat.nativeChatIntegrity()
            if (enableLegacyFileBackups) {
                val current = source.getChatJsonl(avatar, chatFile)
                val currentIntegrity = current.nativeChatIntegrity()
                if (expectedIntegrity.isNotBlank() && currentIntegrity.isNotBlank() && expectedIntegrity != currentIntegrity) {
                    cachedSession = null
                    throw NativeChatIntegrityConflict(
                        avatar = avatar,
                        chatFile = chatFile,
                        expectedIntegrity = expectedIntegrity,
                        actualIntegrity = currentIntegrity,
                    )
                }
                if (current.isNotEmpty()) {
                    source.saveChatJsonl(avatar, backupNameProvider(avatar, chatFile), current.nativeChatDeepCopy())
                }
            } else if (expectedIntegrity.isNotBlank()) {
                val currentIntegrity = source.getChatIntegrity(avatar, chatFile).orEmpty()
                if (currentIntegrity.isNotBlank() && expectedIntegrity != currentIntegrity) {
                    cachedSession = null
                    throw NativeChatIntegrityConflict(
                        avatar = avatar,
                        chatFile = chatFile,
                        expectedIntegrity = expectedIntegrity,
                        actualIntegrity = currentIntegrity,
                    )
                }
            }
            val next = chat.nativeChatDeepCopy()
            next.ensureNativeChatIntegrity()
            source.saveChatJsonl(avatar, chatFile, next)
            cachedSession = CachedChatSession(
                key = key,
                lines = next.nativeChatDeepCopy(),
                integrity = next.nativeChatIntegrity().takeIf { it.isNotBlank() },
            )
            if (enableLegacyFileBackups) {
                pruneNativeBackups(source, avatar, chatFile)
            }
        }
    }

    suspend fun appendCharacterMessages(
        avatar: String,
        chatFile: String,
        newMessages: List<Map<String, Any?>>,
        headerIfMissing: Map<String, Any?>? = null,
    ): Boolean {
        if (newMessages.isEmpty()) return true
        val key = writeLockKey(avatar, chatFile)
        return writeLocks.getOrPut(key) { Mutex() }.withLock {
            val source = dataSourceProvider()
            val cached = cachedSession?.takeIf { it.key == key }
            val expectedIntegrity = cached?.integrity
            val appended = source.appendChatJsonl(
                avatar = avatar,
                chatFile = chatFile,
                newMessages = newMessages,
                headerIfMissing = headerIfMissing,
                expectedIntegrity = expectedIntegrity,
            )
            if (!appended) {
                cachedSession = null
                val actualIntegrity = source.getChatIntegrity(avatar, chatFile).orEmpty()
                throw NativeChatIntegrityConflict(
                    avatar = avatar,
                    chatFile = chatFile,
                    expectedIntegrity = expectedIntegrity.orEmpty(),
                    actualIntegrity = actualIntegrity,
                )
            }
            if (cached != null) {
                val updatedLines = cached.lines.nativeChatDeepCopy()
                if (updatedLines.isEmpty() && headerIfMissing != null) {
                    updatedLines.add(nativeChatDeepCopyValue(headerIfMissing))
                    updatedLines.ensureNativeChatIntegrity()
                }
                newMessages.forEach { message ->
                    updatedLines.add(nativeChatDeepCopyValue(message))
                }
                cachedSession = CachedChatSession(
                    key = key,
                    lines = updatedLines,
                    integrity = updatedLines.nativeChatIntegrity().takeIf { it.isNotBlank() } ?: cached.integrity,
                )
            }
            true
        }
    }

    fun invalidateCache(avatar: String? = null, chatFile: String? = null) {
        if (avatar == null || chatFile == null) {
            cachedSession = null
            return
        }
        val key = writeLockKey(avatar, chatFile)
        if (cachedSession?.key == key) {
            cachedSession = null
        }
    }

    suspend fun listChatNames(avatar: String): Set<String> =
        dataSourceProvider().listCharacterChats(avatar)
            .flatMap { summary -> listOf(summary.id, summary.fileName) }
            .map { it.removeSuffix(".jsonl") }
            .filter { it.isNotBlank() }
            .filterNot(::isNativeChatBackupName)
            .toSet()

    suspend fun rename(avatar: String, originalFile: String, renamedFile: String): String {
        invalidateCache(avatar, originalFile)
        invalidateCache(avatar, renamedFile)
        return dataSourceProvider().renameCharacterChat(avatar, originalFile, renamedFile)
    }

    suspend fun delete(avatar: String, chatFile: String) {
        invalidateCache(avatar, chatFile)
        dataSourceProvider().deleteCharacterChat(avatar, chatFile)
    }

    suspend fun importChat(avatar: String, characterName: String, fileName: String, bytes: ByteArray): List<String> =
        dataSourceProvider().importCharacterChat(avatar, characterName, fileName, bytes)

    suspend fun exportChat(avatar: String, chatFile: String, format: ChatExportFormat): CharacterExportFile =
        dataSourceProvider().exportCharacterChat(avatar, chatFile, format)

    private companion object {
        val writeLocks = ConcurrentHashMap<String, Mutex>()

        fun writeLockKey(avatar: String, chatFile: String): String =
            "$avatar/${chatFile.removeSuffix(".jsonl")}"
    }

    private suspend fun pruneNativeBackups(source: NativeChatDataSource, avatar: String, chatFile: String) {
        if (backupRetentionCount < 0) return
        val base = chatFile.removeSuffix(".jsonl")
        val summaries = runCatching { source.listCharacterChats(avatar) }.getOrElse { return }
        val backups = summaries
            .flatMap { summary -> listOf(summary.id, summary.fileName) }
            .map { it.removeSuffix(".jsonl") }
            .distinct()
            .filter { nativeChatBackupBaseName(it) == base }
            .sortedWith(
                compareByDescending<String> { nativeChatBackupTimestamp(it) ?: it }
                    .thenByDescending { it }
            )

        backups.drop(backupRetentionCount).forEach { backup ->
            runCatching { source.deleteCharacterChat(avatar, "$backup.jsonl") }
        }
    }
}

class NativeChatIntegrityConflict(
    avatar: String,
    chatFile: String,
    expectedIntegrity: String,
    actualIntegrity: String,
) : IllegalStateException(
    "Chat JSONL changed before native save: avatar=$avatar chat=$chatFile expected=$expectedIntegrity actual=$actualIntegrity"
)

class NativeChatRuntime(
    private val store: ChatStore,
    dataSourceProvider: () -> NativeChatDataSource,
) {
    internal val repository: NativeChatRepository = NativeChatRepository(dataSourceProvider)

    suspend fun editMessage(messageId: Int, text: String) =
        mutateCurrent { session ->
            NativeChatJsonOps.editMessage(session.chat, session.resolveRawMessageId(messageId), text)
        }

    suspend fun deleteMessage(messageId: Int) =
        mutateCurrent { session ->
            NativeChatJsonOps.deleteMessage(session.chat, session.resolveRawMessageId(messageId))
        }

    suspend fun setMessageHidden(messageId: Int, hidden: Boolean) =
        mutateCurrent { session ->
            NativeChatJsonOps.setHidden(session.chat, session.resolveRawMessageId(messageId), hidden)
        }

    suspend fun moveMessage(messageId: Int, delta: Int): Boolean =
        mutateCurrent { session ->
            NativeChatJsonOps.moveMessage(session.chat, session.resolveRawMessageId(messageId), delta)
        }

    suspend fun setReasoning(messageId: Int, reasoning: String?) =
        mutateCurrent { session ->
            NativeChatJsonOps.setReasoning(session.chat, session.resolveRawMessageId(messageId), reasoning)
        }

    suspend fun deleteAttachment(messageId: Int, kind: NativeAttachmentKind, index: Int): Boolean =
        mutateCurrent { session ->
            NativeChatJsonOps.deleteAttachment(session.chat, session.resolveRawMessageId(messageId), kind, index)
        }

    suspend fun setMediaDisplay(messageId: Int, display: NativeMediaDisplay) =
        mutateCurrent { session ->
            NativeChatJsonOps.setMediaDisplay(session.chat, session.resolveRawMessageId(messageId), display)
        }

    suspend fun setAuthorsNote(text: String) =
        mutateCurrent { session -> NativeChatJsonOps.setAuthorsNote(session.chat, text) }

    suspend fun setCfg(scale: Double, negativePrompt: String, positivePrompt: String) =
        mutateCurrent { session -> NativeChatJsonOps.setCfg(session.chat, scale, negativePrompt, positivePrompt) }

    suspend fun swipePrevious(messageId: Int): Boolean =
        mutateCurrent { session ->
            NativeChatJsonOps.switchSwipe(session.chat, session.resolveRawMessageId(messageId), delta = -1)
        }

    suspend fun swipeNext(messageId: Int): Boolean =
        mutateCurrent { session ->
            NativeChatJsonOps.switchSwipe(session.chat, session.resolveRawMessageId(messageId), delta = 1)
        }

    suspend fun createSwipe(messageId: Int, text: String) =
        mutateCurrent { session ->
            NativeChatJsonOps.createSwipe(session.chat, session.resolveRawMessageId(messageId), text)
        }

    suspend fun deleteSwipe(messageId: Int, swipeId: Int): Boolean =
        mutateCurrent { session ->
            NativeChatJsonOps.deleteSwipe(session.chat, session.resolveRawMessageId(messageId), swipeId)
        }

    suspend fun createNewChat(avatarOverride: String? = null): String {
        val avatar = avatarOverride?.takeIf { it.isNotBlank() }
            ?: store.avatarUrl.takeIf { it.isNotBlank() }
            ?: error("No active character chat")
        val character = repository.getCharacter(avatar)
        val name = uniqueNewChatName(
            characterName = character.name.ifBlank { avatar.removeSuffix(".png") },
            existing = repository.listChatNames(avatar),
        )
        val chat = newCharacterChat(character)
        repository.save(avatar, name, chat)
        apply(avatar, name, character, chat, resetPagination = true)
        return name
    }

    suspend fun createCheckpoint(messageId: Int, requestedName: String? = null): String {
        val session = loadCurrent()
        val name = requestedName?.takeIf { it.isNotBlank() }
            ?: uniqueName(
                base = session.chatFile.removeSuffix(".jsonl"),
                token = "Checkpoint",
                existing = repository.listChatNames(session.avatar),
            )
        val copy = NativeChatJsonOps.createCheckpoint(
            chat = session.chat,
            currentChatName = session.chatFile,
            messageId = session.resolveRawMessageId(messageId),
            name = name,
        )
        repository.save(session.avatar, copy.linkedName, copy.chatCopy)
        saveAndApply(session)
        return copy.linkedName
    }

    suspend fun createBranch(messageId: Int): String {
        val session = loadCurrent()
        val name = uniqueName(
            base = session.chatFile.removeSuffix(".jsonl"),
            token = "Branch",
            existing = repository.listChatNames(session.avatar),
        )
        val copy = NativeChatJsonOps.createBranch(
            chat = session.chat,
            currentChatName = session.chatFile,
            messageId = session.resolveRawMessageId(messageId),
            name = name,
        )
        repository.save(session.avatar, session.chatFile, session.chat)
        repository.save(session.avatar, copy.linkedName, copy.chatCopy)
        apply(session.avatar, copy.linkedName, session.character, copy.chatCopy, resetPagination = true)
        return copy.linkedName
    }

    suspend fun openChat(chatFile: String) {
        val session = loadCurrent(chatFileOverride = chatFile)
        apply(session.avatar, session.chatFile, session.character, session.chat, resetPagination = true)
    }

    private suspend fun mutateCurrent(block: (NativeLoadedChat) -> Unit): Boolean {
        val session = loadCurrent()
        block(session)
        saveAndApply(session)
        return true
    }

    private suspend fun loadCurrent(chatFileOverride: String? = null): NativeLoadedChat {
        require(store.mode == "character") { "Native chat runtime only supports character chats in Phase 1" }
        val avatar = store.avatarUrl.takeIf { it.isNotBlank() } ?: error("No active character chat")
        val chatFile = chatFileOverride?.takeIf { it.isNotBlank() }
            ?: store.chatFile.takeIf { it.isNotBlank() }
            ?: error("No active chat file")
        val (character, chat) = repository.load(avatar, chatFile)
        val unloadedPrefixCount = if (
            chatFileOverride == null &&
            store.hasMoreHistory &&
            store.messages.isNotEmpty()
        ) {
            (NativeChatJsonOps.messageCount(chat) - store.messages.size).coerceAtLeast(0)
        } else {
            0
        }
        return NativeLoadedChat(
            avatar = avatar,
            chatFile = chatFile,
            character = character,
            chat = chat,
            unloadedPrefixCount = unloadedPrefixCount,
        )
    }

    private suspend fun saveAndApply(session: NativeLoadedChat) {
        repository.save(session.avatar, session.chatFile, session.chat)
        val chatForStore = sliceRawChatForStore(session.chat, session.unloadedPrefixCount)
        apply(
            avatar = session.avatar,
            chatFile = session.chatFile,
            character = session.character,
            chat = chatForStore,
            resetPagination = session.unloadedPrefixCount == 0 && !store.hasMoreHistory,
        )
    }

    private fun apply(
        avatar: String,
        chatFile: String,
        character: CharacterDetail,
        chat: List<Any?>,
        resetPagination: Boolean = false,
    ) {
        val savedHasMore = store.hasMoreHistory
        val savedCursor = store.historyCursor
        store.applySnapshot(
            buildNativeCharacterChatSnapshot(
                avatar = avatar,
                character = character,
                chatFile = chatFile,
                rawChat = chat,
            ),
            markRuntimeReady = false,
        )
        if (resetPagination) {
            store.hasMoreHistory = false
            store.historyCursor = null
            store.isLoadingMoreHistory = false
        } else {
            store.hasMoreHistory = savedHasMore
            store.historyCursor = savedCursor
        }
    }

    private fun uniqueName(base: String, token: String, existing: Set<String>): String {
        val clean = base
            .replace(Regex(" - $token #\\d+$"), "")
            .replace(Regex("^$token #\\d+ - "), "")
        for (i in 1..10_000) {
            val candidate = "$clean - $token #$i"
            if (candidate !in existing) return candidate
        }
        error("Could not generate unique $token chat name")
    }

    private fun uniqueNewChatName(characterName: String, existing: Set<String>): String {
        val base = "$characterName - ${nativeHumanizedDateTime()}"
        if (base !in existing) return base
        for (i in 2..10_000) {
            val candidate = "$base #$i"
            if (candidate !in existing) return candidate
        }
        error("Could not generate unique chat name")
    }

    private fun newCharacterChat(character: CharacterDetail): MutableList<Any?> {
        val created = nativeHumanizedDateTime()
        val sent = nativeMessageTimestamp()
        val chat = mutableListOf<Any?>(
            linkedMapOf(
                "user_name" to "User",
                "character_name" to character.name,
                "create_date" to created,
                "chat_metadata" to linkedMapOf<String, Any?>("integrity" to UUID.randomUUID().toString()),
            )
        )
        firstGreetingMessage(character, sent)?.let { chat += it }
        return chat
    }

    private fun firstGreetingMessage(character: CharacterDetail, sent: String): Map<String, Any?>? {
        val swipes = mutableListOf(character.firstMessage).apply {
            addAll(character.alternateGreetings)
            if (firstOrNull().isNullOrBlank()) removeFirstOrNull()
        }
        val text = swipes.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return linkedMapOf<String, Any?>(
            "name" to character.name,
            "is_user" to false,
            "is_system" to false,
            "send_date" to sent,
            "mes" to text,
            "extra" to linkedMapOf<String, Any?>(),
        ).apply {
            if (swipes.size > 1) {
                put("swipe_id", 0)
                put("swipes", swipes)
                put(
                    "swipe_info",
                    swipes.map {
                        linkedMapOf(
                            "send_date" to sent,
                            "gen_started" to null,
                            "gen_finished" to null,
                            "extra" to linkedMapOf<String, Any?>(),
                        )
                    }
                )
            }
        }
    }

    private data class NativeLoadedChat(
        val avatar: String,
        val chatFile: String,
        val character: CharacterDetail,
        val chat: MutableList<Any?>,
        val unloadedPrefixCount: Int = 0,
    ) {
        fun resolveRawMessageId(uiMessageId: Int): Int =
            unloadedPrefixCount + uiMessageId
    }
}

internal fun sliceRawChatForStore(rawChat: List<Any?>, unloadedPrefixCount: Int): List<Any?> {
    if (unloadedPrefixCount <= 0 || rawChat.isEmpty()) return rawChat
    val header = rawChat.firstOrNull().nativeChatHeaderMetadata()?.let { rawChat.first() }
    val messageRows = if (header != null) rawChat.drop(1) else rawChat
    val slicedMessages = messageRows.drop(unloadedPrefixCount.coerceAtMost(messageRows.size))
    return buildList(slicedMessages.size + 1) {
        if (header != null) add(header)
        addAll(slicedMessages)
    }
}

private const val DEFAULT_NATIVE_BACKUP_RETENTION_COUNT = 5
private val DEFAULT_BACKUP_NAME_PROVIDER: (String, String) -> String = ::defaultNativeChatBackupName
internal const val NATIVE_BACKUP_PREFIX = "__native-backup__"
private const val LEGACY_NATIVE_BACKUP_MARKER = ".native-backup-"

private fun defaultNativeChatBackupName(@Suppress("UNUSED_PARAMETER") avatar: String, chatFile: String): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
    return "$NATIVE_BACKUP_PREFIX${chatFile.removeSuffix(".jsonl")}__$stamp"
}

private fun nativeHumanizedDateTime(): String =
    SimpleDateFormat("yyyy-MM-dd@HH'h'mm'm'ss's'SSS'ms'", Locale.US).format(Date())

private fun nativeMessageTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

internal fun isNativeChatBackupName(name: String): Boolean =
    nativeChatBackupBaseName(name) != null

internal fun nativeChatBackupBaseName(name: String): String? {
    val normalized = name.removeSuffix(".jsonl")
    if (normalized.startsWith(NATIVE_BACKUP_PREFIX)) {
        val rest = normalized.removePrefix(NATIVE_BACKUP_PREFIX)
        val split = rest.lastIndexOf("__")
        if (split > 0) return rest.take(split).takeIf { it.isNotBlank() }
        return null
    }
    val legacyMarkerIndex = normalized.indexOf(LEGACY_NATIVE_BACKUP_MARKER)
    if (legacyMarkerIndex > 0) {
        return normalized.take(legacyMarkerIndex)
    }
    return null
}

internal fun nativeChatBackupTimestamp(name: String): String? {
    val normalized = name.removeSuffix(".jsonl")
    if (normalized.startsWith(NATIVE_BACKUP_PREFIX)) {
        val rest = normalized.removePrefix(NATIVE_BACKUP_PREFIX)
        val split = rest.lastIndexOf("__")
        if (split > 0 && split + 2 < rest.length) return rest.substring(split + 2)
        return null
    }
    val legacyMarkerIndex = normalized.indexOf(LEGACY_NATIVE_BACKUP_MARKER)
    if (legacyMarkerIndex > 0) {
        return normalized.substring(legacyMarkerIndex + LEGACY_NATIVE_BACKUP_MARKER.length)
            .takeIf { it.isNotBlank() }
    }
    return null
}

private fun List<Any?>.nativeChatIntegrity(): String {
    val metadata = firstOrNull().nativeChatHeaderMetadata() ?: return ""
    return metadata["integrity"]?.toString().orEmpty()
}

// ST 服务端 /api/chats/save 要求 payload 首行 integrity 与磁盘文件一致（上游前端在加载时
// 补全后保持不变），因此这里只在缺失时补全，绝不能每次保存都换新值。
private fun MutableList<Any?>.ensureNativeChatIntegrity() {
    val metadata = firstOrNull().nativeChatHeaderMetadata()
    if (metadata != null) {
        if (metadata["integrity"]?.toString().isNullOrBlank()) {
            metadata["integrity"] = UUID.randomUUID().toString()
        }
        return
    }
    add(
        0,
        linkedMapOf<String, Any?>(
            "chat_metadata" to linkedMapOf("integrity" to UUID.randomUUID().toString()),
        )
    )
}

private fun Any?.nativeChatHeaderMetadata(): MutableMap<String, Any?>? {
    @Suppress("UNCHECKED_CAST")
    val header = this as? MutableMap<String, Any?> ?: return null
    val hasHeaderShape = header.containsKey("chat_metadata") ||
        header.containsKey("user_name") ||
        header.containsKey("character_name") ||
        header.containsKey("create_date")
    if (!hasHeaderShape) return null
    @Suppress("UNCHECKED_CAST")
    val existing = header["chat_metadata"] as? MutableMap<String, Any?>
    if (existing != null) return existing
    val metadata = linkedMapOf<String, Any?>()
    header["chat_metadata"] = metadata
    return metadata
}

private fun List<Any?>.nativeChatDeepCopy(): MutableList<Any?> =
    map { nativeChatDeepCopyValue(it) }.toMutableList()

private fun nativeChatDeepCopyValue(value: Any?): Any? =
    when (value) {
        is Map<*, *> -> linkedMapOf<String, Any?>().also { out ->
            value.forEach { (key, nested) ->
                if (key != null) out[key.toString()] = nativeChatDeepCopyValue(nested)
            }
        }
        is List<*> -> value.map { nativeChatDeepCopyValue(it) }
        is Array<*> -> value.map { nativeChatDeepCopyValue(it) }
        else -> value
    }
