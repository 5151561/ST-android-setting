package io.github.sanitised.st.chat

sealed class ChatTarget {
    object Current : ChatTarget()
    data class CharacterChat(val avatar: String, val chatFile: String? = null) : ChatTarget()
    data class GroupChat(val groupId: String, val chatId: String? = null) : ChatTarget()
}
