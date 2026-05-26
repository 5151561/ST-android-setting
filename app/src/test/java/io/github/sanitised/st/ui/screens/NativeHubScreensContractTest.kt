package io.github.sanitised.st.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
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
}
