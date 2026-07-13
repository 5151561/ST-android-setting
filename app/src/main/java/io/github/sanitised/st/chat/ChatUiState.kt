package io.github.sanitised.st.chat

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal fun visibleChatMessages(messages: List<ChatMessage>): List<ChatMessage> =
    messages

internal fun conversationDateLabel(messages: List<ChatMessage>): String? =
    visibleChatMessages(messages)
        .firstOrNull()
        ?.sendDate
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let(::formatChatDateLabel)

/**
 * 把消息里的 send_date 转成本地化日期头。聊天数据里存在两种历史格式:
 * ISO UTC(如 2026-07-12T09:24:06.116Z)与 ST 人类可读格式(如 july 13, 2026 9:41am,
 * 本地时区)。解析失败时原样返回,不做猜测。
 */
internal fun formatChatDateLabel(raw: String): String {
    val trimmed = raw.trim()
    val output = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA)
    val isoUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }
    val stHumanized = SimpleDateFormat("MMMM d, yyyy h:mma", Locale.ENGLISH).apply {
        isLenient = false
    }
    for (parser in listOf(isoUtc, stHumanized)) {
        runCatching { parser.parse(trimmed) }.getOrNull()?.let { return output.format(it) }
    }
    return trimmed
}

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
