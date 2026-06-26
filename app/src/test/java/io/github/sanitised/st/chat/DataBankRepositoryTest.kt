package io.github.sanitised.st.chat

import io.github.sanitised.st.api.CharacterDetail
import org.junit.Assert.assertEquals
import org.junit.Test

class DataBankRepositoryTest {
    @Test
    fun collectsGlobalCharacterAndChatAttachments() {
        val bank = DataBankRepository.collect(
            settings = mapOf(
                "dataBank" to mapOf(
                    "global" to listOf(
                        mapOf("url" to "/global/lore.txt", "name" to "lore.txt", "size" to 100L, "created" to 1L)
                    )
                )
            ),
            character = CharacterDetail(
                id = "Alice.png",
                name = "Alice",
                rawJsonData = """
                    {
                      "data": {
                        "extensions": {
                          "dataBank": {
                            "character": [
                              { "url": "/char/profile.pdf", "name": "profile.pdf", "size": 200, "created": 2 }
                            ]
                          }
                        }
                      }
                    }
                """.trimIndent()
            ),
            chat = mutableListOf(
                mapOf(
                    "chat_metadata" to mapOf(
                        "dataBank" to mapOf(
                            "chat" to listOf(
                                mapOf("url" to "/chat/map.png", "name" to "map.png", "size" to 300L, "created" to 3L)
                            )
                        )
                    )
                )
            )
        )

        assertEquals("lore.txt", bank.global.single().name)
        assertEquals("profile.pdf", bank.character.single().name)
        assertEquals("map.png", bank.chat.single().name)
    }

    @Test
    fun collectsSillyTavernAttachmentSettingsFromRealExtensionLocations() {
        val bank = DataBankRepository.collect(
            settings = mapOf(
                "extension_settings" to mapOf(
                    "attachments" to listOf(
                        mapOf("url" to "/global/world.md", "name" to "world.md", "size" to 111L, "created" to 11L)
                    ),
                    "character_attachments" to mapOf(
                        "Alice.png" to listOf(
                            mapOf("url" to "/char/profile.pdf", "name" to "profile.pdf", "size" to 222L, "created" to 22L)
                        )
                    )
                )
            ),
            character = CharacterDetail(id = "Alice.png", name = "Alice"),
            chat = mutableListOf(
                mapOf(
                    "chat_metadata" to mapOf(
                        "attachments" to listOf(
                            mapOf("url" to "/chat/map.png", "name" to "map.png", "size" to 333L, "created" to 33L)
                        )
                    )
                )
            )
        )

        assertEquals("world.md", bank.global.single().name)
        assertEquals("profile.pdf", bank.character.single().name)
        assertEquals("map.png", bank.chat.single().name)
    }

    @Test
    fun emptySourcesReturnEmptyLists() {
        val bank = DataBankRepository.collect(
            settings = emptyMap(),
            character = CharacterDetail(id = "Alice.png", name = "Alice"),
            chat = emptyList()
        )

        assertEquals(DataBankAttachments(emptyList(), emptyList(), emptyList()), bank)
    }
}
