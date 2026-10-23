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
}
