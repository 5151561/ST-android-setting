package io.github.sanitised.st.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRuntimeExitArchitectureTest {

    @Test
    fun chatRouteDoesNotCreateOrReferenceHiddenWebViewRuntime() {
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()

        listOf(
            "ChatWebViewScreen",
            "ChatRuntimeBridge",
            "BridgeChatEngine",
            "BridgeEvent",
            "BridgeMessage",
            "ChatBridgeModels",
            "DefaultChatRuntimeBridgeActions",
            "WebViewTarget",
            "chatRuntimeActivated",
            "pendingWebViewTarget",
        ).forEach { forbidden ->
            assertFalse("MainActivity still references $forbidden", mainActivity.contains(forbidden))
        }
    }

    @Test
    fun chatRuntimeWebViewSourcesAreRemovedFromTheAppRuntime() {
        listOf(
            "src/main/java/io/github/sanitised/st/ui/webview/ChatWebViewScreen.kt",
            "src/main/java/io/github/sanitised/st/ui/webview/STAndroidBridge.kt",
            "src/main/java/io/github/sanitised/st/ui/webview/WebViewNavigator.kt",
            "src/main/java/io/github/sanitised/st/chat/ChatRuntimeBridge.kt",
            "src/main/java/io/github/sanitised/st/chat/BridgeAlignment.kt",
            "src/main/java/io/github/sanitised/st/chat/ChatBridgeModels.kt",
            "src/main/java/io/github/sanitised/st/chat/engine/BridgeChatEngine.kt",
            "src/main/java/io/github/sanitised/st/chat/engine/ChatRuntimeBridgeActions.kt",
            "src/main/assets/chat_runtime_adapter.js",
        ).forEach { path ->
            assertFalse("$path should not be part of the native runtime", File(path).exists())
        }
    }

    @Test
    fun pastChatsNewChatActionIsNativeInsteadOfPlaceholder() {
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()
        val pastChatsRoute = mainActivity
            .substringAfter("route = STRoutes.PAST_CHATS")
            .substringBefore("route = STRoutes.CHAR_FORM")

        assertFalse(pastChatsRoute.contains("新建聊天原生端点正在接入"))
        assertTrue(pastChatsRoute.contains("nativeChatRuntime.createNewChat(avatar)"))
        assertTrue(pastChatsRoute.contains("openCharacterChatFromCharacterManagement(avatar, newChat)"))
    }
}
