package io.github.sanitised.st.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.ChatSummary
import io.github.sanitised.st.api.GroupSummary
import io.github.sanitised.st.api.TavernCoreApi
import io.github.sanitised.st.data.LocalTavernLibraryReader
import io.github.sanitised.st.data.tavernApi
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalTavernLibrarySnapshot(
    val characters: List<CharacterSummary> = emptyList(),
    val recentChats: List<ChatSummary> = emptyList(),
    val groups: List<GroupSummary> = emptyList()
)

// 对话列表是主屏,预览用尾部读取后放开条数上限(原为 5)。
private const val RECENT_CHAT_LIMIT = 50
private const val GROUP_LIMIT = 50

@Composable
fun rememberLocalTavernLibrarySnapshot(
    dataRoot: File,
    serverRunning: Boolean,
    baseUrl: String,
    refreshKey: Any?,
    clientProvider: () -> TavernCoreApi = { tavernApi(baseUrl) }
): State<LocalTavernLibrarySnapshot> {
    return produceState(
        initialValue = LocalTavernLibrarySnapshot(),
        dataRoot,
        serverRunning,
        baseUrl,
        refreshKey
    ) {
        value = withContext(Dispatchers.IO) {
            val reader = LocalTavernLibraryReader(dataRoot)
            if (serverRunning) {
                runCatching {
                    val client = clientProvider()
                    val localCharacters = reader.listCharacters()
                    val characters = client.listCharacters()
                        .ifEmpty { localCharacters }
                        .withLocalAvatarUrls(localCharacters)
                    val localRecentChats = reader.listRecentChats(RECENT_CHAT_LIMIT)
                    val recentChats = client.listRecentChats()
                        .ifEmpty { localRecentChats }
                        .resolveRecentChats(characters = characters, localChats = localRecentChats)
                    val groups = runCatching { client.listGroups() }
                        .getOrDefault(emptyList())
                        .ifEmpty { reader.listGroups(GROUP_LIMIT) }
                    LocalTavernLibrarySnapshot(
                        characters = characters,
                        recentChats = recentChats,
                        groups = groups.withLocalPreviews(reader)
                    )
                }.getOrElse {
                    localSnapshot(reader)
                }
            } else {
                localSnapshot(reader)
            }
        }
    }
}

private fun localSnapshot(reader: LocalTavernLibraryReader): LocalTavernLibrarySnapshot {
    val characters = reader.listCharacters()
    val recentChats = reader.listRecentChats(RECENT_CHAT_LIMIT)
        .resolveRecentChats(characters = characters, localChats = emptyList())
    return LocalTavernLibrarySnapshot(
        characters = characters,
        recentChats = recentChats,
        groups = reader.listGroups(GROUP_LIMIT)
    )
}

private fun List<CharacterSummary>.withLocalAvatarUrls(
    localCharacters: List<CharacterSummary>
): List<CharacterSummary> {
    if (localCharacters.isEmpty()) return this
    val localById = localCharacters.associateBy { it.id.lowercase() }
    return map { character ->
        val localAvatar = localById[character.id.lowercase()]?.avatarUrl
        if (!localAvatar.isNullOrBlank() && (character.avatarUrl.isNullOrBlank() || character.avatarUrl == character.id)) {
            character.copy(avatarUrl = localAvatar)
        } else {
            character
        }
    }
}

private fun List<ChatSummary>.resolveRecentChats(
    characters: List<CharacterSummary>,
    localChats: List<ChatSummary>
): List<ChatSummary> {
    return map { chat ->
        val resolved = chat.resolveWithCharacters(characters)
        val chatFileStem = resolved.id.substringAfter('/', missingDelimiterValue = resolved.id)
            .removeSuffix(".jsonl")
        val matchingLocal = localChats.firstOrNull { local ->
            val localFileStem = local.id.substringAfter('/', missingDelimiterValue = local.id)
                .removeSuffix(".jsonl")
            localFileStem == chatFileStem && (
                local.characterId.equals(resolved.characterId, ignoreCase = true) ||
                    local.characterName.equals(resolved.characterName, ignoreCase = true)
                )
        } ?: localChats.singleOrNull { local ->
            val localFileStem = local.id.substringAfter('/', missingDelimiterValue = local.id)
                .removeSuffix(".jsonl")
            localFileStem == chatFileStem
        }
        if (matchingLocal != null) {
            val reResolved = resolved.copy(
                id = if (resolved.characterId.isBlank() || !resolved.id.contains('/')) matchingLocal.id else resolved.id,
                characterId = resolved.characterId.takeIf {
                    it.isNotBlank() && characters.any { ch -> ch.id.equals(it, ignoreCase = true) }
                } ?: matchingLocal.characterId,
                avatarUrl = matchingLocal.avatarUrl ?: resolved.avatarUrl
            ).resolveWithCharacters(characters)
            reResolved.copy(avatarUrl = matchingLocal.avatarUrl ?: reResolved.avatarUrl)
        } else {
            resolved
        }
    }
}

private fun List<GroupSummary>.withLocalPreviews(reader: LocalTavernLibraryReader): List<GroupSummary> {
    return map { group ->
        if (group.lastMessage != null) group
        else group.copy(lastMessage = reader.groupChatPreview(group.chatId))
    }
}
