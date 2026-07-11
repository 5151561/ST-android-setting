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
import io.github.sanitised.st.ui.screens.PrototypeAssistPill
import io.github.sanitised.st.ui.screens.PrototypeAvatar
import io.github.sanitised.st.ui.screens.PrototypeGroupAvatar
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ChatQuickStrip(
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
internal fun QuickReplyStrip(
    items: List<QuickReplyItem>,
    enabled: Boolean,
    onExecute: (QuickReplyItem) -> Unit
) {
    if (items.isEmpty()) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    // 上游 Quick Reply 的 icon 是 Font Awesome 类名（如 fa-pencil），不是可展示文本。
                    // 仅当包含非 ASCII 字符（emoji / 可显示符号）时才渲染，避免显示 "fa-pencil" 这类内部标识。
                    // TODO: 长期做 FontAwesome 类名 → Compose icon 的映射。
                    val showIcon = item.icon.isNotBlank() && item.icon.any { it.code > 0x7F }
                    AssistChip(
                        onClick = { if (enabled) onExecute(item) },
                        enabled = enabled,
                        leadingIcon = if (showIcon) {
                            { Text(text = item.icon, style = MaterialTheme.typography.bodySmall) }
                        } else {
                            null
                        },
                        label = {
                            Text(
                                text = item.label.ifBlank { item.message.take(12) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChatInputBar(
    isGenerating: Boolean,
    runtimeReady: Boolean,
    pendingAttachments: List<PendingAttachment>,
    injectedText: String,
    injectedTextToken: Int,
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

    LaunchedEffect(injectedTextToken) {
        if (injectedText.isNotBlank()) text = injectedText
    }

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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    enabled = runtimeReady && !isGenerating,
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
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
internal fun ChatSendButton(
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

        text.isBlank() && !hasPendingAttachments -> FilledIconButton(
            onClick = onVoiceInput,
            enabled = runtimeReady,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "语音输入"
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
internal fun PendingAttachmentStrip(
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
internal fun AttachSheet(
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

internal data class PickedAttachmentFile(
    val name: String,
    val size: Long,
    val bytes: ByteArray
)

internal suspend fun uploadPickedAttachment(
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

internal fun readPickedAttachmentFile(context: Context, uri: Uri): PickedAttachmentFile {
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
internal fun HiddenMessageBadge(modifier: Modifier = Modifier) {
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

