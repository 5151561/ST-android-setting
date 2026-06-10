package io.github.sanitised.st.ui.prototype

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.CharacterChatSummary
import io.github.sanitised.st.api.ChatExportFormat
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.chat.isNativeChatBackupName
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrototypePastChatsScreen(
    status: NodeStatus,
    baseUrl: String,
    avatar: String,
    currentChatFile: String,
    onBack: () -> Unit,
    onOpenChat: (String?) -> Unit,
    onNewChat: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val serverRunning = status.state == NodeState.RUNNING

    var characterName by remember { mutableStateOf("") }
    var chatFiles by remember { mutableStateOf<List<CharacterChatSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<CharacterChatSummary?>(null) }
    var renameTarget by remember { mutableStateOf<CharacterChatSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<CharacterChatSummary?>(null) }

    fun refreshList() {
        if (!serverRunning) return
        scope.launch {
            loading = true
            runCatching { TavernCoreClient(baseUrl = baseUrl).listCharacterChats(avatar) }
                .onSuccess { chatFiles = filterVisibleCharacterChats(it) }
                .onFailure { onShowMessage(it.message ?: "加载聊天列表失败") }
            loading = false
        }
    }

    LaunchedEffect(serverRunning, baseUrl, avatar) {
        if (!serverRunning) {
            loading = false
            return@LaunchedEffect
        }
        runCatching { TavernCoreClient(baseUrl = baseUrl).getCharacter(avatar) }
            .onSuccess { characterName = it.name }
        refreshList()
    }

    val displayName = characterName.ifBlank {
        avatar.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { "角色" }
    }

    val filteredFiles = if (searchQuery.isBlank()) {
        chatFiles
    } else {
        val q = searchQuery.lowercase()
        chatFiles.filter { f ->
            f.lastMessage.lowercase().contains(q) ||
                f.fileName.lowercase().contains(q)
        }
    }

    Scaffold(
        modifier = modifier.navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("历史对话", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "$displayName · ${chatFiles.size} 个存档",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onShowMessage("导入聊天文件功能开发中") }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "导入聊天文件")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChat,
                icon = { Icon(Icons.Filled.AddComment, contentDescription = null) },
                text = { Text("新对话") }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SearchField(
                query = searchQuery,
                active = searchActive,
                placeholder = "搜索这个角色的聊天内容…",
                onQueryChange = { searchQuery = it },
                onActiveChange = { searchActive = it }
            )

            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "正在加载…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (filteredFiles.isEmpty()) {
                EmptyState(
                    hasSearch = searchQuery.isNotBlank(),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    item(key = "section-header") {
                        SectionHeaderRow(count = filteredFiles.size)
                    }
                    items(
                        items = filteredFiles,
                        key = { it.id }
                    ) { file ->
                        val isCurrent = isSameChatFile(file.fileName, currentChatFile)
                        ChatFileRow(
                            file = file,
                            isCurrent = isCurrent,
                            onClick = {
                                if (isCurrent) {
                                    onBack()
                                } else {
                                    onOpenChat(file.fileName)
                                }
                            },
                            onMore = { actionTarget = file }
                        )
                    }
                }
            }
        }
    }

    actionTarget?.let { file ->
        val sheetState = rememberModalBottomSheetState()
        ChatFileActionsSheet(
            file = file,
            isCurrent = isSameChatFile(file.fileName, currentChatFile),
            sheetState = sheetState,
            onDismiss = { actionTarget = null },
            onOpen = {
                actionTarget = null
                if (isSameChatFile(file.fileName, currentChatFile)) {
                    onBack()
                } else {
                    onOpenChat(file.fileName)
                }
            },
            onRename = {
                actionTarget = null
                renameTarget = file
            },
            onExportJsonl = {
                actionTarget = null
                scope.launch {
                    exportAndShare(context, baseUrl, avatar, file.fileName, ChatExportFormat.JSONL, onShowMessage)
                }
            },
            onExportTxt = {
                actionTarget = null
                scope.launch {
                    exportAndShare(context, baseUrl, avatar, file.fileName, ChatExportFormat.TXT, onShowMessage)
                }
            },
            onDelete = {
                actionTarget = null
                deleteTarget = file
            }
        )
    }

    renameTarget?.let { file ->
        RenameDialog(
            currentName = file.fileName.removeSuffix(".jsonl"),
            onConfirm = { newName ->
                renameTarget = null
                scope.launch {
                    runCatching {
                        TavernCoreClient(baseUrl = baseUrl).renameCharacterChat(
                            avatar = avatar,
                            originalFile = file.fileName,
                            renamedFile = newName
                        )
                    }.onSuccess {
                        refreshList()
                    }.onFailure {
                        onShowMessage(it.message ?: "重命名失败")
                    }
                }
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { file ->
        val isCurrent = isSameChatFile(file.fileName, currentChatFile)
        DeleteConfirmDialog(
            fileName = file.fileName.removeSuffix(".jsonl"),
            isCurrent = isCurrent,
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    runCatching {
                        TavernCoreClient(baseUrl = baseUrl).deleteCharacterChat(avatar, file.fileName)
                    }.onSuccess {
                        refreshList()
                        if (isCurrent) {
                            onShowMessage("当前对话已删除，请新建或切换到其他对话")
                        }
                    }.onFailure {
                        onShowMessage(it.message ?: "删除失败")
                    }
                }
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

internal fun filterVisibleCharacterChats(chats: List<CharacterChatSummary>): List<CharacterChatSummary> =
    chats.filterNot { chat ->
        isNativeChatBackupName(chat.id) || isNativeChatBackupName(chat.fileName)
    }

@Composable
private fun SearchField(
    query: String,
    active: Boolean,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = {
                    onQueryChange(it)
                    if (it.isNotEmpty()) onActiveChange(true)
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { })
            )
            if (active && query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        onActiveChange(false)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "清除搜索",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun SectionHeaderRow(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "全部存档",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "最近优先",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChatFileRow(
    file: CharacterChatSummary,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    Surface(onClick = onClick) {
        Box {
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(3.dp)
                        .height(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(3.dp)
                        )
                )
            }
            ListItem(
                modifier = Modifier.padding(start = if (isCurrent) 0.dp else 0.dp),
                colors = if (isCurrent) {
                    ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
                    )
                } else {
                    ListItemDefaults.colors()
                },
                leadingContent = {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Forum,
                                contentDescription = null,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                },
                headlineContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val displayTime = if (isCurrent) "当前对话" else formatChatTime(file.lastMessageAt)
                        Text(
                            text = displayTime,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isCurrent) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(
                                    "进行中",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                },
                supportingContent = {
                    Column {
                        Text(
                            text = file.lastMessage.ifBlank { "(空聊天)" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatChatTimestamp(file.lastMessageAt)} · ${file.fileSize}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${file.messageCount} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "更多操作",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun EmptyState(hasSearch: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Forum,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (hasSearch) "没有找到匹配的聊天" else "这个角色还没有历史对话",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasSearch) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "点击下方按钮开始第一段对话",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatFileActionsSheet(
    file: CharacterChatSummary,
    isCurrent: Boolean,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onExportJsonl: () -> Unit,
    onExportTxt: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Forum, contentDescription = null, modifier = Modifier.size(21.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        formatChatTime(file.lastMessageAt),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        file.lastMessage.ifBlank { "(空聊天)" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SheetActionItem(
                icon = Icons.Filled.PlayArrow,
                label = if (isCurrent) "返回这段对话" else "打开这段对话",
                subtitle = "${file.messageCount} 条消息 · ${formatChatTimestamp(file.lastMessageAt)}",
                onClick = onOpen
            )
            SheetActionItem(
                icon = Icons.Filled.DriveFileRenameOutline,
                label = "重命名",
                subtitle = "给这段存档起个好记的名字",
                onClick = onRename
            )
            SheetActionItem(
                icon = Icons.Filled.DataObject,
                label = "导出为 JSONL",
                subtitle = "原始聊天文件，可再导入",
                onClick = onExportJsonl
            )
            SheetActionItem(
                icon = Icons.Filled.Description,
                label = "导出为纯文本",
                subtitle = ".txt 文档，便于阅读分享",
                onClick = onExportTxt
            )
            SheetActionItem(
                icon = Icons.Filled.Delete,
                label = "删除这段对话",
                subtitle = "不可撤销",
                onClick = onDelete,
                danger = true
            )
        }
    }
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val contentColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(onClick = onClick, color = Color.Transparent) {
        ListItem(
            headlineContent = { Text(label, color = contentColor) },
            supportingContent = subtitle?.let {
                { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            leadingContent = {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名对话") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("新名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotBlank() && name.trim() != currentName
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    fileName: String,
    isCurrent: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除对话") },
        text = {
            Text(
                if (isCurrent) "确定要删除「$fileName」吗？这是当前正在进行的对话，删除后需要新建或切换到其他对话。此操作不可撤销。"
                else "确定要删除「$fileName」吗？此操作不可撤销。"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun isSameChatFile(a: String, b: String): Boolean {
    val na = a.substringAfterLast('/').substringAfterLast('\\').removeSuffix(".jsonl").trim().lowercase()
    val nb = b.substringAfterLast('/').substringAfterLast('\\').removeSuffix(".jsonl").trim().lowercase()
    return na.isNotBlank() && nb.isNotBlank() && na == nb
}

private fun formatChatTime(timestamp: String): String {
    if (timestamp.isBlank()) return "未知时间"
    val ms = timestamp.toLongOrNull()
    if (ms != null && ms > 0) {
        val now = System.currentTimeMillis()
        val diff = now - ms
        return when {
            diff < 60_000 -> "刚才"
            diff < 3600_000 -> "${diff / 60_000} 分钟前"
            diff < 86400_000 -> "${diff / 3600_000} 小时前"
            diff < 86400_000L * 2 -> "昨天"
            diff < 86400_000L * 7 -> "${diff / 86400_000L} 天前"
            diff < 86400_000L * 30 -> "${diff / (86400_000L * 7)} 周前"
            else -> "${diff / (86400_000L * 30)} 个月前"
        }
    }
    return timestamp
}

private fun formatChatTimestamp(timestamp: String): String {
    if (timestamp.isBlank()) return ""
    val ms = timestamp.toLongOrNull()
    if (ms != null && ms > 0) {
        val date = java.text.SimpleDateFormat("yyyy-M-d", java.util.Locale.getDefault())
        return date.format(java.util.Date(ms))
    }
    return timestamp
}

private suspend fun exportAndShare(
    context: Context,
    baseUrl: String,
    avatar: String,
    chatFile: String,
    format: ChatExportFormat,
    onShowMessage: (String) -> Unit
) {
    runCatching {
        val exported = TavernCoreClient(baseUrl = baseUrl).exportCharacterChat(avatar, chatFile, format)
        val cacheDir = File(context.cacheDir, "chat_exports")
        cacheDir.mkdirs()
        val outFile = File(cacheDir, exported.fileName)
        outFile.writeBytes(exported.bytes)

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, outFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = exported.contentType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "导出聊天").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        onShowMessage(it.message ?: "导出失败")
    }
}
