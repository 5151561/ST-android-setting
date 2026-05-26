package io.github.sanitised.st.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
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
        assertTrue(File("src/main/java/io/github/sanitised/st/ui/screens/CharacterDetailScreen.kt").exists())
        assertTrue(navGraph.contains("CHARACTER_NEW"))
        assertTrue(navGraph.contains("CHARACTER_EDIT"))
        assertTrue(navGraph.contains("CHARACTER_DETAIL"))
        assertTrue(mainActivity.contains("CharacterListScreen"))
        assertTrue(mainActivity.contains("CharacterEditScreen"))
        assertTrue(mainActivity.contains("CharacterDetailScreen"))
        assertTrue(strings.contains("character_edit_title"))
        assertTrue(strings.contains("character_list_search_hint"))
    }

    @Test
    fun m2CharacterRoutesUsePrototypeLocalBottomBar() {
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()
        val listScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterListScreen.kt").readText()
        val detailScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterDetailScreen.kt")
        val editScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterEditScreen.kt").readText()

        assertTrue(mainActivity.contains("isCharacterManagementRoute"))
        assertTrue(mainActivity.contains("if (!isCharacterManagementRoute)"))
        assertTrue(listScreen.contains("CharacterLocalBottomBar"))
        assertTrue(detailScreen.exists())
        assertTrue(detailScreen.readText().contains("CharacterLocalBottomBar"))
        assertTrue(editScreen.contains("CharacterLocalBottomBar"))
    }

    @Test
    fun m2CharacterChatEntryPreservesReturnStack() {
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()
        val listScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterListScreen.kt").readText()
        val roleChrome = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterRoleChrome.kt").readText()

        assertTrue(mainActivity.contains("openCharacterChatFromCharacterManagement"))
        assertTrue(mainActivity.contains("onBackToHome = { if (!navController.popBackStack())"))
        assertFalse(mainActivity.contains("onOpenChat = { navigateMainTab(STRoutes.CHAT) }"))
        assertFalse(listScreen.contains("TextButton(onClick = onOpenChat)"))
        assertFalse(roleChrome.contains("CharacterLocalNav.CHAT"))
        assertFalse(roleChrome.contains("character_local_nav_chat"))
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

    @Test
    fun androidLocalesExposeEnglishAndSimplifiedChinese() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val localeConfig = File("src/main/res/xml/locales_config.xml")

        assertTrue(manifest.contains("android:localeConfig=\"@xml/locales_config\""))
        assertTrue(localeConfig.exists())

        val configText = localeConfig.readText()
        assertTrue(configText.contains("android:name=\"en\""))
        assertTrue(configText.contains("android:name=\"zh-CN\""))
    }

    @Test
    fun simplifiedChineseStringsMatchDefaultKeysAndPlaceholders() {
        val defaultStrings = readStringResources(File("src/main/res/values/strings.xml"))
        val chineseStrings = readStringResources(File("src/main/res/values-zh-rCN/strings.xml"))

        assertEquals(defaultStrings.keys, chineseStrings.keys)
        defaultStrings.forEach { (key, value) ->
            assertEquals("placeholder mismatch for $key", placeholders(value), placeholders(chineseStrings.getValue(key)))
        }

        assertEquals("返回", chineseStrings.getValue("back"))
        assertEquals("设置", chineseStrings.getValue("settings_title"))
        assertEquals("角色", chineseStrings.getValue("character_hub_title"))
    }

    @Test
    fun characterManagementDoesNotAddUserVisibleEnglishLiterals() {
        val files = listOf(
            File("src/main/java/io/github/sanitised/st/ui/screens/CharacterListScreen.kt"),
            File("src/main/java/io/github/sanitised/st/ui/screens/CharacterDetailScreen.kt"),
            File("src/main/java/io/github/sanitised/st/ui/screens/CharacterEditScreen.kt")
        ).filter { it.exists() }
        val userVisibleEnglish = Regex(
            """(onShowMessage\(".*[A-Za-z].*"\)|error\.message \?: ".*[A-Za-z].*"|Text\((?:text = )?".*[A-Za-z].*"\)|contentDescription = ".*[A-Za-z].*")"""
        )
        val matches = files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (userVisibleEnglish.containsMatchIn(line)) "${file.name}:${index + 1}: ${line.trim()}" else null
            }
        }

        assertTrue("user-visible English literals found:\n${matches.joinToString("\n")}", matches.isEmpty())
    }

    private fun readStringResources(file: File): Map<String, String> {
        assertTrue("${file.path} should exist", file.exists())
        val stringPattern = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        return stringPattern.findAll(file.readText())
            .associate { match -> match.groupValues[1] to match.groupValues[2] }
    }

    private fun placeholders(value: String): List<String> {
        return Regex("""%(\d+\$)?[sd]|%%""")
            .findAll(value)
            .map { it.value }
            .toList()
    }
}
