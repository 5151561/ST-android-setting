package io.github.sanitised.st.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

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

data class RuntimeToast(
    val seq: Long,
    val type: String,
    val title: String,
    val message: String
)

data class QuickReplyItem(
    val setName: String,
    val label: String,
    val icon: String,
    val message: String,
    val disableSend: Boolean = false,
    val injectInput: Boolean = false,
    val placeBeforeInput: Boolean = false,
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
    var chatQuickReplyConfig by mutableStateOf<Map<String, Any?>>(emptyMap())
    var latestToast by mutableStateOf<RuntimeToast?>(null)
    private var toastSeq = 0L
    var itemizedPrompt by mutableStateOf<ItemizedPrompt?>(null)
    var itemizedPromptLoading by mutableStateOf(false)
    var itemizedPromptError by mutableStateOf<String?>(null)
    var dataBank by mutableStateOf<DataBankAttachments?>(null)
    var dataBankLoading by mutableStateOf(false)
    val messages = mutableStateListOf<ChatMessage>()
    val pendingAttachments = mutableStateListOf<PendingAttachment>()
    val quickReplies = mutableStateListOf<QuickReplyItem>()
    val loadedExtensions = mutableStateListOf<String>()

    fun applySnapshot(snapshot: ChatSnapshot, markRuntimeReady: Boolean = true) {
        if (markRuntimeReady) {
            runtimeState = RuntimeState.READY
        }
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
        chatQuickReplyConfig = snapshot.metadata.optJSONObject("quickReply")?.toNativeMap().orEmpty()
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

    fun clearRuntimeError() {
        runtimeError = null
    }

    fun recordSaveError(message: String) {
        saveError = message
    }

    fun clearSaveError() {
        saveError = null
    }

    fun pushToast(type: String, title: String, message: String) {
        if (title.isBlank() && message.isBlank()) return
        toastSeq += 1
        latestToast = RuntimeToast(seq = toastSeq, type = type, title = title, message = message)
    }

    fun clearToast() {
        latestToast = null
    }

    fun setQuickReplies(items: List<QuickReplyItem>) {
        quickReplies.clear()
        quickReplies.addAll(items)
    }

    fun setLoadedExtensions(names: List<String>) {
        loadedExtensions.clear()
        loadedExtensions.addAll(names)
    }

    fun beginItemizedPromptLoad() {
        itemizedPromptLoading = true
        itemizedPromptError = null
        itemizedPrompt = null
    }

    fun applyItemizedPrompt(prompt: ItemizedPrompt?) {
        itemizedPromptLoading = false
        itemizedPrompt = prompt
        itemizedPromptError = if (prompt == null) "该消息没有提示词分析数据（仅本会话生成过的消息可用）" else null
    }

    fun recordItemizedPromptError(message: String) {
        itemizedPromptLoading = false
        itemizedPromptError = message
    }

    fun clearItemizedPrompt() {
        itemizedPrompt = null
        itemizedPromptLoading = false
        itemizedPromptError = null
    }

    fun beginDataBankLoad() {
        dataBankLoading = true
    }

    fun applyDataBank(attachments: DataBankAttachments) {
        dataBankLoading = false
        dataBank = attachments
    }

    fun clearDataBank() {
        dataBank = null
        dataBankLoading = false
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
        chatQuickReplyConfig = emptyMap()
        latestToast = null
        itemizedPrompt = null
        itemizedPromptLoading = false
        itemizedPromptError = null
        dataBank = null
        dataBankLoading = false
        messages.clear()
        pendingAttachments.clear()
        quickReplies.clear()
        loadedExtensions.clear()
    }
}

private fun JSONObject.toNativeMap(): Map<String, Any?> =
    keys().asSequence().associateWith { key -> opt(key).toNativeValue() }

private fun JSONArray.toNativeList(): List<Any?> =
    (0 until length()).map { index -> opt(index).toNativeValue() }

private fun Any?.toNativeValue(): Any? =
    when (this) {
        null, JSONObject.NULL -> null
        is JSONObject -> toNativeMap()
        is JSONArray -> toNativeList()
        else -> this
    }
