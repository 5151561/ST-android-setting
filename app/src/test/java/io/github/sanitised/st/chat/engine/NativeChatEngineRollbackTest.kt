package io.github.sanitised.st.chat.engine

import io.github.sanitised.st.chat.ChatMessage
import io.github.sanitised.st.chat.ChatStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeChatEngineRollbackTest {

    @Test
    fun removesOptimisticUserAndAssistantMessagesOnFailure() {
        val store = ChatStore()
        store.addMessage(message(0, "old"))
        store.addMessage(message(1, "user"))
        store.addMessage(message(2, "assistant"))

        rollbackOptimisticMessages(store, userMessageId = 1, assistantMessageId = 2)

        assertEquals(listOf(0), store.messages.map { it.id })
    }

    private fun message(id: Int, text: String) = ChatMessage(
        id = id,
        name = "n",
        mes = text,
        isUser = id == 1,
        isSystem = false,
        sendDate = "",
        swipeId = 0,
        swipes = emptyList(),
        extra = JSONObject(),
    )
}
