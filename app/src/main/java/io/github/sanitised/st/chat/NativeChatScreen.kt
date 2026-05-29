package io.github.sanitised.st.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.ThemeMode
import io.github.sanitised.st.api.GroupSummary
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.ui.prototype.PrototypeAssistPill
import io.github.sanitised.st.ui.prototype.PrototypeAvatar
import io.github.sanitised.st.ui.prototype.PrototypeGroupAvatar
import io.github.sanitised.st.ui.webview.ChatWebViewScreen
import io.github.sanitised.st.ui.webview.WebViewTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeChatScreen(
    status: NodeStatus,
    target: WebViewTarget,
    themeMode: ThemeMode,
    store: ChatStore,
    bridge: ChatRuntimeBridge,
    onStartService: () -> Unit,
    onShowLogs: () -> Unit,
    onBackToHome: () -> Unit,
    onOpenPastChats: (() -> Unit)? = null,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessageId by rememberSaveable { mutableStateOf(-1) }
    var editText by rememberSaveable { mutableStateOf("") }
    var deletingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showGroupChatsSheet by remember { mutableStateOf(false) }
    var showAuthorsNoteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(status.state) {
        if (status.state != NodeState.RUNNING) {
            bridge.markRuntimeLoading()
        }
    }

    val readyTargetKey = if (store.runtimeState == RuntimeState.READY) readyTargetCommandKey(target) else null
    LaunchedEffect(readyTargetKey) {
        if (readyTargetKey == null) return@LaunchedEffect
        when (target) {
            WebViewTarget.CHAT -> bridge.requestSnapshot()
            is WebViewTarget.CharacterChat -> bridge.openCharacter(target.avatar, target.chatFile)
            is WebViewTarget.GroupChat -> bridge.openGroup(target.groupId, target.chatId)
        }
    }

    val targetMatched = targetMatchesStore(target, store)
    val visibleCharacterName = if (targetMatched) {
        store.characterName.ifBlank { target.displayLabel() }
    } else {
        target.displayLabel()
    }
    val readyForTarget = store.runtimeState == RuntimeState.READY && targetMatched
    val isGroupMode = store.mode == "group"

    Box(modifier = modifier.fillMaxSize()) {
        ChatWebViewScreen(
            status = status,
            target = target,
            themeMode = themeMode,
            onStartService = onStartService,
            onShowLogs = onShowLogs,
            onBackToHome = onBackToHome,
            chatEventHandler = { json -> bridge.onEvent(json) },
            onWebViewReady = { wv -> bridge.attach(wv) },
            onWebViewDisposed = { wv -> bridge.detach(wv) },
            onRuntimeReset = { bridge.markRuntimeLoading() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(1.dp)
                .alpha(0.01f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            ChatHeader(
                characterName = visibleCharacterName,
                isGenerating = store.isGenerating,
                isGroupMode = isGroupMode,
                runtimeState = store.runtimeState,
                runtimeError = store.runtimeError,
                targetMatched = targetMatched,
                targetLabel = target.displayLabel(),
                chatFile = if (targetMatched) store.chatFile else "",
                onBack = onBackToHome,
                onSearch = { onShowMessage("消息搜索暂未接入原生聊天运行时") },
                onReloadChat = {
                    bridge.reloadChat()
                    onShowMessage("已请求重新同步当前聊天")
                },
                onOpenPastChats = if (isGroupMode) {
                    { showGroupChatsSheet = true }
                } else {
                    onOpenPastChats
                }
            )

            if (store.saveError != null) {
                SaveErrorBanner(
                    message = store.saveError!!,
                    onRetry = { bridge.retrySave() },
                    onDismiss = { bridge.dismissSaveError() }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (!targetMatched || (store.runtimeState == RuntimeState.NOT_READY && store.messages.isEmpty())) {
                    ChatLoadingView(
                        targetLabel = target.displayLabel(),
                        runtimeError = store.runtimeError,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    MessageList(
                        messages = store.messages,
                        characterName = visibleCharacterName,
                        editingMessageId = editingMessageId,
                        editText = editText,
                        onEditTextChange = { editText = it },
                        onSwipePrevious = { messageId -> bridge.swipePrevious(messageId) },
                        onSwipeNext = { messageId -> bridge.swipeNext(messageId) },
                        onRegenerate = { bridge.regenerate() },
                        onContinue = { bridge.continueGeneration() },
                        onLongPress = { message -> selectedMessage = message },
                        onSaveEdit = { messageId ->
                            bridge.editMessage(messageId, editText)
                            editingMessageId = -1
                            onShowMessage("消息已保存")
                        },
                        onCancelEdit = { editingMessageId = -1 },
                        onDeleteFromEdit = { message ->
                            editingMessageId = -1
                            deletingMessage = message
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (editingMessageId < 0) {
                ChatQuickStrip(
                    runtimeReady = readyForTarget,
                    onContinue = { bridge.continueGeneration() },
                    onRegenerate = { bridge.regenerate() },
                    onUnavailableAction = { label -> onShowMessage("$label 功能暂未接入原生聊天运行时") }
                )
            }

            ChatInputBar(
                isGenerating = store.isGenerating,
                runtimeReady = readyForTarget,
                onSend = { text -> bridge.sendMessage(text) },
                onStop = { bridge.stopGeneration() },
                onVoiceInput = { onShowMessage("语音输入暂未接入") },
                onAttachmentAction = { label ->
                    if (label == "作者注") {
                        showAuthorsNoteDialog = true
                    } else {
                        onShowMessage("$label 功能暂未接入原生聊天运行时")
                    }
                }
            )
        }
    }

    if (selectedMessage != null) {
        val message = selectedMessage!!
        MessageActionSheet(
            message = message,
            onDismiss = { selectedMessage = null },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("message", message.mes))
                onShowMessage("已复制到剪贴板")
                selectedMessage = null
            },
            onEdit = {
                editingMessageId = message.id
                editText = message.mes
                selectedMessage = null
            },
            onRegenerate = {
                bridge.regenerate()
                selectedMessage = null
            },
            onHideToggle = {
                if (message.isSystem) {
                    bridge.unhideMessage(message.id)
                    onShowMessage("消息已取消隐藏")
                } else {
                    bridge.hideMessage(message.id)
                    onShowMessage("消息已隐藏（不会被 AI 看到）")
                }
                selectedMessage = null
            },
            onDelete = {
                deletingMessage = message
                selectedMessage = null
            },
            onUnavailableAction = { label ->
                onShowMessage("$label 功能暂未接入原生聊天运行时")
                selectedMessage = null
            }
        )
    }

    if (deletingMessage != null) {
        val message = deletingMessage!!
        DeleteMessageDialog(
            messageName = message.name,
            onConfirm = {
                bridge.deleteMessageFromChat(message.id)
                deletingMessage = null
                onShowMessage("消息已删除")
            },
            onDismiss = { deletingMessage = null }
        )
    }

    if (showGroupChatsSheet && isGroupMode) {
        GroupChatHistorySheet(
            port = status.port,
            groupId = store.avatarUrl,
            currentChatFile = store.chatFile,
            onDismiss = { showGroupChatsSheet = false },
            onOpenChat = { chatId ->
                showGroupChatsSheet = false
                bridge.openGroup(store.avatarUrl, chatId)
            }
        )
    }

    if (showAuthorsNoteDialog) {
        AuthorsNoteDialog(
            currentText = store.authorsNote,
            onDismiss = { showAuthorsNoteDialog = false },
            onSave = { text ->
                bridge.setAuthorsNote(text)
                showAuthorsNoteDialog = false
                onShowMessage("作者注已保存")
            }
        )
    }
}

private fun targetMatchesStore(target: WebViewTarget, store: ChatStore): Boolean {
    return when (target) {
        WebViewTarget.CHAT -> store.chatFile.isNotBlank() ||
            store.characterName.isNotBlank() ||
            store.messages.isNotEmpty()
        is WebViewTarget.CharacterChat -> {
            val characterMatches = listOf(store.avatarUrl, store.characterName)
                .any { identifiersMatch(target.avatar, it) }
            val chatMatches = target.chatFile.isNullOrBlank() ||
                normalizeChatFile(target.chatFile) == normalizeChatFile(store.chatFile)
            characterMatches && chatMatches
        }
        is WebViewTarget.GroupChat -> {
            val groupMatches = store.mode == "group" &&
                identifiersMatch(target.groupId, store.avatarUrl)
            val chatMatches = target.chatId.isNullOrBlank() ||
                normalizeChatFile(target.chatId) == normalizeChatFile(store.chatFile)
            groupMatches && chatMatches
        }
    }
}

private fun WebViewTarget.displayLabel(): String {
    return when (this) {
        WebViewTarget.CHAT -> "对话"
        is WebViewTarget.CharacterChat -> chatFile
            ?.takeIf { it.isNotBlank() }
            ?.substringBeforeLast(".jsonl")
            ?: avatar.substringAfterLast('/').substringBeforeLast('.').ifBlank { "角色聊天" }
        is WebViewTarget.GroupChat -> chatId
            ?.takeIf { it.isNotBlank() }
            ?: groupId.ifBlank { "群聊" }
    }
}

private fun identifiersMatch(expected: String, actual: String): Boolean {
    val left = normalizeIdentifier(expected)
    val right = normalizeIdentifier(actual)
    return left.isNotBlank() && right.isNotBlank() && left == right
}

private fun normalizeIdentifier(value: String?): String {
    return value
        .orEmpty()
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .substringBeforeLast('.')
        .trim()
        .lowercase()
}

private fun normalizeChatFile(value: String?): String {
    return value
        .orEmpty()
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .removeSuffix(".jsonl")
        .trim()
        .lowercase()
}

@Composable
private fun ChatHeader(
    characterName: String,
    isGenerating: Boolean,
    isGroupMode: Boolean,
    runtimeState: RuntimeState,
    runtimeError: String?,
    targetMatched: Boolean,
    targetLabel: String,
    chatFile: String,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onReloadChat: () -> Unit,
    onOpenPastChats: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isGroupMode) {
                    PrototypeGroupAvatar(
                        initials = characterName.take(2).map { it.uppercase() }.ifEmpty { listOf("群") },
                        size = 36.dp
                    )
                } else {
                    PrototypeAvatar(
                        label = characterName.ifBlank { "?" },
                        size = 36.dp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = characterName.ifBlank { "对话" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isGroupMode) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Text(
                                    text = "群聊",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    val subtitle = when {
                        !runtimeError.isNullOrBlank() -> runtimeError
                        !targetMatched -> "正在打开 $targetLabel…"
                        isGenerating -> "生成中…"
                        runtimeState == RuntimeState.NOT_READY -> "正在连接运行时…"
                        runtimeState == RuntimeState.ERROR -> "运行时异常"
                        chatFile.isNotBlank() -> chatFile
                        else -> "运行时已连接"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (runtimeState == RuntimeState.ERROR || !runtimeError.isNullOrBlank()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "搜索消息")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (onOpenPastChats != null) {
                        DropdownMenuItem(
                            text = { Text("历史对话") },
                            leadingIcon = { Icon(Icons.Filled.Forum, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = { menuExpanded = false; onOpenPastChats() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("重新同步") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { menuExpanded = false; onReloadChat() }
                    )
                }
            }
            if (isGenerating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun ChatLoadingView(
    targetLabel: String,
    runtimeError: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (targetLabel.isBlank()) {
                    "正在等待 SillyTavern 运行时…"
                } else {
                    "正在打开 $targetLabel…"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!runtimeError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = runtimeError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    characterName: String,
    editingMessageId: Int,
    editText: String,
    onEditTextChange: (String) -> Unit,
    onSwipePrevious: (Int) -> Unit,
    onSwipeNext: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onLongPress: (ChatMessage) -> Unit,
    onSaveEdit: (Int) -> Unit,
    onCancelEdit: () -> Unit,
    onDeleteFromEdit: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val visibleMessages = visibleChatMessages(messages)
    val dateLabel = conversationDateLabel(messages)

    LaunchedEffect(visibleMessages.size, visibleMessages.lastOrNull()?.mes, imeBottom, dateLabel) {
        chatListScrollTargetIndex(visibleMessages, dateLabel)?.let { index ->
            listState.animateScrollToItem(index)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (dateLabel != null) {
            item(key = "date-chip") {
                DateChip(text = dateLabel)
            }
        }
        items(
            items = visibleMessages,
            key = { msg -> chatMessageItemKey(msg) }
        ) { message ->
            if (message.id == editingMessageId) {
                MessageEditBubble(
                    message = message,
                    characterName = characterName,
                    editText = editText,
                    onEditTextChange = onEditTextChange,
                    maxWidth = if (message.isUser) screenWidth * 0.82f else screenWidth * 0.92f,
                    onSave = { onSaveEdit(message.id) },
                    onCancel = onCancelEdit,
                    onDelete = { onDeleteFromEdit(message) }
                )
            } else {
                MessageBubble(
                    message = message,
                    characterName = characterName,
                    lastAssistant = !message.isUser && visibleMessages.lastOrNull()?.id == message.id,
                    maxWidth = if (message.isUser) screenWidth * 0.82f else screenWidth * 0.92f,
                    onSwipePrevious = onSwipePrevious,
                    onSwipeNext = onSwipeNext,
                    onRegenerate = onRegenerate,
                    onContinue = onContinue,
                    onLongPress = { onLongPress(message) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    characterName: String,
    lastAssistant: Boolean,
    maxWidth: androidx.compose.ui.unit.Dp,
    onSwipePrevious: (Int) -> Unit,
    onSwipeNext: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(
        topStart = if (isUser) 18.dp else 4.dp,
        topEnd = if (isUser) 4.dp else 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp
    )
    val hiddenAlpha = if (message.isSystem) 0.5f else 1f

    Box(modifier = modifier.fillMaxWidth().alpha(hiddenAlpha), contentAlignment = alignment) {
        if (isUser) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .clip(shape)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress
                    )
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (message.isSystem) {
                    HiddenMessageBadge(modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(text = message.mes, style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrototypeAvatar(label = message.name.ifBlank { characterName }, size = 36.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = message.name.ifBlank { characterName },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (message.isSystem) {
                            Spacer(modifier = Modifier.width(6.dp))
                            HiddenMessageBadge()
                        }
                    }
                    Column(
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .clip(shape)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = onLongPress
                            )
                            .background(bubbleColor)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(text = message.mes, style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    if (lastAssistant) {
                        AssistantMessageControls(
                            messageId = message.id,
                            swipeIndex = message.swipeId,
                            swipeCount = message.swipes.size.coerceAtLeast(1),
                            onSwipePrevious = onSwipePrevious,
                            onSwipeNext = onSwipeNext,
                            onRegenerate = onRegenerate,
                            onContinue = onContinue,
                            onMore = onLongPress
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateChip(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessageControls(
    messageId: Int,
    swipeIndex: Int,
    swipeCount: Int,
    onSwipePrevious: (Int) -> Unit,
    onSwipeNext: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onMore: () -> Unit
) {
    Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSwipePrevious(messageId) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            text = "${swipeIndex + 1} / $swipeCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        IconButton(onClick = { onSwipeNext(messageId) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onRegenerate, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = "重写", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onContinue, modifier = Modifier.size(32.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "继续", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.MoreVert, contentDescription = "更多消息操作", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MessageEditBubble(
    message: ChatMessage,
    characterName: String,
    editText: String,
    onEditTextChange: (String) -> Unit,
    maxWidth: androidx.compose.ui.unit.Dp,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        val content: @Composable () -> Unit = {
            Column {
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = message.name.ifBlank { characterName },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "编辑中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                OutlinedTextField(
                    value = editText,
                    onValueChange = onEditTextChange,
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth(),
                    minLines = 3,
                    maxLines = 12,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onSave,
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("保存", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Surface(
                        onClick = onCancel,
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("取消", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (isUser) {
            Column(modifier = Modifier.widthIn(max = maxWidth)) {
                content()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrototypeAvatar(label = message.name.ifBlank { characterName }, size = 36.dp)
                Column(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onHideToggle: () -> Unit,
    onDelete: () -> Unit,
    onUnavailableAction: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "消息操作",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionGridItem(
                    icon = Icons.Filled.ContentCopy,
                    label = "复制",
                    onClick = onCopy
                )
                ActionGridItem(
                    icon = Icons.Filled.Edit,
                    label = "编辑",
                    onClick = onEdit
                )
                if (!message.isUser) {
                    ActionGridItem(
                        icon = Icons.Filled.Refresh,
                        label = "重写",
                        onClick = onRegenerate
                    )
                }
                ActionGridItem(
                    icon = Icons.Filled.Translate,
                    label = "翻译",
                    onClick = { onUnavailableAction("翻译") }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Surface(
                onClick = onHideToggle,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                ListItem(
                    headlineContent = {
                        Text(if (message.isSystem) "取消隐藏" else "从 AI 上下文中隐藏")
                    },
                    supportingContent = {
                        Text(
                            if (message.isSystem) "恢复此消息到 AI 上下文中"
                            else "隐藏后 AI 将不会看到此消息内容",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (message.isSystem) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )
            }

            Surface(
                onClick = onDelete,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            "删除此消息",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionGridItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun DeleteMessageDialog(
    messageName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除消息") },
        text = {
            Text(
                if (messageName.isNotBlank()) {
                    "确定要删除 $messageName 的这条消息吗？此操作无法撤销。"
                } else {
                    "确定要删除这条消息吗？此操作无法撤销。"
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun SaveErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = "保存失败：$message",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onRetry) {
                Text("重试", style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ChatQuickStrip(
    runtimeReady: Boolean,
    onContinue: () -> Unit,
    onRegenerate: () -> Unit,
    onUnavailableAction: (String) -> Unit
) {
    data class QuickAction(
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val text: String,
        val enabled: Boolean,
        val onClick: () -> Unit
    )
    val actions = listOf(
        QuickAction(Icons.AutoMirrored.Filled.ArrowForward, "继续", runtimeReady, onContinue),
        QuickAction(Icons.Filled.Refresh, "重写上条", runtimeReady, onRegenerate),
        QuickAction(Icons.Filled.RecordVoiceOver, "代笔我的消息", runtimeReady) { onUnavailableAction("代笔我的消息") },
        QuickAction(Icons.Filled.EditNote, "剧情推进", runtimeReady) { onUnavailableAction("剧情推进") }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { item ->
            PrototypeAssistPill(
                text = item.text,
                icon = item.icon,
                onClick = item.onClick,
                modifier = Modifier,
                enabled = item.enabled
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    isGenerating: Boolean,
    runtimeReady: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onVoiceInput: () -> Unit,
    onAttachmentAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    var showAttach by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showAttach) {
            AttachSheet(onAction = onAttachmentAction)
        }
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = { showAttach = !showAttach }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (showAttach) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = "附件",
                        tint = if (showAttach) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = if (runtimeReady) "发条消息，或 /? 查看指令" else "正在等待运行时…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.SentimentSatisfied,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    enabled = runtimeReady && !isGenerating,
                    maxLines = 5,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val msg = text.trim()
                            if (msg.isNotEmpty() && runtimeReady && !isGenerating) {
                                onSend(msg)
                                text = ""
                            }
                        }
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                ChatSendButton(
                    text = text,
                    isGenerating = isGenerating,
                    runtimeReady = runtimeReady,
                    onStop = onStop,
                    onVoiceInput = onVoiceInput,
                    onSend = {
                        val msg = text.trim()
                        if (msg.isNotEmpty()) {
                            onSend(msg)
                            text = ""
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatSendButton(
    text: String,
    isGenerating: Boolean,
    runtimeReady: Boolean,
    onStop: () -> Unit,
    onVoiceInput: () -> Unit,
    onSend: () -> Unit
) {
    when {
        isGenerating -> FilledIconButton(
            onClick = onStop,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Icon(Icons.Filled.Close, contentDescription = "停止生成")
        }

        text.isBlank() -> IconButton(
            onClick = onVoiceInput,
            enabled = runtimeReady,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "语音输入",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        else -> FilledIconButton(
            onClick = onSend,
            enabled = runtimeReady,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
        }
    }
}

@Composable
private fun AttachSheet(
    onAction: (String) -> Unit
) {
    val items = listOf(
        Icons.Filled.AttachFile to "附件",
        Icons.Filled.Image to "图片",
        Icons.Filled.Palette to "生成图",
        Icons.AutoMirrored.Filled.VolumeUp to "朗读",
        Icons.Filled.Translate to "翻译",
        Icons.Filled.RecordVoiceOver to "代笔",
        Icons.AutoMirrored.Filled.StickyNote2 to "作者注",
        Icons.Filled.Lightbulb to "思考"
    )
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            items.chunked(4).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (icon, label) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                onClick = { onAction(label) },
                                modifier = Modifier.size(48.dp),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon, contentDescription = null)
                                }
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenMessageBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Filled.VisibilityOff,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "已隐藏",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupChatHistorySheet(
    port: Int,
    groupId: String,
    currentChatFile: String,
    onDismiss: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var group by remember { mutableStateOf<GroupSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(groupId) {
        loading = true
        error = null
        val client = TavernCoreClient(baseUrl = "http://127.0.0.1:$port/")
        runCatching {
            client.listGroups().find { it.id == groupId }
        }.onSuccess { found ->
            group = found
            if (found == null) error = "未找到此群聊"
        }.onFailure { e ->
            error = e.message ?: "加载群聊信息失败"
        }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "群聊历史对话",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                group?.let { g ->
                    Text(
                        text = "· ${g.chats.size} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                error != null -> Text(
                    text = error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                group != null && group!!.chats.isEmpty() -> Text(
                    text = "暂无历史对话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                group != null -> {
                    val chats = group!!.chats
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(chats, key = { it }) { chatId ->
                            val isCurrent = normalizeChatFile(chatId) == normalizeChatFile(currentChatFile)
                            Surface(
                                onClick = { if (!isCurrent) onOpenChat(chatId) },
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = chatId,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    trailingContent = if (isCurrent) {
                                        {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ) {
                                                Text(
                                                    text = "当前",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    } else null,
                                    leadingContent = {
                                        Icon(
                                            Icons.Filled.Forum,
                                            contentDescription = null,
                                            tint = if (isCurrent) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorsNoteDialog(
    currentText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by rememberSaveable(currentText) { mutableStateOf(currentText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("作者注") },
        text = {
            Column {
                Text(
                    text = "作者注会被插入到 AI 上下文中，影响角色的行为和回复风格。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入作者注…") },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
