package io.github.sanitised.st.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.ChatSummary
import io.github.sanitised.st.api.GroupSummary
import io.github.sanitised.st.data.LocalTavernLibraryReader
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
    refreshKey: Any?
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
                    val client = io.github.sanitised.st.api.TavernCoreClient(baseUrl = baseUrl)
                    val characters = client.listCharacters().ifEmpty { reader.listCharacters() }
                    val recentChats = client.listRecentChats().ifEmpty { reader.listRecentChats(RECENT_CHAT_LIMIT) }
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
    return LocalTavernLibrarySnapshot(
        characters = reader.listCharacters(),
        recentChats = reader.listRecentChats(RECENT_CHAT_LIMIT),
        groups = reader.listGroups(GROUP_LIMIT)
    )
}

private fun List<GroupSummary>.withLocalPreviews(reader: LocalTavernLibraryReader): List<GroupSummary> {
    return map { group ->
        if (group.lastMessage != null) group
        else group.copy(lastMessage = reader.groupChatPreview(group.chatId))
    }
}
