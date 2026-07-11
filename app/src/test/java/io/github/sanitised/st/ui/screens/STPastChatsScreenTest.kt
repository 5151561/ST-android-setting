package io.github.sanitised.st.ui.screens

import io.github.sanitised.st.api.CharacterChatSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class STPastChatsScreenTest {

    @Test
    fun filtersNativeBackupsFromVisiblePastChats() {
        val chats = listOf(
            CharacterChatSummary(id = "main", fileName = "main.jsonl"),
            CharacterChatSummary(
                id = "__native-backup__main__20260610-100001-000",
                fileName = "__native-backup__main__20260610-100001-000.jsonl",
            ),
            CharacterChatSummary(
                id = "main.native-backup-20260610-100000-000",
                fileName = "main.native-backup-20260610-100000-000.jsonl",
            ),
        )

        assertEquals(listOf("main.jsonl"), filterVisibleCharacterChats(chats).map { it.fileName })
    }
}
