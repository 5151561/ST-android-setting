package io.github.sanitised.st.chat

import io.github.sanitised.st.chat.contract.ContractFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickReplyRuntimeTest {
    @Test
    fun listsVisibleRepliesFromEnabledSets() {
        val items = QuickReplyRuntime.visibleReplies(
            extensionSettings = ContractFixtures.json("extensions/extension-settings.json"),
            setJsonByName = mapOf(
                "Greetings" to ContractFixtures.text("extensions/quick-replies-greetings.json")
            )
        )

        assertEquals(listOf("Wave", "Draft"), items.map { it.label })
        assertEquals("fa-hand", items.first().icon)
        assertTrue(items.none { it.label == "Hidden" })
    }

    @Test
    fun executingPlainReplySendsMessageWhenSetAllowsSend() {
        val item = QuickReplyItem(setName = "Greetings", label = "Wave", icon = "", message = "waves hello")

        assertEquals(
            QuickReplyExecution.Send("waves hello"),
            QuickReplyRuntime.execute(item)
        )
    }

    @Test
    fun executingDisableSendReplyUpdatesDraftOnly() {
        val item = QuickReplyItem(
            setName = "Greetings",
            label = "Draft",
            icon = "",
            message = "draft only",
            disableSend = true
        )

        assertEquals(
            QuickReplyExecution.Draft("draft only"),
            QuickReplyRuntime.execute(item)
        )
    }

    @Test
    fun executingSlashCommandReportsUnsupportedNativeAction() {
        val item = QuickReplyItem(setName = "Default", label = "New Chat", icon = "", message = "/newchat")

        assertEquals(
            QuickReplyExecution.Unsupported("暂不支持原生执行 Slash Command: /newchat"),
            QuickReplyRuntime.execute(item)
        )
    }
}
