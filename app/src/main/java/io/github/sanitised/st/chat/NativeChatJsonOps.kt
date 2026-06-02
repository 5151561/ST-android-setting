package io.github.sanitised.st.chat

enum class NativeAttachmentKind(val extraKey: String) {
    FILE("files"),
    MEDIA("media"),
}

enum class NativeMediaDisplay(val value: String) {
    LIST("list"),
    GALLERY("gallery"),
}

data class NativeChatCopy(
    val linkedName: String,
    val chatCopy: List<Any?>,
)

object NativeChatJsonOps {
    fun editMessage(chat: MutableList<Any?>, messageId: Int, text: String) {
        val row = mutableMessage(chat, messageId)
        row["mes"] = text
        val swipes = row.stringList("swipes")?.toMutableList() ?: return
        val swipeId = row.intValue("swipe_id").coerceIn(0, swipes.lastIndex.coerceAtLeast(0))
        if (swipeId in swipes.indices) {
            swipes[swipeId] = text
            row["swipes"] = swipes
        }
    }

    fun deleteMessage(chat: MutableList<Any?>, messageId: Int) {
        chat.removeAt(rowIndex(chat, messageId))
    }

    fun setHidden(chat: MutableList<Any?>, messageId: Int, hidden: Boolean) {
        mutableMessage(chat, messageId)["is_system"] = hidden
    }

    fun moveMessage(chat: MutableList<Any?>, messageId: Int, delta: Int): Boolean {
        val from = rowIndex(chat, messageId)
        val toMessageId = messageId + delta
        if (toMessageId < 0 || toMessageId >= messageCount(chat)) return false
        val to = rowIndex(chat, toMessageId)
        val item = chat[from]
        chat[from] = chat[to]
        chat[to] = item
        return true
    }

    fun setReasoning(chat: MutableList<Any?>, messageId: Int, reasoning: String?) {
        val extra = mutableExtra(mutableMessage(chat, messageId))
        if (reasoning.isNullOrBlank()) extra.remove("reasoning")
        else extra["reasoning"] = reasoning
    }

    fun deleteAttachment(chat: MutableList<Any?>, messageId: Int, kind: NativeAttachmentKind, index: Int): Boolean {
        val extra = mutableExtra(mutableMessage(chat, messageId))
        val list = extra.listValue(kind.extraKey)?.toMutableList() ?: return false
        if (index !in list.indices) return false
        list.removeAt(index)
        if (list.isEmpty()) extra.remove(kind.extraKey) else extra[kind.extraKey] = list
        return true
    }

    fun setMediaDisplay(chat: MutableList<Any?>, messageId: Int, display: NativeMediaDisplay) {
        mutableExtra(mutableMessage(chat, messageId))["media_display"] = display.value
    }

    fun switchSwipe(chat: MutableList<Any?>, messageId: Int, delta: Int): Boolean {
        val row = mutableMessage(chat, messageId)
        ensureSwipes(row)
        val swipes = row.stringList("swipes") ?: return false
        if (swipes.isEmpty()) return false
        val current = row.intValue("swipe_id").coerceIn(0, swipes.lastIndex)
        val next = (current + delta).floorMod(swipes.size)
        row["swipe_id"] = next
        row["mes"] = swipes[next]
        return true
    }

    fun createSwipe(chat: MutableList<Any?>, messageId: Int, text: String) {
        val row = mutableMessage(chat, messageId)
        ensureSwipes(row)
        val swipes = row.stringList("swipes")?.toMutableList() ?: mutableListOf()
        swipes.add(text)
        row["swipes"] = swipes
        val info = row.listValue("swipe_info")?.toMutableList() ?: mutableListOf()
        info.add(createSwipeInfo())
        row["swipe_info"] = info
        row["swipe_id"] = swipes.lastIndex
        row["mes"] = text
    }

    fun deleteSwipe(chat: MutableList<Any?>, messageId: Int, swipeId: Int): Boolean {
        val row = mutableMessage(chat, messageId)
        ensureSwipes(row)
        val swipes = row.stringList("swipes")?.toMutableList() ?: return false
        if (swipes.size <= 1 || swipeId !in swipes.indices) return false
        swipes.removeAt(swipeId)
        row["swipes"] = swipes
        val info = row.listValue("swipe_info")?.toMutableList()
        if (info != null && swipeId in info.indices) {
            info.removeAt(swipeId)
            row["swipe_info"] = info
        }
        val current = row.intValue("swipe_id")
        val next = when {
            current > swipeId -> current - 1
            current == swipeId -> swipeId.coerceAtMost(swipes.lastIndex)
            else -> current.coerceAtMost(swipes.lastIndex)
        }
        row["swipe_id"] = next
        row["mes"] = swipes[next]
        return true
    }

