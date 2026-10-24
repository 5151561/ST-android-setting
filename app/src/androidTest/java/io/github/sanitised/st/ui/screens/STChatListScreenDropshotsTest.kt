package io.github.sanitised.st.ui.screens

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dropbox.dropshots.Dropshots
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.sanitised.st.HiltTestActivity
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.ui.theme.STAppTheme
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class STChatListScreenDropshotsTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @get:Rule(order = 2)
    val dropshots = Dropshots()

    @Test
    fun captureChatListScreen() {
        val sampleChats = listOf(
            STChatItem(
                id = "chat-alice",
                characterId = "alice.png",
                chatFile = "alice-2026.jsonl",
                title = "艾莉丝",
                preview = "欢迎来到酒馆。",
                time = "12:30",
                initial = "艾",
                favorite = true,
                kind = STChatKind.DIRECT,
                inProgress = true,
            ),
            STChatItem(
                id = "group-knights",
                characterId = "group-1",
                chatFile = "group-1.jsonl",
                title = "圆桌骑士团",
                preview = "全员集合！",
                time = "昨天",
                initial = "圆",
                favorite = false,
                kind = STChatKind.GROUP,
                memberCount = 4,
            ),
        )

        composeRule.setContent {
            STAppTheme {
                STChatListScreen(
                    status = NodeStatus(state = NodeState.RUNNING, port = 8000, message = "Running"),
                    chatItems = sampleChats,
                    stLabel = "ST 1.12",
                    nodeLabel = "Node 22",
                    onStart = {},
                    onStop = {},
                    onOpenChat = {},
                    onOpenGroupChat = {},
                    onNewChat = {},
                    onNewGroupChat = {},
                    onShowMessage = {},
                )
            }
        }

        composeRule.waitForIdle()
        dropshots.assertSnapshot(
            activity = composeRule.activity,
            name = "STChatListScreen_Running",
        )
    }
}
