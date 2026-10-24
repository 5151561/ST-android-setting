package io.github.sanitised.st.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 架构边界契约守卫测试：
 * 1. 禁止 UI 层 (ui/screens, ui/navigation) 与聊天界面层 (chat) 直接实例化 TavernCoreClient / TauriTavernJniClient。
 * 2. 防止上帝文件 (MainActivity.kt, STAdvancedScreens.kt, STSystemScreens.kt) 与已退役 Node HTTP 传输层回流。
 * 3. 守护 TauriTavern 唯一真相源铁律（禁止客户端伪造探测请求、硬编码模型列表、手搓 connection_profiles）。
 * 4. 确保 UpdateManager 将应用级与界面级偏好设置委托给 UserPreferencesRepository。
 */
class ArchitectureBoundaryContractTest {

    @Test
    fun uiAndChatLayersDoNotDirectlyInstantiateTavernCoreClient() {
        val uiAndChatDirs = listOf(
            File("src/main/java/io/github/sanitised/st/ui"),
            File("src/main/java/io/github/sanitised/st/chat")
        )

        val violations = mutableListOf<String>()
        uiAndChatDirs.forEach { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val content = file.readText()
                    if (content.contains("TavernCoreClient(") || content.contains("TauriTavernJniClient(")) {
                        violations.add(file.relativeTo(File("src/main/java")).path)
                    }
                }
        }

        assertTrue(
            "UI and Chat layers must access backend APIs via Repository or tavernApi provider instead of instantiating client directly. Violations: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun godFilesAndLegacyHttpTransportStayRemovedOrWithinLineBudget() {
        assertFalse(
            "STAdvancedScreens.kt must remain decomposed into single-responsibility screen files.",
            File("src/main/java/io/github/sanitised/st/ui/screens/STAdvancedScreens.kt").exists()
        )
        assertFalse(
            "STSystemScreens.kt must remain decomposed into single-responsibility screen files.",
            File("src/main/java/io/github/sanitised/st/ui/screens/STSystemScreens.kt").exists()
        )

        val retiredHttpFiles = listOf(
            "src/main/java/io/github/sanitised/st/api/TavernCoreApi.kt",
            "src/main/java/io/github/sanitised/st/api/TavernHttpTransport.kt",
            "src/main/java/io/github/sanitised/st/api/TavernCharacterEndpoints.kt",
            "src/main/java/io/github/sanitised/st/api/TavernPresetSecretEndpoints.kt",
            "src/main/java/io/github/sanitised/st/api/TavernWorldPersonaEndpoints.kt",
            "src/main/java/io/github/sanitised/st/api/TextGenerationServer.kt",
            "src/main/kotlin/com/sillytavern/android/runtime/RustRuntimeBridge.kt"
        )
        retiredHttpFiles.forEach { path ->
            assertFalse(
                "Retired Node HTTP / duplicate JNI bridge file must not regress: $path",
                File(path).exists()
            )
        }

        val mainActivityLines = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readLines().size
        assertTrue(
            "MainActivity.kt should stay under 650 lines (actual: $mainActivityLines).",
            mainActivityLines <= 650
        )

        val jniClientLines = File("src/main/java/io/github/sanitised/st/api/TauriTavernJniClient.kt").readLines().size
        assertTrue(
            "TauriTavernJniClient.kt facade should stay under 500 lines (actual: $jniClientLines).",
            jniClientLines <= 500
        )
    }

    @Test
    fun clientDoesNotReinventUpstreamConnectionOrModelLogic() {
        val generationOpsSource =
            File("src/main/java/io/github/sanitised/st/api/TauriTavernJniGenerationOps.kt").readText()
        assertFalse(
            "TauriTavernJniGenerationOps must not hardcode fallbackModelsForSource.",
            generationOpsSource.contains("fallbackModelsForSource")
        )
        assertFalse(
            "TauriTavernJniGenerationOps must not probe chat completion by faking chat generation.",
            generationOpsSource.contains("probeChatCompletionConnection")
        )

        val dataOpsSource =
            File("src/main/java/io/github/sanitised/st/api/TauriTavernJniDataOps.kt").readText()
        assertFalse(
            "TauriTavernJniDataOps must delegate connection profiles to LlmConnectionService instead of settings.connection_profiles.",
            dataOpsSource.contains("listConnectionProfilesFromSettings") ||
                dataOpsSource.contains("saveConnectionProfileToSettings")
        )

        val apiConnectionStateSource =
            File("src/main/java/io/github/sanitised/st/ui/screens/ApiConnectionState.kt").readText()
        assertFalse(
            "ApiConnectionState must not hardcode defaultModels lists.",
            apiConnectionStateSource.contains("defaultModels")
        )
    }

    @Test
    fun updateManagerDelegatesUserPreferencesToRepository() {
        val updateManagerSource = File("src/main/java/io/github/sanitised/st/UpdateManager.kt").readText()
        assertTrue(updateManagerSource.contains("userPreferencesRepository: UserPreferencesRepository"))
        assertTrue(updateManagerSource.contains("userPreferencesRepository.setThemeMode("))
        assertTrue(updateManagerSource.contains("userPreferencesRepository.setFontSize("))
    }
}
