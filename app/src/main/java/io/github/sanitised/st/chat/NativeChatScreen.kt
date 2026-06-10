package io.github.sanitised.st.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.chat.engine.ChatEngine
import io.github.sanitised.st.api.GroupSummary
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.api.WorldInfoSummary
import io.github.sanitised.st.ui.prototype.PrototypeAssistPill
import io.github.sanitised.st.ui.prototype.PrototypeAvatar
import io.github.sanitised.st.ui.prototype.PrototypeGroupAvatar
import io.github.sanitised.st.ui.webview.WebViewTarget
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeChatScreen(
    status: NodeStatus,
    target: WebViewTarget,
    store: ChatStore,
    bridge: ChatRuntimeBridge,
    engine: ChatEngine,
    nativeChatLoadingEnabled: Boolean = false,
    nativeChatLoader: NativeChatLoader? = null,
    nativeChatRuntime: NativeChatRuntime? = null,
    onBackToHome: () -> Unit,
    onOpenPastChats: (() -> Unit)? = null,
    onShowMessage: (String) -> Unit,
    settingsDirty: Boolean = false,
    onSettingsConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessageId by rememberSaveable { mutableStateOf(-1) }
    var editText by rememberSaveable { mutableStateOf("") }
    var deletingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showGroupChatsSheet by remember { mutableStateOf(false) }
    var showAuthorsNoteDialog by remember { mutableStateOf(false) }
    var showCfgDialog by remember { mutableStateOf(false) }
    var showWorldInfoSheet by remember { mutableStateOf(false) }
    var checkpointMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var branchListMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showItemizedSheet by remember { mutableStateOf(false) }
    var showDataBankSheet by remember { mutableStateOf(false) }

    fun uploadAttachment(uri: Uri, isMedia: Boolean) {
        scope.launch {
            uploadPickedAttachment(context, status.port, uri, isMedia)
                .onSuccess { attachment ->
                    store.addPendingAttachment(attachment)
                    onShowMessage("已添加 ${attachment.name}")
                }
                .onFailure { error ->
                    onShowMessage(error.message ?: "附件上传失败")
                }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadAttachment(it, false) }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadAttachment(it, true) }
    }

    LaunchedEffect(status.state) {
        if (status.state != NodeState.RUNNING) {
            bridge.markRuntimeLoading()
        }
    }

    // When the native settings UI changed the API/model, the persistent runtime still
    // holds stale in-memory settings. Reload settings.json (+ reconnect) once the
    // runtime is ready, before opening/refreshing the chat.
    LaunchedEffect(store.runtimeState, settingsDirty) {
        if (settingsDirty && store.runtimeState == RuntimeState.READY) {
            bridge.reloadSettings()
            onSettingsConsumed()
        }
    }

    val nativeTargetKey = if (nativeChatLoadingEnabled && status.state == NodeState.RUNNING) {
        readyTargetCommandKey(target)
    } else {
        null
    }
    LaunchedEffect(nativeTargetKey) {
        if (nativeTargetKey == null) return@LaunchedEffect
        if (target is WebViewTarget.CharacterChat) {
            runCatching {
                nativeChatLoader?.openCharacter(target.avatar, target.chatFile) ?: false
            }.onFailure { error ->
                store.recordCommandError(error.message ?: "原生加载聊天失败")
            }.getOrDefault(false)
        }
    }

    val readyTargetKey = if (store.runtimeState == RuntimeState.READY) readyTargetCommandKey(target) else null
    LaunchedEffect(readyTargetKey) {
        if (readyTargetKey == null) return@LaunchedEffect
        // The runtime WebView is persistent (hosted above the NavHost), so runtime.ready
        // fires only once. Re-trigger a best-effort connect on chat entry so an API that
        // was configured after the runtime first loaded still gets connected.
        bridge.connect(auto = true)
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
    val chatBaseUrl = remember(status.port) { "http://127.0.0.1:${status.port}" }
    val visibleAvatarUrl = if (targetMatched) {
        store.avatarUrl
    } else {
        (target as? WebViewTarget.CharacterChat)?.avatar.orEmpty()
    }
    val nativeReadyForTarget = nativeChatLoadingEnabled && targetMatched
    val readyForTarget = (store.runtimeState == RuntimeState.READY || nativeReadyForTarget) && targetMatched
    val isGroupMode = store.mode == "group"
    val nativeSingleChatRuntime = NativeChatUiRouting.selectNativeSingleChatRuntime(
        nativeChatRuntime = nativeChatRuntime,
        nativeChatLoadingEnabled = nativeChatLoadingEnabled,
        targetMatched = targetMatched,
        isGroupMode = isGroupMode,
    )

    fun launchNativeAction(
        fallback: () -> Unit,
        successMessage: String,
        action: suspend NativeChatRuntime.() -> Unit,
    ) {
        val runtime = nativeSingleChatRuntime
        if (runtime == null) {
            runAlignedBridgeWrite(reload = bridge::reloadChat, write = fallback)
            if (successMessage.isNotBlank()) onShowMessage(successMessage)
            return
        }
        scope.launch {
            runCatching { runtime.action() }
                .onSuccess {
                    if (successMessage.isNotBlank()) onShowMessage(successMessage)
                }
                .onFailure { error ->
                    onShowMessage(error.message ?: "原生聊天操作失败")
                }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // The runtime WebView is hosted persistently in MainActivity (outside the
        // NavHost) so it survives tab navigation and is not reloaded on every chat
        // entry. This screen is now pure native UI driven by ChatStore.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            ChatHeader(
                characterName = visibleCharacterName,
                avatarUrl = visibleAvatarUrl,
                baseUrl = chatBaseUrl,
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
                onOpenCfg = { showCfgDialog = true },
                onOpenWorldInfo = { showWorldInfoSheet = true },
                onOpenDataBank = {
                    bridge.loadDataBank()
                    showDataBankSheet = true
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
                        assistantAvatarUrl = visibleAvatarUrl,
                        baseUrl = chatBaseUrl,
                        port = status.port,
                        editingMessageId = editingMessageId,
                        editText = editText,
                        onEditTextChange = { editText = it },
                        onSwipePrevious = { messageId ->
                            launchNativeAction(
                                fallback = { bridge.swipePrevious(messageId) },
                                successMessage = "",
                            ) {
                                swipePrevious(messageId)
                            }
                        },
                        onSwipeNext = { messageId ->
                            launchNativeAction(
                                fallback = { bridge.swipeNext(messageId) },
                                successMessage = "",
                            ) {
                                swipeNext(messageId)
                            }
                        },
                        onRegenerate = { engine.regenerate() },
                        onContinue = { engine.continueGeneration() },
                        onLongPress = { message -> selectedMessage = message },
                        onSaveEdit = { messageId ->
                            val text = editText
                            launchNativeAction(
                                fallback = {
                                    bridge.editMessage(messageId, text)
                                    editingMessageId = -1
                                },
                                successMessage = "消息已保存",
                            ) {
                                editMessage(messageId, text)
                                editingMessageId = -1
                            }
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
                    onContinue = { engine.continueGeneration() },
                    onRegenerate = { engine.regenerate() },
                    onUnavailableAction = { label -> onShowMessage("$label 功能暂未接入原生聊天运行时") }
                )
                QuickReplyStrip(
                    items = store.quickReplies,
                    enabled = readyForTarget && !store.isGenerating,
                    onExecute = { item -> bridge.executeQuickReply(item.setName, item.label) }
                )
            }

            ChatInputBar(
                isGenerating = store.isGenerating,
                runtimeReady = readyForTarget,
                pendingAttachments = store.pendingAttachments,
                onSend = { text -> engine.send(text) },
                onStop = { engine.stop() },
                onVoiceInput = { onShowMessage("语音输入暂未接入") },
                onRemovePendingAttachment = { attachment -> store.removePendingAttachment(attachment) },
                onAttachmentAction = { label ->
                    when (label) {
                        "附件" -> filePicker.launch("*/*")
                        "图片" -> imagePicker.launch("image/*")
                        "作者注" -> showAuthorsNoteDialog = true
                        else -> onShowMessage("$label 功能暂未接入原生聊天运行时")
                    }
                }
            )
        }

        RuntimeToastHost(
            toast = store.latestToast,
            onDismiss = { store.clearToast() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )
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
                engine.regenerate()
                selectedMessage = null
            },
            onHideToggle = {
                if (message.isSystem) {
                    launchNativeAction(
                        fallback = { bridge.unhideMessage(message.id) },
                        successMessage = "消息已取消隐藏",
                    ) {
                        setMessageHidden(message.id, false)
                    }
                } else {
                    launchNativeAction(
                        fallback = { bridge.hideMessage(message.id) },
                        successMessage = "消息已隐藏（不会被 AI 看到）",
                    ) {
                        setMessageHidden(message.id, true)
                    }
                }
                selectedMessage = null
            },
            onCreateCheckpoint = {
                checkpointMessage = message
                selectedMessage = null
            },
            onCreateBranch = {
                launchNativeAction(
                    fallback = { bridge.createBranch(message.id) },
                    successMessage = "",
                ) {
                    val name = createBranch(message.id)
                    onShowMessage("已创建并打开分支 $name")
                }
                selectedMessage = null
            },
            onViewBranches = {
                branchListMessage = message
                selectedMessage = null
            },
            onItemizedPrompt = {
                bridge.loadItemizedPrompt(message.id)
                showItemizedSheet = true
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
                launchNativeAction(
                    fallback = { bridge.deleteMessageFromChat(message.id) },
                    successMessage = "消息已删除",
                ) {
                    deleteMessage(message.id)
                }
                deletingMessage = null
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

    if (showCfgDialog) {
        CfgScaleDialog(
            scale = store.cfgScale,
            negativePrompt = store.cfgNegativePrompt,
            positivePrompt = store.cfgPositivePrompt,
            onDismiss = { showCfgDialog = false },
            onSave = { scale, negativePrompt, positivePrompt ->
                bridge.setCfg(scale, negativePrompt, positivePrompt)
                showCfgDialog = false
                onShowMessage("CFG 引导已保存")
            }
        )
    }

    if (showWorldInfoSheet) {
        WorldInfoSheet(
            port = status.port,
            currentWorldInfoName = store.worldInfoName,
            onDismiss = { showWorldInfoSheet = false }
        )
    }

    if (checkpointMessage != null) {
        val message = checkpointMessage!!
        CheckpointDialog(
            onDismiss = { checkpointMessage = null },
            onConfirm = { name ->
                launchNativeAction(
                    fallback = { bridge.createCheckpoint(message.id, name.ifBlank { null }) },
                    successMessage = "",
                ) {
                    val created = createCheckpoint(message.id, name)
                    onShowMessage("已创建存档点 $created")
                }
                checkpointMessage = null
            }
        )
    }

    if (branchListMessage != null) {
        val message = branchListMessage!!
        BranchListSheet(
            bookmarkLink = message.bookmarkLink,
            branches = message.branches,
            onDismiss = { branchListMessage = null },
            onOpen = { name ->
                branchListMessage = null
                launchNativeAction(
                    fallback = { bridge.openCheckpoint(name) },
                    successMessage = "已打开 $name",
                ) {
                    openChat(name)
                }
            }
        )
    }

    if (showItemizedSheet) {
        ItemizedPromptSheet(
            prompt = store.itemizedPrompt,
            loading = store.itemizedPromptLoading,
            error = store.itemizedPromptError,
            onDismiss = {
                showItemizedSheet = false
                store.clearItemizedPrompt()
            }
        )
    }

    if (showDataBankSheet) {
        DataBankSheet(
            attachments = store.dataBank,
            loading = store.dataBankLoading,
            port = status.port,
            onDismiss = {
                showDataBankSheet = false
                store.clearDataBank()
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

internal fun attachmentDisplayUrl(port: Int, path: String): String {
    val trimmed = path.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    val normalized = trimmed.removePrefix("/")
    return "http://127.0.0.1:$port/$normalized"
}

internal fun attachmentSizeLabel(size: Long): String {
    if (size <= 0L) return "0 B"
    if (size < 1024L) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}

@Composable
private fun ChatHeader(
    characterName: String,
    avatarUrl: String,
    baseUrl: String,
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
    onOpenCfg: () -> Unit,
    onOpenWorldInfo: () -> Unit,
    onOpenDataBank: () -> Unit,
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
                        imageUrls = listOf(avatarUrl),
                        baseUrl = baseUrl,
                        size = 36.dp
                    )
                } else {
                    PrototypeAvatar(
                        label = characterName.ifBlank { "?" },
                        imageUrl = avatarUrl,
                        baseUrl = baseUrl,
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
                        text = { Text("CFG 引导") },
                        leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { menuExpanded = false; onOpenCfg() }
                    )
                    DropdownMenuItem(
                        text = { Text("世界书") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { menuExpanded = false; onOpenWorldInfo() }
                    )
                    DropdownMenuItem(
                        text = { Text("数据银行") },
                        leadingIcon = { Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { menuExpanded = false; onOpenDataBank() }
                    )
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
    assistantAvatarUrl: String,
    baseUrl: String,
    port: Int,
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
                    assistantAvatarUrl = assistantAvatarUrl,
                    baseUrl = baseUrl,
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
                    assistantAvatarUrl = assistantAvatarUrl,
                    baseUrl = baseUrl,
                    port = port,
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
    assistantAvatarUrl: String,
    baseUrl: String,
    port: Int,
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
    val isToolMessage = message.toolInvocations.isNotEmpty()
    val showHiddenStyle = message.isSystem && !isToolMessage
    val hiddenAlpha = if (showHiddenStyle) 0.5f else 1f

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
                if (showHiddenStyle) {
                    HiddenMessageBadge(modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(text = message.mes, style = MaterialTheme.typography.bodyMedium, color = textColor)
                MessageAttachments(
                    media = message.mediaAttachments,
                    files = message.fileAttachments,
                    port = port,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val messageAvatarUrl = message.extra.optString("force_avatar").ifBlank { assistantAvatarUrl }
                PrototypeAvatar(
                    label = message.name.ifBlank { characterName },
                    imageUrl = messageAvatarUrl,
                    baseUrl = baseUrl,
                    size = 36.dp
                )
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
                        if (showHiddenStyle) {
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
                        if (isToolMessage) {
                            ToolCallGroup(tools = message.toolInvocations)
                        } else {
                            message.reasoning?.let { reasoning ->
                                ReasoningSection(
                                    reasoning = reasoning,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            Text(text = message.mes, style = MaterialTheme.typography.bodyMedium, color = textColor)
                        }
                        MessageAttachments(
                            media = message.mediaAttachments,
                            files = message.fileAttachments,
                            port = port,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        BubbleMeta(
                            hasBookmark = message.bookmarkLink != null,
                            branchCount = message.branches.size,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 4.dp)
                        )
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
private fun MessageAttachments(
    media: List<MediaAttachment>,
    files: List<FileAttachment>,
    port: Int,
    modifier: Modifier = Modifier
) {
    if (media.isEmpty() && files.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        media.forEach { item ->
            AsyncImage(
                model = attachmentDisplayUrl(port, item.url),
                contentDescription = item.title.ifBlank { "图片附件" },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        files.forEach { item ->
            MessageFileCard(
                file = item,
                displayUrl = attachmentDisplayUrl(port, item.url)
            )
        }
    }
}

@Composable
private fun MessageFileCard(
    file: FileAttachment,
    displayUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(displayUrl)))
            }
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name.ifBlank { file.url.substringAfterLast('/') },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = attachmentSizeLabel(file.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReasoningSection(
    reasoning: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "思考过程",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun BubbleMeta(
    hasBookmark: Boolean,
    branchCount: Int,
    modifier: Modifier = Modifier
) {
    if (!hasBookmark && branchCount <= 0) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (hasBookmark) {
            Icon(
                Icons.Filled.Bookmark,
                contentDescription = "存档点",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        if (branchCount > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        Icons.Filled.AccountTree,
                        contentDescription = "分支",
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = branchCount.toString(),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCallGroup(tools: List<ToolInvocation>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.forEachIndexed { index, tool ->
            ToolCallCard(tool = tool, defaultExpanded = index == 0)
        }
    }
}

@Composable
private fun ToolCallCard(
    tool: ToolInvocation,
    defaultExpanded: Boolean
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    val running = tool.result.isBlank()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Build,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = tool.displayName.ifBlank { tool.name }.ifBlank { "工具调用" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (expanded && tool.parameters.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "参数",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = tool.parameters,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "结果",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (running) "执行中…" else tool.result,
                style = MaterialTheme.typography.bodySmall,
                color = if (running) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
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
    assistantAvatarUrl: String,
    baseUrl: String,
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
                val messageAvatarUrl = message.extra.optString("force_avatar").ifBlank { assistantAvatarUrl }
                PrototypeAvatar(
                    label = message.name.ifBlank { characterName },
                    imageUrl = messageAvatarUrl,
                    baseUrl = baseUrl,
                    size = 36.dp
                )
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
    onCreateCheckpoint: () -> Unit,
    onCreateBranch: () -> Unit,
    onViewBranches: () -> Unit,
    onItemizedPrompt: () -> Unit,
    onDelete: () -> Unit,
    onUnavailableAction: (String) -> Unit
) {
    val branchCount = message.branches.size
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
                onClick = onCreateCheckpoint,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                ListItem(
                    headlineContent = { Text("创建存档点") },
                    supportingContent = {
                        Text("为当前消息保存一个快照", style = MaterialTheme.typography.bodySmall)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.BookmarkAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )
            }

            Surface(
                onClick = onCreateBranch,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                ListItem(
                    headlineContent = { Text("创建分支") },
                    supportingContent = {
                        Text("从此消息开启新的聊天线", style = MaterialTheme.typography.bodySmall)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.AccountTree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )
            }

            if (branchCount > 0) {
                Surface(
                    onClick = onViewBranches,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    ListItem(
                        headlineContent = { Text("查看分支") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        trailingContent = {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Text(
                                    text = branchCount.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    )
                }
            }

            if (!message.isUser) {
                Surface(
                    onClick = onItemizedPrompt,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    ListItem(
                        headlineContent = { Text("提示词分析") },
                        supportingContent = {
                            Text("查看本条生成的 token 构成", style = MaterialTheme.typography.bodySmall)
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Analytics,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    )
                }
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
private fun RuntimeToastHost(
    toast: RuntimeToast?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (toast == null) return
    LaunchedEffect(toast.seq) {
        delay(4000)
        onDismiss()
    }
    val container: androidx.compose.ui.graphics.Color
    val content: androidx.compose.ui.graphics.Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    when (toast.type) {
        "error" -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
            icon = Icons.Filled.Error
        }
        "warning" -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            content = MaterialTheme.colorScheme.onTertiaryContainer
            icon = Icons.Filled.Warning
        }
        "success" -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
            icon = Icons.Filled.CheckCircle
        }
        else -> {
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
            icon = Icons.Filled.Info
        }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = content,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (toast.title.isNotBlank()) {
                    Text(
                        text = toast.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (toast.message.isNotBlank()) {
                    Text(
                        text = toast.message,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
private fun QuickReplyStrip(
    items: List<QuickReplyItem>,
    enabled: Boolean,
    onExecute: (QuickReplyItem) -> Unit
) {
    if (items.isEmpty()) return
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    AssistChip(
                        onClick = { if (enabled) onExecute(item) },
                        enabled = enabled,
                        label = {
                            Text(
                                text = item.label.ifBlank { item.message.take(12) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors()
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    isGenerating: Boolean,
    runtimeReady: Boolean,
    pendingAttachments: List<PendingAttachment>,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onVoiceInput: () -> Unit,
    onRemovePendingAttachment: (PendingAttachment) -> Unit,
    onAttachmentAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    var showAttach by rememberSaveable { mutableStateOf(false) }
    val hasPendingAttachments = pendingAttachments.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        if (showAttach) {
            AttachSheet(onAction = onAttachmentAction)
        }
        PendingAttachmentStrip(
            attachments = pendingAttachments,
            onRemove = onRemovePendingAttachment
        )
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
                            val msg = text.trim().ifBlank { "[附件]" }
                            if ((text.isNotBlank() || hasPendingAttachments) && runtimeReady && !isGenerating) {
                                onSend(msg)
                                text = ""
                            }
                        }
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                ChatSendButton(
                    text = text,
                    hasPendingAttachments = hasPendingAttachments,
                    isGenerating = isGenerating,
                    runtimeReady = runtimeReady,
                    onStop = onStop,
                    onVoiceInput = onVoiceInput,
                    onSend = {
                        val msg = text.trim().ifBlank { "[附件]" }
                        if (text.isNotBlank() || hasPendingAttachments) {
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
    hasPendingAttachments: Boolean,
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

        text.isBlank() && !hasPendingAttachments -> IconButton(
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
private fun PendingAttachmentStrip(
    attachments: List<PendingAttachment>,
    onRemove: (PendingAttachment) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { attachment ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .padding(start = 10.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (attachment.isMedia) Icons.Filled.Image else Icons.Filled.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = attachment.name.ifBlank { attachment.url.substringAfterLast('/') },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = attachmentSizeLabel(attachment.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onRemove(attachment) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "移除附件", modifier = Modifier.size(16.dp))
                    }
                }
            }
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

private data class PickedAttachmentFile(
    val name: String,
    val size: Long,
    val bytes: ByteArray
)

private suspend fun uploadPickedAttachment(
    context: Context,
    port: Int,
    uri: Uri,
    isMedia: Boolean
): Result<PendingAttachment> = runCatching {
    if (port <= 0) error("SillyTavern 服务尚未运行")
    val file = withContext(Dispatchers.IO) { readPickedAttachmentFile(context, uri) }
    val base64 = Base64.encodeToString(file.bytes, Base64.NO_WRAP)
    val uploadedPath = TavernCoreClient(baseUrl = "http://127.0.0.1:$port/")
        .uploadFile(name = file.name, base64Data = base64)
    PendingAttachment(
        url = uploadedPath,
        name = file.name,
        size = file.size,
        isMedia = isMedia
    )
}

private fun readPickedAttachmentFile(context: Context, uri: Uri): PickedAttachmentFile {
    val resolver = context.contentResolver
    var name = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.ifBlank { null }
        ?: "attachment"
    var size = 0L

    resolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                name = cursor.getString(nameIndex).orEmpty().ifBlank { name }
            }
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0) {
                size = cursor.getLong(sizeIndex)
            }
        }
    }

    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("无法读取附件内容")
    if (size <= 0L) size = bytes.size.toLong()
    return PickedAttachmentFile(name = name, size = size, bytes = bytes)
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
private fun CfgScaleDialog(
    scale: Float,
    negativePrompt: String,
    positivePrompt: String,
    onDismiss: () -> Unit,
    onSave: (Float, String, String) -> Unit
) {
    var localScale by rememberSaveable(scale) { mutableStateOf(scale.coerceIn(1.0f, 3.0f)) }
    var localNegative by rememberSaveable(negativePrompt) { mutableStateOf(negativePrompt) }
    var localPositive by rememberSaveable(positivePrompt) { mutableStateOf(positivePrompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CFG 引导") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Scale",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f", localScale),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = localScale,
                    onValueChange = { localScale = it },
                    valueRange = 1.0f..3.0f,
                    steps = 19
                )
                OutlinedTextField(
                    value = localNegative,
                    onValueChange = { localNegative = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("负面提示词") },
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = localPositive,
                    onValueChange = { localPositive = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("正面提示词") },
                    minLines = 2,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(localScale, localNegative, localPositive) }) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldInfoSheet(
    port: Int,
    currentWorldInfoName: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var worlds by remember { mutableStateOf<List<WorldInfoSummary>>(emptyList()) }
    var expandedName by remember { mutableStateOf<String?>(null) }
    var expandedBook by remember { mutableStateOf<WorldInfoBook?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingBookName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(port) {
        loading = true
        error = null
        runCatching {
            if (port <= 0) throw IllegalStateException("SillyTavern 服务尚未运行")
            TavernCoreClient(baseUrl = "http://127.0.0.1:$port/").listWorldInfos()
        }.onSuccess { list ->
            worlds = list
        }.onFailure { e ->
            error = e.message ?: "加载世界书失败"
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
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "世界书",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (currentWorldInfoName.isNotBlank()) {
                    Text(
                        text = "· 当前 $currentWorldInfoName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                worlds.isEmpty() -> Text(
                    text = "暂无世界书",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(worlds, key = { it.name }) { world ->
                        val isCurrent = currentWorldInfoName == world.name || currentWorldInfoName == world.id
                        val isExpanded = expandedName == world.name
                        Surface(
                            onClick = {
                                if (isExpanded) {
                                    expandedName = null
                                    expandedBook = null
                                } else {
                                    expandedName = world.name
                                    expandedBook = null
                                    loadingBookName = world.name
                                    scope.launch {
                                        runCatching {
                                            TavernCoreClient(baseUrl = "http://127.0.0.1:$port/")
                                                .getWorldInfo(world.name)
                                        }.onSuccess { book ->
                                            expandedBook = book
                                        }.onFailure { e ->
                                            error = e.message ?: "加载世界书条目失败"
                                        }
                                        loadingBookName = null
                                    }
                                }
                            },
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = world.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    supportingContent = if (world.id != world.name && world.id.isNotBlank()) {
                                        { Text(world.id, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    } else null,
                                    leadingContent = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            tint = if (isCurrent) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isCurrent) {
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
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                                if (isExpanded) {
                                    when {
                                        loadingBookName == world.name -> Box(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                        }
                                        expandedBook != null -> WorldInfoEntriesPreview(expandedBook!!)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldInfoEntriesPreview(book: WorldInfoBook) {
    Column(
        modifier = Modifier.padding(start = 52.dp, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (book.entries.isEmpty()) {
            Text(
                text = "没有条目",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            book.entries.take(12).forEach { entry ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            text = entry.keys.joinToString(", ").ifBlank { "无关键词" },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = entry.comment.ifBlank { "无备注" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckpointDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.BookmarkAdd, contentDescription = null) },
        title = { Text("创建存档点") },
        text = {
            Column {
                Text(
                    text = "为当前消息创建一个聊天存档快照，之后可随时回到这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("存档点名称（留空自动命名）") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchListSheet(
    bookmarkLink: String?,
    branches: List<String>,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
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
                    Icons.Filled.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "分支列表",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "点击任意分支或存档点以打开对应聊天线",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (bookmarkLink != null) {
                    item(key = "checkpoint:$bookmarkLink") {
                        BranchRow(
                            name = bookmarkLink,
                            isBookmark = true,
                            onClick = { onOpen(bookmarkLink) }
                        )
                    }
                }
                items(branches, key = { "branch:$it" }) { branch ->
                    BranchRow(
                        name = branch,
                        isBookmark = false,
                        onClick = { onOpen(branch) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BranchRow(
    name: String,
    isBookmark: Boolean,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        ListItem(
            headlineContent = {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = if (isBookmark) {
                { Text("存档点", style = MaterialTheme.typography.bodySmall) }
            } else {
                { Text("分支", style = MaterialTheme.typography.bodySmall) }
            },
            leadingContent = {
                Icon(
                    if (isBookmark) Icons.Filled.Bookmark else Icons.Filled.AccountTree,
                    contentDescription = null,
                    tint = if (isBookmark) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemizedPromptSheet(
    prompt: ItemizedPrompt?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
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
                    Icons.Filled.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "提示词分析",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (prompt != null) {
                    Text(
                        text = "· 消息 #${prompt.mesId}",
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
                ) { CircularProgressIndicator() }
                error != null -> Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                prompt != null -> {
                    val maxTokens = prompt.components.maxOfOrNull { it.tokens }?.coerceAtLeast(1) ?: 1
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item(key = "total") {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = prompt.total.toString(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "总 token 数",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                        }
                        items(prompt.components, key = { it.name }) { comp ->
                            ItemizedComponentRow(
                                name = comp.name,
                                tokens = comp.tokens,
                                fraction = comp.tokens.toFloat() / maxTokens
                            )
                        }
                        item(key = "meta") {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                                if (prompt.presetName.isNotBlank() || prompt.modelUsed.isNotBlank()) {
                                    Text(
                                        text = "预设 ${prompt.presetName.ifBlank { "—" }} · 模型 ${prompt.modelUsed.ifBlank { "—" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (prompt.apiUsed.isNotBlank() || prompt.tokenizer.isNotBlank()) {
                                    Text(
                                        text = "API ${prompt.apiUsed.ifBlank { "—" }} · 分词器 ${prompt.tokenizer.ifBlank { "—" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemizedComponentRow(
    name: String,
    tokens: Int,
    fraction: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(72.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            text = tokens.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataBankSheet(
    attachments: DataBankAttachments?,
    loading: Boolean,
    port: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("全局", "角色", "当前聊天")
    val current = when (tab) {
        0 -> attachments?.global
        1 -> attachments?.character
        else -> attachments?.chat
    } ?: emptyList()

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
                    Icons.Filled.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "数据银行",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, label ->
                    val count = when (index) {
                        0 -> attachments?.global?.size ?: 0
                        1 -> attachments?.character?.size ?: 0
                        else -> attachments?.chat?.size ?: 0
                    }
                    AssistChip(
                        onClick = { tab = index },
                        label = { Text(if (count > 0) "$label ($count)" else label) },
                        colors = if (tab == index) {
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            AssistChipDefaults.assistChipColors()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                current.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "这个范围还没有文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(current, key = { it.url }) { file ->
                        Surface(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(attachmentDisplayUrl(port, file.url)))
                                    )
                                }
                            },
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        file.name.ifBlank { file.url.substringAfterLast('/') },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        attachmentSizeLabel(file.size),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
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
