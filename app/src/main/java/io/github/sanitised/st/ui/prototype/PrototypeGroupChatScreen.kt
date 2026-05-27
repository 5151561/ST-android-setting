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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.sanitised.st.ui.navigation.LocalSTOpenDrawer
import java.util.Collections

data class PrototypeGroup(
    val id: String,
    val name: String,
    val members: List<PrototypeCharacterCard>,
    val lastMessage: String,
    val time: String,
    val unread: Int = 0
)

@Composable
fun PrototypeGroupChatScreen(
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val fallbackChars = prototypeFallbackCharacters()
    val openDrawer = LocalSTOpenDrawer.current
    
    // Stateful list of groups
    val groupChats = remember {
        mutableStateListOf(
            PrototypeGroup(
                id = "group/1",
                name = "雨夜小聚",
                members = listOf(fallbackChars[0], fallbackChars[4], fallbackChars[3]), // Aria, Zoey, Kael
                lastMessage = "Zoey: 那个小蛋糕真的超级好吃！",
                time = "刚才",
                unread = 2
            ),
            PrototypeGroup(
                id = "group/2",
                name = "银河探索队",
                members = listOf(fallbackChars[1], fallbackChars[2]), // Vex, Eleanor
                lastMessage = "Captain Vex: Wraith号准备跃迁，大家抓稳。",
                time = "今天 10:15",
                unread = 0
            )
        )
    }

    var isCreating by remember { mutableStateOf(false) }
    
    // Form fields for New Group Chat
    var groupName by remember { mutableStateOf("雨夜小聚") }
    var selectedChars by remember { mutableStateOf(fallbackChars.take(3)) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (isCreating) {
        // --- VIEW 2: NEW GROUP CHAT FORM ---
        if (showAddDialog) {
            val availableChars = fallbackChars.filter { char -> selectedChars.none { it.id == char.id } }
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
                            Text("所有角色已全部添加到群聊中。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            availableChars.forEach { char ->
                                Surface(
                                    onClick = {
                                        selectedChars = selectedChars + char
                                        showAddDialog = false
                                        onShowMessage("已添加 ${char.name}")
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
                                        PrototypeAvatar(char.initial, size = 36.dp, gradient = char.gradient)
                                        Column {
                                            Text(char.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                            Text(char.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
            ) {
                // AppBar
                PrototypeTopHeader(
                    title = "新建群聊",
                    leading = {
                        PrototypeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", { isCreating = false })
                    },
                    actions = {
                        PrototypeIconButton(
                            icon = Icons.Filled.Check,
                            contentDescription = "确认",
                            onClick = {
                                if (groupName.isBlank()) {
                                    onShowMessage("请输入群聊名称")
                                    return@PrototypeIconButton
                                }
                                if (selectedChars.isEmpty()) {
                                    onShowMessage("请至少选择一个群聊成员")
                                    return@PrototypeIconButton
                                }
                                // Create new group chat
                                groupChats.add(
                                    PrototypeGroup(
                                        id = "group/${groupChats.size + 1}",
                                        name = groupName,
                                        members = selectedChars,
                                        lastMessage = "系统: 群聊创建成功，开始聊天吧！",
                                        time = "刚才",
                                        unread = 0
                                    )
                                )
                                onShowMessage("群聊 \"$groupName\" 创建成功！")
                                isCreating = false
                            },
                            tonal = true
                        )
                    },
                    titleBottomPadding = 0.dp
                )

                // Group name input
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = {
                        Text(
                            "给这场对话起个名字…",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    textStyle = TextStyle(
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true
                )

                // Participants section
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
                    selectedChars.forEach { char ->
                        Box {
                            PrototypeAvatar(
                                label = char.initial,
                                size = 56.dp,
                                gradient = char.gradient
                            )
                            // Remove button overlay (Fully Clickable!)
                            Surface(
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .clickable {
                                        selectedChars = selectedChars.filter { it.id != char.id }
                                        onShowMessage("已移除 ${char.name}")
                                    },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "移除",
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                    // Add participant button (dashed circle - Clickable!)
                    Surface(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "添加参与者"
                            )
                        }
                    }
                }

                // Response order section
                PrototypeSectionHeader("响应顺序")
                Text(
                    text = "调整顺序以排序。AI 会按自上而下的顺序轮流发言。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
                
                if (selectedChars.isEmpty()) {
                    Text(
                        text = "暂无群聊成员，请添加成员。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                } else {
                    selectedChars.forEachIndexed { index, char ->
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
                                Icon(
                                    imageVector = Icons.Filled.DragIndicator,
                                    contentDescription = "拖动排序",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                PrototypeAvatar(
                                    label = char.initial,
                                    size = 40.dp,
                                    gradient = char.gradient
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = char.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${index + 1} 顺序",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                // Interactive order reordering buttons!
                                IconButton(
                                    enabled = index > 0,
                                    onClick = {
                                        val list = selectedChars.toMutableList()
                                        Collections.swap(list, index, index - 1)
                                        selectedChars = list
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowUpward,
                                        contentDescription = "上移",
                                        tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                }
                                
                                IconButton(
                                    enabled = index < selectedChars.lastIndex,
                                    onClick = {
                                        val list = selectedChars.toMutableList()
                                        Collections.swap(list, index, index + 1)
                                        selectedChars = list
                                    }
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

                // Group behavior section
                PrototypeSectionHeader("群聊行为")
                GroupChatToggleRow("自动选择下一位发言者", "模型根据上下文选择", true)
                GroupChatToggleRow("允许角色互相回应", "多轮链式发言", false)
                GroupChatToggleRow("@提及才发言", null, false)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    } else {
        // --- VIEW 1: GROUP CHAT LIST SCREEN (DEFAULT) ---
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
                                    PrototypeIconButton(
                                        icon = Icons.Filled.Menu,
                                        contentDescription = "打开抽屉",
                                        onClick = openDrawer
                                    )
                                },
                                actions = {
                                    PrototypeIconButton(
                                        icon = Icons.Filled.Add,
                                        contentDescription = "新建群聊",
                                        onClick = { isCreating = true }
                                    )
                                }
                            )
                        }
                    }

                    if (groupChats.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "暂无群聊会话",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "点击右下角按钮创建一个新群聊",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    } else {
                        items(groupChats, key = { it.id }) { group ->
                            Surface(
                                onClick = { onShowMessage("正在进入群聊 \"${group.name}\"...") },
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
                                        initials = group.members.map { it.initial },
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
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = group.time,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = group.lastMessage,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (group.unread > 0) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                PrototypeBadge(
                                                    label = group.unread.toString(),
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Premium FAB to create new group chat
                ExtendedFloatingActionButton(
                    onClick = { isCreating = true },
                    icon = { Icon(Icons.Filled.Chat, contentDescription = null) },
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
}

@Composable
private fun GroupChatToggleRow(
    label: String,
    sub: String?,
    defaultOn: Boolean
) {
    var checked by remember { mutableStateOf(defaultOn) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { checked = !checked }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
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
        Switch(checked = checked, onCheckedChange = { checked = it })
    }
}
