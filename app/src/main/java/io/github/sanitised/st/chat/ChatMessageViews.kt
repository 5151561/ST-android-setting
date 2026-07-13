@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

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
import androidx.compose.runtime.snapshotFlow
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
import io.github.sanitised.st.chat.ui.AssistantMessageControls
import io.github.sanitised.st.chat.ui.ChatBubbleSurface
import io.github.sanitised.st.chat.ui.ChatDateChip
import io.github.sanitised.st.chat.ui.ChatRichText
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

@Composable
internal fun ChatHeader(
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
                    STGroupAvatar(
                        initials = characterName.take(2).map { it.uppercase() }.ifEmpty { listOf("群") },
                        imageUrls = listOf(avatarUrl),
                        baseUrl = baseUrl,
                        size = 36.dp
                    )
                } else {
                    STAvatar(
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
internal fun ChatLoadingView(
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
internal fun MessageList(
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
    val imeInsets = WindowInsets.ime
    val visibleMessages = visibleChatMessages(messages)
    // 日期头只由首条消息的 send_date 决定;conversationDateLabel 每次调用都要
    // 新建多个 SimpleDateFormat,不能放在重组热路径上直接算。
    val firstSendDate = visibleMessages.firstOrNull()?.sendDate
    val dateLabel = remember(firstSendDate) { conversationDateLabel(visibleMessages) }

    // 滚动跟随统一走 snapshotFlow:组合期不读会变的状态,列表本体不再因
    // 滚动跟随而重组。新消息(条数变化)动画滚到底;流式输出只让最后一条变长,
    // 这时用非动画贴底——原实现把 mes 当 LaunchedEffect key,生成期间每个
    // 节流 tick 都重启一次滚动动画,动画互相打断造成持续卡顿。
    // 另外只有本来就停在底部附近时才跟随,用户上翻阅读旧消息不再被拽回。
    LaunchedEffect(messages, dateLabel) {
        var lastCount = -1
        snapshotFlow { messages.size to messages.lastOrNull()?.mes }
            .collect { (count, _) ->
                val target = chatListScrollTargetIndex(visibleChatMessages(messages), dateLabel)
                    ?: return@collect
                val countChanged = count != lastCount
                lastCount = count
                if (countChanged) {
                    listState.animateScrollToItem(target)
                } else {
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@collect
                    if (lastVisible >= info.totalItemsCount - 2) {
                        listState.scrollToItem(target)
                    }
                }
            }
    }
    // 键盘弹出/收起时保持贴底。原实现在组合期读 WindowInsets.ime,
    // 键盘动画每一帧都会让整个消息列表重组;snapshotFlow 在快照系统里观察,
    // 不触发任何重组。
    LaunchedEffect(listState, imeInsets, density) {
        snapshotFlow { imeInsets.getBottom(density) }
            .collect {
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0) listState.scrollToItem(total - 1)
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
                ChatDateChip(text = dateLabel)
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
internal fun MessageBubble(
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
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val isToolMessage = message.toolInvocations.isNotEmpty()
    val showHiddenStyle = message.isSystem && !isToolMessage
    val hiddenAlpha = if (showHiddenStyle) 0.5f else 1f

    Box(modifier = modifier.fillMaxWidth().alpha(hiddenAlpha), contentAlignment = alignment) {
        if (isUser) {
            ChatBubbleSurface(
                isUser = true,
                maxWidth = maxWidth,
                onLongPress = onLongPress
            ) {
                if (showHiddenStyle) {
                    HiddenMessageBadge(modifier = Modifier.padding(bottom = 4.dp))
                }
                ChatRichText(text = message.mes, color = textColor)
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
                STAvatar(
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
                    ChatBubbleSurface(
                        isUser = false,
                        maxWidth = maxWidth,
                        onLongPress = onLongPress
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
                            ChatRichText(text = message.mes, color = textColor)
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
internal fun MessageAttachments(
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
internal fun MessageFileCard(
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
internal fun ReasoningSection(
    reasoning: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .heightIn(min = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = reasoning,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.55f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
internal fun BubbleMeta(
    hasBookmark: Boolean,
    branchCount: Int,
    modifier: Modifier = Modifier
) {
    if (!hasBookmark && branchCount <= 0) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = branchCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ToolCallGroup(tools: List<ToolInvocation>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.forEachIndexed { index, tool ->
            ToolCallCard(tool = tool, defaultExpanded = index == 0)
        }
    }
}

@Composable
internal fun ToolCallCard(
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Text(
                            text = "执行中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = tool.parameters,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (running) {
                Text(
                    text = "执行中…",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "结果",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = tool.result,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}


@Composable
internal fun MessageEditBubble(
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
                STAvatar(
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

