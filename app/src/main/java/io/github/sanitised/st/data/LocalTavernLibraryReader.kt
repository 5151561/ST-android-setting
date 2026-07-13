package io.github.sanitised.st.data

import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.ChatSummary
import io.github.sanitised.st.api.GroupSummary
import io.github.sanitised.st.api.StJson
import io.github.sanitised.st.chat.isNativeChatBackupName
import java.io.File
import java.io.RandomAccessFile

class LocalTavernLibraryReader(
    private val dataRoot: File,
    private val userHandle: String = DEFAULT_USER_HANDLE
) {
    fun listCharacters(limit: Int = DEFAULT_LIST_LIMIT): List<CharacterSummary> {
        val charactersDir = File(userDir, "characters")
        return charactersDir.safeFiles()
            .asSequence()
            .filter { it.isFile && !it.name.startsWith(".") }
            .filter { it.extension.lowercase() in CHARACTER_EXTENSIONS }
            .sortedByDescending { it.lastModified() }
            .take(limit.coerceAtLeast(0))
            .map { file ->
                CharacterSummary(
                    id = file.name,
                    name = file.displayName(),
                    avatarUrl = file.toURI().toString()
                )
            }
            .toList()
    }

    fun listRecentChats(limit: Int = DEFAULT_LIST_LIMIT): List<ChatSummary> {
        val chatsDir = File(userDir, "chats")
        return chatsDir.safeFiles()
            .asSequence()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .flatMap { characterDir ->
                characterDir.safeFiles()
                    .asSequence()
                    .filter { it.isFile && !it.name.startsWith(".") && it.extension.equals("jsonl", ignoreCase = true) }
                    .filterNot { isNativeChatBackupName(it.nameWithoutExtension) }
                    .map { chatFile -> characterDir to chatFile }
            }
            .sortedByDescending { (_, chatFile) -> chatFile.lastModified() }
            .take(limit.coerceAtLeast(0))
            .map { (characterDir, chatFile) ->
                ChatSummary(
                    id = "${characterDir.name}/${chatFile.nameWithoutExtension}",
                    characterId = characterDir.avatarFileName(),
                    characterName = characterDir.displayName(),
                    avatarUrl = characterDir.characterAvatarUrl(),
                    lastMessage = chatFile.lastMessagePreview(),
                    lastUpdated = chatFile.lastModified()
                )
            }
            .toList()
    }

    fun listGroups(limit: Int = DEFAULT_LIST_LIMIT): List<GroupSummary> {
        val groupsDir = File(userDir, "groups")
        return groupsDir.safeFiles()
            .asSequence()
            .filter { it.isFile && !it.name.startsWith(".") && it.extension.equals("json", ignoreCase = true) }
            .mapNotNull { file -> file.groupSummary() }
            .sortedByDescending { it.lastUpdated }
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    /** 读取群聊 jsonl 的最后一条消息文本（服务端 /api/groups/all 不含预览，本地补齐）。 */
    fun groupChatPreview(chatId: String): String? {
        if (chatId.isBlank()) return null
        return groupChatFile(chatId)?.lastMessagePreview()
    }

    private fun File.groupSummary(): GroupSummary? {
        val raw = runCatching { StJson.parse(readText(Charsets.UTF_8)) }.getOrNull() as? Map<*, *> ?: return null
        val id = (raw["id"] as? String).orEmpty().ifBlank { nameWithoutExtension }
        val chats = (raw["chats"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        val chatId = (raw["chat_id"] as? String).orEmpty().ifBlank { chats.lastOrNull().orEmpty() }
        val lastChatAt = (chats + chatId)
            .filter { it.isNotBlank() }
            .distinct()
            .mapNotNull { groupChatFile(it)?.lastModified() }
            .maxOrNull()
            ?: lastModified()
        return GroupSummary(
            id = id,
            name = (raw["name"] as? String).orEmpty().ifBlank { "未命名群聊" },
            members = (raw["members"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            chatId = chatId,
            chats = chats,
            lastUpdated = lastChatAt,
            avatarUrl = (raw["avatar_url"] as? String).orEmpty(),
            isFavorite = raw["fav"] == true,
            lastMessage = groupChatPreview(chatId)
        )
    }

    private fun groupChatFile(chatId: String): File? {
        val dir = File(userDir, "group chats")
        return listOf("$chatId.jsonl", chatId)
            .map { File(dir, it) }
            .firstOrNull { it.isFile }
    }

    private val userDir: File
        get() = File(dataRoot, userHandle)

    private fun File.safeFiles(): List<File> =
        runCatching { listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())

    private fun File.displayName(): String {
        val baseName = if (isFile) nameWithoutExtension else name
        return baseName.replace('_', ' ').trim().ifBlank { name }
    }

    // 聊天目录名是 avatar 文件名去掉 .png(上游约定);/api/characters/get 等接口
    // 需要完整文件名,直接拿目录名调用会 404。
    private fun File.avatarFileName(): String {
        val charactersDir = File(userDir, "characters")
        return listOf("$name.png", name)
            .firstOrNull { File(charactersDir, it).isFile }
            ?: "$name.png"
    }

    private fun File.characterAvatarUrl(): String? {
        val charactersDir = File(userDir, "characters")
        val candidates = listOf(name, "$name.png").distinct()
        return candidates
            .asSequence()
            .map { File(charactersDir, it) }
            .firstOrNull { it.isFile }
            ?.toURI()
            ?.toString()
    }

    private fun File.lastMessagePreview(): String? {
        return runCatching {
            tailLines()
                .asReversed()
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .mapNotNull { line -> line.messageText() }
                .firstOrNull()
        }.getOrNull()
    }

    // 只读文件尾部而不是整份 jsonl:长聊天可达数 MB,列表要一次读几十份。
    // 从截断点起第一行可能不完整,交给 messageText 的容错丢弃。
    private fun File.tailLines(maxBytes: Int = PREVIEW_TAIL_BYTES): List<String> {
        val fileLength = length()
        if (fileLength <= 0) return emptyList()
        val readLength = minOf(fileLength, maxBytes.toLong()).toInt()
        val bytes = ByteArray(readLength)
        RandomAccessFile(this, "r").use { raf ->
            raf.seek(fileLength - readLength)
            raf.readFully(bytes)
        }
        val lines = String(bytes, Charsets.UTF_8).split('\n')
        return if (readLength < fileLength && lines.size > 1) lines.drop(1) else lines
    }

    private fun String.messageText(): String? {
        val value = runCatching { StJson.parse(this) }.getOrNull() as? Map<*, *> ?: return null
        return (value["mes"] as? String)?.trim()?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val DEFAULT_USER_HANDLE = "default-user"
        const val DEFAULT_LIST_LIMIT = 5
        const val PREVIEW_TAIL_BYTES = 64 * 1024
        val CHARACTER_EXTENSIONS = setOf("png", "json", "charx")
    }
}