    fun createCheckpoint(
        chat: MutableList<Any?>,
        currentChatName: String,
        messageId: Int,
        name: String,
    ): NativeChatCopy {
        val row = mutableMessage(chat, messageId)
        mutableExtra(row)["bookmark_link"] = name
        return NativeChatCopy(
            linkedName = name,
            chatCopy = copyThroughMessage(chat, currentChatName, messageId),
        )
    }

    fun createBranch(
        chat: MutableList<Any?>,
        currentChatName: String,
        messageId: Int,
        name: String,
    ): NativeChatCopy {
        val row = mutableMessage(chat, messageId)
        val extra = mutableExtra(row)
        val branches = extra.listValue("branches")?.toMutableList() ?: mutableListOf()
        branches.add(name)
        extra["branches"] = branches
        return NativeChatCopy(
            linkedName = name,
            chatCopy = copyThroughMessage(chat, currentChatName, messageId),
        )
    }

    internal fun mutableMessage(chat: MutableList<Any?>, messageId: Int): MutableMap<String, Any?> {
        val index = rowIndex(chat, messageId)
        val row = chat[index].asStringKeyMap().toMutableLinkedMap()
        chat[index] = row
        return row
    }

    internal fun messageCount(chat: List<Any?>): Int = chat.size - headerOffset(chat)

    private fun copyThroughMessage(chat: List<Any?>, currentChatName: String, messageId: Int): List<Any?> {
        val offset = headerOffset(chat)
        val header = if (offset == 1) chat.first().asStringKeyMap().toMutableLinkedMap() else linkedMapOf()
        val metadata = header["chat_metadata"].asStringKeyMap().toMutableLinkedMap()
        metadata["main_chat"] = currentChatName.removeSuffix(".jsonl")
        header["chat_metadata"] = metadata
        if (!header.containsKey("user_name")) header["user_name"] = "unused"
        if (!header.containsKey("character_name")) header["character_name"] = "unused"
        val endExclusive = rowIndex(chat, messageId) + 1
        return listOf(header) + chat.subList(offset, endExclusive).map { deepCopy(it) }
    }

    private fun rowIndex(chat: List<Any?>, messageId: Int): Int {
        val index = headerOffset(chat) + messageId
        require(messageId >= 0 && index in chat.indices) { "Invalid message index: $messageId" }
        return index
    }

    private fun headerOffset(chat: List<Any?>): Int =
        if (chat.firstOrNull().asStringKeyMap().isChatHeader()) 1 else 0

    private fun Map<String, Any?>.isChatHeader(): Boolean =
        containsKey("chat_metadata") || containsKey("user_name") || containsKey("character_name") || containsKey("create_date")

    private fun mutableExtra(row: MutableMap<String, Any?>): MutableMap<String, Any?> {
        val extra = row["extra"].asStringKeyMap().toMutableLinkedMap()
        row["extra"] = extra
        return extra
    }

    private fun ensureSwipes(row: MutableMap<String, Any?>) {
        val mes = row["mes"]?.toString() ?: ""
        if (row.stringList("swipes") == null) row["swipes"] = listOf(mes)
        if (row["swipe_id"] !is Number) row["swipe_id"] = 0
        val swipes = row.stringList("swipes") ?: emptyList()
        val info = row.listValue("swipe_info")?.toMutableList()
        if (info == null || info.size != swipes.size) {
            row["swipe_info"] = swipes.map { createSwipeInfo() }
        }
    }

    private fun createSwipeInfo(): Map<String, Any?> =
        linkedMapOf(
            "send_date" to "",
            "gen_started" to null,
            "gen_finished" to null,
            "extra" to emptyMap<String, Any?>(),
        )

    private fun Int.floorMod(modulus: Int): Int =
        ((this % modulus) + modulus) % modulus

    private fun MutableMap<String, Any?>.stringList(key: String): List<String>? =
        when (val value = this[key]) {
            is List<*> -> value.map { it?.toString() ?: "" }
            is Array<*> -> value.map { it?.toString() ?: "" }
            else -> null
        }

    private fun Map<String, Any?>.intValue(key: String, default: Int = 0): Int =
        when (val value = this[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }

    private fun Map<String, Any?>.listValue(key: String): List<Any?>? =
        when (val value = this[key]) {
            is List<*> -> value.toList()
            is Array<*> -> value.toList()
            else -> null
        }

    private fun Any?.asStringKeyMap(): Map<String, Any?> =
        (this as? Map<*, *>)?.mapNotNull { (key, value) ->
            key?.toString()?.let { it to value }
        }?.toMap() ?: emptyMap()

    private fun Map<String, Any?>.toMutableLinkedMap(): MutableMap<String, Any?> =
        LinkedHashMap(this)

    private fun deepCopy(value: Any?): Any? =
        when (value) {
            is Map<*, *> -> linkedMapOf<String, Any?>().also { out ->
                value.forEach { (key, nested) ->
                    if (key != null) out[key.toString()] = deepCopy(nested)
                }
            }
            is List<*> -> value.map { deepCopy(it) }
            is Array<*> -> value.map { deepCopy(it) }
            else -> value
        }
}
