package io.github.sanitised.st.api

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TauriTavernJniClientContractTest {

    @Test
    fun `healthCheck unwraps ok envelope`() = runTest {
        val client = TauriTavernJniClient { command, _ ->
            assertEquals("ping", command)
            """{"ok":true,"data":{"status":"ok","service":"TauriTavern"}}"""
        }
        val health = client.healthCheck()
        assertTrue(health.ok)
        assertEquals("TauriTavern", health.version)
    }

    @Test
    fun `healthCheck returns ok false when bridge returns error envelope`() = runTest {
        val client = TauriTavernJniClient { _, _ ->
            """{"ok":false,"error":{"code":"not_initialized","message":"Runtime not initialized"}}"""
        }
        val health = client.healthCheck()
        assertFalse(health.ok)
    }

    @Test
    fun `listCharacters and getCharacter map Rust CharacterDto fields accurately`() = runTest {
        val recordedCommands = mutableListOf<String>()
        val client = TauriTavernJniClient { command, payload ->
            recordedCommands += command
            when (command) {
                "characters_list" -> """
                    {
                      "ok": true,
                      "data": [
                        {
                          "name": "Seraphina",
                          "avatar": "Seraphina.png",
                          "description": "Mage of Dawn",
                          "first_mes": "Greetings!",
                          "fav": true,
                          "tags": ["magic", "elf"],
                          "date_last_chat": 1700000123
                        }
                      ]
                    }
                """.trimIndent()
                "character_get" -> {
                    assertTrue(payload.contains("Seraphina.png"))
                    """
                    {
                      "ok": true,
                      "data": {
                        "name": "Seraphina",
                        "avatar": "Seraphina.png",
                        "description": "Mage of Dawn",
                        "personality": "Wise",
                        "scenario": "Tower",
                        "first_mes": "Greetings!",
                        "mes_example": "<START>",
                        "creatorcomment": "v1 notes",
                        "system_prompt": "Stay in character",
                        "post_history_instructions": "Never break role",
                        "alternate_greetings": ["Hi there!", "Welcome back!"],
                        "tags": ["magic", "elf"],
                        "fav": true,
                        "extensions": {
                          "talkativeness": 0.7,
                          "world": "Eldoria",
                          "depth_prompt": {
                            "prompt": "Remember the runes",
                            "depth": 3,
                            "role": "system"
                          }
                        }
                      }
                    }
                    """.trimIndent()
                }
                else -> """{"ok":true,"data":{}}"""
            }
        }

        val list = client.listCharacters()
        assertEquals(1, list.size)
        assertEquals("Seraphina", list[0].name)
        assertEquals("Seraphina.png", list[0].id)
        assertTrue(list[0].isFavorite)
        assertEquals(listOf("magic", "elf"), list[0].tags)

        val detail = client.getCharacter("Seraphina.png")
        assertEquals("Seraphina", detail.name)
        assertEquals(listOf("Hi there!", "Welcome back!"), detail.alternateGreetings)
        assertEquals("Eldoria", detail.world)
        assertEquals("Remember the runes", detail.depthPrompt)
        assertEquals(3, detail.depthPromptDepth)
        assertEquals(listOf("characters_list", "character_get"), recordedCommands)
    }

    @Test
    fun `generateChatCompletionStream polls stream start and read until done`() = runTest {
        var readCallCount = 0
        val client = TauriTavernJniClient { command, _ ->
            when (command) {
                "chat_completion_stream_start" ->
                    """{"ok":true,"data":{"streamId":"stream-42"}}"""
                "chat_completion_stream_read" -> {
                    readCallCount++
                    if (readCallCount == 1) {
                        """
                        {
                          "ok": true,
                          "data": {
                            "streamId": "stream-42",
                            "status": "running",
                            "events": [
                              {"type": "chunk", "seq": 1, "data": "data: {\"choices\":[{\"delta\":{\"content\":\"Hello \"}}]}"},
                              {"type": "chunk", "seq": 2, "data": "world!"}
                            ]
                          }
                        }
                        """.trimIndent()
                    } else {
                        """
                        {
                          "ok": true,
                          "data": {
                            "streamId": "stream-42",
                            "status": "done",
                            "events": [
                              {"type": "done", "seq": 3}
                            ]
                          }
                        }
                        """.trimIndent()
                    }
                }
                else -> """{"ok":true,"data":{}}"""
            }
        }

        val chunks = client.generateChatCompletionStream(mapOf("model" to "gpt-4o")).toList()
        assertEquals(listOf("Hello ", "world!"), chunks)
        assertEquals(2, readCallCount)
    }

    @Test
    fun `error envelope throws IllegalStateException with code and message`() = runTest {
        val client = TauriTavernJniClient { _, _ ->
            """{"ok":false,"error":{"code":"not_found","message":"Character missing"}}"""
        }
        val thrown = runCatching { client.getCharacter("missing.png") }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertTrue(thrown?.message.orEmpty().contains("[not_found] Character missing"))
    }

    @Test
    fun `fetchModels and testConnection send non-null string fields for chat_completion_status`() = runTest {
        val capturedPayloads = mutableListOf<String>()
        val client = TauriTavernJniClient { command, payload ->
            assertEquals("chat_completion_status", command)
            capturedPayloads += payload
            """{"ok":true,"data":{"data":[{"id":"gemini-2.5-flash"},{"id":"gemini-2.5-pro"}]}}"""
        }

        val models = client.fetchModels(mode = "cc", sourceValue = "makersuite", apiServer = "")
        assertEquals(listOf("gemini-2.5-flash", "gemini-2.5-pro"), models)

        val testResult = client.testConnection(mode = "cc", sourceValue = "makersuite", apiServer = "")
        assertTrue(testResult.success)
        assertEquals(listOf("gemini-2.5-flash", "gemini-2.5-pro"), testResult.models)

        capturedPayloads.forEach { rawJson ->
            assertFalse("Payload should not contain null reverse_proxy: $rawJson", rawJson.contains("\"reverse_proxy\":null"))
            assertFalse("Payload should not contain null custom_url: $rawJson", rawJson.contains("\"custom_url\":null"))
            assertTrue("Payload should contain empty reverse_proxy string: $rawJson", rawJson.contains("\"reverse_proxy\":\"\""))
        }
    }

    @Test
    fun `testConnection routes custom provider endpoint to custom_url instead of reverse_proxy`() = runTest {
        var capturedPayload = ""
        val client = TauriTavernJniClient { command, payload ->
            assertEquals("chat_completion_status", command)
            capturedPayload = payload
            """{"ok":true,"data":{"data":[{"id":"deepseek-v3"}]}}"""
        }

        val result = client.testConnection(
            mode = "cc",
            sourceValue = "custom",
            apiServer = "https://my-oneapi.example.com/v1"
        )
        assertTrue(result.success)
        assertEquals(listOf("deepseek-v3"), result.models)
        assertTrue(capturedPayload.contains("\"custom_url\":\"https://my-oneapi.example.com/v1\""))
        assertTrue(capturedPayload.contains("\"reverse_proxy\":\"\""))
    }

    @Test
    fun `testConnection forwards bypass_status_check and reports ready detail when status is bypassed`() = runTest {
        var capturedStatusPayload = ""
        val client = TauriTavernJniClient { command, payload ->
            when (command) {
                "settings_get" -> """
                    {
                      "ok": true,
                      "data": {
                        "oai_settings": {
                          "chat_completion_source": "claude",
                          "claude_model": "claude-3-7-sonnet-20250219",
                          "bypass_status_check": true
                        }
                      }
                    }
                """.trimIndent()
                "chat_completion_status" -> {
                    capturedStatusPayload = payload
                    """{"ok":true,"data":{"data":[],"bypass":true}}"""
                }
                else -> """{"ok":true,"data":{}}"""
            }
        }

        val result = client.testConnection(
            mode = "cc",
            sourceValue = "claude",
            apiServer = ""
        )
        assertTrue(result.success)
        assertTrue(result.models.isEmpty())
        assertTrue(
            "Expected bypass_status_check=true in status payload: $capturedStatusPayload",
            capturedStatusPayload.contains("\"bypass_status_check\":true")
        )
        assertTrue(
            "Expected ready detail message with configured model, got: ${result.detail}",
            result.detail.orEmpty().contains("连接就绪") &&
                result.detail.orEmpty().contains("claude-3-7-sonnet-20250219")
        )
    }

    @Test
    fun `saveConnectionProfile with non-empty url preserves model in description and applyConnectionProfile restores model and rotates secret`() = runTest {
        var savedConnectionJson = ""
        var savedSettingsJson = ""
        var rotatedSecretPayload = ""

        val client = TauriTavernJniClient { command, payload ->
            when (command) {
                "settings_get" -> """
                    {
                      "ok": true,
                      "data": {
                        "main_api": "openai",
                        "oai_settings": {
                          "chat_completion_source": "custom",
                          "custom_url": "https://relay.example.com/v1",
                          "custom_model": "gpt-4.1-mini",
                          "custom_api_format": "openai_compat"
                        },
                        "power_user": {}
                      }
                    }
                """.trimIndent()
                "secrets_read_state" -> """
                    {
                      "ok": true,
                      "data": {
                        "api_key_custom": [
                          {"id": "sec-custom-99", "label": "RelayKey", "active": true}
                        ]
                      }
                    }
                """.trimIndent()
                "save_llm_connection" -> {
                    savedConnectionJson = payload
                    """{"ok":true,"data":{}}"""
                }
                "list_llm_connections" -> """
                    {
                      "ok": true,
                      "data": [
                        {
                          "id": "my-relay",
                          "displayName": "My Relay",
                          "chatCompletionSource": "custom",
                          "description": "custom · gpt-4.1-mini"
                        }
                      ]
                    }
                """.trimIndent()
                "load_llm_connection" -> """
                    {
                      "ok": true,
                      "data": {
                        "id": "my-relay",
                        "displayName": "My Relay",
                        "description": "custom · gpt-4.1-mini",
                        "provider": {
                          "chatCompletionSource": "custom",
                          "customApiFormat": "openai_compat"
                        },
                        "endpoint": {
                          "baseUrl": "https://relay.example.com/v1"
                        },
                        "auth": {
                          "secretRef": {
                            "key": "api_key_custom",
                            "id": "sec-custom-99"
                          }
                        },
                        "routing": {}
                      }
                    }
                """.trimIndent()
                "secrets_rotate" -> {
                    rotatedSecretPayload = payload
                    """{"ok":true,"data":{}}"""
                }
                "settings_save" -> {
                    savedSettingsJson = payload
                    """{"ok":true,"data":{}}"""
                }
                else -> """{"ok":true,"data":{}}"""
            }
        }

        client.saveConnectionProfile(
            ConnectionProfile(
                label = "My Relay",
                url = "https://relay.example.com/v1",
                lastConnection = 123L
            )
        )
        assertTrue(
            "Expected save_llm_connection payload to preserve 'custom · gpt-4.1-mini' description even when url is non-empty: $savedConnectionJson",
            savedConnectionJson.contains("\"description\":\"custom · gpt-4.1-mini\"")
        )

        val applied = client.applyConnectionProfile("My Relay")
        assertTrue(applied)
        assertTrue(
            "Expected applyConnectionProfile to restore custom_model in oai_settings: $savedSettingsJson",
            savedSettingsJson.contains("\"custom_model\":\"gpt-4.1-mini\"")
        )
        assertTrue(
            "Expected applyConnectionProfile to rotate active secret via secrets_rotate: $rotatedSecretPayload",
            rotatedSecretPayload.contains("\"key\":\"api_key_custom\"") &&
                rotatedSecretPayload.contains("\"id\":\"sec-custom-99\"")
        )
    }

    @Test
    fun `real JNI bridge seeds default Seraphina character and presets from tauritavern_default assets`() = runTest {
        val tempRoot = java.nio.file.Files.createTempDirectory("tt-jni-default-seed-test").toFile()
        try {
            val candidates = listOf(
                java.io.File("src/main/assets/tauritavern_default/content"),
                java.io.File("app/src/main/assets/tauritavern_default/content"),
                java.io.File("../TauriTavern/default/content"),
                java.io.File("TauriTavern/default/content")
            )
            val defaultContentDir = candidates.firstOrNull { it.isDirectory }
                ?: error("Could not locate tauritavern default/content directory from ${java.io.File(".").absolutePath}")

            TauriTavernJniBridge.initialize(
                dataRoot = tempRoot.absolutePath,
                defaultContentRoot = defaultContentDir.absolutePath
            )

            val client = TauriTavernJniClient.instance
            val characters = client.listCharacters()
            assertTrue(
                "Expected default character Seraphina to be seeded and returned by listCharacters(), got: $characters",
                characters.any { it.id == "default_Seraphina.png" && it.name == "Seraphina" }
            )

            val seraphina = client.getCharacter("default_Seraphina.png")
            assertEquals("Seraphina", seraphina.name)
            assertTrue(seraphina.description.isNotBlank())
            assertTrue(seraphina.firstMessage.isNotBlank())

            val worlds = client.listWorldInfos()
            assertTrue(
                "Expected default world Eldoria to be seeded, got: $worlds",
                worlds.any { it.name == "Eldoria" }
            )

            val presetLibrary = client.getPresetLibrary()
            val openaiCategory = presetLibrary.categories.firstOrNull { it.apiId == "openai" }
            assertTrue(
                "Expected Default OpenAI preset to be seeded in PresetLibrary, got: ${presetLibrary.categories}",
                openaiCategory?.presets?.any { it.name == "Default" } == true
            )

            // Verify missing chat files return empty instead of [NOT_FOUND] error
            val missingChat = client.getChatJsonl("default_Seraphina.png", "missing-chat")
            assertTrue(missingChat.isEmpty())
            val missingGroupChat = client.getGroupChatJsonl("missing-group-chat")
            assertTrue(missingGroupChat.isEmpty())
            val missingTail = client.getChatTailJsonl("default_Seraphina.png", "missing-chat", maxLines = 10)
            assertEquals(null, missingTail.header)
            assertTrue(missingTail.messages.isEmpty())
            assertFalse(missingTail.hasMoreBefore)

            // Verify O(1) incremental append, header integrity read, and tail/before pagination over real JNI
            val chatFile = "2026-10-23@18h00m00s"
            val header = mapOf(
                "user_name" to "User",
                "character_name" to "Seraphina",
                "chat_metadata" to mapOf("integrity" to "integ-123")
            )
            val appendedFirst = client.appendChatJsonl(
                avatar = "default_Seraphina.png",
                chatFile = chatFile,
                newMessages = listOf(
                    mapOf("name" to "Seraphina", "is_user" to false, "mes" to "Msg 1"),
                    mapOf("name" to "User", "is_user" to true, "mes" to "Msg 2")
                ),
                headerIfMissing = header
            )
            assertTrue(appendedFirst)
            assertEquals("integ-123", client.getChatIntegrity("default_Seraphina.png", chatFile))

            val appendedSecond = client.appendChatJsonl(
                avatar = "default_Seraphina.png",
                chatFile = chatFile,
                newMessages = listOf(
                    mapOf("name" to "Seraphina", "is_user" to false, "mes" to "Msg 3"),
                    mapOf("name" to "User", "is_user" to true, "mes" to "Msg 4")
                ),
                expectedIntegrity = "integ-123"
            )
            assertTrue(appendedSecond)

            val tail = client.getChatTailJsonl("default_Seraphina.png", chatFile, maxLines = 2)
            assertEquals(listOf("Msg 3", "Msg 4"), tail.messages.map { it["mes"] })
            assertTrue(tail.hasMoreBefore)

            val before = client.getChatBeforeJsonl(
                avatar = "default_Seraphina.png",
                chatFile = chatFile,
                cursor = requireNotNull(tail.cursor),
                maxLines = 2
            )
            assertEquals(listOf("Msg 1", "Msg 2"), before.messages.map { it["mes"] })
            assertFalse(before.hasMoreBefore)

            // Verify listRecentChats maps Rust ChatSearchResultDto accurately and can be opened via NativeChatLoader
            val recentChats = client.listRecentChats()
            val recentForSeraphina = recentChats.firstOrNull {
                it.id == "default_Seraphina/$chatFile"
            } ?: error("Expected recent chat for default_Seraphina/$chatFile, got: $recentChats")
            assertEquals("default_Seraphina.png", recentForSeraphina.characterId)
            assertEquals("Seraphina", recentForSeraphina.characterName)
            assertEquals("Msg 4", recentForSeraphina.lastMessage)
            assertTrue(recentForSeraphina.lastUpdated > 0L)

            val chatItem = io.github.sanitised.st.ui.screens.buildHomeChatItems(
                io.github.sanitised.st.ui.screens.LocalTavernLibrarySnapshot(
                    characters = characters,
                    recentChats = recentChats
                )
            ).first { it.chatFile == chatFile }
            assertEquals("default_Seraphina.png", chatItem.characterId)
            assertEquals(chatFile, chatItem.chatFile)

            val store = io.github.sanitised.st.chat.ChatStore()
            val loader = io.github.sanitised.st.chat.NativeChatLoader(store) { client }
            val opened = loader.openCharacter(
                avatar = chatItem.characterId,
                chatFile = chatItem.chatFile
            )
            assertTrue("Expected NativeChatLoader.openCharacter to succeed from chat list item", opened)
            assertEquals("default_Seraphina.png", store.avatarUrl)
            assertEquals(chatFile, store.chatFile)
            assertEquals(4, store.messages.size)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
