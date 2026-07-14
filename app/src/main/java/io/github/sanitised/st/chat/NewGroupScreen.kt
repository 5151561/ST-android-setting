package io.github.sanitised.st.chat

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.ui.screens.STAvatar
import io.github.sanitised.st.ui.screens.STGroupAvatar
import io.github.sanitised.st.ui.screens.stGradientFor

// SillyTavern group_activation_strategy (scripts/group-chats.js):
// NATURAL=0, LIST=1, MANUAL=2, POOLED=3.
internal fun activationStrategyId(strategy: String): Int = when (strategy) {
    "natural" -> 0
    "list" -> 1
    "manual" -> 2
    "pooled" -> 3
    else -> 0
}

// Deterministic accent gradient per member so the same character always renders
// with the same colors without needing the avatar image to be loaded.
private val MEMBER_GRADIENTS = listOf(
    listOf(Color(0xFFFFD7B0), Color(0xFFA55A2A)),
    listOf(Color(0xFFD8C4A3), Color(0xFF6B4E2B)),
    listOf(Color(0xFFC8E5B7), Color(0xFF3D6B3A)),
    listOf(Color(0xFF8FB6C6), Color(0xFF2F5567)),
    listOf(Color(0xFFF5B0C8), Color(0xFFA8366A)),
    listOf(Color(0xFFB8B2A4), Color(0xFF46443B)),
    listOf(Color(0xFFB3C7F5), Color(0xFF3A4E8A)),
    listOf(Color(0xFFE3C2F5), Color(0xFF6A3A8A))
)

internal fun gradientFor(seed: String): List<Color> {
    if (seed.isEmpty()) return MEMBER_GRADIENTS[0]
    val index = (seed.hashCode() and Int.MAX_VALUE) % MEMBER_GRADIENTS.size
    return MEMBER_GRADIENTS[index]
}

internal fun memberInitial(name: String): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewGroupScreen(
    characters: List<CharacterSummary>,
    loading: Boolean,
    baseUrl: String = "",
    onClose: () -> Unit,
    onCreate: (name: String, members: List<String>, activationStrategy: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var groupName by remember { mutableStateOf("") }
    var selectedStrategy by remember { mutableStateOf("natural") }
    val selectedIds = remember { mutableStateListOf<String>() }

    // The available carousel is everything not yet selected, in library order.
    val selectedMembers = selectedIds.mapNotNull { id -> characters.firstOrNull { it.id == id } }
    val availableCandidates = characters.filter { it.id !in selectedIds }

    val canCreate = groupName.isNotBlank() && selectedMembers.isNotEmpty()

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
                        enabled = canCreate,
                        onClick = {
                            if (canCreate) {
                                onCreate(
                                    groupName.trim(),
                                    selectedIds.toList(),
                                    activationStrategyId(selectedStrategy)
                                )
                            }
                        }
                    ) {
                        Text(
                            "创建",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (canCreate) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
                    NewGroupHeaderAvatar(members = selectedMembers, baseUrl = baseUrl, size = 64.dp)

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

            // 加载 / 空角色库提示
            if (loading) {
                item {
                    Text(
                        text = "正在读取角色…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            } else if (characters.isEmpty()) {
                item {
                    Text(
                        text = "还没有可用角色，请先在「角色」页导入或创建角色。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            // 2. 已选成员顺序调整
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

            if (selectedMembers.isEmpty()) {
                item {
                    Text(
                        text = "从下面的角色库中选择至少一位参与者。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            itemsIndexed(selectedMembers, key = { _, m -> m.id }) { index, m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 4.dp)) {
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val moved = selectedIds.removeAt(index)
                                    selectedIds.add(index - 1, moved)
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
                                    val moved = selectedIds.removeAt(index)
                                    selectedIds.add(index + 1, moved)
                                }
                            },
                            enabled = index < selectedMembers.lastIndex,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "下移")
                        }
                    }

                    STAvatar(
                        label = m.name,
                        imageUrl = m.avatarUrl,
                        baseUrl = baseUrl,
                        size = 40.dp,
                        gradient = stGradientFor(m.id.hashCode())
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = m.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "第 ${index + 1} 位发言",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { selectedIds.remove(m.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "移除角色",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 3. 待选角色库
            item {
                SectionHeader(title = "添加更多角色")
            }
            item {
                if (availableCandidates.isEmpty()) {
                    Text(
                        text = if (characters.isEmpty()) "暂无角色" else "已添加全部角色",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        availableCandidates.forEach { c ->
                            Column(
                                modifier = Modifier
                                    .width(64.dp)
                                    .clickable { selectedIds.add(c.id) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.size(56.dp)) {
                                    STAvatar(
                                        label = c.name,
                                        imageUrl = c.avatarUrl,
                                        baseUrl = baseUrl,
                                        size = 56.dp,
                                        gradient = stGradientFor(c.id.hashCode())
                                    )

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
            }

            // 4. 回复策略
            item {
                SectionHeader(title = "回复策略")
            }
            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

@Composable
private fun NewGroupHeaderAvatar(members: List<CharacterSummary>, baseUrl: String, size: Dp) {
    if (members.isEmpty()) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradientFor("group"))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Group,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size / 2)
            )
        }
    } else {
        STGroupAvatar(
            initials = members.map { memberInitial(it.name) },
            imageUrls = members.map { it.avatarUrl },
            baseUrl = baseUrl,
            size = size
        )
    }
}
