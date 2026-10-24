package io.github.sanitised.st.ui.screens

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.ui.theme.STAppTheme

/**
 * 覆盖 9 种屏幕尺寸矩阵（400/610/900dp 宽 × 400/500/1000dp 高）+ 400×500 暗色主题 + 400×500 大字号（fontScale = 1.5f）。
 * 注意：当前 AGP 9.2.1 < 9.5.0-alpha03，使用独立插件 `com.android.compose.screenshot`（已标记废弃，后续升级 AGP ≥ 9.5.0-alpha03 时迁移至 testSuites）。
 */
@Preview(name = "W400_H400", widthDp = 400, heightDp = 400, showBackground = true)
@Preview(name = "W400_H500", widthDp = 400, heightDp = 500, showBackground = true)
@Preview(name = "W400_H1000", widthDp = 400, heightDp = 1000, showBackground = true)
@Preview(name = "W610_H400", widthDp = 610, heightDp = 400, showBackground = true)
@Preview(name = "W610_H500", widthDp = 610, heightDp = 500, showBackground = true)
@Preview(name = "W610_H1000", widthDp = 610, heightDp = 1000, showBackground = true)
@Preview(name = "W900_H400", widthDp = 900, heightDp = 400, showBackground = true)
@Preview(name = "W900_H500", widthDp = 900, heightDp = 500, showBackground = true)
@Preview(name = "W900_H1000", widthDp = 900, heightDp = 1000, showBackground = true)
@Preview(
    name = "W400_H500_FontScale1_5",
    widthDp = 400,
    heightDp = 500,
    fontScale = 1.5f,
    showBackground = true,
)
@Preview(
    name = "W400_H500_Dark",
    widthDp = 400,
    heightDp = 500,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
annotation class STChatListScreenMatrixPreview

private val previewChats = listOf(
    STChatItem(
        id = "chat-alice",
        characterId = "alice.png",
        chatFile = "alice-2026.jsonl",
        title = "艾莉丝",
        preview = "欢迎来到酒馆，今天想聊点什么？",
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
        preview = "全员集合，准备出发！",
        time = "昨天",
        initial = "圆",
        favorite = false,
        kind = STChatKind.GROUP,
        memberCount = 4,
        isCheckpoint = true,
        unread = true,
    ),
)

class STChatListScreenScreenshotTest {

    @PreviewTest
    @STChatListScreenMatrixPreview
    @Composable
    fun STChatListScreenPreview() {
        STAppTheme {
            STChatListScreen(
                status = NodeStatus(state = NodeState.RUNNING, port = 8000, message = "Running"),
                chatItems = previewChats,
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
