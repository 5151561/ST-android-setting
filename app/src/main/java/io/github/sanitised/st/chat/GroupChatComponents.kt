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
import io.github.sanitised.st.ui.prototype.PrototypeAvatar
import io.github.sanitised.st.ui.prototype.prototypeAvatarImageUrl
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
// GroupAvatar — 将群成员头像拼贴成圆角格栅
// ─────────────────────────────────────────────────────────────
@Composable
fun GroupAvatar(ids: List<String>, members: List<DemoGroupMember>, baseUrl: String, size: Dp, modifier: Modifier = Modifier) {
    val activeMembers = ids.take(4).mapNotNull { id -> members.find { it.id == id } }
    val radius = size * 0.28f
    val gap = 1.5.dp
    
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (activeMembers.isEmpty()) {
            Text("群", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Bold)
        } else if (activeMembers.size == 1) {
            val m = activeMembers[0]
            GroupAvatarTile(
                member = m,
                baseUrl = baseUrl,
                fontSize = (size.value * 0.42f).sp,
                modifier = Modifier
                    .fillMaxSize()
            )
        } else if (activeMembers.size == 3) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column - Full Height
                GroupAvatarTile(
                    member = activeMembers[0],
                    baseUrl = baseUrl,
                    fontSize = (size.value * 0.3f).sp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                Spacer(modifier = Modifier.width(gap))
                // Right Column - Two Rows (top and bottom)
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    GroupAvatarTile(
                        member = activeMembers[1],
                        baseUrl = baseUrl,
                        fontSize = (size.value * 0.22f).sp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(gap))
                    GroupAvatarTile(
                        member = activeMembers[2],
                        baseUrl = baseUrl,
                        fontSize = (size.value * 0.22f).sp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Top Left
                    GroupAvatarTile(
                        member = activeMembers[0],
                        baseUrl = baseUrl,
                        fontSize = (size.value * 0.22f).sp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(gap))
                    // Top Right
                    val topRight = activeMembers.getOrNull(1)
                    if (topRight != null) {
                        GroupAvatarTile(
                            member = topRight,
                            baseUrl = baseUrl,
                            fontSize = (size.value * 0.22f).sp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(gap))
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Bottom Left
                    val bottomLeft = activeMembers.getOrNull(2)
                    if (bottomLeft != null) {
                        GroupAvatarTile(
                            member = bottomLeft,
                            baseUrl = baseUrl,
                            fontSize = (size.value * 0.22f).sp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    Spacer(modifier = Modifier.width(gap))
                    // Bottom Right
                    val bottomRight = activeMembers.getOrNull(3)
                    if (bottomRight != null) {
                        GroupAvatarTile(
                            member = bottomRight,
                            baseUrl = baseUrl,
                            fontSize = (size.value * 0.22f).sp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupAvatarTile(
    member: DemoGroupMember,
    baseUrl: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    var imageLoadFailed by remember(member.avatarUrl, baseUrl) { mutableStateOf(false) }
    val imageModel = remember(member.avatarUrl, baseUrl) { prototypeAvatarImageUrl(baseUrl, member.avatarUrl) }
    Box(
        modifier = modifier.background(Brush.linearGradient(member.avatarGrad)),
        contentAlignment = Alignment.Center
    ) {
        if (imageModel != null && !imageLoadFailed) {
            AsyncImage(
                model = imageModel,
                contentDescription = member.name,
                contentScale = ContentScale.Crop,
                onError = { imageLoadFailed = true },
                modifier = Modifier.matchParentSize()
            )
        }
        if (imageModel == null || imageLoadFailed) {
            Text(member.initial, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun GroupMemberAvatar(
    member: DemoGroupMember,
    baseUrl: String,
    size: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    ringColor: Color? = null,
    ringWidth: Dp = 0.dp
) {
    var imageLoadFailed by remember(member.avatarUrl, baseUrl) { mutableStateOf(false) }
    val imageModel = remember(member.avatarUrl, baseUrl) { prototypeAvatarImageUrl(baseUrl, member.avatarUrl) }
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .alpha(alpha)
            .background(Brush.linearGradient(member.avatarGrad))
            .border(ringWidth, ringColor ?: Color.Transparent, shape),
        contentAlignment = Alignment.Center
    ) {
        if (imageModel != null && !imageLoadFailed) {
            AsyncImage(
                model = imageModel,
                contentDescription = member.name,
                contentScale = ContentScale.Crop,
                onError = { imageLoadFailed = true },
                modifier = Modifier.matchParentSize()
            )
        }
        if (imageModel == null || imageLoadFailed) {
            Text(member.initial, color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// GroupChatHeader — 群聊头部栏
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatHeader(
    group: DemoGroup,
    members: List<DemoGroupMember>,
    baseUrl: String,
    onBack: () -> Unit,
    onHeaderClick: () -> Unit,
    onMembersIconClick: () -> Unit,
    onNavigateToNewGroup: () -> Unit,
    onOpenSettings: () -> Unit,
    onMemberClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onHeaderClick() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GroupAvatar(ids = group.members, members = members, baseUrl = baseUrl, size = 38.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = "详情设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "${group.members.size} 位成员 · ${getStrategyLabel(group.strategy)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onMembersIconClick) {
                Icon(Icons.Filled.Group, contentDescription = "群成员管理", modifier = Modifier.size(22.dp))
            }
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多", modifier = Modifier.size(22.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("新建群聊") },
                        leadingIcon = { Icon(Icons.Filled.GroupAdd, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onNavigateToNewGroup()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("群聊设置") },
                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onOpenSettings()
                        }
                    )
                }
            }
        }
        
        // 横向滚动成员指示器
        MemberStrip(
            members = members,
            baseUrl = baseUrl,
            onAddMemberClick = onMembersIconClick,
            onMemberClick = onMemberClick
        )
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

// ─────────────────────────────────────────────────────────────
// MemberStrip — 横向滑动状态指示器
// ─────────────────────────────────────────────────────────────
@Composable
fun MemberStrip(
    members: List<DemoGroupMember>,
    baseUrl: String,
    onAddMemberClick: () -> Unit,
    onMemberClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        members.forEach { m ->
            val alpha = if (m.muted) 0.45f else 1f

            Column(
                modifier = Modifier
                    .width(52.dp)
                    .clickable { onMemberClick(m.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.size(44.dp)) {
                    GroupMemberAvatar(
                        member = m,
                        baseUrl = baseUrl,
                        size = 44.dp,
                        alpha = alpha,
                        ringColor = if (!m.muted) m.accent else Color.Transparent,
                        ringWidth = if (!m.muted) 2.dp else 0.dp
                    )
                    
                    // 右下角徽章
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 3.dp, y = 3.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (m.muted) MaterialTheme.colorScheme.surfaceVariant else m.accent)
                            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (m.muted) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "静音",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(10.dp)
                            )
                        } else {
                            Text(
                                text = m.queue.toString(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2A1A08)
                            )
                        }
                    }
                }
                Text(
                    text = m.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (m.muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // 快捷添加按钮（虚线圆圈，对齐设计稿 dashed circle）
        val dashColor = MaterialTheme.colorScheme.outlineVariant
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable { onAddMemberClick() }
                .drawBehind {
                    drawCircle(
                        color = dashColor,
                        radius = size.minDimension / 2f - 1.dp.toPx(),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PersonAdd,
                contentDescription = "添加角色",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
