package io.github.sanitised.st.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeHubScreensContractTest {
    @Test
    fun nativeHubsDoNotExposeBrokenFullWebViewManagerActions() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val screens = File("src/main/java/io/github/sanitised/st/ui/screens/M1HubScreens.kt").readText()
        val navGraph = File("src/main/java/io/github/sanitised/st/ui/navigation/STNavGraph.kt").readText()

        assertFalse(strings.contains("Full Manager"))
        assertFalse(strings.contains("Open Full Tools"))
        assertFalse(screens.contains("AdvancedWebEntryCard"))
        assertFalse(navGraph.contains("WEBVIEW_CHARACTERS"))
        assertFalse(navGraph.contains("WEBVIEW_TOOLS"))
    }

    @Test
    fun m2CharacterScreensExposeNativeListAndEditorRoutes() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val navGraph = File("src/main/java/io/github/sanitised/st/ui/navigation/STNavGraph.kt").readText()
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()

        assertTrue(File("src/main/java/io/github/sanitised/st/ui/screens/CharacterListScreen.kt").exists())
        assertTrue(File("src/main/java/io/github/sanitised/st/ui/screens/CharacterEditScreen.kt").exists())
        assertTrue(navGraph.contains("CHARACTER_NEW"))
        assertTrue(navGraph.contains("CHARACTER_EDIT"))
        assertTrue(mainActivity.contains("CharacterListScreen"))
        assertTrue(mainActivity.contains("CharacterEditScreen"))
        assertTrue(strings.contains("character_edit_title"))
        assertTrue(strings.contains("character_list_search_hint"))
    }

    @Test
    fun m2CharacterManagementExposesOriginalManagerP0Controls() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val listScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterListScreen.kt").readText()
        val editScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterEditScreen.kt").readText()
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()

        assertTrue(listScreen.contains("onDuplicateCharacter"))
        assertTrue(listScreen.contains("onDeleteCharacter"))
        assertTrue(mainActivity.contains("onShowMessage = { message -> viewModel.showTransientMessage(message) }"))
        assertTrue(strings.contains("character_action_duplicate"))
        assertTrue(strings.contains("character_action_delete"))
        assertTrue(strings.contains("character_action_avatar"))
        assertTrue(strings.contains("character_action_export_json"))
        assertTrue(strings.contains("character_action_export_png"))
        assertTrue(strings.contains("character_delete_chats"))

        assertTrue(editScreen.contains("alternateGreetingsText"))
        assertTrue(editScreen.contains("depthPromptDepthText"))
        assertTrue(editScreen.contains("talkativenessText"))
        assertTrue(editScreen.contains("renameCharacter"))
        assertTrue(editScreen.contains("duplicateCharacter"))
        assertTrue(editScreen.contains("deleteCharacter"))
        assertTrue(editScreen.contains("updateCharacterAvatar"))
        assertTrue(editScreen.contains("exportCharacter"))
        assertTrue(listScreen.contains("importCharacter"))
        assertTrue(strings.contains("character_edit_alternate_greetings"))
        assertTrue(strings.contains("character_edit_depth_prompt"))
        assertTrue(strings.contains("character_edit_personality"))
        assertTrue(strings.contains("character_edit_scenario"))
    }
}
