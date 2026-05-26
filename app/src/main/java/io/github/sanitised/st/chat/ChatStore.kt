package io.github.sanitised.st.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class RuntimeState {
    NOT_READY,
    READY,
    ERROR
}

class ChatStore {
    var runtimeState by mutableStateOf(RuntimeState.NOT_READY)
    var characterName by mutableStateOf("")
    var avatarUrl by mutableStateOf("")
    var chatFile by mutableStateOf("")
    var isGenerating by mutableStateOf(false)
    var runtimeError by mutableStateOf<String?>(null)
    val messages = mutableStateListOf<ChatMessage>()

    fun applySnapshot(snapshot: ChatSnapshot) {
        characterName = snapshot.characterName
        avatarUrl = snapshot.avatarUrl
        chatFile = snapshot.chatFile
        isGenerating = snapshot.isGenerating
        messages.clear()
        messages.addAll(snapshot.messages)
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
    }

    fun updateMessage(message: ChatMessage) {
        val idx = messages.indexOfFirst { it.id == message.id }
        if (idx >= 0) {
            messages[idx] = message
        } else {
            messages.add(message)
        }
    }

    fun deleteMessage(messageId: Int) {
        messages.removeAll { it.id == messageId }
    }

    fun reset() {
        runtimeState = RuntimeState.NOT_READY
        characterName = ""
        avatarUrl = ""
        chatFile = ""
        isGenerating = false
        runtimeError = null
        messages.clear()
    }
}
