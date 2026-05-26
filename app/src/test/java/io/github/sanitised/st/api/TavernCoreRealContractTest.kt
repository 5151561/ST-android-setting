package io.github.sanitised.st.api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TavernCoreRealContractTest {
    @Test
    fun realSillyTavernPreservesCharacterAvatarAndTagContracts() = runBlocking {
        val baseUrl = realContractBaseUrl()
        assumeTrue(
            "Set ST_CONTRACT_BASE_URL or -Dst.contract.baseUrl to run against a real SillyTavern server.",
            !baseUrl.isNullOrBlank()
        )

        val client = TavernCoreClient(baseUrl = baseUrl!!)
        val suffix = System.currentTimeMillis().toString(36)
        val name = "STContract_$suffix"
        val embeddedTag = "contract-embedded-$suffix"
        val mergedTag = "contract-merged-$suffix"
        val tagId = "st-contract-$suffix"
        var avatar: String? = null
        var originalSettings: STTagSettings? = null

        try {
            assertTrue(client.healthCheck().ok)
            originalSettings = client.getTagSettings()

            avatar = client.createCharacter(
                CharacterSaveRequest(
                    name = name,
                    description = "Created by real ST contract test.",
                    firstMessage = "Hello from the contract test.",
                    tags = listOf(embeddedTag),
                    isFavorite = true
                )
            )

            val complexJsonData = """
                {"contract_unknown_top_level":{"preserve":true},"data":{"extensions":{"contract_extension":{"enabled":true,"marker":"$suffix"}}}}
            """.trimIndent()

            client.updateCharacter(
                CharacterSaveRequest(
                    avatar = avatar,
                    name = name,
                    description = "Updated by real ST contract test.",
                    firstMessage = "Updated hello.",
                    creatorNotes = "Contract notes $suffix",
                    systemPrompt = "Contract system prompt.",
                    postHistoryInstructions = "Contract post history.",
                    tags = listOf(embeddedTag),
                    creator = "ST Android",
                    characterVersion = "contract-$suffix",
                    world = "Contract World",
                    talkativeness = 0.42,
                    alternateGreetings = listOf("Alt one $suffix", "Alt two $suffix"),
                    depthPrompt = "Contract depth prompt.",
                    depthPromptDepth = 2,
                    depthPromptRole = "user",
                    rawJsonData = complexJsonData,
                    isFavorite = true
                )
            )

            val edited = client.getCharacter(avatar)
            assertEquals(name, edited.name)
            assertEquals("Updated by real ST contract test.", edited.description)
            assertTrue(edited.isFavorite)
            assertTrue(edited.tags.contains(embeddedTag))
            assertEquals(listOf("Alt one $suffix", "Alt two $suffix"), edited.alternateGreetings)
            assertTrue(edited.rawJsonData, edited.rawJsonData.contains("contract_unknown_top_level"))
            assertTrue(edited.rawJsonData, edited.rawJsonData.contains("contract_extension"))
            assertTrue(edited.rawJsonData, edited.rawJsonData.contains(suffix))

            client.mergeCharacterAttributes(
                avatar = avatar,
                isFavorite = false,
                embeddedTags = listOf(mergedTag)
            )
            val merged = client.getCharacter(avatar)
            assertFalse(merged.isFavorite)
            assertTrue(merged.tags.contains(mergedTag))

            client.updateCharacterAvatar(
                avatar = avatar,
                fileName = "contract-avatar.png",
                bytes = onePixelPng()
            )
            val exportedPng = client.exportCharacter(avatar, CharacterExportFormat.PNG)
            assertTrue(exportedPng.bytes.isNotEmpty())

            client.saveTagSettings(
                originalSettings.copy(
                    tags = originalSettings.tags.filterNot { it.id == tagId } + STTag(
                        id = tagId,
                        name = "ST Contract $suffix",
                        color = "#22AA66",
                        isFolder = false,
                        sortOrder = (originalSettings.tags.maxOfOrNull { it.sortOrder } ?: 0) + 1
                    ),
                    tagMap = originalSettings.tagMap + (
                        avatar to (originalSettings.tagMap[avatar].orEmpty() + tagId).distinct()
                    )
                )
            )
            val createdTagSettings = client.getTagSettings()
            assertTrue(createdTagSettings.tags.any { it.id == tagId && it.name == "ST Contract $suffix" })
            assertTrue(createdTagSettings.tagMap[avatar].orEmpty().contains(tagId))

            client.saveTagSettings(
                createdTagSettings.copy(
                    tags = createdTagSettings.tags.map { tag ->
                        if (tag.id == tagId) {
                            tag.copy(name = "ST Contract Renamed $suffix", isFolder = true)
                        } else {
                            tag
                        }
                    }
                )
            )
            val renamedTagSettings = client.getTagSettings()
            assertTrue(
                renamedTagSettings.tags.any {
                    it.id == tagId && it.name == "ST Contract Renamed $suffix" && it.isFolder
                }
            )

            client.saveTagSettings(
                renamedTagSettings.copy(
                    tags = renamedTagSettings.tags.filterNot { it.id == tagId },
                    tagMap = renamedTagSettings.tagMap.mapValues { (_, ids) ->
                        ids.filterNot { it == tagId }
                    }
                )
            )
            val deletedTagSettings = client.getTagSettings()
            assertFalse(deletedTagSettings.tags.any { it.id == tagId })
            assertFalse(deletedTagSettings.tagMap.values.any { ids -> tagId in ids })
        } finally {
            val cleanAvatar = avatar
            val cleanSettings = originalSettings
            if (cleanSettings != null) {
                runCatching { client.saveTagSettings(cleanSettings) }
            }
            if (!cleanAvatar.isNullOrBlank()) {
                runCatching { client.deleteCharacter(cleanAvatar, deleteChats = true) }
            }
        }
    }

    private fun realContractBaseUrl(): String? =
        System.getenv("ST_CONTRACT_BASE_URL")
            ?: System.getProperty("st.contract.baseUrl")

    private fun onePixelPng(): ByteArray = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGOSHzRgAAAAABJRU5ErkJggg=="
    )
}
