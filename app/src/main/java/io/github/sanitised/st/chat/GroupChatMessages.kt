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
import io.github.sanitised.st.chat.ui.AssistantMessageControls
import io.github.sanitised.st.chat.ui.ChatBubbleSurface
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
// 富文本渲染：行内格式转换函数 (*斜体* -> 灰色斜体, "说话" -> Primary色)
// ─────────────────────────────────────────────────────────────
fun gfmt(line: String, primaryColor: Color, outlineColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '*') {
                val close = line.indexOf('*', i + 1)
                if (close > i) {
                    withStyle(
                        style = SpanStyle(
                            color = outlineColor.copy(alpha = 0.85f),
                            fontStyle = FontStyle.Italic
                        )
                    ) {
                        append(line.substring(i + 1, close))
                    }
                    i = close + 1
                    continue
                }
            }
            if (ch == '"' || ch == '“' || ch == '”') {
                val close = line.indexOf(if (ch == '“') '”' else ch, i + 1)
                if (close > i) {
                    withStyle(style = SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                        append(line.substring(i, close + 1))
                    }
                    i = close + 1
                    continue
                }
            }
            
            // 普通文本
            var nextSpecial = i
            while (nextSpecial < line.length && line[nextSpecial] != '*' && line[nextSpecial] != '"' && line[nextSpecial] != '“') {
                nextSpecial++
            }
            append(line.substring(i, nextSpecial))
            i = nextSpecial
        }
    }
}

@Composable
fun GText(text: String) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        text.split('\n').forEachIndexed { index, line ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = gfmt(line, primary, outline),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// GroupMesAssistant — 消息气泡 (AI 角色版)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupMesAssistant(
    msg: ChatMessage,
    member: DemoGroupMember,
    baseUrl: String,
    showControls: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GroupMemberAvatar(member = member, baseUrl = baseUrl, size = 36.dp)
        
        Column(modifier = Modifier.weight(1f)) {
            // Header Row
            Row(
                modifier = Modifier.padding(bottom = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = member.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = member.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatGroupTime(msg.sendDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 文本气泡:共享外壳 + 群聊特有的成员 accent 竖条(对齐设计稿 border-left)
            ChatBubbleSurface(
                isUser = false,
                maxWidth = (LocalConfiguration.current.screenWidthDp * 0.92f).dp,
                accent = member.accent
            ) {
                // applySwipe 与流式生成都会同步更新 mes,直接渲染即可。
                GText(text = msg.mes)
            }

            // 最后一发操作行(swipe 切换 + 重写/继续/更多),与单聊共用同一组件
            if (showControls) {
                AssistantMessageControls(
                    messageId = msg.id,
                    swipeIndex = msg.swipeId,
                    swipeCount = msg.swipes.size.coerceAtLeast(1),
                    onSwipePrevious = { onSwipeLeft() },
                    onSwipeNext = { onSwipeRight() },
                    onRegenerate = onRegenerate,
                    onContinue = onContinue,
                    onMore = onMore
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// GroupMesUser — 消息气泡 (用户版)
// ─────────────────────────────────────────────────────────────
@Composable
fun GroupMesUser(msg: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier.widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.82f).dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "你 · ${formatGroupTime(msg.sendDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            ChatBubbleSurface(isUser = true) {
                Text(
                    text = msg.mes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// TypingRow — 动态脉动的成员打字状态
// ─────────────────────────────────────────────────────────────
@Composable
fun TypingRow(member: DemoGroupMember, baseUrl: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupMemberAvatar(member = member, baseUrl = baseUrl, size = 36.dp)
        
        Spacer(modifier = Modifier.width(10.dp))

        val typingShape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
        Box(
            modifier = Modifier
                .clip(typingShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(member.accent)
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            Text(
                text = member.name,
                style = MaterialTheme.typography.labelMedium,
                color = member.accent,
                fontWeight = FontWeight.Bold
            )

            // 3点跳动动画
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                val infiniteTransition = rememberInfiniteTransition()
                
                listOf(0, 1, 2).forEach { index ->
                    val dy by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = -6f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 1000
                                0.0f at 0 using FastOutSlowInEasing
                                -6f at 300 using FastOutSlowInEasing
                                0.0f at 600 using FastOutSlowInEasing
                                0.0f at 1000 using FastOutSlowInEasing
                            },
                            repeatMode = RepeatMode.Restart,
                            initialStartOffset = StartOffset(index * 160)
                        )
                    )
                    
                    Box(
                        modifier = Modifier
                            .offset(y = dy.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// NextSpeakerBar — 「下一位发言」点名条
// ─────────────────────────────────────────────────────────────
@Composable
fun NextSpeakerBar(
    strategy: String,
    onBarClick: () -> Unit
) {
    Surface(
        onClick = onBarClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Campaign,
                    contentDescription = "下一位发言",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "下一位发言",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = getStrategyActionLabel(strategy),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ExpandLess,
                    contentDescription = "展开策略面板",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// AutoModeBanner — 自动接龙模式下的浮动指示横幅
// ─────────────────────────────────────────────────────────────
@Composable
fun AutoModeBanner(
    nextSpeakerName: String,
    nextSpeakerGrad: List<Color>,
    seconds: Int,
    onPause: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.PlayCircle,
            contentDescription = "自动接龙中",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "自动接龙中",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold
            )
            
            // Avatar
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(nextSpeakerGrad)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = nextSpeakerName.take(1),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = "${seconds}s 后轮到 $nextSpeakerName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Button(
            onClick = onPause,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black.copy(alpha = 0.18f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Icon(Icons.Filled.Pause, contentDescription = "暂停", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("暂停", fontSize = 13.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// GroupComposer — 底部富文本输入面板
// ─────────────────────────────────────────────────────────────
@Composable
fun GroupComposer(
    onSend: (String) -> Unit
) {
    var textValue by remember { mutableStateOf("") }
    var composerHint by remember { mutableStateOf<String?>(null) }
    
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = { composerHint = "可从这里添加素材、世界书或图片" }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                // 输入外壳
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = textValue,
                        onValueChange = {
                            textValue = it
                            composerHint = null
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (textValue.isEmpty()) {
                                Text(
                                    "发条消息，或 @ 点名某位角色",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            innerTextField()
                        }
                    )

                    Icon(
                        imageVector = Icons.Filled.AlternateEmail,
                        contentDescription = "点名某位角色",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (!textValue.endsWith("@")) {
                                    textValue = if (textValue.isEmpty() || textValue.endsWith(" ")) "$textValue@" else "$textValue @"
                                }
                                composerHint = "输入角色名即可点名接话"
                            }
                    )
                }
                
                // 发送按钮
                IconButton(
                    onClick = {
                        if (textValue.isNotBlank()) {
                            onSend(textValue)
                            textValue = ""
                            composerHint = null
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", modifier = Modifier.size(22.dp))
                }
            }
            composerHint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 66.dp, end = 16.dp, bottom = 8.dp)
                )
            }
        }
    }
}
