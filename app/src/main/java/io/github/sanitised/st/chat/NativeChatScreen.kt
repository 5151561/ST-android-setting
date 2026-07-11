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
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.chat.engine.ChatEngine
import io.github.sanitised.st.api.GroupSummary
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.api.WorldInfoBook
import io.github.sanitised.st.api.WorldInfoSummary
import io.github.sanitised.st.ui.screens.STAssistPill
import io.github.sanitised.st.ui.screens.STAvatar
import io.github.sanitised.st.ui.screens.STGroupAvatar
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeChatScreen(
    status: NodeStatus,
    target: ChatTarget,
    store: ChatStore,
    engine: ChatEngine,
    nativeChatLoadingEnabled: Boolean = true,
    nativeChatLoader: NativeChatLoader? = null,
    nativeChatRuntime: NativeChatRuntime? = null,
    quickReplyDataRoot: File? = null,
    itemizedPromptStore: ItemizedPromptStore = ItemizedPromptStore.Global,
    onBackToHome: () -> Unit,
    onOpenPastChats: (() -> Unit)? = null,
    onShowMessage: (String) -> Unit,
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
    var quickReplyDraftToken by remember { mutableStateOf(0) }
    var quickReplyDraftText by remember { mutableStateOf("") }

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
            store.markRuntimeUnavailable("服务未运行")
        } else if (store.runtimeState == RuntimeState.NOT_READY) {
            // 服务就绪后清掉滞留的"服务未运行",否则该错误会永远占据副标题和加载页。
            store.clearRuntimeError()
        }
    }

    val nativeTargetKey = if (nativeChatLoadingEnabled && status.state == NodeState.RUNNING) {
        readyTargetCommandKey(target)
    } else {
        null
    }
    LaunchedEffect(nativeTargetKey) {
        if (nativeTargetKey == null) return@LaunchedEffect
        if (target is ChatTarget.CharacterChat) {
            // 服务刚上报就绪时首个请求仍可能瞬时失败,重试兜底,避免一次失败后永久卡住。
            var lastError: Throwable? = null
            repeat(3) { attempt ->
                val result = runCatching {
                    nativeChatLoader?.openCharacter(target.avatar, target.chatFile) ?: false
                }
                when {
                    result.getOrDefault(false) -> return@LaunchedEffect
                    result.isSuccess -> {
                        // 打开成功但没有可加载的聊天文件。未指定聊天文件说明角色从未聊过
                        // (或角色卡 chat 字段指向的文件已不存在),对齐上游行为直接建新聊天;
                        // 指定了聊天文件则明确报错,两种情况都不能静默滞留在加载页。
                        if (target.chatFile.isNullOrBlank()) {
                            val created = runCatching { nativeChatRuntime?.createNewChat(target.avatar) }
                            if (!created.getOrNull().isNullOrBlank()) return@LaunchedEffect
                            lastError = created.exceptionOrNull() ?: IllegalStateException("无法为该角色创建新聊天")
                        } else {
                            store.recordCommandError("聊天文件 ${target.chatFile} 不存在或为空")
                            return@LaunchedEffect
                        }
                    }
                    else -> lastError = result.exceptionOrNull()
                }
                delay(1000L * (attempt + 1))
            }
            store.recordCommandError(lastError?.message ?: "原生加载聊天失败")
        }
    }

    val targetMatched = targetMatchesStore(target, store)
    LaunchedEffect(quickReplyDataRoot, status.state, targetMatched, store.chatFile, store.avatarUrl, store.chatQuickReplyConfig) {
        val root = quickReplyDataRoot ?: return@LaunchedEffect
        if (status.state != NodeState.RUNNING || !targetMatched) return@LaunchedEffect
        val replies = withContext(Dispatchers.IO) {
            runCatching {
                QuickReplyRuntime.visibleReplies(
                    dataRoot = root,
                    chatMetadata = store.chatQuickReplyConfig,
                    characterAvatar = store.avatarUrl,
                )
            }.getOrDefault(emptyList())
        }
        store.setQuickReplies(replies)
    }

    val visibleCharacterName = if (targetMatched) {
        store.characterName.ifBlank { target.displayLabel() }
    } else {
        target.displayLabel()
    }
    val chatBaseUrl = remember(status.port) { "http://127.0.0.1:${status.port}" }
    val visibleAvatarUrl = if (targetMatched) {
        store.avatarUrl
    } else {
        (target as? ChatTarget.CharacterChat)?.avatar.orEmpty()
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
        successMessage: String,
        action: suspend NativeChatRuntime.() -> Unit,
    ) {
        val runtime = nativeSingleChatRuntime
        if (runtime == null) {
            onShowMessage("当前会话暂不支持该原生操作")
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
                    scope.launch {
                        val loaded = if (target is ChatTarget.CharacterChat) {
                            nativeChatLoader?.openCharacter(target.avatar, target.chatFile) == true
                        } else {
                            false
                        }
                        onShowMessage(if (loaded) "已重新加载当前聊天" else "当前聊天无法重新加载")
                    }
                },
                onOpenCfg = { showCfgDialog = true },
                onOpenWorldInfo = { showWorldInfoSheet = true },
                onOpenDataBank = {
                    showDataBankSheet = true
                    store.beginDataBankLoad()
                    scope.launch {
                        runCatching {
                            DataBankRepository {
                                TavernCoreClient("http://127.0.0.1:${status.port}")
                            }.load(store.avatarUrl, store.chatFile)
                        }.onSuccess { bank ->
                            store.applyDataBank(bank)
                        }.onFailure { error ->
                            store.applyDataBank(DataBankAttachments(emptyList(), emptyList(), emptyList()))
                            onShowMessage(error.message ?: "Data Bank 加载失败")
                        }
                    }
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
                    onRetry = { onShowMessage("保存重试已由原生保存流程自动处理") },
                    onDismiss = { store.clearSaveError() }
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
                                successMessage = "",
                            ) {
                                swipePrevious(messageId)
                            }
                        },
                        onSwipeNext = { messageId ->
                            launchNativeAction(
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
                    onExecute = { item ->
                        when (val result = QuickReplyRuntime.execute(item)) {
                            is QuickReplyExecution.Send -> engine.send(result.text)
                            is QuickReplyExecution.Draft -> {
                                quickReplyDraftText = result.text
                                quickReplyDraftToken += 1
                            }
                            is QuickReplyExecution.Unsupported -> onShowMessage(result.reason)
                        }
                    }
                )
            }

            ChatInputBar(
                isGenerating = store.isGenerating,
                runtimeReady = readyForTarget,
                pendingAttachments = store.pendingAttachments,
                injectedText = quickReplyDraftText,
                injectedTextToken = quickReplyDraftToken,
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
                            successMessage = "消息已取消隐藏",
                    ) {
                        setMessageHidden(message.id, false)
                    }
                } else {
                    launchNativeAction(
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
                store.applyItemizedPrompt(itemizedPromptStore.get(message.id))
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
            onOpenChat = {
                showGroupChatsSheet = false
                onShowMessage("群聊历史切换请从群聊详情页打开")
            }
        )
    }

    if (showAuthorsNoteDialog) {
        AuthorsNoteDialog(
            currentText = store.authorsNote,
            onDismiss = { showAuthorsNoteDialog = false },
            onSave = { text ->
                launchNativeAction(
                    successMessage = "作者注已保存",
                ) { setAuthorsNote(text) }
                showAuthorsNoteDialog = false
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
                launchNativeAction(
                    successMessage = "CFG 引导已保存",
                ) { setCfg(scale.toDouble(), negativePrompt, positivePrompt) }
                showCfgDialog = false
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

internal fun targetMatchesStore(target: ChatTarget, store: ChatStore): Boolean {
    return when (target) {
        ChatTarget.Current -> store.chatFile.isNotBlank() ||
            store.characterName.isNotBlank() ||
            store.messages.isNotEmpty()
        is ChatTarget.CharacterChat -> {
            val characterMatches = listOf(store.avatarUrl, store.characterName)
                .any { identifiersMatch(target.avatar, it) }
            val chatMatches = target.chatFile.isNullOrBlank() ||
                normalizeChatFile(target.chatFile) == normalizeChatFile(store.chatFile)
            characterMatches && chatMatches
        }
        is ChatTarget.GroupChat -> {
            val groupMatches = store.mode == "group" &&
                identifiersMatch(target.groupId, store.avatarUrl)
            val chatMatches = target.chatId.isNullOrBlank() ||
                normalizeChatFile(target.chatId) == normalizeChatFile(store.chatFile)
            groupMatches && chatMatches
        }
    }
}

internal fun ChatTarget.displayLabel(): String {
    return when (this) {
        ChatTarget.Current -> "对话"
        is ChatTarget.CharacterChat -> chatFile
            ?.takeIf { it.isNotBlank() }
            ?.substringBeforeLast(".jsonl")
            ?: avatar.substringAfterLast('/').substringBeforeLast('.').ifBlank { "角色聊天" }
        is ChatTarget.GroupChat -> chatId
            ?.takeIf { it.isNotBlank() }
            ?: groupId.ifBlank { "群聊" }
    }
}

internal fun identifiersMatch(expected: String, actual: String): Boolean {
    val left = normalizeIdentifier(expected)
    val right = normalizeIdentifier(actual)
    return left.isNotBlank() && right.isNotBlank() && left == right
}

internal fun normalizeIdentifier(value: String?): String {
    return value
        .orEmpty()
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .substringBeforeLast('.')
        .trim()
        .lowercase()
}

internal fun normalizeChatFile(value: String?): String {
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

