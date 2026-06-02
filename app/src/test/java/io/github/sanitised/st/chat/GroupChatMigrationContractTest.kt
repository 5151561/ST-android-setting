package io.github.sanitised.st.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatMigrationContractTest {
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
        val source = File("src/main/java/io/github/sanitised/st/chat/GroupChatScreen.kt").readText()

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
