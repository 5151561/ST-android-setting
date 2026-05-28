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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.ChatSummary
import io.github.sanitised.st.ui.navigation.LocalSTOpenDrawer

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues

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
    }
    var selectedFilter by remember { mutableIntStateOf(0) }

    val filteredChats = remember(chatItems, selectedFilter) {
        when (selectedFilter) {
            1 -> chatItems.filter { it.favorite }
            2 -> chatItems.filter { it.streaming }
            3 -> chatItems.filter { it.id.contains("group") }
            4 -> chatItems.filter { it.id.contains("checkpoint") }
            else -> chatItems
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 104.dp)
            ) {
                item {
                    Column(modifier = Modifier.statusBarsPadding()) {
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
                            items = listOf(
                                "全部 ${chatItems.size}",
                                "收藏 ${chatItems.count { it.favorite }}",
                                "进行中 ${chatItems.count { it.streaming }}",
                                "群聊 ${chatItems.count { it.id.contains("group") }}",
                                "检查点 ${chatItems.count { it.id.contains("checkpoint") }}"
                            ),
                            selectedIndex = selectedFilter,
                            onSelected = { selectedFilter = it },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        )
                    }
                }

                if (status.state != NodeState.RUNNING) {
                    item {
                        PrototypeServiceInlineCard(
                            status = status,
                            stLabel = stLabel,
                            nodeLabel = nodeLabel,
                            onStart = onStart,
                            onStop = onStop,
                            onShowMessage = onShowMessage,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                items(filteredChats, key = { it.id }) { chat ->
                    PrototypeChatListItem(
                        item = chat,
                        onClick = { onOpenChat(chat) }
                    )
                }
            }

            ExtendedFloatingActionButton(
                onClick = {
                    val firstChat = chatItems.firstOrNull()
                    if (firstChat != null) {
                        onOpenChat(firstChat)
                    } else {
                        onShowMessage("没有历史对话，请在“角色”页选择角色开始聊天")
                    }
                },
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
private fun PrototypeChatListItem(
    item: PrototypeChatItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.id.contains("group")) {
                val groupInitials = when (item.id) {
                    "group/1" -> listOf("A", "Z", "K")
                    "group/2" -> listOf("V", "E")
                    else -> listOf(item.initial)
                }
                PrototypeGroupAvatar(
                    initials = groupInitials,
                    size = 52.dp
                )
            } else {
                PrototypeAvatar(
                    label = item.initial,
                    size = 52.dp,
                    gradient = prototypeGradientFor(item.id.hashCode())
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // First row: Name + Star <--> Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.favorite) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "已收藏",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                // Second row: [● 进行中] Preview <--> Unread badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.streaming) {
                            Text(
                                text = "● 进行中 · ",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = item.preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (item.unread > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        PrototypeBadge(
                            label = item.unread.toString(),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
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
