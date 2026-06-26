package io.github.sanitised.st.chat

import io.github.sanitised.st.chat.contract.ContractFixtures
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QuickReplyRuntimeTest {
    @get:Rule
    val temp = TemporaryFolder()

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
    fun listsVisibleRepliesFromQuickReplyV2Settings() {
        val items = QuickReplyRuntime.visibleReplies(
            extensionSettings = mapOf(
                "quickReplyV2" to mapOf(
                    "isEnabled" to true,
                    "config" to mapOf(
                        "setList" to listOf(
                            mapOf("set" to "Greetings", "isVisible" to true)
                        )
                    )
                )
            ),
            setJsonByName = mapOf(
                "Greetings" to ContractFixtures.text("extensions/quick-replies-greetings.json")
            )
        )

        assertEquals(listOf("Wave", "Draft"), items.map { it.label })
    }

    @Test
    fun mergesGlobalChatAndCharacterQuickReplyV2Sets() {
        val items = QuickReplyRuntime.visibleReplies(
            extensionSettings = mapOf(
                "quickReplyV2" to mapOf(
                    "isEnabled" to true,
                    "config" to mapOf(
                        "setList" to listOf(mapOf("set" to "Global", "isVisible" to true))
                    ),
                    "characterConfigs" to mapOf(
                        "Alice.png" to mapOf(
                            "setList" to listOf(mapOf("set" to "Character", "isVisible" to true))
                        )
                    )
                )
            ),
            setJsonByName = mapOf(
                "Global" to quickReplySetJson("Global", "Global action"),
                "Chat" to quickReplySetJson("Chat", "Chat action"),
                "Character" to quickReplySetJson("Character", "Character action"),
            ),
            chatMetadata = mapOf(
                "quickReply" to mapOf(
                    "setList" to listOf(mapOf("set" to "Chat", "isVisible" to true))
                )
            ),
            characterAvatar = "Alice.png",
        )

        assertEquals(listOf("Global action", "Chat action", "Character action"), items.map { it.label })
    }

    @Test
    fun readsQuickReplySettingsFromSillyTavernExtensionSettingsFile() {
        val userDir = temp.newFolder("default-user")
        File(userDir, "QuickReplies").apply { mkdirs() }
            .resolve("Greetings.json")
            .writeText(ContractFixtures.text("extensions/quick-replies-greetings.json"), Charsets.UTF_8)
        File(userDir, "settings.json").writeText(
            """
                {
                  "extension_settings": {
                    "quickReplyV2": {
                      "isEnabled": true,
                      "config": {
                        "setList": [
                          { "set": "Greetings", "isVisible": true }
                        ]
                      }
                    }
                  }
                }
            """.trimIndent(),
            Charsets.UTF_8
        )

        val items = QuickReplyRuntime.visibleReplies(temp.root)

        assertEquals(listOf("Wave", "Draft"), items.map { it.label })
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

    private fun quickReplySetJson(name: String, label: String): String =
        """
            {
              "version": 2,
              "name": "$name",
              "qrList": [
                { "id": 1, "label": "$label", "message": "$label", "isHidden": false }
              ]
            }
        """.trimIndent()
}
