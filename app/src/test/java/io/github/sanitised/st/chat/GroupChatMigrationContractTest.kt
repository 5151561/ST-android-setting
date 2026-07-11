package io.github.sanitised.st.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatMigrationContractTest {
    // 群聊界面已按职责拆分为多文件(GroupChatScreen + Components/Messages/Sheets/Data),
    // 契约标记散落其中,守卫读取拆分后的全部群聊源文件。
    private fun groupChatSources(): String = listOf(
        "GroupChatScreen.kt",
        "GroupChatComponents.kt",
        "GroupChatMessages.kt",
        "GroupChatSheets.kt",
        "GroupChatData.kt"
    ).joinToString("\n") { name ->
        File("src/main/java/io/github/sanitised/st/chat/$name").readText()
    }

    @Test
    fun migrationPlanUsesRealSillyTavernGroupStrategyValues() {
        val plan = File("../docs/group_chat_migration_plan.md").readText()

        assertTrue(plan.contains("0 = 自然顺序 (Natural order)"))
        assertTrue(plan.contains("1 = 列表顺序 (List order)"))
        assertTrue(plan.contains("2 = 手动点名 (Manual)"))
        assertTrue(plan.contains("3 = 池化顺序 (Pooled order)"))
        assertFalse(plan.contains("@提及才发言"))
    }

    @Test
    fun migrationDocsDoNotTellDevelopersToCallMissingGroupGenerateRestEndpoint() {
        val plan = File("../docs/group_chat_migration_plan.md").readText()

        val walkthrough = File(
            System.getProperty("user.home"),
            ".gemini/antigravity/brain/d786845c-3b3b-4d40-8db3-3c7958603af6/walkthrough.md"
        )
        if (walkthrough.isFile) {
            assertFalse(walkthrough.readText().contains("替换 `SpeakerSheet` 发言动作为 `/api/groups/generate` REST 请求"))
        }
        assertTrue(plan.contains("并没有独立的 `/api/groups/generate` REST 接口"))
    }

    @Test
    fun groupMemberSpeakerActionIsNotAnEmptyPlaceholderButton() {
        val sources = listOf(
            "GroupChatScreen.kt",
            "GroupMembersScreen.kt",
            "GroupSettingsScreen.kt",
            "NewGroupScreen.kt"
        ).associateWith { name ->
            File("src/main/java/io/github/sanitised/st/chat/$name").readText()
        }

        assertFalse(
            "Chat screens should not expose controls with empty click handlers.",
            sources.values.any { it.contains("onClick = {}") }
        )
        assertTrue(sources.getValue("GroupMembersScreen.kt").contains("onRequestSpeak: () -> Unit"))
    }

    @Test
    fun nextSpeakerBarReflectsTheSelectedStrategy() {
        val source = groupChatSources()

        assertFalse(source.contains("if (strategy == \"manual\") \"由你点名\" else \"自动 · 自然顺序\""))
        assertTrue(source.contains("getStrategyActionLabel(strategy)"))
    }

    @Test
    fun activationStrategyStringsMapToRealSillyTavernEnumValues() {
        // group_activation_strategy in SillyTavern/public/scripts/group-chats.js.
        assertTrue(activationStrategyId("natural") == 0)
        assertTrue(activationStrategyId("list") == 1)
        assertTrue(activationStrategyId("manual") == 2)
        assertTrue(activationStrategyId("pooled") == 3)
        // Unknown values fall back to NATURAL rather than throwing.
        assertTrue(activationStrategyId("???") == 0)
    }

    @Test
    fun groupChatDetailLoadsRealGroupDataByIdInsteadOfStaticDemo() {
        val screen = File("src/main/java/io/github/sanitised/st/chat/GroupChatScreen.kt").readText()

        // The screen must take the route's groupId/chatId and load real data.
        assertTrue(screen.contains("groupId: String"))
        assertTrue(screen.contains("chatId: String?"))
        assertTrue(screen.contains("getGroupChatJsonl("))
        assertTrue(screen.contains("listGroups().find { it.id == groupId }"))
        // The previous build hardcoded the demo group as the active conversation.
        assertFalse(
            "Active group must not be hardcoded to the demo group.",
            screen.contains("id = \"rainynight\"")
        )

        // The detail route must pass the nav args through to the screen.
        val activity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()
        val detailRoute = activity.substringAfter("route = STRoutes.GROUP_CHAT_DETAIL")
            .substringBefore("composable(")
        assertTrue(detailRoute.contains("groupId = gid"))
        assertTrue(detailRoute.contains("chatId = cid.takeIf"))
    }

    @Test
    fun groupSettingsAndMembersScreensPersistRealDataViaEditGroup() {
        val settings = File("src/main/java/io/github/sanitised/st/chat/GroupSettingsScreen.kt").readText()
        val members = File("src/main/java/io/github/sanitised/st/chat/GroupMembersScreen.kt").readText()
        val api = File("src/main/java/io/github/sanitised/st/api/TavernCoreApi.kt").readText()

        // Both screens take the group id + base url and persist via editGroup.
        assertTrue(settings.contains("groupId: String"))
        assertTrue(settings.contains("editGroup("))
        assertTrue(settings.contains("deleteGroup("))
        assertTrue(members.contains("groupId: String"))
        assertTrue(members.contains("editGroup("))
        assertTrue(members.contains("disabledMembers ="))

        // No hardcoded demo state remains as the source of truth.
        assertFalse(settings.contains("mutableStateOf(\"雨夜小聚\")"))
        assertFalse(members.contains("DemoGroupMember(\"aria\""))

        // The REST client exposes the edit/delete endpoints.
        assertTrue(api.contains("suspend fun editGroup(group: GroupSummary)"))
        assertTrue(api.contains("suspend fun deleteGroup(groupId: String)"))
        assertTrue(api.contains("api/groups/edit"))
        assertTrue(api.contains("api/groups/delete"))
    }

    @Test
    fun conversationSwitcherListsRealGroupChatsNotDemoArchives() {
        val screen = groupChatSources()

        // The switcher is fed real chats and can switch / start a new conversation.
        assertTrue(screen.contains("conversations: List<DemoConversation>"))
        assertTrue(screen.contains("groupState.value.chats"))
        // The old internal demo archive list is gone.
        assertFalse(screen.contains("周末桌游夜"))
        assertFalse(screen.contains("深夜书店打烊后"))
    }

    @Test
    fun groupSwipeIsPersistedToTheGroupJsonl() {
        val screen = File("src/main/java/io/github/sanitised/st/chat/GroupChatScreen.kt").readText()
        assertTrue(screen.contains("fun applySwipe("))
        assertTrue(screen.contains("\"swipe_id\"] = newSwipeId"))
        assertTrue(screen.contains("saveGroupChatJsonl("))
    }

    @Test
    fun groupRepliesAreGeneratedNativelyNotFakedOrStubbed() {
        val screen = File("src/main/java/io/github/sanitised/st/chat/GroupChatScreen.kt").readText()

        // AI replies must go through the native generator, persisted to the group JSONL.
        assertTrue(screen.contains("NativeGroupGenerator"))
        assertTrue(screen.contains("generator.generate("))
        assertTrue(screen.contains("saveGroupChatJsonl("))
        // No more "coming soon" stub and no hardcoded demo reply text.
        assertFalse(screen.contains("群聊原生生成正在接入中"))
        assertFalse(screen.contains("那今天的可可多加些鲜奶油"))
    }

    @Test
    fun newGroupScreenIsWiredToCreateGroupApiInsteadOfBeingANoOp() {
        // Regression for the full-device report: the create button used to be
        // onCreate = { _, _, _ -> navController.popBackStack() }, so no group was
        // ever persisted. The route must load real characters and call createGroup.
        val activity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()

        assertFalse(
            "New group create handler must not discard its arguments.",
            activity.contains("onCreate = { _, _, _ -> navController.popBackStack() }")
        )
        val newGroupRoute = activity.substringAfter("composable(\"group-chat/new\")")
            .substringBefore("composable(")
        assertTrue(newGroupRoute.contains("listCharacters()"))
        assertTrue(newGroupRoute.contains("createGroup("))
        assertTrue(newGroupRoute.contains("GroupCreateRequest("))

        // The screen builds groups from real characters, not demo placeholders.
        val screen = File("src/main/java/io/github/sanitised/st/chat/NewGroupScreen.kt").readText()
        assertTrue(screen.contains("characters: List<CharacterSummary>"))
        assertFalse(screen.contains("DEMO_PLACEHOLDER"))
        assertFalse(screen.contains("DemoGroupMember("))
    }
}
