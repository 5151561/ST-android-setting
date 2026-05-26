package io.github.sanitised.st.api

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TavernCoreClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueCsrf(token: String = "csrf-token") {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"token\":\"$token\"}"))
    }

    private fun assertCsrfRequest() {
        val request = server.takeRequest()
        assertEquals("/csrf-token", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun healthCheckReturnsOkWhenRootRespondsSuccessfully() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>SillyTavern</html>"))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        assertTrue(client.healthCheck().ok)
    }

    @Test
    fun healthCheckReturnsNotOkWhenRootIsUnavailable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("starting"))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        assertFalse(client.healthCheck().ok)
    }

    @Test
    fun listCharactersCallsSillyTavernCharactersAllAndMapsSummaryFields() = runBlocking {
        enqueueCsrf()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {
                        "name": "Seraphina",
                        "avatar": "Seraphina.png",
                        "date_last_chat": 2000,
                        "tags": ["mage"],
                        "data": {
                          "creator_notes": "Found in archives.",
                          "tags": ["friend", "story"],
                          "extensions": { "fav": true }
                        }
                      }
                    ]
                    """.trimIndent()
                )
        )

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val characters = client.listCharacters()

        assertCsrfRequest()
        assertEquals("/api/characters/all", server.takeRequest().path)
        assertEquals(1, characters.size)
        assertEquals("Seraphina.png", characters.first().id)
        assertEquals("Seraphina", characters.first().name)
        assertEquals("Seraphina.png", characters.first().avatarUrl)
        assertEquals("Found in archives.", characters.first().creatorNotes)
        assertEquals(listOf("friend", "story"), characters.first().tags)
        assertTrue(characters.first().isFavorite)
        assertEquals(2000L, characters.first().lastChatAt)
    }

    @Test
    fun postRequestsIncludeCsrfTokenWhenServerProvidesIt() = runBlocking {
        enqueueCsrf("csrf-123")
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        client.listCharacters()

        assertEquals("/csrf-token", server.takeRequest().path)
        val apiRequest = server.takeRequest()
        assertEquals("/api/characters/all", apiRequest.path)
        assertEquals("csrf-123", apiRequest.getHeader("x-csrf-token"))
    }

    @Test
    fun listCharactersSurfacesApiErrors() = runBlocking {
        enqueueCsrf()
        server.enqueue(MockResponse().setResponseCode(500).setBody("broken"))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        var thrown: IllegalStateException? = null
        try {
            client.listCharacters()
        } catch (error: IllegalStateException) {
            thrown = error
        }

        assertCsrfRequest()
        assertEquals("/api/characters/all", server.takeRequest().path)
        assertTrue(thrown?.message.orEmpty().contains("500"))
    }

    @Test
    fun getCharacterPostsAvatarUrlAndMapsEditableFields() = runBlocking {
        enqueueCsrf()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "name": "Seraphina",
                      "avatar": "Seraphina.png",
                      "description": "A careful archivist.",
                      "first_mes": "Welcome back.",
                      "mes_example": "<START>",
                      "data": {
                        "creator_notes": "Keep her precise.",
                        "system_prompt": "Stay in character.",
                        "post_history_instructions": "Remember the archive.",
                        "tags": ["archive"],
                        "creator": "Tester",
                        "character_version": "1.0",
                        "alternate_greetings": ["Again.", "Once more."],
                        "extensions": {
                          "fav": true,
                          "world": "Archive World",
                          "talkativeness": 0.7,
                          "depth_prompt": {
                            "prompt": "Remember the locked cabinet.",
                            "depth": 3,
                            "role": "assistant"
                          }
                        }
                      },
                      "chat": "Seraphina - 2026.jsonl",
                      "create_date": "2026-05-26T10:00:00.000Z",
                      "json_data": "{\"foreign_field\":true}"
                    }
                    """.trimIndent()
                )
        )

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val character = client.getCharacter("Seraphina.png")

        assertCsrfRequest()
        val request = server.takeRequest()
        assertEquals("/api/characters/get", request.path)
        assertTrue(request.body.readUtf8().contains("\"avatar_url\":\"Seraphina.png\""))
        assertEquals("Seraphina.png", character.id)
        assertEquals("Seraphina", character.name)
        assertEquals("A careful archivist.", character.description)
        assertEquals("Welcome back.", character.firstMessage)
        assertEquals("<START>", character.messageExample)
        assertEquals("Keep her precise.", character.creatorNotes)
        assertEquals("Stay in character.", character.systemPrompt)
        assertEquals("Remember the archive.", character.postHistoryInstructions)
        assertEquals(listOf("archive"), character.tags)
        assertEquals("Tester", character.creator)
        assertEquals("1.0", character.characterVersion)
        assertEquals("Archive World", character.world)
        assertEquals(0.7, character.talkativeness, 0.001)
        assertEquals(listOf("Again.", "Once more."), character.alternateGreetings)
        assertEquals("Remember the locked cabinet.", character.depthPrompt)
        assertEquals(3, character.depthPromptDepth)
        assertEquals("assistant", character.depthPromptRole)
        assertEquals("Seraphina - 2026.jsonl", character.chat)
        assertEquals("2026-05-26T10:00:00.000Z", character.createDate)
        assertEquals("{\"foreign_field\":true}", character.rawJsonData)
        assertTrue(character.isFavorite)
    }

    @Test
    fun createCharacterPostsCreatePayloadAndReturnsAvatarName() = runBlocking {
        enqueueCsrf()
        server.enqueue(MockResponse().setResponseCode(200).setBody("Seraphina.png"))
        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val avatar = client.createCharacter(
            CharacterSaveRequest(
                name = "Seraphina",
                description = "A careful archivist.",
                firstMessage = "Welcome back.",
                tags = listOf("archive"),
                isFavorite = true
            )
        )

        assertCsrfRequest()
        val request = server.takeRequest()
        assertEquals("/api/characters/create", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"ch_name\":\"Seraphina\""))
        assertTrue(body.contains("\"description\":\"A careful archivist.\""))
        assertTrue(body.contains("\"first_mes\":\"Welcome back.\""))
        assertTrue(body.contains("\"tags\":[\"archive\"]"))
        assertTrue(body.contains("\"fav\":\"true\""))
        assertEquals("Seraphina.png", avatar)
    }

    @Test
    fun updateCharacterPostsEditPayloadWithAdvancedFields() = runBlocking {
        enqueueCsrf()
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        client.updateCharacter(
            CharacterSaveRequest(
                avatar = "Seraphina.png",
                name = "Seraphina",
                description = "Updated description",
                firstMessage = "Updated hello",
                personality = "Careful and direct.",
                scenario = "Inside the archive.",
                messageExample = "<START>",
                creatorNotes = "Notes for users.",
                systemPrompt = "Stay precise.",
                postHistoryInstructions = "Use the ledger.",
                tags = listOf("archive", "updated"),
                creator = "Tester",
                characterVersion = "1.1",
                world = "Archive World",
                talkativeness = 0.8,
                alternateGreetings = listOf("Hello again."),
                depthPrompt = "Keep context.",
                depthPromptDepth = 2,
                depthPromptRole = "user",
                chat = "Seraphina - 2026.jsonl",
                createDate = "2026-05-26T10:00:00.000Z",
                rawJsonData = "{\"foreign_field\":true}",
                isFavorite = false
            )
        )

        assertCsrfRequest()
        val request = server.takeRequest()
        assertEquals("/api/characters/edit", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"avatar_url\":\"Seraphina.png\""))
        assertTrue(body, body.contains("\"ch_name\":\"Seraphina\""))
        assertTrue(body, body.contains("\"description\":\"Updated description\""))
        assertTrue(body, body.contains("\"first_mes\":\"Updated hello\""))
        assertTrue(body, body.contains("\"personality\":\"Careful and direct.\""))
        assertTrue(body, body.contains("\"scenario\":\"Inside the archive.\""))
        assertTrue(body, body.contains("\"mes_example\":\"<START>\""))
        assertTrue(body, body.contains("\"creator_notes\":\"Notes for users.\""))
        assertTrue(body, body.contains("\"system_prompt\":\"Stay precise.\""))
        assertTrue(body, body.contains("\"post_history_instructions\":\"Use the ledger.\""))
        assertTrue(body, body.contains("\"tags\":[\"archive\",\"updated\"]"))
        assertTrue(body, body.contains("\"creator\":\"Tester\""))
        assertTrue(body, body.contains("\"character_version\":\"1.1\""))
        assertTrue(body, body.contains("\"world\":\"Archive World\""))
        assertTrue(body, body.contains("\"talkativeness\":0.8"))
        assertTrue(body, body.contains("\"alternate_greetings\":[\"Hello again.\"]"))
        assertTrue(body, body.contains("\"depth_prompt_prompt\":\"Keep context.\""))
        assertTrue(body, body.contains("\"depth_prompt_depth\":2"))
        assertTrue(body, body.contains("\"depth_prompt_role\":\"user\""))
        assertTrue(body, body.contains("\"chat\":\"Seraphina - 2026.jsonl\""))
        assertTrue(body, body.contains("\"create_date\":\"2026-05-26T10:00:00.000Z\""))
        assertTrue(body, body.contains("\"json_data\":\"{\\\"foreign_field\\\":true}\""))
        assertTrue(body, body.contains("\"fav\":\"false\""))
    }

    @Test
    fun renameDuplicateAndDeleteUseSillyTavernCharacterManagementApis() = runBlocking {
        enqueueCsrf()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"avatar":"Seraphina_New.png"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"path":"Seraphina_New_1.png"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val renamed = client.renameCharacter("Seraphina.png", "Seraphina New")
        val duplicated = client.duplicateCharacter("Seraphina_New.png")
        client.deleteCharacter("Seraphina_New_1.png", deleteChats = true)

        assertCsrfRequest()
        val renameRequest = server.takeRequest()
        assertEquals("/api/characters/rename", renameRequest.path)
        assertTrue(renameRequest.body.readUtf8().contains("\"new_name\":\"Seraphina New\""))
        assertEquals("Seraphina_New.png", renamed)

        val duplicateRequest = server.takeRequest()
        assertEquals("/api/characters/duplicate", duplicateRequest.path)
        assertTrue(duplicateRequest.body.readUtf8().contains("\"avatar_url\":\"Seraphina_New.png\""))
        assertEquals("Seraphina_New_1.png", duplicated)

        val deleteRequest = server.takeRequest()
        assertEquals("/api/characters/delete", deleteRequest.path)
        val deleteBody = deleteRequest.body.readUtf8()
        assertTrue(deleteBody.contains("\"avatar_url\":\"Seraphina_New_1.png\""))
        assertTrue(deleteBody.contains("\"delete_chats\":true"))
    }

    @Test
    fun listCharacterChatsMapsPastChatMetadata() = runBlocking {
        enqueueCsrf()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {
                        "file_id": "Seraphina - 2026",
                        "file_name": "Seraphina - 2026.jsonl",
                        "file_size": "2 KB",
                        "chat_items": 12,
                        "mes": "Last archive note.",
                        "last_mes": "2026-05-26T10:00:00.000Z"
                      }
                    ]
                    """.trimIndent()
                )
        )

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val chats = client.listCharacterChats("Seraphina.png")

        assertCsrfRequest()
        val request = server.takeRequest()
        assertEquals("/api/characters/chats", request.path)
        assertTrue(request.body.readUtf8().contains("\"avatar_url\":\"Seraphina.png\""))
        assertEquals(1, chats.size)
        assertEquals("Seraphina - 2026", chats.first().id)
        assertEquals("Seraphina - 2026.jsonl", chats.first().fileName)
        assertEquals("2 KB", chats.first().fileSize)
        assertEquals(12, chats.first().messageCount)
        assertEquals("Last archive note.", chats.first().lastMessage)
        assertEquals("2026-05-26T10:00:00.000Z", chats.first().lastMessageAt)
    }

    @Test
    fun importExportAndAvatarUseOriginalCharacterFileApis() = runBlocking {
        enqueueCsrf()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"file_name":"Imported"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("updated"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader("Content-Disposition", "attachment; filename=\"Imported.json\"")
                .setBody("""{"name":"Imported"}""")
        )

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val imported = client.importCharacter("Imported.png", "png-data".toByteArray())
        client.updateCharacterAvatar("Imported.png", "avatar.png", "avatar-data".toByteArray())
        val exported = client.exportCharacter("Imported.png", CharacterExportFormat.JSON)

        assertCsrfRequest()
        val importRequest = server.takeRequest()
        assertEquals("/api/characters/import", importRequest.path)
        val importBody = importRequest.body.readUtf8()
        assertTrue(importBody.contains("name=\"avatar\"; filename=\"Imported.png\""))
        assertTrue(importBody.contains("name=\"file_type\""))
        assertTrue(importBody.contains("png"))
        assertEquals("Imported.png", imported)

        val avatarRequest = server.takeRequest()
        assertEquals("/api/characters/edit-avatar", avatarRequest.path)
        val avatarBody = avatarRequest.body.readUtf8()
        assertTrue(avatarBody.contains("name=\"avatar\"; filename=\"avatar.png\""))
        assertTrue(avatarBody.contains("name=\"avatar_url\""))
        assertTrue(avatarBody.contains("Imported.png"))

        val exportRequest = server.takeRequest()
        assertEquals("/api/characters/export", exportRequest.path)
        val exportBody = exportRequest.body.readUtf8()
        assertTrue(exportBody.contains("\"avatar_url\":\"Imported.png\""))
        assertTrue(exportBody.contains("\"format\":\"json\""))
        assertEquals("Imported.json", exported.fileName)
        assertEquals("application/json", exported.contentType)
        assertEquals("""{"name":"Imported"}""", exported.bytes.toString(Charsets.UTF_8))
    }
}
