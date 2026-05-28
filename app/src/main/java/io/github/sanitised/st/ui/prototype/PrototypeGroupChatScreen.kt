package io.github.sanitised.st.ui.prototype

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.GroupCreateRequest
import io.github.sanitised.st.api.GroupSummary
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.ui.navigation.LocalSTOpenDrawer
import java.util.Collections
import kotlinx.coroutines.launch

@Composable
fun PrototypeGroupChatScreen(
    status: NodeStatus,
    baseUrl: String,
    onOpenGroupChat: (String, String?) -> Unit,
    onStartService: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val openDrawer = LocalSTOpenDrawer.current
    val scope = rememberCoroutineScope()
    val serverRunning = status.state == NodeState.RUNNING

    var groups by remember { mutableStateOf<List<GroupSummary>>(emptyList()) }
    var characters by remember { mutableStateOf<List<CharacterSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var selectedChars by remember { mutableStateOf<List<CharacterSummary>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var autoSelectNext by remember { mutableStateOf(true) }
    var allowSelfResponses by remember { mutableStateOf(false) }
    var mentionOnly by remember { mutableStateOf(false) }

    fun refreshGroups() {
        if (!serverRunning) {
            groups = emptyList()
            characters = emptyList()
            return
        }
        scope.launch {
            loading = true
            val client = TavernCoreClient(baseUrl)
            runCatching {
                val loadedGroups = client.listGroups()
                val loadedCharacters = client.listCharacters()
                loadedGroups to loadedCharacters
            }.onSuccess { (loadedGroups, loadedCharacters) ->
                groups = loadedGroups
                characters = loadedCharacters
            }.onFailure { error ->
                onShowMessage(error.message ?: "群聊列表加载失败")
            }
            loading = false
        }
    }

    LaunchedEffect(serverRunning, baseUrl) {
        refreshGroups()
    }

    if (showAddDialog) {
        val availableChars = characters.filter { char -> selectedChars.none { it.id == char.id } }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加群聊成员") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (availableChars.isEmpty()) {
                        Text("没有可添加的角色。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        availableChars.forEachIndexed { index, char ->
                            val card = char.toPrototypeCharacterCard(index)
                            Surface(
                                onClick = {
                                    selectedChars = selectedChars + char
                                    showAddDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PrototypeAvatar(card.initial, size = 36.dp, gradient = card.gradient)
                                    Column {
                                        Text(card.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                        Text(card.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }

    if (isCreating) {
        GroupCreateView(
            groupName = groupName,
            selectedChars = selectedChars,
            serverRunning = serverRunning,
            autoSelectNext = autoSelectNext,
            allowSelfResponses = allowSelfResponses,
            mentionOnly = mentionOnly,
            onGroupNameChange = { groupName = it },
            onAutoSelectNextChange = { autoSelectNext = it },
            onAllowSelfResponsesChange = { allowSelfResponses = it },
            onMentionOnlyChange = {
                mentionOnly = it
                if (it) autoSelectNext = false
            },
            onBack = { isCreating = false },
            onAddMember = { showAddDialog = true },
            onRemoveMember = { char -> selectedChars = selectedChars.filterNot { it.id == char.id } },
            onMoveMember = { from, to ->
                val next = selectedChars.toMutableList()
                Collections.swap(next, from, to)
                selectedChars = next
            },
            onCreate = {
                if (groupName.isBlank()) {
                    onShowMessage("请输入群聊名称")
                    return@GroupCreateView
                }
                if (selectedChars.isEmpty()) {
                    onShowMessage("请至少选择一个群聊成员")
                    return@GroupCreateView
                }
                if (!serverRunning) {
                    onShowMessage("请先启动 SillyTavern 服务")
                    return@GroupCreateView
                }
                scope.launch {
                    runCatching {
                        TavernCoreClient(baseUrl).createGroup(
                            GroupCreateRequest(
                                name = groupName.trim(),
                                members = selectedChars.map { it.id },
                                allowSelfResponses = allowSelfResponses,
                                activationStrategy = when {
                                    mentionOnly -> 2
                                    autoSelectNext -> 0
                                    else -> 1
                                }
                            )
                        )
                    }.onSuccess { created ->
                        groups = (groups + created).sortedByDescending { it.lastUpdated }
                        groupName = ""
                        selectedChars = emptyList()
                        autoSelectNext = true
                        allowSelfResponses = false
                        mentionOnly = false
                        isCreating = false
                        onOpenGroupChat(created.id, created.chatId.takeIf { it.isNotBlank() })
                    }.onFailure { error ->
                        onShowMessage(error.message ?: "群聊创建失败")
                    }
                }
            },
            modifier = modifier
        )
    } else {
        GroupListView(
            groups = groups,
            loading = loading,
            serverRunning = serverRunning,
            onStartService = onStartService,
            onOpenDrawer = openDrawer,
            onRefresh = { refreshGroups() },
            onCreate = { isCreating = true },
            onOpenGroupChat = onOpenGroupChat,
            modifier = modifier
        )
    }
}

@Composable
private fun GroupCreateView(
    groupName: String,
    selectedChars: List<CharacterSummary>,
    serverRunning: Boolean,
    autoSelectNext: Boolean,
    allowSelfResponses: Boolean,
    mentionOnly: Boolean,
    onGroupNameChange: (String) -> Unit,
    onAutoSelectNextChange: (Boolean) -> Unit,
    onAllowSelfResponsesChange: (Boolean) -> Unit,
    onMentionOnlyChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onAddMember: () -> Unit,
    onRemoveMember: (CharacterSummary) -> Unit,
    onMoveMember: (Int, Int) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
        ) {
            PrototypeTopHeader(
                title = "新建群聊",
                leading = {
                    PrototypeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                },
                actions = {
                    PrototypeIconButton(
                        icon = Icons.Filled.Check,
                        contentDescription = "确认",
                        onClick = onCreate,
                        tonal = true
                    )
                },
                titleBottomPadding = 0.dp
            )

            OutlinedTextField(
                value = groupName,
                onValueChange = onGroupNameChange,
                placeholder = {
                    Text(
                        "给这场对话起个名字…",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                textStyle = TextStyle(fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                enabled = serverRunning
            )

            Text(
                text = "参与者 · ${selectedChars.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedChars.forEachIndexed { index, char ->
                    val card = char.toPrototypeCharacterCard(index)
                    Box {
                        PrototypeAvatar(label = card.initial, size = 56.dp, gradient = card.gradient)
                        Surface(
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .clickable { onRemoveMember(char) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
                Surface(
                    onClick = onAddMember,
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = serverRunning
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = "添加参与者")
                    }
                }
            }

            PrototypeSectionHeader("响应顺序")
            if (selectedChars.isEmpty()) {
                Text(
                    text = "暂无群聊成员，请添加成员。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            } else {
                selectedChars.forEachIndexed { index, char ->
                    val card = char.toPrototypeCharacterCard(index)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Filled.DragIndicator, contentDescription = "拖动排序", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            PrototypeAvatar(label = card.initial, size = 40.dp, gradient = card.gradient)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(card.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("${index + 1} 顺序", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(
                                enabled = index > 0,
                                onClick = { onMoveMember(index, index - 1) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowUpward,
                                    contentDescription = "上移",
                                    tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                            IconButton(
                                enabled = index < selectedChars.lastIndex,
                                onClick = { onMoveMember(index, index + 1) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDownward,
                                    contentDescription = "下移",
                                    tint = if (index < selectedChars.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }

            PrototypeSectionHeader("群聊行为")
            GroupChatToggleRow(
                label = "自动选择下一位发言者",
                sub = "模型根据上下文选择",
                checked = autoSelectNext,
                onCheckedChange = {
                    onAutoSelectNextChange(it)
                    if (it) onMentionOnlyChange(false)
                }
            )
            GroupChatToggleRow(
                label = "允许角色互相回应",
                sub = "多轮链式发言",
                checked = allowSelfResponses,
                onCheckedChange = onAllowSelfResponsesChange
            )
            GroupChatToggleRow(
                label = "@提及才发言",
                sub = null,
                checked = mentionOnly,
                onCheckedChange = onMentionOnlyChange
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GroupListView(
    groups: List<GroupSummary>,
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
            PrototypeGroupAvatar(
                initials = group.members.take(3).map { it.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?" }.ifEmpty { listOf("群") },
                size = 52.dp
            )
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
                        text = group.lastUpdated.toGroupTimeLabel(),
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

@Composable
private fun GroupChatToggleRow(
    label: String,
    sub: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun Long.toGroupTimeLabel(): String {
    if (this <= 0L) return "未知时间"
    val age = System.currentTimeMillis() - this
    val minute = 60_000L
    val hour = minute * 60
    val day = hour * 24
    return when {
        age < minute -> "刚才"
        age < hour -> "${age / minute} 分钟前"
        age < day -> "今天"
        age < day * 2 -> "昨天"
        else -> "${(age / day).coerceAtLeast(1)} 天前"
    }
}
