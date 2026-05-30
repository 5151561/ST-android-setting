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

data class PendingAttachment(
    val url: String,
    val name: String,
    val size: Long,
    val isMedia: Boolean
)

class ChatStore {
    var runtimeState by mutableStateOf(RuntimeState.NOT_READY)
    var characterName by mutableStateOf("")
    var avatarUrl by mutableStateOf("")
    var chatFile by mutableStateOf("")
    var mode by mutableStateOf("character")
    var isGenerating by mutableStateOf(false)
    var runtimeError by mutableStateOf<String?>(null)
    var saveError by mutableStateOf<String?>(null)
    var authorsNote by mutableStateOf("")
    var cfgScale by mutableStateOf(1.0f)
    var cfgNegativePrompt by mutableStateOf("")
    var cfgPositivePrompt by mutableStateOf("")
    var worldInfoName by mutableStateOf("")
    val messages = mutableStateListOf<ChatMessage>()
    val pendingAttachments = mutableStateListOf<PendingAttachment>()

    fun applySnapshot(snapshot: ChatSnapshot) {
        runtimeState = RuntimeState.READY
        runtimeError = null
        mode = snapshot.mode
        characterName = snapshot.characterName
        avatarUrl = snapshot.avatarUrl
        chatFile = snapshot.chatFile
        isGenerating = snapshot.isGenerating
        authorsNote = snapshot.metadata.optString("authorsNote", "")
        cfgScale = snapshot.metadata.optDouble("cfgScale", 1.0).toFloat()
        cfgNegativePrompt = snapshot.metadata.optString("cfgNegativePrompt", "")
        cfgPositivePrompt = snapshot.metadata.optString("cfgPositivePrompt", "")
        worldInfoName = snapshot.metadata.optString("worldInfo", "")
        messages.clear()
        messages.addAll(snapshot.messages)
    }

    fun addPendingAttachment(attachment: PendingAttachment) {
        pendingAttachments.add(attachment)
    }

    fun removePendingAttachment(attachment: PendingAttachment) {
        pendingAttachments.remove(attachment)
    }

    fun clearPendingAttachments() {
        pendingAttachments.clear()
    }

    fun markRuntimeReady() {
        runtimeState = RuntimeState.READY
        runtimeError = null
    }

    fun markRuntimeUnavailable(message: String? = null) {
        runtimeState = RuntimeState.NOT_READY
        isGenerating = false
        runtimeError = message
    }

    fun markRuntimeError(message: String) {
        runtimeState = RuntimeState.ERROR
        isGenerating = false
        runtimeError = message
    }

    fun recordCommandError(message: String) {
        runtimeError = message
    }

    fun recordSaveError(message: String) {
        saveError = message
    }

    fun clearSaveError() {
        saveError = null
    }

    fun addMessage(message: ChatMessage) {
        upsertMessage(message)
    }

    fun updateMessage(message: ChatMessage) {
        upsertMessage(message)
    }

    private fun upsertMessage(message: ChatMessage) {
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
        mode = "character"
        isGenerating = false
        runtimeError = null
        saveError = null
        authorsNote = ""
        cfgScale = 1.0f
        cfgNegativePrompt = ""
        cfgPositivePrompt = ""
        worldInfoName = ""
        messages.clear()
        pendingAttachments.clear()
    }
}
