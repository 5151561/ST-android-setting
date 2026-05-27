package io.github.sanitised.st.ui.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.ChatSummary
import io.github.sanitised.st.ui.navigation.LocalSTOpenDrawer

@Composable
fun PrototypeChatListScreen(
    status: NodeStatus,
    recentChats: List<ChatSummary>,
    stLabel: String,
    nodeLabel: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenChat: (PrototypeChatItem) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val openDrawer = LocalSTOpenDrawer.current
    val chatItems = remember(recentChats) {
        recentChats.mapIndexed { index, chat -> chat.toPrototypeChatItem(index) }
            .ifEmpty { prototypeFallbackChats() }
    }
    var selectedFilter by remember { mutableIntStateOf(0) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 104.dp)
            ) {
                PrototypeTopHeader(
                    title = "对话",
                    leading = {
                        PrototypeIconButton(
                            icon = Icons.Filled.Menu,
                            contentDescription = "打开抽屉",
                            onClick = openDrawer
                        )
                    },
                    actions = {
                        PrototypeIconButton(
                            icon = Icons.Filled.Search,
                            contentDescription = "搜索会话",
                            onClick = { onShowMessage("搜索会话稍后接入") }
                        )
                        PrototypeIconButton(
                            icon = Icons.Filled.FilterList,
                            contentDescription = "过滤会话",
                            onClick = { onShowMessage("过滤会话稍后接入") }
                        )
                    }
                )

                PrototypeChipRow(
                    items = listOf("全部 ${chatItems.size}", "收藏 ${chatItems.count { it.favorite }}", "进行中", "群聊", "检查点"),
                    selectedIndex = selectedFilter,
                    onSelected = { selectedFilter = it },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                if (status.state != NodeState.RUNNING) {
                    PrototypeServiceInlineCard(
                        status = status,
                        stLabel = stLabel,
                        nodeLabel = nodeLabel,
                        onStart = onStart,
                        onStop = onStop,
                        onShowMessage = onShowMessage,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
                    )
                }

                Column(modifier = Modifier.padding(top = 8.dp)) {
                    chatItems.forEach { chat ->
                        PrototypeChatRow(
                            item = chat,
                            onClick = { onOpenChat(chat) }
                        )
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = { onOpenChat(chatItems.first()) },
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("新对话") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun PrototypeChatRow(
    item: PrototypeChatItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
            .height(80.dp)
            .padding(horizontal = 16.dp)
            .then(androidx.compose.ui.Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PrototypeListItem(
            headline = item.title,
            supporting = buildString {
                if (item.streaming) append("● 进行中 · ")
                append(item.preview)
            },
            leading = {
                PrototypeAvatar(
                    label = item.initial,
                    size = 52.dp,
                    gradient = prototypeGradientFor(item.id.hashCode())
                )
            },
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = item.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (item.favorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    } else if (item.unread > 0) {
                        PrototypeBadge(
                            label = item.unread.toString(),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            onClick = onClick
        )
    }
}

@Composable
private fun PrototypeServiceInlineCard(
    status: NodeStatus,
    stLabel: String,
    nodeLabel: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val runningLike = status.state == NodeState.STARTING || status.state == NodeState.RUNNING
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrototypeStatusDot(
                    color = when (status.state) {
                        NodeState.RUNNING -> MaterialTheme.colorScheme.tertiary
                        NodeState.ERROR -> MaterialTheme.colorScheme.error
                        NodeState.STARTING, NodeState.STOPPING -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when (status.state) {
                        NodeState.RUNNING -> "本地服务运行中"
                        NodeState.STARTING -> "正在唤醒 SillyTavern…"
                        NodeState.ERROR -> "服务启动异常"
                        else -> "SillyTavern 尚未启动"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "$stLabel · $nodeLabel · :${status.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (runningLike) {
                    OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("停止")
                    }
                } else {
                    Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("启动服务")
                    }
                }
                OutlinedButton(onClick = { onShowMessage("服务就绪后会进入原生聊天") }) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                }
            }
        }
    }
}
