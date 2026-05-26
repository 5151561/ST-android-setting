package io.github.sanitised.st.ui.webview

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewConfigurationSourceTest {
    @Test
    fun embeddedSillyTavernWebViewDisablesSystemDarkening() {
        val source = File("src/main/java/io/github/sanitised/st/ui/webview/ChatWebViewScreen.kt").readText()

        assertTrue(
            "WebView should opt out of Android 13+ algorithmic darkening.",
            source.contains("isAlgorithmicDarkeningAllowed = false")
        )
        assertTrue(
            "WebView should disable legacy Force Dark on Android 10-12.",
            source.contains("WebSettings.FORCE_DARK_OFF")
        )
    }

    @Test
    fun embeddedSillyTavernWebViewDoesNotTrustSavedLoadedUrlState() {
        val source = File("src/main/java/io/github/sanitised/st/ui/webview/ChatWebViewScreen.kt").readText()

        assertTrue(
            "A recreated WebView starts at about:blank, so readiness must be based on WebView.url.",
            source.contains(".hasLoadedBaseUrl(baseUrl)")
        )
        assertTrue(
            "The loaded URL flag must not survive WebView recreation.",
            !source.contains("loadedUrl by rememberSaveable")
        )
    }

    @Test
    fun embeddedSillyTavernWebViewWritesPageDiagnosticsToLogs() {
        val source = File("src/main/java/io/github/sanitised/st/ui/webview/ChatWebViewScreen.kt").readText()

        assertTrue(
            "WebView console output should be copied to service.log.",
            source.contains("onConsoleMessage")
        )
        assertTrue(
            "Page lifecycle events should be copied to service.log.",
            source.contains("onPageCommitVisible")
        )
        assertTrue(
            "After page load, the app should record basic DOM state for blank-page debugging.",
            source.contains("logDocumentState")
        )
    }

    @Test
    fun embeddedSillyTavernWebViewIsNotDestroyedByStateDrivenRecomposition() {
        val source = File("src/main/java/io/github/sanitised/st/ui/webview/ChatWebViewScreen.kt").readText()

        assertTrue(
            "The WebView instance should be remembered directly, not stored through Compose state.",
            source.contains("val webView = remember(context)")
        )
        assertTrue(
            "The WebView holder state must not make AndroidView factory writes trigger recomposition.",
            !source.contains("var webView by remember { mutableStateOf<WebView?>")
        )
        assertTrue(
            "The WebView must not be destroyed when the webView state key changes during recomposition.",
            !source.contains("DisposableEffect(webView)")
        )
        assertTrue(
            "AndroidView should reuse the remembered WebView instead of publishing a fresh one to state.",
            !source.contains("webView = this")
        )
    }
}
