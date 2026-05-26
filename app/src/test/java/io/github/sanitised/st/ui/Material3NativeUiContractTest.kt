package io.github.sanitised.st.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Material3NativeUiContractTest {
    @Test
    fun nativePagesUseMaterialColorSchemeInsteadOfLegacySTColors() {
        val sourceRoot = File("src/main/java/io/github/sanitised/st")
        val allowedLegacyThemeFiles = setOf(
            "src/main/java/io/github/sanitised/st/ui/theme/STColors.kt",
            "src/main/java/io/github/sanitised/st/ui/theme/STTheme.kt"
        )

        val offenders = sourceRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.invariantSeparatorsPath in allowedLegacyThemeFiles }
            .filter { it.readText().contains("STTheme.colors") }
            .map { it.invariantSeparatorsPath }
            .toList()

        assertTrue(
            "Native UI files should use MaterialTheme.colorScheme; legacy STTheme.colors usages: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun themeSupportsDynamicAndBrandColorSources() {
        val themeFile = File("src/main/java/io/github/sanitised/st/ui/theme/STTheme.kt").readText()
        val modelsFile = File("src/main/java/io/github/sanitised/st/MainViewModelModels.kt").readText()

        assertTrue(modelsFile.contains("enum class ThemeColorSource"))
        assertTrue(themeFile.contains("dynamicLightColorScheme"))
        assertTrue(themeFile.contains("dynamicDarkColorScheme"))
        assertTrue(themeFile.contains("colorSource: ThemeColorSource"))
        assertTrue(themeFile.contains("typography ="))
        assertTrue(themeFile.contains("shapes ="))
    }

    @Test
    fun mainNavigationUsesMaterial3BarAndRail() {
        val bottomBarFile = File("src/main/java/io/github/sanitised/st/ui/navigation/STBottomBar.kt").readText()

        assertTrue(bottomBarFile.contains("NavigationBar("))
        assertTrue(bottomBarFile.contains("NavigationBarItem("))
        assertTrue(bottomBarFile.contains("NavigationRail("))
        assertTrue(bottomBarFile.contains("NavigationRailItem("))
        assertFalse(bottomBarFile.contains("clickable { onNavigate"))
    }
}
