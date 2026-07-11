package io.github.sanitised.st.data

import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.ChatSummary
import io.github.sanitised.st.api.StJson
import java.io.File

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
            readLines(Charsets.UTF_8)
                .asReversed()
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .mapNotNull { line -> line.messageText() }
                .firstOrNull()
        }.getOrNull()
    }

    private fun String.messageText(): String? {
        val value = StJson.parse(this) as? Map<*, *> ?: return null
        return (value["mes"] as? String)?.trim()?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val DEFAULT_USER_HANDLE = "default-user"
        const val DEFAULT_LIST_LIMIT = 5
        val CHARACTER_EXTENSIONS = setOf("png", "json", "charx")
    }
}
