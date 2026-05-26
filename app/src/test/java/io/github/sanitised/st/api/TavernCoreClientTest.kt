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
    fun settingsSnapshotListCallsSillyTavernEndpointAndMapsFiles() = runBlocking {
        enqueueCsrf()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      { "name": "settings_default-user_20260526.json", "date": 1748246400000, "size": 42 }
                    ]
                    """.trimIndent()
                )
        )

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val snapshots = client.listSettingsSnapshots()

        assertCsrfRequest()
        assertEquals("/api/settings/get-snapshots", server.takeRequest().path)
        assertEquals(1, snapshots.size)
        assertEquals("settings_default-user_20260526.json", snapshots.first().name)
        assertEquals(1748246400000L, snapshots.first().date)
        assertEquals(42L, snapshots.first().size)
    }

    @Test
    fun settingsSnapshotActionsUseSnapshotNamePayloads() = runBlocking {
        enqueueCsrf()
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"theme\":\"dark\"}"))
        server.enqueue(MockResponse().setResponseCode(204))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        client.makeSettingsSnapshot()
        val loaded = client.loadSettingsSnapshot("settings_default-user_20260526.json")
        client.restoreSettingsSnapshot("settings_default-user_20260526.json")

        assertCsrfRequest()
        assertEquals("/api/settings/make-snapshot", server.takeRequest().path)
        val loadRequest = server.takeRequest()
        assertEquals("/api/settings/load-snapshot", loadRequest.path)
        assertTrue(loadRequest.body.readUtf8().contains("\"name\":\"settings_default-user_20260526.json\""))
        assertEquals("{\"theme\":\"dark\"}", loaded)
        val restoreRequest = server.takeRequest()
        assertEquals("/api/settings/restore-snapshot", restoreRequest.path)
        assertTrue(restoreRequest.body.readUtf8().contains("\"name\":\"settings_default-user_20260526.json\""))
    }

    @Test
    fun worldInfoActionsUseSillyTavernWorldInfoEndpoints() = runBlocking {
        enqueueCsrf()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{ "file_id": "archive", "name": "Archive World", "extensions": { "tag": "rp" } }]""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "name": "Archive World",
                      "entries": {
                        "7": {
                          "uid": 7,
                          "key": ["archive", "ledger"],
                          "keysecondary": ["sealed"],
                          "comment": "Locked room",
                          "content": "The ledger is hidden.",
                          "order": 20,
                          "depth": 3,
                          "position": 1,
                          "constant": true,
                          "selective": true,
                          "disable": false,
                          "foreign_field": "keep"
                        }
                      },
                      "extensions": { "tag": "rp" }
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val worlds = client.listWorldInfos()
        val book = client.getWorldInfo("archive")
        client.saveWorldInfo(book.copy(entries = book.entries.map { it.copy(disabled = true) }))
        client.deleteWorldInfo("archive")

        assertCsrfRequest()
        assertEquals("/api/worldinfo/list", server.takeRequest().path)
        assertEquals("archive", worlds.single().id)
        assertEquals("Archive World", worlds.single().name)
        val getRequest = server.takeRequest()
        assertEquals("/api/worldinfo/get", getRequest.path)
        assertTrue(getRequest.body.readUtf8().contains("\"name\":\"archive\""))
        assertEquals("Archive World", book.name)
        assertEquals(7, book.entries.single().uid)
        assertEquals(listOf("archive", "ledger"), book.entries.single().keys)
        assertEquals("keep", book.entries.single().raw["foreign_field"])
        val saveRequest = server.takeRequest()
        assertEquals("/api/worldinfo/edit", saveRequest.path)
        val saveBody = saveRequest.body.readUtf8()
        assertTrue(saveBody.contains("\"name\":\"Archive World\""))
        assertTrue(saveBody.contains("\"entries\""))
        assertTrue(saveBody.contains("\"uid\":7"))
        assertTrue(saveBody.contains("\"disable\":true"))
        assertTrue(saveBody.contains("\"foreign_field\":\"keep\""))
        val deleteRequest = server.takeRequest()
        assertEquals("/api/worldinfo/delete", deleteRequest.path)
        assertTrue(deleteRequest.body.readUtf8().contains("\"name\":\"archive\""))
    }

    @Test
    fun personaListAndSaveMergeAvatarApiWithPowerUserSettings() = runBlocking {
        enqueueCsrf()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""["default.png","writer.png"]"""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "settings": "{\"theme\":\"dark\",\"power_user\":{\"personas\":{\"default.png\":\"Default user\"},\"persona_descriptions\":{\"default.png\":{\"title\":\"Main\",\"description\":\"Careful tester\",\"connections\":[{\"type\":\"character\",\"id\":\"A.png\"}]}},\"default_persona\":\"default.png\"}}"
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "settings": "{\"theme\":\"dark\",\"power_user\":{\"personas\":{\"default.png\":\"Default user\"},\"persona_descriptions\":{\"default.png\":{\"title\":\"Main\",\"description\":\"Careful tester\",\"connections\":[{\"type\":\"character\",\"id\":\"A.png\"}]}},\"default_persona\":\"default.png\"}}"
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"result":"ok"}"""))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val personas = client.listPersonas()
        client.savePersona(
            PersonaSaveRequest(
                avatar = "writer.png",
                name = "Writer",
                title = "Long RP",
                description = "Writes in long form.",
                makeDefault = true
            )
        )

        assertCsrfRequest()
        assertEquals("/api/avatars/get", server.takeRequest().path)
        assertEquals("/api/settings/get", server.takeRequest().path)
        assertEquals(2, personas.size)
        assertEquals("Default user", personas.first { it.avatar == "default.png" }.name)
        assertTrue(personas.first { it.avatar == "default.png" }.isDefault)
        assertEquals("writer.png", personas.first { it.avatar == "writer.png" }.avatar)
        assertEquals("/api/settings/get", server.takeRequest().path)
        val saveRequest = server.takeRequest()
        assertEquals("/api/settings/save", saveRequest.path)
        val saveBody = saveRequest.body.readUtf8()
        assertTrue(saveBody.contains("\"theme\":\"dark\""))
        assertTrue(saveBody.contains("\"writer.png\":\"Writer\""))
        assertTrue(saveBody.contains("\"title\":\"Long RP\""))
        assertTrue(saveBody.contains("\"description\":\"Writes in long form.\""))
        assertTrue(saveBody.contains("\"default_persona\":\"writer.png\""))
        assertTrue(saveBody.contains("\"connections\""))
    }

    @Test
    fun secretActionsUseSillyTavernSecretEndpointsWithoutPlaintextReads() = runBlocking {
        enqueueCsrf()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "api_key_openrouter": [
                        { "id": "s1", "value": "*******abc", "label": "OpenRouter main", "active": true }
                      ],
                      "api_key_openai": null
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"s2"}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val states = client.listSecrets()
        val newId = client.writeSecret("api_key_openai", "sk-test", "OpenAI")
        client.rotateSecret("api_key_openrouter", "s1")
        client.renameSecret("api_key_openrouter", "s1", "Primary")
        client.deleteSecret("api_key_openrouter", "s1")

        assertCsrfRequest()
        assertEquals("/api/secrets/read", server.takeRequest().path)
        assertEquals("OpenRouter main", states.first { it.key == "api_key_openrouter" }.entries.single().label)
        assertEquals("s2", newId)
        val writeRequest = server.takeRequest()
        assertEquals("/api/secrets/write", writeRequest.path)
        assertTrue(writeRequest.body.readUtf8().contains("\"value\":\"sk-test\""))
        val rotateRequest = server.takeRequest()
        assertEquals("/api/secrets/rotate", rotateRequest.path)
        assertTrue(rotateRequest.body.readUtf8().contains("\"id\":\"s1\""))
        val renameRequest = server.takeRequest()
        assertEquals("/api/secrets/rename", renameRequest.path)
        assertTrue(renameRequest.body.readUtf8().contains("\"label\":\"Primary\""))
        val deleteRequest = server.takeRequest()
        assertEquals("/api/secrets/delete", deleteRequest.path)
        assertTrue(deleteRequest.body.readUtf8().contains("\"key\":\"api_key_openrouter\""))
    }

    @Test
    fun presetLibraryMapsSettingsPayloadAndSavesPresetFiles() = runBlocking {
        enqueueCsrf()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "settings": "{\"openai_settings\":\"Creative\",\"textgenerationwebui_settings\":{\"preset\":\"Kobold\"}}",
                      "openai_setting_names": ["Creative"],
                      "openai_settings": ["{\"temperature\":0.8}"],
                      "textgenerationwebui_preset_names": ["Kobold"],
                      "textgenerationwebui_presets": ["{\"temperature\":0.7}"],
                      "instruct": [{ "name": "Alpaca", "input_sequence": "### Instruction:" }],
                      "context": [{ "name": "Default Context", "story_string": "{{char}}" }],
                      "sysprompt": [{ "name": "Roleplay", "content": "Stay in character." }],
                      "reasoning": [{ "name": "Reasoning", "prefix": "<think>" }]
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"name":"Creative copy"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"isDefault":true,"preset":{"temperature":1}}"""))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val library = client.getPresetLibrary()
        client.savePreset("openai", "Creative copy", """{"temperature":0.5}""")
        client.deletePreset("openai", "Creative copy")
        val restored = client.restorePreset("openai", "Creative")

        assertCsrfRequest()
        assertEquals("/api/settings/get", server.takeRequest().path)
        assertTrue(library.categories.any { it.apiId == "openai" && it.presets.single().name == "Creative" })
        assertTrue(library.categories.any { it.apiId == "instruct" && it.presets.single().name == "Alpaca" })
        val saveRequest = server.takeRequest()
        assertEquals("/api/presets/save", saveRequest.path)
        assertTrue(saveRequest.body.readUtf8().contains("\"apiId\":\"openai\""))
        val deleteRequest = server.takeRequest()
        assertEquals("/api/presets/delete", deleteRequest.path)
        assertTrue(deleteRequest.body.readUtf8().contains("\"name\":\"Creative copy\""))
        val restoreRequest = server.takeRequest()
        assertEquals("/api/presets/restore", restoreRequest.path)
        assertTrue(restoreRequest.body.readUtf8().contains("\"name\":\"Creative\""))
        assertTrue(restored.contains("\"temperature\":1"))
    }

    @Test
    fun presetSelectionMergesIntoSettingsWithoutDroppingUnknownFields() = runBlocking {
        enqueueCsrf()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "settings": "{\"theme\":\"dark\",\"openai_settings\":\"Creative\",\"textgenerationwebui_settings\":{\"preset\":\"Kobold\",\"temperature\":0.7},\"foreign\":\"keep\"}"
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"result":"ok"}"""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "settings": "{\"theme\":\"dark\",\"openai_settings\":\"Creative\",\"textgenerationwebui_settings\":{\"preset\":\"Kobold\",\"temperature\":0.7},\"foreign\":\"keep\"}"
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"result":"ok"}"""))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        client.selectPreset("openai", "Precise")
        client.selectPreset("textgenerationwebui", "Simple")

        assertCsrfRequest()
        assertEquals("/api/settings/get", server.takeRequest().path)
        val openAiSave = server.takeRequest()
        assertEquals("/api/settings/save", openAiSave.path)
        val openAiBody = openAiSave.body.readUtf8()
        assertTrue(openAiBody.contains("\"openai_settings\":\"Precise\""))
        assertTrue(openAiBody.contains("\"foreign\":\"keep\""))
        assertEquals("/api/settings/get", server.takeRequest().path)
        val textCompletionSave = server.takeRequest()
        assertEquals("/api/settings/save", textCompletionSave.path)
        val textCompletionBody = textCompletionSave.body.readUtf8()
        assertTrue(textCompletionBody.contains("\"preset\":\"Simple\""))
        assertTrue(textCompletionBody.contains("\"temperature\":0.7"))
        assertTrue(textCompletionBody.contains("\"foreign\":\"keep\""))
    }

    @Test
    fun chatBackupActionsUseSillyTavernBackupEndpoints() = runBlocking {
        enqueueCsrf()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {
                        "file_name": "chat_20260526.jsonl",
                        "file_size": "2 KB",
                        "chat_items": 4,
                        "last_mes": 1748246400000,
                        "mes": "Last backup line"
                      }
                    ]
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/jsonl")
                .setHeader("Content-Disposition", "attachment; filename=\"chat_20260526.jsonl\"")
                .setBody("{}\n")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val backups = client.listChatBackups()
        val downloaded = client.downloadChatBackup("chat_20260526.jsonl")
        client.deleteChatBackup("chat_20260526.jsonl")

        assertCsrfRequest()
        assertEquals("/api/backups/chat/get", server.takeRequest().path)
        assertEquals("chat_20260526.jsonl", backups.single().fileName)
        assertEquals(4, backups.single().messageCount)
        val downloadRequest = server.takeRequest()
        assertEquals("/api/backups/chat/download", downloadRequest.path)
        assertTrue(downloadRequest.body.readUtf8().contains("\"name\":\"chat_20260526.jsonl\""))
        assertEquals("chat_20260526.jsonl", downloaded.fileName)
        val deleteRequest = server.takeRequest()
        assertEquals("/api/backups/chat/delete", deleteRequest.path)
        assertTrue(deleteRequest.body.readUtf8().contains("\"name\":\"chat_20260526.jsonl\""))
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
                          "chub": { "full_path": "lore/seraphina" },
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
        assertEquals("https://chub.ai/characters/lore/seraphina", character.sourceUrl)
        assertTrue(character.isFavorite)
    }

    @Test
    fun createCharacterPostsMultipartPayloadAndReturnsAvatarName() = runBlocking {
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
            ),
            CharacterUpload(fileName = "avatar.png", bytes = "png-data".toByteArray())
        )

        assertCsrfRequest()
        val request = server.takeRequest()
        assertEquals("/api/characters/create", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"avatar\"; filename=\"avatar.png\""))
        assertTrue(body.contains("name=\"ch_name\""))
        assertTrue(body.contains("Seraphina"))
        assertTrue(body.contains("name=\"description\""))
        assertTrue(body.contains("A careful archivist."))
        assertTrue(body.contains("name=\"first_mes\""))
        assertTrue(body.contains("Welcome back."))
        assertTrue(body.contains("name=\"tags\""))
        assertTrue(body.contains("archive"))
        assertTrue(body.contains("name=\"fav\""))
        assertTrue(body.contains("true"))
        assertEquals("Seraphina.png", avatar)
    }

    @Test
    fun updateCharacterPostsMultipartPayloadWithAdvancedFields() = runBlocking {
        enqueueCsrf()
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val client = TavernCoreClient(baseUrl = server.url("/").toString())
        val complexJsonData = """
            {"foreign_field":{"nested":[1,true,{"keep":"原样"}]},"data":{"extensions":{"third_party":{"enabled":true}}}}
        """.trimIndent()

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
                rawJsonData = complexJsonData,
                sourceUrl = "https://example.test/seraphina",
                isFavorite = false
            ),
            CharacterUpload(fileName = "avatar.png", bytes = "avatar-data".toByteArray())
        )

        assertCsrfRequest()
        val request = server.takeRequest()
        assertEquals("/api/characters/edit", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("name=\"avatar\"; filename=\"avatar.png\""))
        assertTrue(body, body.contains("name=\"avatar_url\""))
        assertTrue(body, body.contains("Seraphina.png"))
        assertTrue(body, body.contains("name=\"ch_name\""))
        assertTrue(body, body.contains("Seraphina"))
        assertTrue(body, body.contains("name=\"description\""))
        assertTrue(body, body.contains("Updated description"))
        assertTrue(body, body.contains("name=\"first_mes\""))
        assertTrue(body, body.contains("Updated hello"))
        assertTrue(body, body.contains("name=\"personality\""))
        assertTrue(body, body.contains("Careful and direct."))
        assertTrue(body, body.contains("name=\"scenario\""))
        assertTrue(body, body.contains("Inside the archive."))
        assertTrue(body, body.contains("name=\"mes_example\""))
        assertTrue(body, body.contains("<START>"))
        assertTrue(body, body.contains("name=\"creator_notes\""))
        assertTrue(body, body.contains("Notes for users."))
        assertTrue(body, body.contains("name=\"system_prompt\""))
        assertTrue(body, body.contains("Stay precise."))
        assertTrue(body, body.contains("name=\"post_history_instructions\""))
        assertTrue(body, body.contains("Use the ledger."))
        assertTrue(body, body.contains("name=\"tags\""))
        assertTrue(body, body.contains("archive, updated"))
        assertTrue(body, body.contains("name=\"creator\""))
        assertTrue(body, body.contains("Tester"))
        assertTrue(body, body.contains("name=\"character_version\""))
        assertTrue(body, body.contains("1.1"))
        assertTrue(body, body.contains("name=\"world\""))
        assertTrue(body, body.contains("Archive World"))
        assertTrue(body, body.contains("name=\"talkativeness\""))
        assertTrue(body, body.contains("0.8"))
        assertTrue(body, body.contains("name=\"alternate_greetings\""))
        assertTrue(body, body.contains("Hello again."))
        assertTrue(body, body.contains("name=\"depth_prompt_prompt\""))
        assertTrue(body, body.contains("Keep context."))
        assertTrue(body, body.contains("name=\"depth_prompt_depth\""))
        assertTrue(body, body.contains("2"))
        assertTrue(body, body.contains("name=\"depth_prompt_role\""))
        assertTrue(body, body.contains("user"))
        assertTrue(body, body.contains("name=\"chat\""))
        assertTrue(body, body.contains("Seraphina - 2026.jsonl"))
        assertTrue(body, body.contains("name=\"create_date\""))
        assertTrue(body, body.contains("2026-05-26T10:00:00.000Z"))
        assertTrue(body, body.contains("name=\"json_data\""))
        assertTrue(body, body.contains(complexJsonData))
        assertTrue(body, body.contains("name=\"extensions\""))
        assertTrue(body, body.contains("\"source_url\":\"https://example.test/seraphina\""))
        assertTrue(body, body.contains("name=\"fav\""))
        assertTrue(body, body.contains("false"))
    }

    @Test
    fun mergeCharacterAttributesUsesSillyTavernPatchEndpoint() = runBlocking {
        enqueueCsrf()
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        client.mergeCharacterAttributes(
            avatar = "Seraphina.png",
            isFavorite = true,
            embeddedTags = listOf("archive", "assistant")
        )

        assertCsrfRequest()
        val request = server.takeRequest()
        assertEquals("/api/characters/merge-attributes", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"avatar\":\"Seraphina.png\""))
        assertTrue(body.contains("\"fav\":true"))
        assertTrue(body.contains("\"tags\":[\"archive\",\"assistant\"]"))
        assertTrue(body.contains("\"extensions\":{\"fav\":true}"))
    }

    @Test
    fun settingsTagsReadAndSavePreserveUnknownSettings() = runBlocking {
        enqueueCsrf()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "settings": "{\"tags\":[{\"id\":\"tag-1\",\"name\":\"Lore\",\"color\":\"#1A73E8\",\"folder_type\":\"character\"},{\"id\":\"tag-remove\",\"name\":\"Old\",\"color\":\"#888888\"}],\"tag_map\":{\"Seraphina.png\":[\"tag-1\",\"tag-remove\"]},\"other\":42}",
                      "world_names": ["Archive World"]
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"result":"ok"}"""))
        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val settings = client.getTagSettings()
        client.saveTagSettings(
            settings.copy(
                tags = listOf(
                    STTag(id = "tag-1", name = "Renamed Lore", color = "#1A73E8", isFolder = true),
                    STTag(id = "tag-new", name = "New Tag", color = "#22AA66")
                ),
                tagMap = mapOf("Seraphina.png" to listOf("tag-1", "tag-new"))
            )
        )

        assertCsrfRequest()
        assertEquals("/api/settings/get", server.takeRequest().path)
        assertEquals(listOf("Archive World"), settings.worldNames)
        assertEquals("tag-1", settings.tags.first().id)
        assertEquals("Lore", settings.tags.first().name)
        assertEquals(listOf("tag-1", "tag-remove"), settings.tagMap.getValue("Seraphina.png"))

        val saveRequest = server.takeRequest()
        assertEquals("/api/settings/save", saveRequest.path)
        val saveBody = saveRequest.body.readUtf8()
        assertTrue(saveBody.contains("\"other\":42"))
        assertTrue(saveBody.contains("\"tag_map\":{\"Seraphina.png\":[\"tag-1\",\"tag-new\"]}"))
        assertTrue(saveBody.contains("\"name\":\"Renamed Lore\""))
        assertTrue(saveBody.contains("\"id\":\"tag-new\""))
        assertFalse(saveBody.contains("tag-remove"))
    }

    @Test
    fun externalCharacterImportDownloadsContentThenImportsCharacter() = runBlocking {
        enqueueCsrf()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setHeader("Content-Disposition", "attachment; filename=\"Imported.png\"")
                .setHeader("X-Custom-Content-Type", "character")
                .setBody("png-data")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"file_name":"Imported"}"""))
        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val imported = client.importExternalCharacter("https://chub.ai/example/card")

        assertCsrfRequest()
        val downloadRequest = server.takeRequest()
        assertEquals("/api/content/importURL", downloadRequest.path)
        assertTrue(downloadRequest.body.readUtf8().contains("\"url\":\"https://chub.ai/example/card\""))

        val importRequest = server.takeRequest()
        assertEquals("/api/characters/import", importRequest.path)
        assertTrue(importRequest.body.readUtf8().contains("name=\"avatar\"; filename=\"Imported.png\""))
        assertEquals("Imported.png", imported)
    }

    @Test
    fun characterChatManagementUsesSillyTavernChatApis() = runBlocking {
        enqueueCsrf()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true,"sanitizedFileName":"renamed"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"message":"Chat saved","result":"{\"mes\":\"hello\"}"}""")
        )
        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val renamed = client.renameCharacterChat("Seraphina.png", "old.jsonl", "renamed.jsonl")
        client.deleteCharacterChat("Seraphina.png", "renamed.jsonl")
        val exported = client.exportCharacterChat("Seraphina.png", "renamed.jsonl", ChatExportFormat.JSONL)

        assertCsrfRequest()
        val renameRequest = server.takeRequest()
        assertEquals("/api/chats/rename", renameRequest.path)
        val renameBody = renameRequest.body.readUtf8()
        assertTrue(renameBody.contains("\"avatar_url\":\"Seraphina.png\""))
        assertTrue(renameBody.contains("\"original_file\":\"old.jsonl\""))
        assertTrue(renameBody.contains("\"renamed_file\":\"renamed.jsonl\""))
        assertEquals("renamed", renamed)

        val deleteRequest = server.takeRequest()
        assertEquals("/api/chats/delete", deleteRequest.path)
        assertTrue(deleteRequest.body.readUtf8().contains("\"chatfile\":\"renamed.jsonl\""))

        val exportRequest = server.takeRequest()
        assertEquals("/api/chats/export", exportRequest.path)
        val exportBody = exportRequest.body.readUtf8()
        assertTrue(exportBody.contains("\"file\":\"renamed.jsonl\""))
        assertTrue(exportBody.contains("\"format\":\"jsonl\""))
        assertEquals("renamed.jsonl", exported.fileName)
        assertEquals("""{"mes":"hello"}""", exported.bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun importCharacterChatPostsMultipartChatImportPayload() = runBlocking {
        enqueueCsrf()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"res":true,"fileNames":["Seraphina imported.jsonl"]}"""))
        val client = TavernCoreClient(baseUrl = server.url("/").toString())

        val imported = client.importCharacterChat(
            avatar = "Seraphina.png",
            characterName = "Seraphina",
            fileName = "chat.jsonl",
            bytes = """{"user_name":"User"}""".toByteArray()
        )

        assertCsrfRequest()
        val request = server.takeRequest()
        assertEquals("/api/chats/import", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"avatar\"; filename=\"chat.jsonl\""))
        assertTrue(body.contains("name=\"file_type\""))
        assertTrue(body.contains("jsonl"))
        assertTrue(body.contains("name=\"avatar_url\""))
        assertTrue(body.contains("Seraphina.png"))
        assertTrue(body.contains("name=\"character_name\""))
        assertTrue(body.contains("Seraphina"))
        assertEquals(listOf("Seraphina imported.jsonl"), imported)
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
