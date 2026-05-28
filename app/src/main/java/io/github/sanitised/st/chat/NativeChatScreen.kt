package io.github.sanitised.st.chat

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.ThemeMode
import io.github.sanitised.st.ui.prototype.PrototypeAssistPill
import io.github.sanitised.st.ui.prototype.PrototypeAvatar
import io.github.sanitised.st.ui.webview.ChatWebViewScreen
import io.github.sanitised.st.ui.webview.WebViewTarget

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
    modifier: Modifier = Modifier
) {
    LaunchedEffect(status.state) {
        if (status.state != NodeState.RUNNING) {
            bridge.markRuntimeLoading()
        }
    }

    LaunchedEffect(store.runtimeState, target) {
        if (store.runtimeState != RuntimeState.READY) return@LaunchedEffect
        when (target) {
            WebViewTarget.CHAT -> bridge.requestSnapshot()
            is WebViewTarget.CharacterChat -> bridge.openCharacter(target.avatar, target.chatFile)
        }
    }

    val targetMatched = targetMatchesStore(target, store)
    val visibleCharacterName = if (targetMatched) {
        store.characterName.ifBlank { target.displayLabel() }
    } else {
        target.displayLabel()
    }
    val readyForTarget = store.runtimeState == RuntimeState.READY && targetMatched

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
                runtimeState = store.runtimeState,
                runtimeError = store.runtimeError,
                targetMatched = targetMatched,
                targetLabel = target.displayLabel(),
                onBack = onBackToHome
            )

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
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            ChatQuickStrip(
                runtimeReady = readyForTarget,
                onContinue = { bridge.continueGeneration() },
                onRegenerate = { bridge.regenerate() }
            )

            ChatInputBar(
                isGenerating = store.isGenerating,
                runtimeReady = readyForTarget,
                onSend = { text -> bridge.sendMessage(text) },
                onStop = { bridge.stopGeneration() }
            )
        }
    }
}

private fun targetMatchesStore(target: WebViewTarget, store: ChatStore): Boolean {
    return when (target) {
        WebViewTarget.CHAT -> true
        is WebViewTarget.CharacterChat -> {
            val characterMatches = listOf(store.avatarUrl, store.characterName)
                .any { identifiersMatch(target.avatar, it) }
            val chatMatches = target.chatFile.isNullOrBlank() ||
                normalizeChatFile(target.chatFile) == normalizeChatFile(store.chatFile)
            characterMatches && chatMatches
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
    runtimeState: RuntimeState,
    runtimeError: String?,
    targetMatched: Boolean,
    targetLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                PrototypeAvatar(
                    label = characterName.ifBlank { "?" },
                    size = 36.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = characterName.ifBlank { "对话" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when {
                        !runtimeError.isNullOrBlank() -> runtimeError
                        !targetMatched -> "正在打开 $targetLabel…"
                        isGenerating -> "生成中…"
                        runtimeState == RuntimeState.NOT_READY -> "正在连接运行时…"
                        runtimeState == RuntimeState.ERROR -> "运行时异常"
                        else -> "Claude Sonnet · 200k 上下文"
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
            IconButton(onClick = {}) {
                Icon(Icons.Filled.Search, contentDescription = "搜索消息")
            }
            IconButton(onClick = {}) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
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
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    LaunchedEffect(messages.size, messages.lastOrNull()?.mes, imeBottom) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "date-chip") {
            DateChip(text = "今天 14:00")
        }
        items(
            items = messages,
            key = { msg -> "${msg.id}_${msg.swipeId}" }
        ) { message ->
            if (!message.isSystem) {
                MessageBubble(
                    message = message,
                    characterName = characterName,
                    lastAssistant = !message.isUser && messages.lastOrNull { !it.isSystem }?.id == message.id,
                    maxWidth = screenWidth * 0.82f
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    characterName: String,
    lastAssistant: Boolean,
    maxWidth: androidx.compose.ui.unit.Dp,
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

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        if (isUser) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .clip(shape)
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(text = message.mes, style = MaterialTheme.typography.bodyMedium, color = textColor)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrototypeAvatar(label = message.name.ifBlank { characterName }, size = 36.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.name.ifBlank { characterName },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Column(
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .clip(shape)
                            .background(bubbleColor)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(text = message.mes, style = MaterialTheme.typography.bodyMedium, color = textColor)
                    }
                    if (lastAssistant) {
                        AssistantMessageControls(
                            swipeIndex = message.swipeId,
                            swipeCount = message.swipes.size.coerceAtLeast(1)
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
    swipeIndex: Int,
    swipeCount: Int
) {
    Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            text = "${swipeIndex + 1} / $swipeCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        IconButton(onClick = {}, modifier = Modifier.size(28.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        listOf(Icons.Filled.Refresh, Icons.AutoMirrored.Filled.ArrowForward, Icons.Filled.MoreVert).forEach { icon ->
            IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ChatQuickStrip(
    runtimeReady: Boolean,
    onContinue: () -> Unit,
    onRegenerate: () -> Unit
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
        QuickAction(Icons.Filled.RecordVoiceOver, "代笔我的消息", false) {},
        QuickAction(Icons.Filled.EditNote, "剧情推进", false) {}
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
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    var showAttach by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showAttach) {
            AttachSheet()
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
            onClick = {},
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
private fun AttachSheet() {
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
