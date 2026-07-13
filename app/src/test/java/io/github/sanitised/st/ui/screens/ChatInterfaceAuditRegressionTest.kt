package io.github.sanitised.st.ui.screens

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
    fun stCharacterDetailDoesNotUseFakeFallbackBiography() {
        val source = File("src/main/java/io/github/sanitised/st/ui/screens/STCharacterScreens.kt").readText()

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
    fun drawerBadgesAreComputedInsteadOfStaticSTNumbers() {
        val source = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()

        assertFalse(source.contains("badgeText = \"23\""))
        assertFalse(source.contains("badgeText = \"247\""))
        assertFalse(source.contains("badgeText = \"2\""))
        assertFalse(source.contains("badgeText = \"4\""))
        assertFalse(source.contains("supportingText = \"6 个\""))
        assertTrue(source.contains("rememberSaveable(stateSaver = chatTargetSaver())"))
    }

    @Test
    fun chatListFiltersClassifyByRealFieldsInsteadOfIdSubstring() {
        val source = File("src/main/java/io/github/sanitised/st/ui/screens/STHomeScreen.kt").readText()

        // 群聊/检查点必须来自模型层的真实字段(GroupSummary / 检查点命名约定),
        // 不允许在 UI 层拿 id 子串猜分类。
        assertFalse(source.contains("id.contains(\"group\")"))
        assertFalse(source.contains("id.contains(\"checkpoint\")"))
        assertTrue(source.contains("it.kind == STChatKind.GROUP"))
        assertTrue(source.contains("it.isCheckpoint"))
    }

    @Test
    fun stFallbackFactoriesStayRemoved() {
        val source = File("src/main/java/io/github/sanitised/st/ui/screens/STModels.kt").readText()

        assertFalse(source.contains("fun stFallbackChats"))
        assertFalse(source.contains("fun stFallbackCharacters"))
    }

    @Test
    fun characterLibraryUsesRealTagsAndGuardsOfflineReader() {
        val source = File("src/main/java/io/github/sanitised/st/ui/screens/STCharacterScreens.kt").readText()

        assertTrue(source.contains("stCharacterTagFilters(characters)"))
        assertTrue(source.contains("runCatching { reader.listCharacters() }"))
        assertFalse(source.contains("listOf(\"全部\", \"收藏\", \"最近\", \"日常\", \"奇幻\", \"科幻\", \"历史\")"))
    }

    @Test
    fun characterSearchUsesInlineSearchBarInsteadOfDialog() {
        val characterSource = File("src/main/java/io/github/sanitised/st/ui/screens/STCharacterScreens.kt").readText()
        val componentSource = File("src/main/java/io/github/sanitised/st/ui/screens/STComponents.kt").readText()

        assertFalse(characterSource.contains("searchDialogOpen"))
        assertFalse(characterSource.contains("title = { Text(\"搜索角色\") }"))
        assertTrue(characterSource.contains("STSearchBar("))
        assertTrue(characterSource.contains("onValueChange = { searchQuery = it }"))
        assertTrue(componentSource.contains("fun STSearchBar("))
        assertTrue(componentSource.contains("onValueChange: (String) -> Unit"))
    }

    @Test
    fun offlineCharacterLibraryCanRenderLocalCharacters() {
        val source = File("src/main/java/io/github/sanitised/st/ui/screens/STCharacterScreens.kt").readText()

        assertFalse(source.contains("!serverRunning -> STOfflineBlock"))
        assertTrue(source.contains("!serverRunning && characters.isEmpty() -> STOfflineBlock"))
        assertTrue(source.indexOf("loading ->") < source.indexOf("!serverRunning && characters.isEmpty()"))
    }

    @Test
    fun chatListStreamingAndUnreadStatesAreBackedByRealSources() {
        // 未读/进行中曾是原型里的假状态,禁止渲染;现在有真实数据源后放开,
        // 但必须接在真实来源上:进行中 = chatStore.isGenerating,未读 = ChatSeenStore 打点。
        val home = File("src/main/java/io/github/sanitised/st/ui/screens/STHomeScreen.kt").readText()
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()
        val models = File("src/main/java/io/github/sanitised/st/ui/screens/STModels.kt").readText()

        assertTrue(home.contains("item.inProgress"))
        assertTrue(home.contains("item.unread"))
        assertTrue(mainActivity.contains("chatStore.isGenerating && chatStore.mode == \"character\""))
        assertTrue(mainActivity.contains("chatSeenStore.markSeen"))
        assertTrue(models.contains("seenAt > 0L && lastUpdated > seenAt"))
    }

    @Test
    fun groupHubScreenStaysMergedIntoUnifiedChatList() {
        // 群聊列表页与对话页功能重复,已删除;群聊入口只保留在对话页
        // (列表混排 + 长按「新对话」创建群聊)。不允许旧的独立群聊 hub 回流。
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()
        val routes = File("src/main/java/io/github/sanitised/st/ui/navigation/STNavGraph.kt").readText()

        assertFalse(File("src/main/java/io/github/sanitised/st/ui/screens/STGroupChatScreen.kt").exists())
        assertFalse(routes.contains("const val GROUP_CHAT ="))
        assertFalse(mainActivity.contains("DrawerNavItem(STRoutes.GROUP_CHAT"))
        assertTrue(mainActivity.contains("onNewGroupChat = { navController.navigate(\"group-chat/new\") }"))
    }

    @Test
    fun homeFabLongPressOpensGroupCreationAndEntersCreatedGroup() {
        val home = File("src/main/java/io/github/sanitised/st/ui/screens/STHomeScreen.kt").readText()
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()

        assertTrue(home.contains("onLongClick = { showNewChatMenu = true }"))
        assertTrue(home.contains("Text(\"创建群聊\")"))
        // 创建成功后直接进入新群聊,而不是停在来路页面
        assertTrue(mainActivity.contains("openGroupChat(created.id, null)"))
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
        assertFalse(openGroupChatBlock.contains("pendingChatTarget = ChatTarget.GroupChat"))
        assertFalse(openGroupChatBlock.contains("navController.navigate(STRoutes.CHAT)"))
        assertTrue(groupDetailRouteBlock.contains("GroupChatScreen("))
    }

    @Test
    fun chatNavigationDoesNotShowPreviousChatMessagesForUnmatchedTarget() {
        // The store is no longer force-reset on every chat entry. Stale messages
        // from a previous chat must be suppressed by gating the message list on
        // targetMatched until the new native snapshot arrives.
        val nativeChat = File("src/main/java/io/github/sanitised/st/chat/NativeChatScreen.kt").readText()

        assertTrue(nativeChat.contains("val targetMatched = targetMatchesStore(target, store)"))
        assertTrue(nativeChat.contains("if (!targetMatched ||"))
        assertTrue(nativeChat.contains("ChatLoadingView("))
    }

    @Test
    fun genericChatTargetDoesNotTreatEmptyStoreAsMatched() {
        val source = File("src/main/java/io/github/sanitised/st/chat/NativeChatScreen.kt").readText()

        assertFalse(source.contains("ChatTarget.Current -> true"))
        assertTrue(source.contains("ChatTarget.Current -> store.chatFile.isNotBlank()"))
    }

    @Test
    fun nativeChatHydratesCharacterChatThroughNativeLoader() {
        val nativeChat = File("src/main/java/io/github/sanitised/st/chat/NativeChatScreen.kt").readText()
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()

        assertTrue(nativeChat.contains("nativeChatLoader?.openCharacter"))
        assertTrue(nativeChat.contains("val nativeReadyForTarget = nativeChatLoadingEnabled && targetMatched"))
        assertTrue(mainActivity.contains("nativeChatLoadingEnabled = true"))
        assertTrue(mainActivity.contains("nativeChatLoader = nativeChatLoader"))
    }

    @Test
    fun nativeChatLoadsQuickRepliesWithChatAndCharacterContext() {
        val nativeChat = File("src/main/java/io/github/sanitised/st/chat/NativeChatScreen.kt").readText()

        assertTrue(nativeChat.contains("chatMetadata = store.chatQuickReplyConfig"))
        assertTrue(nativeChat.contains("characterAvatar = store.avatarUrl"))
    }

    @Test
    fun mainActivityDoesNotCreateHiddenChatRuntimeHost() {
        val mainActivity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()

        assertFalse(mainActivity.contains("ChatWebViewScreen"))
        assertFalse(mainActivity.contains("chatRuntimeActivated"))
        assertFalse(mainActivity.contains("pendingWebViewTarget"))
    }
}
