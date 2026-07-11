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
    fun mainNavigationIsDrawerOnlyWithoutBottomBar() {
        // 底部导航栏与抽屉路由重复,已整体移除;守卫防止回流。
        val scaffoldFile = File("src/main/java/io/github/sanitised/st/ui/navigation/STNavigationScaffold.kt").readText()

        assertTrue(scaffoldFile.contains("ModalNavigationDrawer("))
        assertTrue(scaffoldFile.contains("ModalDrawerSheet("))
        assertFalse(scaffoldFile.contains("NavigationBar("))
        assertFalse(scaffoldFile.contains("NavigationRail("))
        assertFalse(scaffoldFile.contains("BottomNavItem"))
        assertFalse(File("src/main/java/io/github/sanitised/st/ui/navigation/STBottomBar.kt").exists())
    }

    @Test
    fun hiddenNavigationDisablesDrawerGestures() {
        val scaffoldFile = File("src/main/java/io/github/sanitised/st/ui/navigation/STNavigationScaffold.kt").readText()

        assertTrue(scaffoldFile.contains("ModalNavigationDrawer("))
        assertTrue(scaffoldFile.contains("gesturesEnabled = showNavigation"))
    }

    @Test
    fun mainActivityAutoOpensBrowserWhenServiceBecomesReadyAndPreferenceIsEnabled() {
        val mainActivityFile = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()

        assertTrue(mainActivityFile.contains("val tavernReadyForAutoOpen = remember { mutableStateOf(false) }"))
        assertTrue(mainActivityFile.contains("client.healthCheck().ok"))
        assertTrue(mainActivityFile.contains("viewModel.autoOpenBrowserWhenReady.value && justBecameReady"))
        assertTrue(mainActivityFile.contains("openSillyTavernInBrowser(statusState.value.port)"))
        assertTrue(mainActivityFile.contains("autoOpenBrowserTriggeredForCurrentRun.value = true"))
    }
}
