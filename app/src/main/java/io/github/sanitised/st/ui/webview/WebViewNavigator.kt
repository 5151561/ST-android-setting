package io.github.sanitised.st.ui.webview

import android.content.Context
import android.webkit.WebView
import org.json.JSONObject

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

    fun navigateToTarget(webView: WebView, target: WebViewTarget) {
        when (target) {
            WebViewTarget.CHAT -> Unit
            is WebViewTarget.CharacterChat -> {
                webView.evaluateJavascript(characterChatScript(target), null)
            }
        }
    }

    private fun characterChatScript(target: WebViewTarget.CharacterChat): String {
        val avatar = JSONObject.quote(target.avatar)
        val chatFile = target.chatFile?.takeIf { it.isNotBlank() }?.let(JSONObject::quote) ?: "null"
        return """
            (function() {
              const avatar = $avatar;
              const chatFile = $chatFile;
              let attempts = 0;
              const maxAttempts = 25;
              const normalizeChat = (value) => value ? String(value).replace(/\.jsonl$/i, '') : null;
              const findCharacterIndex = (context) => {
                const characters = Array.isArray(context?.characters) ? context.characters : [];
                return characters.findIndex((character) => {
                  if (!character) return false;
                  return character.avatar === avatar ||
                    character.avatar_url === avatar ||
                    character.filename === avatar ||
                    character.name === avatar;
                });
              };
              const run = () => {
                attempts += 1;
                const root = globalThis.SillyTavern;
                const context = root && typeof root.getContext === 'function' ? root.getContext() : null;
                if (!context) {
                  if (attempts < maxAttempts) setTimeout(run, 200);
                  return;
                }
                const characterIndex = findCharacterIndex(context);
                const selectCharacter = context.selectCharacterById || globalThis.selectCharacterById;
                if (characterIndex >= 0 && typeof selectCharacter === 'function') {
                  selectCharacter(characterIndex, { switchMenu: false });
                }
                const openCharacterChat = context.openCharacterChat || globalThis.openCharacterChat;
                const targetChat = normalizeChat(chatFile);
                if (targetChat && typeof openCharacterChat === 'function') {
                  setTimeout(() => openCharacterChat(targetChat), 250);
                }
                if (characterIndex < 0 && attempts < maxAttempts) setTimeout(run, 200);
              };
              run();
            })();
        """.trimIndent()
    }
}
