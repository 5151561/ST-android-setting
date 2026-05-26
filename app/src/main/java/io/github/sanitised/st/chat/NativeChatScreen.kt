package io.github.sanitised.st.chat

import android.webkit.WebView
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.ThemeMode
import io.github.sanitised.st.ui.theme.STTheme
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
    Column(modifier = modifier.fillMaxSize()) {
        ChatHeader(
            characterName = store.characterName,
            isGenerating = store.isGenerating,
            runtimeState = store.runtimeState
        )

        Box(modifier = Modifier.weight(1f)) {
            if (store.runtimeState == RuntimeState.NOT_READY && store.messages.isEmpty()) {
                ChatLoadingView(modifier = Modifier.fillMaxSize())
            } else {
                MessageList(
                    messages = store.messages,
                    isGenerating = store.isGenerating,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Runtime WebView is kept invisible but alive
            Box(modifier = Modifier.size(0.dp)) {
                ChatWebViewScreen(
                    status = status,
                    target = target,
                    themeMode = themeMode,
                    onStartService = onStartService,
                    onShowLogs = onShowLogs,
                    onBackToHome = onBackToHome,
                    chatEventHandler = { json -> bridge.onEvent(json) },
                    onWebViewReady = { wv -> bridge.attach(wv) }
                )
            }
        }

        ChatInputBar(
            isGenerating = store.isGenerating,
            runtimeReady = store.runtimeState == RuntimeState.READY,
            onSend = { text -> bridge.sendMessage(text) },
            onStop = { bridge.stopGeneration() }
        )
    }
}

@Composable
private fun ChatHeader(
    characterName: String,
    isGenerating: Boolean,
    runtimeState: RuntimeState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = STTheme.colors.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = characterName.ifBlank { "Chat" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = when {
                    isGenerating -> "Generating..."
                    runtimeState == RuntimeState.NOT_READY -> "Connecting..."
                    runtimeState == RuntimeState.ERROR -> "Runtime error"
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (runtimeState == RuntimeState.ERROR) STTheme.colors.danger else STTheme.colors.muted
                    )
                }
            }
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun ChatLoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Waiting for SillyTavern runtime...",
                style = MaterialTheme.typography.bodyMedium,
                color = STTheme.colors.muted
            )
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun MessageList(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    LaunchedEffect(messages.size, messages.lastOrNull()?.mes) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            items = messages,
            key = { msg -> "${msg.id}_${msg.swipeId}" }
        ) { message ->
            if (!message.isSystem) {
                MessageBubble(
                    message = message,
                    maxWidth = screenWidth * 0.82f
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    maxWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isUser) STTheme.colors.surfaceWarm else STTheme.colors.surface
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (!isUser) {
                Text(
                    text = message.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = STTheme.colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = message.mes,
                style = MaterialTheme.typography.bodyMedium,
                color = STTheme.colors.fg
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

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = STTheme.colors.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (runtimeReady) "Type a message..." else "Waiting for runtime...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                enabled = runtimeReady && !isGenerating,
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = STTheme.colors.accent,
                    unfocusedBorderColor = STTheme.colors.border
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

            if (isGenerating) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(STTheme.colors.danger)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Stop",
                        tint = STTheme.colors.accentOn
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        val msg = text.trim()
                        if (msg.isNotEmpty()) {
                            onSend(msg)
                            text = ""
                        }
                    },
                    enabled = text.isNotBlank() && runtimeReady,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (text.isNotBlank() && runtimeReady) STTheme.colors.accent
                            else STTheme.colors.border
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = STTheme.colors.accentOn
                    )
                }
            }
        }
    }
}
