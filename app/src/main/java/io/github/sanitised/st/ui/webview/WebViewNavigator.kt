package io.github.sanitised.st.ui.webview

import android.webkit.WebView

enum class WebViewTarget {
    CHAT,
    CHARACTERS,
    TOOLS
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

    fun navigateToTarget(webView: WebView, target: WebViewTarget) {
        val script = targetScript(target) ?: return
        webView.evaluateJavascript(script, null)
    }

    private fun targetScript(target: WebViewTarget): String? {
        val terms = when (target) {
            WebViewTarget.CHAT -> return null
            WebViewTarget.CHARACTERS -> listOf("characters", "character management", "角色")
            WebViewTarget.TOOLS -> listOf("extensions", "world info", "tools", "preset", "工具", "世界书", "预设")
        }
        val encodedTerms = terms.joinToString(",") { "'${it.replace("'", "\\'")}'" }
        return """
            (function() {
              const terms = [$encodedTerms];
              const selectors = [
                'button',
                'a',
                '[role="button"]',
                '[title]',
                '[aria-label]',
                '.menu_button',
                '.drawer-toggle'
              ];
              const matches = (node) => {
                const text = [
                  node.textContent,
                  node.getAttribute && node.getAttribute('title'),
                  node.getAttribute && node.getAttribute('aria-label'),
                  node.id,
                  node.className
                ].filter(Boolean).join(' ').toLowerCase();
                return terms.some((term) => text.includes(term.toLowerCase()));
              };
              for (const selector of selectors) {
                const node = Array.from(document.querySelectorAll(selector)).find(matches);
                if (node) {
                  node.click();
                  return true;
                }
              }
              return false;
            })();
        """.trimIndent()
    }
}
