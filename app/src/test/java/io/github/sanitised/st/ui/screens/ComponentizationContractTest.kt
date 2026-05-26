package io.github.sanitised.st.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentizationContractTest {
    @Test
    fun characterScreensUseSharedComponentsInsteadOfLocalDuplicates() {
        val listScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterListScreen.kt").readText()
        val detailScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterDetailScreen.kt").readText()
        val editScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterEditScreen.kt").readText()

        val combinedCharacterScreens = listScreen + detailScreen + editScreen
        listOf(
            "private fun CharacterInfoCard",
            "private fun CharacterDetailInfoCard",
            "private fun CharacterEditorInfoCard",
            "private fun CharacterDetailPanel",
            "private fun CharacterEditorSection"
        ).forEach { duplicateFunction ->
            assertFalse("$duplicateFunction should be replaced with shared components", combinedCharacterScreens.contains(duplicateFunction))
        }

        assertTrue(combinedCharacterScreens.contains("STInfoCard("))
        assertTrue(combinedCharacterScreens.contains("STSectionCard("))
        assertTrue(combinedCharacterScreens.contains("STConfirmDialog("))
        assertTrue(combinedCharacterScreens.contains("FavoriteIconButton("))
        assertTrue(combinedCharacterScreens.contains("CharacterTagCheckboxList("))
    }

    @Test
    fun operationProgressPromptsUseSharedProgressComponents() {
        val updatePrompt = File("src/main/java/io/github/sanitised/st/UiUpdatePrompt.kt").readText()
        val manageScreen = File("src/main/java/io/github/sanitised/st/UiManageSt.kt").readText()

        assertTrue(File("src/main/java/io/github/sanitised/st/ui/components/STCards.kt").exists())
        assertTrue(File("src/main/java/io/github/sanitised/st/ui/components/CharacterSharedComponents.kt").exists())
        assertTrue(updatePrompt.contains("STOperationProgressCard("))
        assertTrue(updatePrompt.contains("STProgressBlock("))
        assertTrue(manageScreen.contains("STOperationProgressCard("))
        assertFalse(manageScreen.contains("CustomSourceDownloadCard("))
    }

    @Test
    fun componentizationDoesNotChangeStringResourceKeys() {
        val defaultStrings = readStringResourceKeys(File("src/main/res/values/strings.xml"))
        val chineseStrings = readStringResourceKeys(File("src/main/res/values-zh-rCN/strings.xml"))

        assertEquals(defaultStrings, chineseStrings)
    }

    private fun readStringResourceKeys(file: File): Set<String> {
        assertTrue("${file.path} should exist", file.exists())
        return Regex("""<string\s+name="([^"]+)"""")
            .findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
    }
}
