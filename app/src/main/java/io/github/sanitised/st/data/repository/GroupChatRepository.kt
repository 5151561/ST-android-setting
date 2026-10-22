package io.github.sanitised.st.data.repository

import io.github.sanitised.st.api.GroupCreateRequest
import io.github.sanitised.st.api.GroupSummary
import io.github.sanitised.st.api.TavernCoreApi
import io.github.sanitised.st.data.LocalTavernLibraryReader

interface GroupChatRepository {
    suspend fun listGroups(serverRunning: Boolean = true): List<GroupSummary>
    suspend fun createGroup(request: GroupCreateRequest): GroupSummary
    suspend fun editGroup(group: GroupSummary)
    suspend fun deleteGroup(groupId: String)
    suspend fun getGroupChatJsonl(chatId: String): MutableList<Any?>
    suspend fun saveGroupChatJsonl(chatId: String, chat: List<Any?>)
    fun groupChatPreview(chatId: String): String? = null
}

class DefaultGroupChatRepository(
    private val clientProvider: () -> TavernCoreApi,
    private val localReader: LocalTavernLibraryReader? = null,
) : GroupChatRepository {
    override suspend fun listGroups(serverRunning: Boolean): List<GroupSummary> {
        return if (serverRunning) {
            clientProvider().listGroups().map { group ->
                if (group.lastMessage.isNullOrBlank()) {
                    group.copy(lastMessage = localReader?.groupChatPreview(group.chatId))
                } else {
                    group
                }
            }
        } else {
            localReader?.listGroups().orEmpty()
        }
    }

    override suspend fun createGroup(request: GroupCreateRequest): GroupSummary =
        clientProvider().createGroup(request)

    override suspend fun editGroup(group: GroupSummary) {
        clientProvider().editGroup(group)
    }

    override suspend fun deleteGroup(groupId: String) {
        clientProvider().deleteGroup(groupId)
    }

    override suspend fun getGroupChatJsonl(chatId: String): MutableList<Any?> =
        clientProvider().getGroupChatJsonl(chatId)

    override suspend fun saveGroupChatJsonl(chatId: String, chat: List<Any?>) {
        clientProvider().saveGroupChatJsonl(chatId, chat)
    }

    override fun groupChatPreview(chatId: String): String? =
        localReader?.groupChatPreview(chatId)
}
