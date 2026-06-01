package io.github.sanitised.st.ui.prototype

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInterfaceAuditRegressionTest {
    @Test
    fun nativeChatScreenDoesNotShowHardcodedFakeRuntimeOrDateData() {
        val source = File("src/main/java/io/github/sanitised/st/chat/NativeChatScreen.kt").readText()

        assertFalse(source.contains("Claude Sonnet"))
        assertFalse(source.contains("200k 上下文"))
        assertFalse(source.contains("今天 14:00"))
    }

    @Test
    fun prototypeCharacterDetailDoesNotUseFakeFallbackBiography() {
        val source = File("src/main/java/io/github/sanitised/st/ui/prototype/PrototypeCharacterScreens.kt").readText()

        listOf(
            "原型演示",
            "v2.1",
            "中文 · 双语",
            "她不是英雄，也不是反派",
            "冷静 / 直接 / 私下幽默",
            "短句、偶尔粗口、行话很多",
            "诶？又是你啊",
            "备选 1"
        ).forEach { fakeText ->
            assertFalse("Unexpected fake fallback: $fakeText", source.contains(fakeText))
        }
    }

    @Test
    fun drawerBadgesAreComputedInsteadOfStaticPrototypeNumbers() {
        val source = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()

        assertFalse(source.contains("badgeText = \"23\""))
        assertFalse(source.contains("badgeText = \"247\""))
        assertFalse(source.contains("badgeText = \"2\""))
        assertFalse(source.contains("badgeText = \"4\""))
        assertFalse(source.contains("supportingText = \"6 个\""))
        assertTrue(source.contains("rememberSaveable(stateSaver = webViewTargetSaver())"))
    }

    @Test
    fun chatListFiltersDoNotClassifyRealChatsByIdSubstring() {
        val source = File("src/main/java/io/github/sanitised/st/ui/prototype/PrototypeHomeScreen.kt").readText()

        assertFalse(source.contains("id.contains(\"group\")"))
        assertFalse(source.contains("id.contains(\"checkpoint\")"))
        assertFalse(source.contains("进行中"))
    }

    @Test
    fun prototypeFallbackFactoriesStayRemoved() {
        val source = File("src/main/java/io/github/sanitised/st/ui/prototype/PrototypeModels.kt").readText()

        assertFalse(source.contains("fun prototypeFallbackChats"))
        assertFalse(source.contains("fun prototypeFallbackCharacters"))
    }

    @Test
    fun characterLibraryUsesRealTagsAndGuardsOfflineReader() {
        val source = File("src/main/java/io/github/sanitised/st/ui/prototype/PrototypeCharacterScreens.kt").readText()

        assertTrue(source.contains("prototypeCharacterTagFilters(characters)"))
        assertTrue(source.contains("runCatching { reader.listCharacters() }"))
        assertFalse(source.contains("listOf(\"全部\", \"收藏\", \"最近\", \"日常\", \"奇幻\", \"科幻\", \"历史\")"))
    }

    @Test
    fun characterSearchUsesInlineSearchBarInsteadOfDialog() {
        val characterSource = File("src/main/java/io/github/sanitised/st/ui/prototype/PrototypeCharacterScreens.kt").readText()
        val componentSource = File("src/main/java/io/github/sanitised/st/ui/prototype/PrototypeComponents.kt").readText()

        assertFalse(characterSource.contains("searchDialogOpen"))
        assertFalse(characterSource.contains("title = { Text(\"搜索角色\") }"))
        assertTrue(characterSource.contains("PrototypeSearchBar("))
        assertTrue(characterSource.contains("onValueChange = { searchQuery = it }"))
        assertTrue(componentSource.contains("fun PrototypeSearchBar("))
        assertTrue(componentSource.contains("onValueChange: (String) -> Unit"))
    }

    @Test
    fun offlineCharacterLibraryCanRenderLocalCharacters() {
        val source = File("src/main/java/io/github/sanitised/st/ui/prototype/PrototypeCharacterScreens.kt").readText()

        assertFalse(source.contains("!serverRunning -> PrototypeOfflineBlock"))
        assertTrue(source.contains("!serverRunning && characters.isEmpty() -> PrototypeOfflineBlock"))
        assertTrue(source.indexOf("loading ->") < source.indexOf("!serverRunning && characters.isEmpty()"))
    }

    @Test
    fun chatListDoesNotRenderUnavailableStreamingOrUnreadState() {
        val source = File("src/main/java/io/github/sanitised/st/ui/prototype/PrototypeHomeScreen.kt").readText()

        assertFalse(source.contains("item.streaming"))
        assertFalse(source.contains("item.unread"))
        assertFalse(source.contains("● 进行中"))
    }

    @Test
    fun groupListUsesDedicatedNewGroupRouteWithoutLegacyInlineCreateView() {
        val source = File("src/main/java/io/github/sanitised/st/ui/prototype/PrototypeGroupChatScreen.kt").readText()

        assertFalse(source.contains("GroupCreateView"))
        assertFalse(source.contains("isCreating"))
        assertFalse(source.contains("GroupCreateRequest"))
        assertTrue(source.contains("onCreate = onNavigateToNewGroup"))
    }

    @Test
    fun drawerGroupChatRouteShowsGroupListBeforeOpeningSpecificGroupChat() {
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()
        val groupRouteBlock = mainActivity
            .substringAfter("composable(STRoutes.GROUP_CHAT) {")
            .substringBefore("route = STRoutes.GROUP_CHAT_DETAIL")
        val groupListSource = File("src/main/java/io/github/sanitised/st/ui/prototype/PrototypeGroupChatScreen.kt").readText()

        assertTrue(groupRouteBlock.contains("PrototypeGroupChatScreen("))
        assertTrue(groupRouteBlock.contains("onOpenGroupChat = openGroupChat"))
        assertFalse(Regex("""(?<!Prototype)GroupChatScreen\(""").containsMatchIn(groupRouteBlock))
        assertTrue(groupListSource.contains("onOpenGroupChat(group.id, group.chatId.takeIf { it.isNotBlank() })"))
    }

    @Test
    fun groupListSelectionOpensGroupChatDetailInsteadOfGenericChatRoute() {
        val routes = File("src/main/java/io/github/sanitised/st/ui/navigation/STNavGraph.kt").readText()
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()
        val openGroupChatBlock = mainActivity
            .substringAfter("val openGroupChat: (String, String?) -> Unit = { groupId, chatId ->")
            .substringBefore("val dynamicDrawerItems")
        val groupDetailRouteBlock = mainActivity
            .substringAfter("route = STRoutes.GROUP_CHAT_DETAIL")
            .substringBefore("composable(\"group-chat/settings\")")

        assertTrue(routes.contains("const val GROUP_CHAT_DETAIL"))
        assertTrue(routes.contains("fun groupChatDetail(groupId: String, chatId: String?)"))
        assertTrue(openGroupChatBlock.contains("navController.navigate(STRoutes.groupChatDetail(groupId, chatId))"))
        assertFalse(openGroupChatBlock.contains("pendingWebViewTarget = WebViewTarget.GroupChat"))
        assertFalse(openGroupChatBlock.contains("navController.navigate(STRoutes.CHAT)"))
        assertTrue(groupDetailRouteBlock.contains("GroupChatScreen("))
    }

    @Test
    fun chatNavigationDoesNotShowPreviousChatMessagesForUnmatchedTarget() {
        // The runtime WebView is now hosted persistently above the NavHost, so the
        // store is no longer force-reset on every chat entry (that reset caused the
        // multi-second reload). Stale messages from a previous chat must instead be
        // suppressed by gating the message list on targetMatched: when the requested
        // target does not match what the store currently holds, the loading view is
        // shown until the new snapshot arrives.
        val nativeChat = File("src/main/java/io/github/sanitised/st/chat/NativeChatScreen.kt").readText()

        assertTrue(nativeChat.contains("val targetMatched = targetMatchesStore(target, store)"))
        assertTrue(nativeChat.contains("if (!targetMatched ||"))
        assertTrue(nativeChat.contains("ChatLoadingView("))
    }

    @Test
    fun genericChatTargetDoesNotTreatEmptyStoreAsMatched() {
        val source = File("src/main/java/io/github/sanitised/st/chat/NativeChatScreen.kt").readText()

        assertFalse(source.contains("WebViewTarget.CHAT -> true"))
        assertTrue(source.contains("WebViewTarget.CHAT -> store.chatFile.isNotBlank()"))
    }
}
