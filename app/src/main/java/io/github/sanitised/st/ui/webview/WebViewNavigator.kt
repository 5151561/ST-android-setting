package io.github.sanitised.st.ui.webview

import android.webkit.WebView

enum class WebViewTarget {
    CHAT
}

object WebViewNavigator {
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

    fun navigateToTarget(target: WebViewTarget) {
        when (target) {
            WebViewTarget.CHAT -> Unit
        }
    }
}
