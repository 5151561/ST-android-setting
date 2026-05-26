package io.github.sanitised.st.ui.screens

import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.STTag
import io.github.sanitised.st.api.STTagSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterTagToolsTest {
    @Test
    fun importEmbeddedTagsCreatesStTagsAndAssignments() {
        val settings = STTagSettings(
            tags = listOf(STTag(id = "existing", name = "Existing")),
            tagMap = mapOf("Seraphina.png" to listOf("existing")),
            rawSettings = mapOf("other" to 42)
        )
        val characters = listOf(
            CharacterSummary(id = "Seraphina.png", name = "Seraphina", tags = listOf("Archive", "Existing")),
            CharacterSummary(id = "Rowan.png", name = "Rowan", tags = listOf("Archive"))
        )

        val updated = CharacterTagTools.importEmbeddedTags(settings, characters)

        assertTrue(updated.rawSettings.containsKey("other"))
        assertTrue(updated.tags.any { it.name == "Archive" })
        val archiveId = updated.tags.first { it.name == "Archive" }.id
        assertTrue(updated.tagMap.getValue("Seraphina.png").contains(archiveId))
        assertTrue(updated.tagMap.getValue("Rowan.png").contains(archiveId))
        assertEquals(1, updated.tags.count { it.name == "Existing" })
    }

    @Test
    fun renameAndDeleteTagPreserveTagMapIntegrity() {
        val settings = STTagSettings(
            tags = listOf(
                STTag(id = "folder", name = "Folder", isFolder = true),
                STTag(id = "mood", name = "Mood")
            ),
            tagMap = mapOf(
                "Seraphina.png" to listOf("folder", "mood"),
                "Rowan.png" to listOf("mood")
            ),
            rawSettings = mapOf("other" to 42)
        )

        val renamed = CharacterTagTools.renameTag(settings, "folder", "Archive", isFolder = true)
        val deleted = CharacterTagTools.deleteTag(renamed, "mood")

        assertEquals("Archive", renamed.tags.first { it.id == "folder" }.name)
        assertTrue(renamed.tags.first { it.id == "folder" }.isFolder)
        assertTrue(deleted.rawSettings.containsKey("other"))
        assertFalse(deleted.tags.any { it.id == "mood" })
        assertEquals(listOf("folder"), deleted.tagMap.getValue("Seraphina.png"))
        assertEquals(emptyList<String>(), deleted.tagMap.getValue("Rowan.png"))
    }

    @Test
    fun drilldownTagsForFolderShowsTagsInsideFolderCharacters() {
        val settings = STTagSettings(
            tags = listOf(
                STTag(id = "folder", name = "Folder", isFolder = true),
                STTag(id = "inside", name = "Inside"),
                STTag(id = "outside", name = "Outside")
            ),
            tagMap = mapOf(
                "Seraphina.png" to listOf("folder", "inside"),
                "Rowan.png" to listOf("outside")
            )
        )
        val characters = listOf(
            CharacterSummary(id = "Seraphina.png", name = "Seraphina"),
            CharacterSummary(id = "Rowan.png", name = "Rowan")
        )

        assertEquals(
            listOf("Inside"),
            CharacterTagTools.drilldownTagsForFolder(characters, settings, "Folder").map { it.name }
        )
    }
}
