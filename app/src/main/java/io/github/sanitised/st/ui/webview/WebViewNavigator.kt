package io.github.sanitised.st.ui.webview

import android.content.Context
import android.webkit.WebView

sealed class WebViewTarget {
    object CHAT : WebViewTarget()
    data class CharacterChat(val avatar: String, val chatFile: String? = null) : WebViewTarget()
}

object WebViewNavigator {
    private var adapterInjected = false

    fun injectAndroidRuntimeFlags(webView: WebView) {
        webView.evaluateJavascript(
            """
            (function() {
              window.ST_ANDROID = true;
              document.documentElement.dataset.stAndroid = '1';
            })();
            """.trimIndent(),
            null
        )
    }

    fun injectChatRuntimeAdapter(webView: WebView, context: Context) {
        if (adapterInjected) return
        val script = context.assets.open("chat_runtime_adapter.js")
            .bufferedReader(Charsets.UTF_8)
            .readText()
        webView.evaluateJavascript(script, null)
        adapterInjected = true
    }

    fun resetInjectionState() {
        adapterInjected = false
    }

    @Suppress("UNUSED_PARAMETER")
    fun navigateToTarget(webView: WebView, target: WebViewTarget) {
        when (target) {
            WebViewTarget.CHAT -> Unit
            is WebViewTarget.CharacterChat -> Unit
        }
    }
}
