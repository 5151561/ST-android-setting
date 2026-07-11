@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package io.github.sanitised.st.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sanitised.st.api.GroupSummary
import io.github.sanitised.st.api.TavernCoreClient
import io.github.sanitised.st.chat.engine.GroupReply
import io.github.sanitised.st.chat.engine.NativeGroupGenerator
import io.github.sanitised.st.chat.engine.pickGroupSpeaker
import io.github.sanitised.st.ui.screens.STAvatar
import io.github.sanitised.st.ui.screens.stAvatarImageUrl
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ─────────────────────────────────────────────────────────────
// SpeakerSheet — 原生 Compose M3 底部点名发言器
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerSheet(
    members: List<DemoGroupMember>,
    baseUrl: String,
    onDismiss: () -> Unit,
    onSelectSpeaker: (String) -> Unit,
    onToggleMute: (String) -> Unit,
    onTriggerAuto: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Drag Handle line
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    .padding(bottom = 12.dp)
            )
            
            // Header Row
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "让谁接话？",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // 自动挑选 / 随机 按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTriggerAuto,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("自动挑选", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                
                OutlinedButton(
                    onClick = {
                        val active = members.filter { !it.muted }
                        if (active.isNotEmpty()) {
                            onSelectSpeaker(active.random().id)
                        }
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Filled.Casino, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("随机一位", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            
            Text(
                text = "或点名一位成员",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            )
            
            // 成员列表
            members.forEach { m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.size(44.dp)) {
                        GroupMemberAvatar(
                            member = m,
                            baseUrl = baseUrl,
                            size = 44.dp,
                            alpha = if (m.muted) 0.5f else 1f,
                            ringColor = if (!m.muted) m.accent else Color.Transparent,
                            ringWidth = 2.dp
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = m.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            if (m.muted) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ) {
                                    Text(
                                        text = "已静音",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = m.role,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    IconButton(onClick = { onToggleMute(m.id) }) {
                        Icon(
                            imageVector = if (m.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "切换静音",
                            tint = if (m.muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Button(
                        onClick = { onSelectSpeaker(m.id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("发言", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

internal fun getStrategyLabel(id: String): String {
    return when (id) {
        "manual" -> "手动"
        "natural" -> "自然顺序"
        "list" -> "列表顺序"
        "pooled" -> "池化顺序"
        else -> id
    }
}

internal fun getStrategyActionLabel(id: String): String {
    return when (id) {
        "manual" -> "由你点名"
        "natural" -> "自动 · 自然顺序"
        "list" -> "自动 · 列表顺序"
        "pooled" -> "自动 · 池化顺序"
        else -> getStrategyLabel(id)
    }
}

// ─────────────────────────────────────────────────────────────
// 切换对话弹层数据模型
// ─────────────────────────────────────────────────────────────
// DEMO_PLACEHOLDER: 群聊历史对话/检查点/分支的静态模拟数据，对接 ST 后需替换为 /api/chats 列表。
enum class DemoConvKind { CHAT, CHECKPOINT, BRANCH }

data class DemoConversation(
    val id: String,
    val title: String,
    val kind: DemoConvKind,
    val messageCount: Int,
    val preview: String,
    val timeInfo: String,
    val active: Boolean = false
)

// ─────────────────────────────────────────────────────────────
// ConversationSwitcherSheet — 点群名后从顶部下拉的「切换对话」面板
// ─────────────────────────────────────────────────────────────
@Composable
fun ConversationSwitcherSheet(
    group: DemoGroup,
    members: List<DemoGroupMember>,
    baseUrl: String,
    conversations: List<DemoConversation>,
    onDismiss: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onManageAll: () -> Unit
) {
    // 群聊原生路径暂不支持检查点/分支，仅展示真实对话存档。
    val checkpoints = emptyList<DemoConversation>()

    var query by remember { mutableStateOf("") }
    fun matches(c: DemoConversation) =
        query.isBlank() || c.title.contains(query, true) || c.preview.contains(query, true)
    val filteredConvs = conversations.filter { matches(it) }
    val filteredChecks = checkpoints.filter { matches(it) }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val scrimSource = remember { MutableInteractionSource() }
    val panelSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = scrimSource, indication = null) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(max = (screenHeightDp * 0.86f).dp)
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                // 拦截面板内的点击，避免穿透到遮罩
                .clickable(interactionSource = panelSource, indication = null) {}
                .padding(top = 8.dp, bottom = 10.dp)
        ) {
            // 顶部标题行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GroupAvatar(ids = group.members, members = members, baseUrl = baseUrl, size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "切换对话",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${group.name} · ${conversations.size + checkpoints.size} 个存档",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.ExpandLess, contentDescription = "收起", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 搜索框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "搜索这个群的聊天内容…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        inner()
                    }
                )
            }

            // 可滚动列表
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                if (filteredConvs.isNotEmpty()) {
                    ConvSectionLabel("全部对话")
                    filteredConvs.forEach { c ->
                        ConversationRow(c = c, onClick = { onSelectConversation(c.id) })
                    }
                }
                if (filteredChecks.isNotEmpty()) {
                    ConvSectionLabel("检查点与分支")
                    filteredChecks.forEach { c ->
                        ConversationRow(c = c, onClick = { onSelectConversation(c.id) })
                    }
                }
                if (filteredConvs.isEmpty() && filteredChecks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("没有匹配的对话", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            // 底部操作
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNewConversation,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.AddComment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("新对话", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onManageAll,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("管理全部", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun ConvSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
internal fun ConversationRow(c: DemoConversation, onClick: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val iconBg = if (c.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val icon = when (c.kind) {
        DemoConvKind.CHAT -> Icons.Filled.Forum
        DemoConvKind.CHECKPOINT -> Icons.Filled.Flag
        DemoConvKind.BRANCH -> Icons.Filled.ForkRight
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (c.active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 当前对话左侧主色竖条
        if (c.active) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (c.active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = c.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (c.active) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = "进行中",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "${c.messageCount} 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = c.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
            Text(
                text = c.timeInfo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        if (c.active) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "当前对话",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "对话操作", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("重命名") }, onClick = { menuExpanded = false })
                    DropdownMenuItem(text = { Text("导出") }, onClick = { menuExpanded = false })
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// GroupMessageActionSheet — 长按消息的操作面板(复制/编辑/删除)
// ─────────────────────────────────────────────────────────────
@Composable
internal fun GroupMessageActionSheet(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                "消息操作",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Text(
                text = message.mes.take(80),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionGridItem(icon = Icons.Filled.ContentCopy, label = "复制", onClick = onCopy)
                ActionGridItem(icon = Icons.Filled.Edit, label = "编辑", onClick = onEdit)
                ActionGridItem(icon = Icons.Filled.Delete, label = "删除", onClick = onDelete)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// GroupMessageEditDialog — 编辑消息正文
// ─────────────────────────────────────────────────────────────
@Composable
internal fun GroupMessageEditDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑消息") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                enabled = draft.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
