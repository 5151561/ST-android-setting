package io.github.sanitised.st.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.sanitised.st.SecondaryTopAppBar
import io.github.sanitised.st.api.GroupSummary
import io.github.sanitised.st.api.TavernCoreClient
import kotlinx.coroutines.launch

// generation_mode: 0=swap, 1=join(exclude muted), 2=join_all(include muted)
private fun genModeName(value: Int): String = when (value) {
    1 -> "join"
    2 -> "join_all"
    else -> "swap"
}

private fun genModeId(name: String): Int = when (name) {
    "join" -> 1
    "join_all" -> 2
    else -> 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    groupId: String,
    baseUrl: String,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var base by remember { mutableStateOf<GroupSummary?>(null) }
    var loaded by remember { mutableStateOf(false) }

    var groupName by remember { mutableStateOf("") }
    var strategy by remember { mutableStateOf("natural") }
    var genMode by remember { mutableStateOf("swap") }
    var autoMode by remember { mutableStateOf(false) }
    var autoDelay by remember { mutableStateOf(5) }
    var selfResponses by remember { mutableStateOf(false) }
    var hideMutedSprites by remember { mutableStateOf(false) }
    var fav by remember { mutableStateOf(false) }
    var externalMedia by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val members = remember { mutableStateListOf<String>() }
    val membersMockList = remember { mutableStateListOf<DemoGroupMember>() }

    LaunchedEffect(groupId) {
        val client = TavernCoreClient(baseUrl)
        val group = runCatching { client.listGroups().find { it.id == groupId } }.getOrNull()
        if (group == null) {
            onShowMessage("找不到群聊")
            return@LaunchedEffect
        }
        groupName = group.name
        strategy = groupStrategyName(group.activationStrategy)
        genMode = genModeName(group.generationMode)
        autoDelay = group.autoModeDelay
        selfResponses = group.allowSelfResponses
        fav = group.isFavorite
        val byId = runCatching { client.listCharacters() }.getOrDefault(emptyList()).associateBy { it.id }
        members.clear(); members.addAll(group.members)
        membersMockList.clear()
        membersMockList.addAll(group.members.mapIndexed { index, avatar ->
            val character = byId[avatar]
            val name = character?.name ?: avatar.removeSuffix(".png")
            DemoGroupMember(
                id = avatar, name = name, subtitle = "", accent = gradientFor(avatar).last(),
                role = "", queue = index + 1, muted = avatar in group.disabledMembers,
                avatarUrl = character?.avatarUrl ?: avatar,
                avatarGrad = gradientFor(avatar), initial = memberInitial(name)
            )
        })
        base = group
        loaded = true
    }

    // 去抖持久化：任一字段变化 400ms 后写回 /api/groups/edit（首帧与基线相同则跳过）。
    val current = base?.copy(
        name = groupName,
        activationStrategy = activationStrategyId(strategy),
        generationMode = genModeId(genMode),
        autoModeDelay = autoDelay,
        allowSelfResponses = selfResponses,
        isFavorite = fav
    )
    LaunchedEffect(current) {
        val updated = current ?: return@LaunchedEffect
        if (!loaded || updated == base) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        runCatching { TavernCoreClient(baseUrl).editGroup(updated) }
            .onSuccess { base = updated }
            .onFailure { onShowMessage(it.message ?: "保存群设置失败") }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除群聊") },
            text = { Text("将彻底移除「${groupName}」的配置与历史存档，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        runCatching { TavernCoreClient(baseUrl).deleteGroup(groupId) }
                            .onSuccess { onShowMessage("已删除群聊"); onBack() }
                            .onFailure { onShowMessage(it.message ?: "删除失败") }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } }
        )
    }

    Scaffold(
        topBar = {
            SecondaryTopAppBar(
                title = "群聊设置",
                onBack = onBack
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
            // 1. 群标识卡 (Group ID Card)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(modifier = Modifier.size(72.dp)) {
                        GroupAvatar(ids = members, members = membersMockList, baseUrl = baseUrl, size = 72.dp)
                        // Edit Avatar small button
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "修改头像",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
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
                        Text(
                            text = "${members.size} 位成员",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            // 2. 回复策略 (Response Strategy)
            item {
                SectionHeader(title = "回复策略")
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioCard(
                        icon = Icons.Filled.TouchApp,
                        title = "手动",
                        desc = "只有你点名时角色才发言",
                        selected = strategy == "manual",
                        onClick = { strategy = "manual" }
                    )
                    RadioCard(
                        icon = Icons.Filled.Forum,
                        title = "自然顺序",
                        desc = "模型按上下文挑选下一位发言者",
                        selected = strategy == "natural",
                        onClick = { strategy = "natural" }
                    )
                    RadioCard(
                        icon = Icons.Filled.FormatListNumbered,
                        title = "列表顺序",
                        desc = "严格按群成员顺序轮流发言",
                        selected = strategy == "list",
                        onClick = { strategy = "list" }
                    )
                    RadioCard(
                        icon = Icons.Filled.Shuffle,
                        title = "池化顺序",
                        desc = "每人发言一次后再开启新一轮",
                        selected = strategy == "pooled",
                        onClick = { strategy = "pooled" }
                    )
                }
            }

            // 3. 生成模式 (Generation Mode)
            item {
                SectionHeader(
                    title = "生成模式",
                    trailing = {
                        Icon(
                            imageVector = Icons.Filled.HelpOutline,
                            contentDescription = "帮助说明",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioCard(
                        title = "切换角色卡",
                        desc = "每次只加载当前发言者的角色卡（极省 token）",
                        selected = genMode == "swap",
                        onClick = { genMode = "swap" }
                    )
                    RadioCard(
                        title = "合并角色卡 · 排除静音",
                        desc = "把在场活跃成员的角色卡拼接，静音的不计入上下文",
                        selected = genMode == "join",
                        onClick = { genMode = "join" }
                    )
                    RadioCard(
                        title = "合并角色卡 · 含静音",
                        desc = "所有在场成员的角色卡均拼入上下文，包含静音角色",
                        selected = genMode == "join_all",
                        onClick = { genMode = "join_all" }
                    )
                }
            }
            
            // 合并模式下的辅助提示卡片
            if (genMode != "swap") {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                .padding(10.dp, 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "已切换为「合并角色卡」模式，您可在高级设置中配置角色拼接前缀 / 后缀关系。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 4. 自动模式 (Auto Mode)
            item {
                SectionHeader(title = "自动接龙")
            }
            item {
                ListItem(
                    leadingContent = { SetIcon(name = Icons.Filled.PlayCircle) },
                    headlineContent = { Text("自动模式", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(if (autoMode) "角色之间将自动轮流接龙发言" else "关闭 · 每轮发言均需手动点名触发") },
                    trailingContent = { Switch(checked = autoMode, onCheckedChange = { autoMode = it }) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 72.dp))
            }
            
            // 每轮间隔 Slider
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (autoMode) 1f else 0.45f)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("每轮发言间隔", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("$autoDelay 秒", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = { if (autoDelay > 1 && autoMode) autoDelay-- },
                            enabled = autoMode && autoDelay > 1,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "减少", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        
                        Slider(
                            value = autoDelay.toFloat(),
                            onValueChange = { if (autoMode) autoDelay = it.toInt() },
                            valueRange = 1f..30f,
                            steps = 29,
                            modifier = Modifier.weight(1f),
                            enabled = autoMode
                        )
                        
                        IconButton(
                            onClick = { if (autoDelay < 30 && autoMode) autoDelay++ },
                            enabled = autoMode && autoDelay < 30,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "增加", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // 5. 行为 (Behavior)
            item {
                SectionHeader(title = "行为")
            }
            item {
                ListItem(
                    leadingContent = { SetIcon(name = Icons.Filled.RecordVoiceOver) },
                    headlineContent = { Text("允许角色自我回复", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("在没有人类干预下，同一角色可连续发言多次") },
                    trailingContent = { Switch(checked = selfResponses, onCheckedChange = { selfResponses = it }) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 72.dp))
            }
            item {
                ListItem(
                    leadingContent = { SetIcon(name = Icons.Filled.HideImage) },
                    headlineContent = { Text("隐藏静音成员立绘", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("被标记静音的角色，其立绘背景板将不予在场渲染") },
                    trailingContent = { Switch(checked = hideMutedSprites, onCheckedChange = { hideMutedSprites = it }) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // 6. 知识与场景 (Lorebook & Context)
            item {
                SectionHeader(title = "知识与场景")
            }
            item {
                ListItem(
                    leadingContent = { SetIcon(name = Icons.Filled.AutoStories) },
                    headlineContent = { Text("群聊世界书", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("常去的咖啡馆") },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {}
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 72.dp))
            }
            item {
                ListItem(
                    leadingContent = { SetIcon(name = Icons.Filled.Landscape) },
                    headlineContent = { Text("场景覆盖", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("为本群单独设定开场与情境") },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {}
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 72.dp))
            }
            item {
                ListItem(
                    leadingContent = { SetIcon(name = Icons.Filled.Sell) },
                    headlineContent = { Text("标签", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("日常 · 群像 · 治愈") },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable {}
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 72.dp))
            }
            item {
                ListItem(
                    leadingContent = { SetIcon(name = Icons.Filled.Link) },
                    headlineContent = { Text("允许外部媒体", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("角色卡与对话中的外链资产在连接时允许直载") },
                    trailingContent = { Switch(checked = externalMedia, onCheckedChange = { externalMedia = it }) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // 7. 管理 (Management)
            item {
                SectionHeader(title = "管理")
            }
            item {
                ListItem(
                    leadingContent = { SetIcon(name = Icons.Filled.Star) },
                    headlineContent = { Text("收藏此群聊", fontWeight = FontWeight.Bold) },
                    trailingContent = { Switch(checked = fav, onCheckedChange = { fav = it }) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 72.dp))
            }
            item {
                ListItem(
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    },
                    headlineContent = {
                        Text(
                            "删除群聊",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    supportingContent = { Text("彻底移除此群组的所有配置和历史存档文件") },
                    modifier = Modifier.clickable { showDeleteDialog = true }
                )
            }
        }
    }
}

@Composable
fun RadioCard(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun SetIcon(
    name: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = name,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
