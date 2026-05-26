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
    fun m2CharacterRoutesUseSharedMainBottomBar() {
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()
        val listScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterListScreen.kt").readText()
        val detailScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterDetailScreen.kt")
        val editScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterEditScreen.kt").readText()
        val roleChrome = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterRoleChrome.kt")

        assertFalse(mainActivity.contains("isCharacterManagementRoute"))
        assertFalse(mainActivity.contains("if (!isCharacterManagementRoute)"))
        assertTrue(mainActivity.contains("bottomBarSelectedRoute"))
        assertTrue(mainActivity.contains("STBottomBar("))
        assertFalse(listScreen.contains("CharacterLocalBottomBar"))
        assertTrue(detailScreen.exists())
        assertFalse(detailScreen.readText().contains("CharacterLocalBottomBar"))
        assertFalse(editScreen.contains("CharacterLocalBottomBar"))
        assertFalse(roleChrome.exists())
    }

    @Test
    fun m2CharacterChatEntryPreservesReturnStack() {
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()
        val listScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterListScreen.kt").readText()

        assertTrue(mainActivity.contains("openCharacterChatFromCharacterManagement"))
        assertTrue(mainActivity.contains("onBackToHome = { if (!navController.popBackStack())"))
        assertFalse(mainActivity.contains("onOpenChat = { navigateMainTab(STRoutes.CHAT) }"))
        assertFalse(listScreen.contains("TextButton(onClick = onOpenChat)"))
    }

    @Test
    fun m2CharacterEditorHidesLocalDock() {
        val editScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterEditScreen.kt").readText()

        assertFalse(editScreen.contains("CharacterLocalBottomBar"))
    }

    @Test
    fun m2CharacterManagementExposesOriginalManagerP0Controls() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val listScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterListScreen.kt").readText()
        val editScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterEditScreen.kt").readText()
        val detailScreen = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterDetailScreen.kt").readText()
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

        assertTrue(editScreen.contains("alternateGreetings"))
        assertTrue(editScreen.contains("CharacterAlternateGreetingsEditor"))
        assertTrue(editScreen.contains("depthPromptDepthText"))
        assertTrue(editScreen.contains("talkativenessText"))
        assertTrue(editScreen.contains("CharacterTalkativenessField"))
        assertTrue(editScreen.contains("renameCharacter"))
        assertTrue(editScreen.contains("duplicateCharacter"))
        assertTrue(editScreen.contains("deleteCharacter"))
        assertTrue(editScreen.contains("updateCharacterAvatar"))
        assertTrue(editScreen.contains("exportCharacter"))
        assertTrue(listScreen.contains("importCharacter"))
        assertTrue(listScreen.contains("onSelectAll"))
        assertTrue(listScreen.contains("onDuplicateSelected"))
        assertTrue(listScreen.contains("onEditBatchTags"))
        assertTrue(listScreen.contains("showTagManager"))
        assertTrue(editScreen.contains("onUpdateAvatarNow"))
        assertTrue(editScreen.contains("CharacterAvatarImage"))
        assertTrue(editScreen.contains("CharacterTokenCounterSection"))
        assertFalse(editScreen.contains("CharacterAvatarProcessingMode"))
        assertTrue(editScreen.contains("replaceCharacterFromFile"))
        assertTrue(editScreen.contains("replaceCharacterFromSource"))
        assertTrue(detailScreen.contains("CharacterLinkRow"))
        assertTrue(detailScreen.contains("character_detail_assistant_label"))
        assertTrue(File("src/main/java/io/github/sanitised/st/ui/screens/CharacterEditTools.kt").exists())
        assertTrue(strings.contains("character_edit_alternate_greetings"))
        assertTrue(strings.contains("character_edit_depth_prompt"))
        assertTrue(strings.contains("character_edit_personality"))
        assertTrue(strings.contains("character_edit_scenario"))
        assertTrue(strings.contains("character_token_section"))
        assertFalse(strings.contains("character_avatar_processing_original"))
        assertFalse(strings.contains("character_avatar_processing_png"))
        assertFalse(strings.contains("character_avatar_processing_crop"))
        assertTrue(strings.contains("character_action_replace_file"))
        assertTrue(strings.contains("character_action_update_source"))
        assertTrue(strings.contains("character_detail_assistant_label"))
        assertTrue(strings.contains("character_batch_select_all"))
        assertTrue(strings.contains("character_batch_unfavorite"))
        assertTrue(strings.contains("character_batch_duplicate"))
        assertTrue(strings.contains("character_tags_manage"))
        assertTrue(strings.contains("character_avatar_update_now"))
        assertTrue(strings.contains("character_chat_rename_title"))
        assertFalse(File("src/main/java/io/github/sanitised/st/ui/screens/CharacterDetailScreen.kt").readText().contains("-renamed"))
    }

    @Test
    fun m3P0ImportFlowShowsPrecheckBeforeOverwrite() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()

        assertTrue(mainActivity.contains("NodeBackup.inspectImportUri"))
        assertTrue(mainActivity.contains("backupImportPreviewText"))
        assertTrue(mainActivity.contains("confirmEnabled = dialog.canImport"))
        assertTrue(strings.contains("dialog_import_checking_body"))
        assertTrue(strings.contains("backup_precheck_snapshot_advice"))
        assertTrue(strings.contains("backup_precheck_item_present"))
    }

    @Test
    fun m3P0ManageScreenExposesSettingsSnapshots() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val manageScreen = File("src/main/java/io/github/sanitised/st/UiManageSt.kt").readText()
        val viewModel = File("src/main/java/io/github/sanitised/st/MainViewModel.kt").readText()
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()

        assertTrue(strings.contains("settings_snapshot_title"))
        assertTrue(manageScreen.contains("onCreateSettingsSnapshot"))
        assertTrue(manageScreen.contains("onRestoreSettingsSnapshot"))
        assertTrue(viewModel.contains("listSettingsSnapshots"))
        assertTrue(mainActivity.contains("PendingDialog.RestoreSettingsSnapshot"))
    }

    @Test
    fun m3P0LogsScreenExposesDiagnosticsExport() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val logsScreen = File("src/main/java/io/github/sanitised/st/UiLogs.kt").readText()
        val viewModel = File("src/main/java/io/github/sanitised/st/MainViewModel.kt").readText()
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()
        val nodeService = File("src/main/java/io/github/sanitised/st/NodeService.kt").readText()

        assertTrue(strings.contains("logs_export_diagnostics"))
        assertTrue(strings.contains("diagnostics_export_complete"))
        assertTrue(logsScreen.contains("onExportDiagnostics"))
        assertTrue(mainActivity.contains("diagnosticExportLauncher"))
        assertTrue(viewModel.contains("exportDiagnostics"))
        assertTrue(nodeService.contains("unexpected exit"))
    }

    @Test
    fun toolsHubDoesNotDuplicateManageStBackupControls() {
        val toolsHub = File("src/main/java/io/github/sanitised/st/ui/screens/M1HubScreens.kt").readText()
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()

        assertFalse(toolsHub.contains("tools_hub_backup_title"))
        assertFalse(toolsHub.contains("onExportData"))
        assertFalse(toolsHub.contains("onImportData"))
        assertFalse(mainActivity.contains("onExportData = triggerExport"))
        assertFalse(mainActivity.contains("onImportData = triggerImport"))
    }

    @Test
    fun m3P1ExposesNativeCrossSystemRoutesAndTools() {
        val routes = File("src/main/java/io/github/sanitised/st/ui/navigation/STNavGraph.kt").readText()
        val toolsHub = File("src/main/java/io/github/sanitised/st/ui/screens/M1HubScreens.kt").readText()
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()
        val characterDetail = File("src/main/java/io/github/sanitised/st/ui/screens/CharacterDetailScreen.kt").readText()
        val strings = File("src/main/res/values/strings.xml").readText()

        assertTrue(File("src/main/java/io/github/sanitised/st/ui/screens/M3P1Screens.kt").exists())
        listOf(
            "WORLD_INFO",
            "PERSONA",
            "PRESETS",
            "CONNECTIONS",
            "CHAT_BACKUPS"
        ).forEach { routeName ->
            assertTrue("missing route $routeName", routes.contains(routeName))
            assertTrue("route $routeName not wired", mainActivity.contains("STRoutes.$routeName"))
        }

        listOf(
            "onOpenWorldInfo",
            "onOpenPersona",
            "onOpenPresets",
            "onOpenConnections",
            "onOpenChatBackups"
        ).forEach { callback ->
            assertTrue("missing tools callback $callback", toolsHub.contains(callback))
        }

        assertTrue(mainActivity.contains("WorldInfoScreen"))
        assertTrue(mainActivity.contains("PersonaScreen"))
        assertTrue(mainActivity.contains("PresetLiteScreen"))
        assertTrue(mainActivity.contains("ConnectionProfilesScreen"))
        assertTrue(mainActivity.contains("ChatBackupsScreen"))
        assertFalse(characterDetail.contains("character_lorebook_unavailable"))
        assertFalse(characterDetail.contains("character_persona_unavailable"))
        assertTrue(strings.contains("m3_world_info_title"))
        assertTrue(strings.contains("m3_persona_title"))
        assertTrue(strings.contains("m3_presets_title"))
        assertTrue(strings.contains("m3_connections_title"))
        assertTrue(strings.contains("m3_chat_backups_title"))
    }

    @Test
    fun m3P1PersonaAndPresetsUseListThenDetailEditors() {
        val screens = File("src/main/java/io/github/sanitised/st/ui/screens/M3P1Screens.kt").readText()

        assertTrue(screens.contains("PersonaViewMode.LIST"))
        assertTrue(screens.contains("PersonaViewMode.DETAIL"))
        assertTrue(screens.contains("PresetViewMode.LIST"))
        assertTrue(screens.contains("PresetViewMode.DETAIL"))
        assertTrue(screens.contains("PersonaDetailEditor"))
        assertTrue(screens.contains("PresetDetailEditor"))
        assertTrue(screens.contains("onBackToList"))
        assertFalse(screens.contains("selectedAvatar?.let { avatar ->\n            STSectionCard"))
        assertFalse(screens.contains("selectedApiId?.let { apiId ->\n            STSectionCard"))
    }

    @Test
    fun m3P1ChatBackupsUseLongPressMultiSelectDelete() {
        val screens = File("src/main/java/io/github/sanitised/st/ui/screens/M3P1Screens.kt").readText()

        assertTrue(screens.contains("combinedClickable"))
        assertTrue(screens.contains("selectedBackupNames"))
        assertTrue(screens.contains("deleteSelectedChatBackups"))
        assertTrue(screens.contains("m3_chat_backups_delete_selected"))
        assertFalse(
            screens.contains(
                "Icon(Icons.Filled.Delete, contentDescription = null)\n" +
                    "                        Spacer(Modifier.width(6.dp))\n" +
                    "                        Text(stringResource(R.string.delete))"
            )
        )
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
