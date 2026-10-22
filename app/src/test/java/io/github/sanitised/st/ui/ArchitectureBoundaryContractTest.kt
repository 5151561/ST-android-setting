package io.github.sanitised.st.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 架构边界契约守卫测试：
 * 1. 禁止 UI 层 (ui/screens, ui/navigation) 与聊天界面层 (chat) 直接实例化 TavernCoreClient。
 * 2. 防止上帝文件 (MainActivity.kt, TavernCoreApi.kt, STAdvancedScreens.kt, STSystemScreens.kt) 回流膨胀。
 * 3. 确保 UpdateManager 将应用级与界面级偏好设置委托给 UserPreferencesRepository。
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
                    if (content.contains("TavernCoreClient(")) {
                        violations.add(file.relativeTo(File("src/main/java")).path)
                    }
                }
        }

        assertTrue(
            "UI and Chat layers must access network APIs via Repository or tavernApi provider instead of instantiating TavernCoreClient directly. Violations: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun godFilesStayDecomposedAndWithinLineBudget() {
        assertFalse(
            "STAdvancedScreens.kt must remain decomposed into single-responsibility screen files.",
            File("src/main/java/io/github/sanitised/st/ui/screens/STAdvancedScreens.kt").exists()
        )
        assertFalse(
            "STSystemScreens.kt must remain decomposed into single-responsibility screen files.",
            File("src/main/java/io/github/sanitised/st/ui/screens/STSystemScreens.kt").exists()
        )

        val mainActivityLines = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readLines().size
        assertTrue(
            "MainActivity.kt should stay under 650 lines (actual: $mainActivityLines).",
            mainActivityLines <= 650
        )

        val tavernCoreApiLines = File("src/main/java/io/github/sanitised/st/api/TavernCoreApi.kt").readLines().size
        assertTrue(
            "TavernCoreApi.kt facade should stay under 500 lines (actual: $tavernCoreApiLines).",
            tavernCoreApiLines <= 500
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
