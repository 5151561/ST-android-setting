package io.github.sanitised.st.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(
    onClose: () -> Unit,
    onCreate: (String, List<String>, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // DEMO_PLACEHOLDER: 这一部分定义新建群向导的静态模拟状态。创建成功后，将参数传出，用于调用 POST /api/groups/create。
    var groupName by remember { mutableStateOf("雨夜小聚") }
    var selectedStrategy by remember { mutableStateOf("natural") }
    
    val selectedMembers = remember {
        mutableStateListOf(
            DemoGroupMember("aria", "Aria", "咖啡馆的女店员", Color(0xFFFFD7B0), "咖啡馆店员", 1, false, listOf(Color(0xFFFFD7B0), Color(0xFFA55A2A)), "A"),
            DemoGroupMember("eleanor", "Eleanor Wright", "维多利亚时代小说家", Color(0xFFE8D3AC), "维多利亚小说家", 2, false, listOf(Color(0xFFD8C4A3), Color(0xFF6B4E2B)), "E"),
            DemoGroupMember("kael", "Kael", "吟游精灵", Color(0xFFC8E5B7), "吟游精灵", 3, true, listOf(Color(0xFFC8E5B7), Color(0xFF3D6B3A)), "K")
        )
    }

    val availableCandidates = remember {
        mutableStateListOf(
            DemoGroupMember("vex", "Captain Vex", "银河走私船 Wraith 号船长", Color(0xFF8FB6C6), " Wraith 船长", 4, false, listOf(Color(0xFF8FB6C6), Color(0xFF2F5567)), "V"),
            DemoGroupMember("zoey", "Zoey", "高中同桌 / 闺蜜", Color(0xFFF5B0C8), "高中发小", 5, false, listOf(Color(0xFFF5B0C8), Color(0xFFA8366A)), "Z"),
            DemoGroupMember("archive", "档案室", "神秘档案员", Color(0xFFB8B2A4), "SCP 档案员", 6, false, listOf(Color(0xFFB8B2A4), Color(0xFF46443B)), "档")
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
                title = {
                    Text(
                        "新建群聊",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (groupName.isNotBlank() && selectedMembers.isNotEmpty()) {
                                onCreate(groupName, selectedMembers.map { it.id }, selectedStrategy)
                            }
                        }
                    ) {
                        Text(
                            "创建",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
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
            // 1. 群聊名称与大头像
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    GroupAvatar(
                        ids = selectedMembers.map { it.id },
                        members = selectedMembers,
                        size = 64.dp
                    )
                    
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        placeholder = { Text("给这场对话起个名字…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true
                    )
                }
            }

            // 2. 已选成员顺序调整 (Draggable / Sortable list)
            item {
                SectionHeader(
                    title = "参与者 · 发言顺序",
                    trailing = {
                        Text(
                            "${selectedMembers.size} 已选",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            itemsIndexed(selectedMembers) { index, m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // drag drop icons for micro-adjustment
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 4.dp)) {
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val temp = selectedMembers[index]
                                    selectedMembers[index] = selectedMembers[index - 1]
                                    selectedMembers[index - 1] = temp
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Filled.ArrowDropUp, contentDescription = "上移")
                        }
                        Icon(
                            imageVector = Icons.Filled.DragIndicator,
                            contentDescription = "排序手柄",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                        IconButton(
                            onClick = {
                                if (index < selectedMembers.lastIndex) {
                                    val temp = selectedMembers[index]
                                    selectedMembers[index] = selectedMembers[index + 1]
                                    selectedMembers[index + 1] = temp
                                }
                            },
                            enabled = index < selectedMembers.lastIndex,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "下移")
                        }
                    }
                    
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(m.avatarGrad)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(m.initial, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = m.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "第 ${index + 1} 位发言",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            selectedMembers.removeAt(index)
                            availableCandidates.add(m)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "移除角色",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 3. 待选角色库 (Horizontal Carousel)
            item {
                SectionHeader(title = "添加更多角色")
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    availableCandidates.forEachIndexed { index, c ->
                        Column(
                            modifier = Modifier
                                .width(64.dp)
                                .clickable {
                                    availableCandidates.removeAt(index)
                                    selectedMembers.add(c)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(56.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(c.avatarGrad)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(c.initial, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                                
                                // Green Plus badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 2.dp, y = 2.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "加入",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Text(
                                text = c.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 4. 快速策略选择 (Quick strategy selector)
            item {
                SectionHeader(title = "回复策略")
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val strategies = listOf(
                        Triple("manual", "手动", Icons.Filled.TouchApp),
                        Triple("natural", "自然顺序", Icons.Filled.Forum),
                        Triple("list", "列表顺序", Icons.Filled.FormatListNumbered),
                        Triple("pooled", "池化顺序", Icons.Filled.Shuffle)
                    )
                    strategies.forEach { (id, label, icon) ->
                        val isSel = id == selectedStrategy
                        InputChip(
                            selected = isSel,
                            onClick = { selectedStrategy = id },
                            label = { Text(label, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = InputChipDefaults.inputChipBorder(
                                enabled = true,
                                selected = isSel,
                                selectedBorderColor = Color.Transparent,
                                borderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
            
            // 底部提示说明
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "稍后可在群聊设置里调整生成模式、自动接龙与每位成员的静音状态。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
