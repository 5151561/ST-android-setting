package io.github.sanitised.st.ui.screens

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
fun STChatListScreen(
    status: NodeStatus,
    recentChats: List<ChatSummary>,
    stLabel: String,
    nodeLabel: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenChat: (STChatItem) -> Unit,
    onNewChat: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val openDrawer = LocalSTOpenDrawer.current
    val chatItems = remember(recentChats) {
        recentChats.map { chat -> chat.toSTChatItem() }
    }
    val baseUrl = remember(status.port) { "http://127.0.0.1:${status.port}" }
    var selectedFilter by remember { mutableIntStateOf(0) }

    val filteredChats = remember(chatItems, selectedFilter) {
        when (selectedFilter) {
            1 -> chatItems.filter { it.favorite }
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
                        STTopHeader(
                            title = "对话",
                            leading = {
                                STIconButton(
                                    icon = Icons.Filled.Menu,
                                    contentDescription = "打开抽屉",
                                    onClick = openDrawer
                                )
                            },
                            actions = {
                                STIconButton(
                                    icon = Icons.Filled.Search,
                                    contentDescription = "搜索会话",
                                    onClick = { onShowMessage("搜索会话功能开发中") }
                                )
                                STIconButton(
                                    icon = Icons.Filled.FilterList,
                                    contentDescription = "过滤会话",
                                    onClick = { onShowMessage("过滤会话功能开发中") }
                                )
                            }
                        )

                        STChipRow(
                            items = listOf(
                                "全部 ${chatItems.size}",
                                "置顶 ${chatItems.count { it.favorite }}"
                            ),
                            selectedIndex = selectedFilter,
                            onSelected = { selectedFilter = it },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        )
                    }
                }

                if (status.state != NodeState.RUNNING) {
                    item {
                        STServiceInlineCard(
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
                    STChatListItem(
                        item = chat,
                        baseUrl = baseUrl,
                        onClick = { onOpenChat(chat) }
                    )
                }
            }

            ExtendedFloatingActionButton(
                onClick = onNewChat,
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
private fun STChatListItem(
    item: STChatItem,
    baseUrl: String,
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
            if (item.kind == STChatKind.GROUP) {
                STGroupAvatar(
                    initials = listOf(item.initial),
                    imageUrls = listOf(item.avatarUrl),
                    baseUrl = baseUrl,
                    size = 52.dp
                )
            } else {
                STAvatar(
                    label = item.initial,
                    imageUrl = item.avatarUrl,
                    baseUrl = baseUrl,
                    size = 52.dp,
                    gradient = stGradientFor(item.id.hashCode())
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
                                contentDescription = "已置顶",
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
                Text(
                    text = item.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun STServiceInlineCard(
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
                STStatusDot(
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
