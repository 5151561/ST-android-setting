package io.github.sanitised.st.ui.prototype

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.CharacterSaveRequest
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.ui.navigation.LocalSTOpenDrawer
import io.github.sanitised.st.ui.screens.readPickedDocument
import kotlinx.coroutines.launch

private val characterImportMimeTypes = arrayOf(
    "application/json",
    "image/png",
    "application/x-yaml",
    "text/yaml",
    "application/octet-stream",
    "*/*"
)

@Composable
fun PrototypeCharacterLibraryScreen(
    status: NodeStatus,
    baseUrl: String,
    onStartService: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onCreateCharacter: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openDrawer = LocalSTOpenDrawer.current
    val serverRunning = status.state == NodeState.RUNNING
    var selectedFilter by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var searchDialogOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var characters by remember { mutableStateOf<List<CharacterSummary>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val client = TavernCoreClient(baseUrl = baseUrl)
                uris.forEach { uri ->
                    val doc = context.readPickedDocument(uri)
                    client.importCharacter(doc.fileName, doc.bytes)
                }
            }.onSuccess {
                onShowMessage("角色导入成功")
                refreshKey++
            }.onFailure { err ->
                onShowMessage(err.message ?: "角色导入失败")
            }
        }
    }

    LaunchedEffect(serverRunning, baseUrl, refreshKey) {
        if (!serverRunning) {
            loading = true
            val paths = io.github.sanitised.st.AppPaths(context)
            val reader = io.github.sanitised.st.data.LocalTavernLibraryReader(paths.dataDir)
            characters = reader.listCharacters()
            error = null
            loading = false
            return@LaunchedEffect
        }
        loading = true
        runCatching { TavernCoreClient(baseUrl = baseUrl).listCharacters() }
            .onSuccess {
                characters = it
                error = null
            }
            .onFailure { error = it.message ?: "角色加载失败" }
        loading = false
    }

    val cards = remember(characters, searchQuery, selectedFilter) {
        characters.mapIndexed { index, item -> item.toPrototypeCharacterCard(index) }
            .filter { card ->
                val queryMatched = searchQuery.isBlank() ||
                    card.name.contains(searchQuery, ignoreCase = true) ||
                    card.subtitle.contains(searchQuery, ignoreCase = true) ||
                    card.tags.any { it.contains(searchQuery, ignoreCase = true) }
                val filterMatched = when (selectedFilter) {
                    1 -> card.favorite
                    2 -> card.messageCount > 0
                    else -> true
                }
                queryMatched && filterMatched
            }
    }

    if (searchDialogOpen) {
        AlertDialog(
            onDismissRequest = { searchDialogOpen = false },
            title = { Text("搜索角色") },
            text = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { searchDialogOpen = false }) { Text("完成") }
            },
            dismissButton = {
                TextButton(onClick = {
                    searchQuery = ""
                    searchDialogOpen = false
                }) { Text("清空") }
            }
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                PrototypeTopHeader(
                    title = "角色",
                    titleBottomPadding = 12.dp,
                    leading = {
                        PrototypeIconButton(
                            icon = Icons.Filled.Menu,
                            contentDescription = "打开抽屉",
                            onClick = openDrawer
                        )
                    },
                    actions = {
                        PrototypeIconButton(
                            icon = Icons.Filled.Download,
                            contentDescription = "导入角色",
                            onClick = { importLauncher.launch(characterImportMimeTypes) }
                        )
                        PrototypeIconButton(
                            icon = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "排序",
                            onClick = { onShowMessage("按最近更新排序") }
                        )
                    }
                )
                PrototypeSearchBar(
                    text = searchQuery.ifBlank { "搜索 ${cards.size} 个角色…" },
                    onClick = { searchDialogOpen = true },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                PrototypeChipRow(
                    items = listOf("全部", "收藏", "最近", "日常", "奇幻", "科幻", "历史"),
                    selectedIndex = selectedFilter,
                    onSelected = { selectedFilter = it },
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp)
                )
                when {
                    !serverRunning -> PrototypeOfflineBlock(onStartService = onStartService)
                    loading -> PrototypeInfoBlock("正在加载角色库…", "请稍候，正在从本地 SillyTavern 读取角色。")
                    error != null -> PrototypeInfoBlock("角色加载失败", error.orEmpty())
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            top = 4.dp,
                            end = 16.dp,
                            bottom = 104.dp
                        )
                    ) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            PrototypeSectionHeader(
                                title = if (selectedFilter == 1) "收藏" else "所有角色",
                                modifier = Modifier.padding(horizontal = 0.dp),
                                trailing = {
                                    Text(
                                        text = "${cards.size} 个",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        }
                        items(cards, key = { it.id }) { card ->
                            PrototypeCharacterCardView(
                                card = card,
                                onClick = { onOpenCharacter(card.id) },
                                onOpenChat = { onOpenChat(card.id) }
                            )
                        }
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = onCreateCharacter,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("创建") },
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
fun PrototypeCharacterProfileScreen(
    status: NodeStatus,
    baseUrl: String,
    avatar: String?,
    onStartService: () -> Unit,
    onBack: () -> Unit,
    onOpenChat: (String?) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val serverRunning = status.state == NodeState.RUNNING
    var detail by remember { mutableStateOf<CharacterDetail?>(null) }
    var loading by remember { mutableStateOf(false) }
    var favorite by remember { mutableStateOf(false) }

    LaunchedEffect(serverRunning, baseUrl, avatar) {
        if (!serverRunning || avatar.isNullOrBlank()) return@LaunchedEffect
        loading = true
        runCatching { TavernCoreClient(baseUrl = baseUrl).getCharacter(avatar) }
            .onSuccess {
                detail = it
                favorite = it.isFavorite
            }
            .onFailure { onShowMessage(it.message ?: "角色详情加载失败") }
        loading = false
    }

    val fallback = remember(avatar) {
        PrototypeCharacterCard(
            id = avatar.orEmpty(),
            name = avatar?.substringBeforeLast('.')?.replace('_', ' ')?.trim()?.ifBlank { "未知角色" } ?: "未知角色",
            subtitle = "本地角色卡",
            tags = emptyList(),
            initial = avatar?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            messageCount = 0,
            favorite = false,
            gradient = prototypeGradientFor(avatar.hashCode())
        )
    }
    val card = detail?.toPrototypeCharacterCard(0) ?: fallback

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrototypeIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack
                )
                Spacer(modifier = Modifier.weight(1f))
                PrototypeIconButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = "开始对话",
                    onClick = { onOpenChat(null) }
                )
                PrototypeIconButton(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    onClick = { onShowMessage("更多角色操作功能开发中") }
                )
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CharacterHero(card = card)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { onOpenChat(null) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("开始对话")
                    }
                    IconButton(
                        onClick = {
                            val target = avatar
                            if (!serverRunning || target.isNullOrBlank()) {
                                favorite = !favorite
                                return@IconButton
                            }
                            scope.launch {
                                val next = !favorite
                                runCatching {
                                    TavernCoreClient(baseUrl = baseUrl).mergeCharacterAttributes(target, isFavorite = next)
                                }.onSuccess {
                                    favorite = next
                                }.onFailure { onShowMessage(it.message ?: "收藏状态保存失败") }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (favorite || card.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "收藏",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { onShowMessage("复制角色功能开发中") }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制")
                    }
                    IconButton(onClick = { onShowMessage("分享角色功能开发中") }) {
                        Icon(Icons.Filled.Share, contentDescription = "分享")
                    }
                }
                if (!serverRunning) {
                    PrototypeOfflineBlock(onStartService = onStartService)
                } else if (loading) {
                    PrototypeInfoBlock("正在读取角色…", "从 SillyTavern 加载角色卡内容。")
                }
                CharacterDetailSections(detail = detail, fallback = card)
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun PrototypeCharacterCreateScreen(
    status: NodeStatus,
    baseUrl: String,
    onStartService: () -> Unit,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val running = status.state == NodeState.RUNNING
    var name by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var greeting by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrototypeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "返回", onBack)
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = running && name.isNotBlank() && !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            runCatching {
                                TavernCoreClient(baseUrl).createCharacter(
                                    CharacterSaveRequest(
                                        name = name.trim(),
                                        description = description.trim().ifBlank { subtitle.trim() },
                                        firstMessage = greeting.trim(),
                                        creatorNotes = subtitle.trim()
                                    )
                                )
                            }.onSuccess { avatar ->
                                onShowMessage("角色已创建")
                                onSaved(avatar)
                            }.onFailure { error ->
                                onShowMessage(error.message ?: "角色创建失败")
                            }
                            saving = false
                        }
                    }
                ) {
                    Text(if (saving) "保存中…" else "保存")
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CharacterHero(
                    card = PrototypeCharacterCard(
                        id = "new",
                        name = name.ifBlank { "新角色" },
                        subtitle = subtitle.ifBlank { "给这张角色卡写一句简介" },
                        tags = listOf("新建"),
                        initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "+",
                        messageCount = 0,
                        favorite = false,
                        gradient = prototypeGradientFor(4)
                    )
                )
                if (!running) {
                    PrototypeOfflineBlock(onStartService = onStartService)
                }
                PrototypeSectionHeader("基本信息")
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("角色名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subtitle,
                        onValueChange = { subtitle = it },
                        label = { Text("一句话简介") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("角色描述") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = greeting,
                        onValueChange = { greeting = it },
                        label = { Text("开场白") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PrototypeCharacterCardView(
    card: PrototypeCharacterCard,
    onClick: () -> Unit,
    onOpenChat: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(276.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(Brush.linearGradient(card.gradient.map { Color(it) }))
            ) {
                val density = LocalDensity.current
                val heightPx = remember { with(density) { 170.dp.toPx() } }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                                radius = heightPx * 1.5f
                            )
                        )
                )
                Text(
                    text = card.initial,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 84.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.Center)
                )
                if (card.favorite) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.42f),
                        contentColor = Color(0xFFFFD7B0)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(16.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onOpenChat,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "开始对话",
                        tint = Color.White
                    )
                }
                PrototypeBadge(
                    label = "${card.messageCount} 消息",
                    containerColor = Color.Black.copy(alpha = 0.50f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    card.tags.ifEmpty { listOf("角色卡") }.forEach { tag ->
                        PrototypeBadge(label = tag)
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterHero(card: PrototypeCharacterCard) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Brush.linearGradient(card.gradient.map { Color(it) }))
    ) {
        val density = LocalDensity.current
        val heightPx = remember { with(density) { 220.dp.toPx() } }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        radius = heightPx * 1.5f
                    )
                )
        )
        Text(
            text = card.initial,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 110.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.Center)
        )
        val surfaceColor = MaterialTheme.colorScheme.surface
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, surfaceColor.copy(alpha = 0.85f), surfaceColor),
                        startY = heightPx * 0.4f
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = card.name,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = card.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                card.tags.ifEmpty { listOf("角色卡") }.forEach { tag ->
                    PrototypeBadge(
                        label = tag,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterDetailSections(
    detail: CharacterDetail?,
    fallback: PrototypeCharacterCard
) {
    PrototypeEditSection(title = "基本信息", icon = Icons.Filled.Badge, open = true) {
        PrototypeFieldRow("角色名", detail?.name ?: fallback.name)
        PrototypeFieldRow("创作者", detail?.creator?.ifBlank { "未知" } ?: "原型演示")
        PrototypeFieldRow("版本", detail?.characterVersion?.ifBlank { "未标注" } ?: "v2.1")
        PrototypeFieldRow("语言", "中文 · 双语")
    }
    PrototypeEditSection(title = "角色描述", icon = Icons.Filled.Description, open = true, count = detail?.description?.length) {
        Text(
            text = detail?.description?.ifBlank { fallback.subtitle }
                ?: "${fallback.subtitle}。她不是英雄，也不是反派；她只是想把故事继续讲下去。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        PrototypeFieldRow("个性", detail?.personality?.lineSequence()?.firstOrNull()?.ifBlank { null } ?: "冷静 / 直接 / 私下幽默")
        PrototypeFieldRow("说话方式", detail?.personality?.lineSequence()?.drop(1)?.firstOrNull()?.ifBlank { null } ?: "短句、偶尔粗口、行话很多")
    }
    PrototypeEditSection(title = "开场白", icon = Icons.Filled.EmojiPeople, open = true, count = detail?.firstMessage?.length) {
        Text(
            text = "\"${detail?.firstMessage?.ifBlank { "诶？又是你啊。今天还是老样子？" } ?: "诶？又是你啊。今天还是老样子？"}\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier
                .padding(start = 16.dp, bottom = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("主开场", "备选 1", "备选 2").forEach { PrototypeBadge(it) }
        }
    }
    PrototypeEditSection(title = "场景 (Scenario)", icon = Icons.Filled.Theaters) {}
    PrototypeEditSection(title = "对话示例", icon = Icons.Filled.Forum, count = if (detail?.messageExample.isNullOrBlank()) null else "已设置") {}
    PrototypeEditSection(title = "高级 — Persona / Post-history Instructions", icon = Icons.Filled.Tune) {}
    PrototypeEditSection(title = "绑定世界书", icon = Icons.Filled.AutoStories, count = detail?.world?.takeIf { it.isNotBlank() }) {}
}

@Composable
private fun PrototypeEditSection(
    title: String,
    icon: ImageVector,
    open: Boolean = false,
    count: Any? = null,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(open) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (count != null) {
                Text(
                    text = if (count is Int) "$count 字" else count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) content()
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun PrototypeFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PrototypeOfflineBlock(onStartService: () -> Unit) {
    PrototypeInfoBlock(
        title = "本地服务未启动",
        body = "启动 SillyTavern 后会加载你的真实角色库。",
        action = {
            Button(onClick = onStartService) { Text("启动服务") }
        }
    )
}

@Composable
private fun PrototypeInfoBlock(
    title: String,
    body: String,
    action: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = if (action == null) 0.dp else 12.dp)
            )
            action?.invoke()
        }
    }
}
