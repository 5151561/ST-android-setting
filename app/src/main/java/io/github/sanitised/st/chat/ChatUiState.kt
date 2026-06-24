package io.github.sanitised.st.chat

internal fun visibleChatMessages(messages: List<ChatMessage>): List<ChatMessage> =
    messages

internal fun conversationDateLabel(messages: List<ChatMessage>): String? =
    visibleChatMessages(messages)
        .firstOrNull()
        ?.sendDate
        ?.trim()
        ?.takeIf { it.isNotBlank() }

internal fun chatListScrollTargetIndex(
    visibleMessages: List<ChatMessage>,
    dateLabel: String?
): Int? {
    val itemCount = visibleMessages.size + if (dateLabel != null) 1 else 0
    return if (itemCount > 0) itemCount - 1 else null
}

internal fun chatMessageItemKey(message: ChatMessage): String = "message-${message.id}"

internal fun readyTargetCommandKey(target: ChatTarget): String =
    when (target) {
        ChatTarget.Current -> "snapshot"
        is ChatTarget.CharacterChat -> "character:${target.avatar}:${target.chatFile.orEmpty()}"
        is ChatTarget.GroupChat -> "group:${target.groupId}:${target.chatId.orEmpty()}"
    }
