package io.github.sanitised.st.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMembersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // DEMO_PLACEHOLDER: 这一部分定义群成员管理的静态状态。在真实 ST 客户端中，这些状态需要被同步至后端，并重新发送 /api/groups/edit。
    val activeMembers = remember {
        mutableStateListOf(
            DemoGroupMember("aria", "Aria", "咖啡馆的女店员", Color(0xFFFFD7B0), "咖啡馆店员", 1, false, listOf(Color(0xFFFFD7B0), Color(0xFFA55A2A)), "A"),
            DemoGroupMember("eleanor", "Eleanor Wright", "维多利亚时代小说家", Color(0xFFE8D3AC), "维多利亚小说家", 2, false, listOf(Color(0xFFD8C4A3), Color(0xFF6B4E2B)), "E"),
            DemoGroupMember("kael", "Kael", "吟游精灵", Color(0xFFC8E5B7), "吟游精灵", 3, true, listOf(Color(0xFFC8E5B7), Color(0xFF3D6B3A)), "K")
        )
    }

    val candidates = remember {
        mutableStateListOf(
            DemoGroupMember("vex", "Captain Vex", "银河走私船 Wraith 号船长", Color(0xFF8FB6C6), " Wraith 船长", 4, false, listOf(Color(0xFF8FB6C6), Color(0xFF2F5567)), "V"),
            DemoGroupMember("zoey", "Zoey", "高中同桌 / 闺蜜", Color(0xFFF5B0C8), "高中发小", 5, false, listOf(Color(0xFFF5B0C8), Color(0xFFA8366A)), "Z"),
            DemoGroupMember("archive", "档案室", "神秘档案员", Color(0xFFB8B2A4), "SCP 档案员", 6, false, listOf(Color(0xFFB8B2A4), Color(0xFF46443B)), "档")
        )
    }
    var requestedSpeakerName by remember { mutableStateOf<String?>(null) }

    // 重新校准 queue 指数
    fun recalibrateQueue() {
        activeMembers.forEachIndexed { index, member ->
            activeMembers[index] = member.copy(queue = index + 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Text(
                        "群成员",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Done, contentDescription = "完成", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 说明文本
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "点击上下箭头可微调发言顺序；静音的成员不会自动发言，但你仍可单独点名。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
            requestedSpeakerName?.let { speaker ->
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Campaign,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "已点名 $speaker 接话",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 当前群成员 Section
            item {
                SectionHeader(
                    title = "当前成员",
                    trailing = {
                        Text(
                            "${activeMembers.size} 位",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            itemsIndexed(activeMembers) { index, m ->
                MemberManageRow(
                    member = m,
                    order = index + 1,
                    isFirst = index == 0,
                    isLast = index == activeMembers.lastIndex,
                    onMoveUp = {
                        if (index > 0) {
                            val temp = activeMembers[index]
                            activeMembers[index] = activeMembers[index - 1]
                            activeMembers[index - 1] = temp
                            recalibrateQueue()
                        }
                    },
                    onMoveDown = {
                        if (index < activeMembers.lastIndex) {
                            val temp = activeMembers[index]
                            activeMembers[index] = activeMembers[index + 1]
                            activeMembers[index + 1] = temp
                            recalibrateQueue()
                        }
                    },
                    onToggleMute = {
                        activeMembers[index] = m.copy(muted = !m.muted)
                    },
                    onRequestSpeak = {
                        requestedSpeakerName = m.name
                    },
                    onRemove = {
                        activeMembers.removeAt(index)
                        candidates.add(m)
                        recalibrateQueue()
                    }
                )
            }

            // 添加成员 Section
            item {
                SectionHeader(title = "添加成员")
            }

            if (candidates.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "所有可选角色已加入当前群聊",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(candidates) { index, c ->
                    ListItem(
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(c.avatarGrad)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(c.initial, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        headlineContent = {
                            Text(
                                c.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        supportingContent = {
                            Text(
                                c.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Button(
                                onClick = {
                                    candidates.removeAt(index)
                                    activeMembers.add(c)
                                    recalibrateQueue()
                                },
                                shape = RoundedCornerShape(17.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("加入", fontSize = 13.sp)
                            }
                        },
                        modifier = Modifier.clickable { }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// MemberManageRow — 单个成员管理项 (带手势/上下微调)
// ─────────────────────────────────────────────────────────────
@Composable
fun MemberManageRow(
    member: DemoGroupMember,
    order: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleMute: () -> Unit,
    onRequestSpeak: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 微调排序手柄 (Prototype 级别: 采用上下点选，兼具 drag_indicator 标志)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onMoveUp,
                enabled = !isFirst,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropUp,
                    contentDescription = "向上移动",
                    tint = if (isFirst) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Filled.DragIndicator,
                contentDescription = "拖拽微调发言顺序",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            IconButton(
                onClick = onMoveDown,
                enabled = !isLast,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "向下移动",
                    tint = if (isLast) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        Spacer(modifier = Modifier.width(6.dp))
        
        // 带有顺序徽章的头像
        Box(modifier = Modifier.size(44.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .alpha(if (member.muted) 0.5f else 1f)
                    .background(Brush.linearGradient(member.avatarGrad))
                    .border(2.dp, if (!member.muted) member.accent else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(member.initial, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            
            // 顺序小徽章 (Top Left)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (member.muted) MaterialTheme.colorScheme.surfaceVariant else member.accent)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = order.toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (member.muted) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF2A1A08)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 名字与角色
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (member.muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                if (member.muted) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(
                            text = "静音",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = member.role,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // 快捷发言点名
        IconButton(onClick = onRequestSpeak) {
            Icon(
                imageVector = Icons.Filled.Campaign,
                contentDescription = "让 TA 现在发言",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 快捷静音切换
        IconButton(onClick = onToggleMute) {
            Icon(
                imageVector = if (member.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = "切换静音",
                tint = if (member.muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        }
        
        // 更多操作
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多成员操作", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("从该群聊中移除", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SectionHeader — 区域标题
// ─────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(
    title: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        if (trailing != null) {
            trailing()
        }
    }
}
