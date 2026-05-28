package io.github.sanitised.st.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.ChatSummary
import io.github.sanitised.st.data.LocalTavernLibraryReader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalTavernLibrarySnapshot(
    val characters: List<CharacterSummary> = emptyList(),
    val recentChats: List<ChatSummary> = emptyList()
)

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
            if (serverRunning) {
                runCatching {
                    val client = io.github.sanitised.st.api.TavernCoreClient(baseUrl = baseUrl)
                    val characters = client.listCharacters()
                    val recentChats = client.listRecentChats()
                    LocalTavernLibrarySnapshot(
                        characters = characters,
                        recentChats = recentChats
                    )
                }.getOrElse {
                    val reader = LocalTavernLibraryReader(dataRoot)
                    LocalTavernLibrarySnapshot(
                        characters = reader.listCharacters(),
                        recentChats = reader.listRecentChats()
                    )
                }
            } else {
                val reader = LocalTavernLibraryReader(dataRoot)
                LocalTavernLibrarySnapshot(
                    characters = reader.listCharacters(),
                    recentChats = reader.listRecentChats()
                )
            }
        }
    }
}
