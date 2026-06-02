package io.github.sanitised.st.ui.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.GroupSummary
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.ui.navigation.LocalSTOpenDrawer
import kotlinx.coroutines.launch

@Composable
fun PrototypeGroupChatScreen(
    status: NodeStatus,
    baseUrl: String,
    onOpenGroupChat: (String, String?) -> Unit,
    onStartService: () -> Unit,
    onShowMessage: (String) -> Unit,
    onNavigateToNewGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openDrawer = LocalSTOpenDrawer.current
    val scope = rememberCoroutineScope()
    val serverRunning = status.state == NodeState.RUNNING

    var groups by remember { mutableStateOf<List<GroupSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    fun refreshGroups() {
        if (!serverRunning) {
            groups = emptyList()
            return
        }
        scope.launch {
            loading = true
            val client = TavernCoreClient(baseUrl)
            runCatching {
                client.listGroups()
            }.onSuccess { loadedGroups ->
                groups = loadedGroups
            }.onFailure { error ->
                onShowMessage(error.message ?: "群聊列表加载失败")
            }
            loading = false
        }
    }

    LaunchedEffect(serverRunning, baseUrl) {
        refreshGroups()
    }

    GroupListView(
        groups = groups,
        baseUrl = baseUrl,
        loading = loading,
        serverRunning = serverRunning,
        onStartService = onStartService,
        onOpenDrawer = openDrawer,
        onRefresh = { refreshGroups() },
        onCreate = onNavigateToNewGroup,
        onOpenGroupChat = onOpenGroupChat,
        modifier = modifier
    )
}

@Composable
private fun GroupListView(
    groups: List<GroupSummary>,
    baseUrl: String,
    loading: Boolean,
    serverRunning: Boolean,
    onStartService: () -> Unit,
    onOpenDrawer: () -> Unit,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onOpenGroupChat: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 104.dp)
            ) {
                item {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        PrototypeTopHeader(
                            title = "群聊",
                            leading = {
                                PrototypeIconButton(Icons.Filled.Menu, "打开抽屉", onOpenDrawer)
                            },
                            actions = {
                                PrototypeIconButton(Icons.Filled.Refresh, "刷新群聊", onRefresh)
                                PrototypeIconButton(Icons.Filled.Add, "新建群聊", onCreate)
                            }
                        )
                    }
                }

                when {
                    !serverRunning -> item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("SillyTavern 尚未启动", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = onStartService) { Text("启动服务") }
                        }
                    }
                    loading -> item {
                        Text(
                            text = "正在读取群聊…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                    groups.isEmpty() -> item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("暂无群聊会话", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "点击右下角按钮创建一个新群聊",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    else -> items(groups, key = { it.id }) { group ->
                        GroupListItem(
                            group = group,
                            baseUrl = baseUrl,
                            onClick = { onOpenGroupChat(group.id, group.chatId.takeIf { it.isNotBlank() }) }
                        )
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                text = { Text("新建群聊") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun GroupListItem(
    group: GroupSummary,
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
            if (group.avatarUrl.isNotBlank()) {
                PrototypeAvatar(
                    label = group.name.ifBlank { "群" },
                    imageUrl = group.avatarUrl,
                    baseUrl = baseUrl,
                    size = 52.dp,
                    gradient = prototypeGradientFor(group.id.hashCode())
                )
            } else {
                PrototypeGroupAvatar(
                    initials = group.members.take(3).map { it.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?" }.ifEmpty { listOf("群") },
                    imageUrls = group.members.take(3),
                    baseUrl = baseUrl,
                    size = 52.dp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = prototypeRelativeTimeLabel(group.lastUpdated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        "成员 ${group.members.size} 人",
                        group.chatId.takeIf { it.isNotBlank() }?.let { "当前聊天 $it" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
