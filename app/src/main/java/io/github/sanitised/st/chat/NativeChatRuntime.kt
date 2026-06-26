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
    private val backupNameProvider: (avatar: String, chatFile: String) -> String = ::defaultNativeChatBackupName,
    private val backupRetentionCount: Int = DEFAULT_NATIVE_BACKUP_RETENTION_COUNT,
) {
    suspend fun load(avatar: String, chatFile: String): Pair<CharacterDetail, MutableList<Any?>> {
        val source = dataSourceProvider()
        return source.getCharacter(avatar) to source.getChatJsonl(avatar, chatFile)
    }

    suspend fun getCharacter(avatar: String): CharacterDetail =
        dataSourceProvider().getCharacter(avatar)

    suspend fun save(avatar: String, chatFile: String, chat: List<Any?>) {
        val key = writeLockKey(avatar, chatFile)
        writeLocks.getOrPut(key) { Mutex() }.withLock {
            val source = dataSourceProvider()
            val current = source.getChatJsonl(avatar, chatFile)
            val expectedIntegrity = chat.nativeChatIntegrity()
            val currentIntegrity = current.nativeChatIntegrity()
            if (expectedIntegrity.isNotBlank() && currentIntegrity.isNotBlank() && expectedIntegrity != currentIntegrity) {
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
            val next = chat.nativeChatDeepCopy()
            next.refreshNativeChatIntegrity()
            source.saveChatJsonl(avatar, chatFile, next)
            pruneNativeBackups(source, avatar, chatFile)
        }
    }

    suspend fun listChatNames(avatar: String): Set<String> =
        dataSourceProvider().listCharacterChats(avatar)
            .flatMap { summary -> listOf(summary.id, summary.fileName) }
            .map { it.removeSuffix(".jsonl") }
            .filter { it.isNotBlank() }
            .filterNot(::isNativeChatBackupName)
            .toSet()

    suspend fun rename(avatar: String, originalFile: String, renamedFile: String): String =
        dataSourceProvider().renameCharacterChat(avatar, originalFile, renamedFile)

    suspend fun delete(avatar: String, chatFile: String) {
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
    private val repository = NativeChatRepository(dataSourceProvider)

    suspend fun editMessage(messageId: Int, text: String) =
        mutateCurrent { NativeChatJsonOps.editMessage(it, messageId, text) }

    suspend fun deleteMessage(messageId: Int) =
        mutateCurrent { NativeChatJsonOps.deleteMessage(it, messageId) }

    suspend fun setMessageHidden(messageId: Int, hidden: Boolean) =
        mutateCurrent { NativeChatJsonOps.setHidden(it, messageId, hidden) }

    suspend fun moveMessage(messageId: Int, delta: Int): Boolean =
        mutateCurrent { NativeChatJsonOps.moveMessage(it, messageId, delta) }

    suspend fun setReasoning(messageId: Int, reasoning: String?) =
        mutateCurrent { NativeChatJsonOps.setReasoning(it, messageId, reasoning) }

    suspend fun deleteAttachment(messageId: Int, kind: NativeAttachmentKind, index: Int): Boolean =
        mutateCurrent { NativeChatJsonOps.deleteAttachment(it, messageId, kind, index) }

    suspend fun setMediaDisplay(messageId: Int, display: NativeMediaDisplay) =
        mutateCurrent { NativeChatJsonOps.setMediaDisplay(it, messageId, display) }

    suspend fun setAuthorsNote(text: String) =
        mutateCurrent { NativeChatJsonOps.setAuthorsNote(it, text) }

    suspend fun setCfg(scale: Double, negativePrompt: String, positivePrompt: String) =
        mutateCurrent { NativeChatJsonOps.setCfg(it, scale, negativePrompt, positivePrompt) }

    suspend fun swipePrevious(messageId: Int): Boolean =
        mutateCurrent { NativeChatJsonOps.switchSwipe(it, messageId, delta = -1) }

    suspend fun swipeNext(messageId: Int): Boolean =
        mutateCurrent { NativeChatJsonOps.switchSwipe(it, messageId, delta = 1) }

    suspend fun createSwipe(messageId: Int, text: String) =
        mutateCurrent { NativeChatJsonOps.createSwipe(it, messageId, text) }

    suspend fun deleteSwipe(messageId: Int, swipeId: Int): Boolean =
        mutateCurrent { NativeChatJsonOps.deleteSwipe(it, messageId, swipeId) }

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
        apply(avatar, name, character, chat)
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
            messageId = messageId,
            name = name,
        )
        repository.save(session.avatar, copy.linkedName, copy.chatCopy)
        saveAndApply(session.avatar, session.chatFile, session.character, session.chat)
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
            messageId = messageId,
            name = name,
        )
        repository.save(session.avatar, session.chatFile, session.chat)
        repository.save(session.avatar, copy.linkedName, copy.chatCopy)
        apply(session.avatar, copy.linkedName, session.character, copy.chatCopy)
        return copy.linkedName
    }

    suspend fun openChat(chatFile: String) {
        val session = loadCurrent(chatFileOverride = chatFile)
        apply(session.avatar, session.chatFile, session.character, session.chat)
    }

    private suspend fun mutateCurrent(block: (MutableList<Any?>) -> Unit): Boolean {
        val session = loadCurrent()
        block(session.chat)
        saveAndApply(session.avatar, session.chatFile, session.character, session.chat)
        return true
    }

    private suspend fun loadCurrent(chatFileOverride: String? = null): NativeLoadedChat {
        require(store.mode == "character") { "Native chat runtime only supports character chats in Phase 1" }
        val avatar = store.avatarUrl.takeIf { it.isNotBlank() } ?: error("No active character chat")
        val chatFile = chatFileOverride?.takeIf { it.isNotBlank() }
            ?: store.chatFile.takeIf { it.isNotBlank() }
            ?: error("No active chat file")
        val (character, chat) = repository.load(avatar, chatFile)
        return NativeLoadedChat(avatar, chatFile, character, chat)
    }

    private suspend fun saveAndApply(
        avatar: String,
        chatFile: String,
        character: CharacterDetail,
        chat: MutableList<Any?>,
    ) {
        repository.save(avatar, chatFile, chat)
        apply(avatar, chatFile, character, chat)
    }

    private fun apply(avatar: String, chatFile: String, character: CharacterDetail, chat: List<Any?>) {
        store.applySnapshot(
            buildNativeCharacterChatSnapshot(
                avatar = avatar,
                character = character,
                chatFile = chatFile,
                rawChat = chat,
            ),
            markRuntimeReady = false,
        )
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
    )
}

private const val DEFAULT_NATIVE_BACKUP_RETENTION_COUNT = 5
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

private fun MutableList<Any?>.refreshNativeChatIntegrity() {
    val metadata = firstOrNull().nativeChatHeaderMetadata()
    if (metadata != null) {
        metadata["integrity"] = UUID.randomUUID().toString()
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
