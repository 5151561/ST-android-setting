package io.github.sanitised.st.ui.screens

import androidx.activity.compose.setContent
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.sanitised.st.HiltTestActivity
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.ui.theme.STAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class STChatListScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val sampleChats = listOf(
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
            isCheckpoint = true,
        ),
    )

    @Test
    fun rendersChatItemsAndFiltersByChipSemantics() {
        val runningStatus = NodeStatus(state = NodeState.RUNNING, port = 8000, message = "Running")

        composeRule.setContent {
            STAppTheme {
                STChatListScreen(
                    status = runningStatus,
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

        composeRule.onNodeWithText("对话").assertIsDisplayed()
        composeRule.onNodeWithText("艾莉丝").assertIsDisplayed()
        composeRule.onNodeWithText("圆桌骑士团").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("已置顶").assertIsDisplayed()

        // 点击「群聊 1」筛选 chip，仅展示群聊项
        composeRule.onNodeWithText("群聊 1").performClick()
        composeRule.onNodeWithText("圆桌骑士团").assertIsDisplayed()
        composeRule.onNodeWithText("艾莉丝").assertDoesNotExist()

        // 点击「收藏 1」筛选 chip，仅展示收藏项
        composeRule.onNodeWithText("收藏 1").performClick()
        composeRule.onNodeWithText("艾莉丝").assertIsDisplayed()
        composeRule.onNodeWithText("圆桌骑士团").assertDoesNotExist()
    }

    @Test
    fun stoppedServiceShowsInlineStartCardAndEmptyListRendersCleanly() {
        var startClicked = false
        val stoppedStatus = NodeStatus(state = NodeState.STOPPED, port = 8000, message = "Stopped")

        composeRule.setContent {
            STAppTheme {
                STChatListScreen(
                    status = stoppedStatus,
                    chatItems = emptyList(),
                    stLabel = "ST 1.12",
                    nodeLabel = "Node 22",
                    onStart = { startClicked = true },
                    onStop = {},
                    onOpenChat = {},
                    onOpenGroupChat = {},
                    onNewChat = {},
                    onNewGroupChat = {},
                    onShowMessage = {},
                )
            }
        }

        composeRule.onNodeWithText("SillyTavern 尚未启动").assertIsDisplayed()
        composeRule.onNodeWithText("全部 0").assertIsDisplayed()
        composeRule.onNodeWithText("启动服务").performClick()
        assertTrue(startClicked)
    }

    @Test
    fun clickingChatGroupAndNewChatInvokesCallbacks() {
        var openedDirect: STChatItem? = null
        var openedGroup: STChatItem? = null
        var newChatTriggered = false
        val runningStatus = NodeStatus(state = NodeState.RUNNING, port = 8000, message = "Running")

        composeRule.setContent {
            STAppTheme {
                STChatListScreen(
                    status = runningStatus,
                    chatItems = sampleChats,
                    stLabel = "ST 1.12",
                    nodeLabel = "Node 22",
                    onStart = {},
                    onStop = {},
                    onOpenChat = { openedDirect = it },
                    onOpenGroupChat = { openedGroup = it },
                    onNewChat = { newChatTriggered = true },
                    onNewGroupChat = {},
                    onShowMessage = {},
                )
            }
        }

        composeRule.onNodeWithText("艾莉丝").performClick()
        assertEquals("chat-alice", openedDirect?.id)

        composeRule.onNodeWithText("圆桌骑士团").performClick()
        assertEquals("group-knights", openedGroup?.id)

        composeRule.onNodeWithText("新对话").performClick()
        assertTrue(newChatTriggered)
    }

    @Test
    fun activityRecreationRestoresSelectedFilterChip() {
        val runningStatus = NodeStatus(state = NodeState.RUNNING, port = 8000, message = "Running")
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            STAppTheme {
                STChatListScreen(
                    status = runningStatus,
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

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent(content = content)
        }

        composeRule.onNodeWithText("群聊 1").performClick()
        composeRule.onNodeWithText("艾莉丝").assertDoesNotExist()
        composeRule.onNodeWithText("圆桌骑士团").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent(content = content)
        }

        // 重建后 rememberSaveable 保持选中「群聊 1」筛选
        composeRule.onNodeWithText("艾莉丝").assertDoesNotExist()
        composeRule.onNodeWithText("圆桌骑士团").assertIsDisplayed()
    }

    @Test
    fun rendersUnderExpandedWidthAndLargeFontScaleOverride() {
        val runningStatus = NodeStatus(state = NodeState.RUNNING, port = 8000, message = "Running")

        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(900.dp, 800.dp)) then
                    DeviceConfigurationOverride.FontScale(1.5f)
            ) {
                STAppTheme {
                    STChatListScreen(
                        status = runningStatus,
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
        }

        composeRule.onNodeWithText("对话").assertIsDisplayed()
        composeRule.onNodeWithText("艾莉丝").assertIsDisplayed()
        composeRule.onNodeWithText("圆桌骑士团").assertIsDisplayed()
        composeRule.onNodeWithText("新对话").assertIsDisplayed()
    }
}
