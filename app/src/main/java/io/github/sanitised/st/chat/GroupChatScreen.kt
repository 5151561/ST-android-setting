package io.github.sanitised.st.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import io.github.sanitised.st.ui.prototype.PrototypeAvatar
import org.json.JSONObject

// DEMO_PLACEHOLDER: 这一部分定义群组聊天的静态模拟数据，对接 ST API 后需替换为 API 实体。
data class DemoGroup(
    val id: String,
    val name: String,
    val members: List<String>,
    val strategy: String,
    val genMode: String,
    val autoMode: Boolean,
    val autoDelay: Int,
    val selfResponses: Boolean,
    val hideMutedSprites: Boolean,
    val fav: Boolean,
    val lorebook: String,
    val tags: List<String>
)

data class DemoGroupMember(
    val id: String,
    val name: String,
    val subtitle: String,
    val accent: Color,
    val role: String,
    val queue: Int,
    val muted: Boolean,
    val avatarGrad: List<Color>,
    val initial: String
)

data class DemoGroupMessage(
    val role: String,
    val speaker: String?, // null if user
    val mesId: Int,
    val time: String,
    val text: String,
    val swipes: Pair<Int, Int>? = null // (current_index, total)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onNavigateToNewGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    // DEMO_PLACEHOLDER: 以下状态数据全部为原型模拟数据。开发对接时，应当由 ChatStore 和 ChatRuntimeBridge 接管。
    val groupState = remember {
        mutableStateOf(
            DemoGroup(
                id = "rainynight",
                name = "雨夜小聚",
                members = listOf("aria", "eleanor", "kael"),
                strategy = "natural", // manual, natural, list, pooled
                genMode = "swap",
                autoMode = false,
                autoDelay = 5,
                selfResponses = false,
                hideMutedSprites = false,
                fav = true,
                lorebook = "常去的咖啡馆",
                tags = listOf("日常", "群像", "治愈")
            )
        )
    }

    val membersList = remember {
        mutableStateListOf(
            DemoGroupMember("aria", "Aria", "咖啡馆的女店员", Color(0xFFFFD7B0), "咖啡馆店员", 1, false, listOf(Color(0xFFFFD7B0), Color(0xFFA55A2A)), "A"),
            DemoGroupMember("eleanor", "Eleanor Wright", "维多利亚时代小说家", Color(0xFFE8D3AC), "维多利亚小说家", 2, false, listOf(Color(0xFFD8C4A3), Color(0xFF6B4E2B)), "E"),
            DemoGroupMember("kael", "Kael", "吟游精灵", Color(0xFFC8E5B7), "吟游精灵", 3, true, listOf(Color(0xFFC8E5B7), Color(0xFF3D6B3A)), "K")
        )
    }

    val threadMessages = remember {
        mutableStateListOf(
            DemoGroupMessage("assistant", "aria", 0, "20:58", "*她把三把湿透的伞收进门口的铁桶，回头时眼睛弯成了月牙。*\n\n都到齐啦？外头雨大得很——我先煮上热可可。今晚不赶客，你们想坐到几点都行。"),
            DemoGroupMessage("user", null, 1, "21:00", "难得凑齐一次。Eleanor，你上回说卡在最后一章，今天带稿子来了吗？"),
            DemoGroupMessage("assistant", "eleanor", 2, "21:01", "*她从帆布包里抽出一沓纸，边角还沾着雨。*\n\n带了。说实话……我写了三个版本的结尾，自己都拿不准。要不一会儿读给你们听，帮我挑一个？"),
            DemoGroupMessage("assistant", "kael", 3, "21:02", "*指尖在桌沿轻轻敲出节拍。*\n\n结尾啊，得像一首歌的最后一个音——可以不响亮，但要让人记很久。读吧，我听着。"),
            DemoGroupMessage("user", null, 4, "21:03", "我也想听。Aria，第一杯可可先给 Eleanor，她现在最需要点勇气。"),
            DemoGroupMessage("assistant", "aria", 5, "21:03", "*她把最满的那杯推到 Eleanor 面前，又顺手点了一支小蜡烛。*\n\n给。慢慢读，没人催你。", swipes = Pair(0, 2))
        )
    }

    var typingSpeakerId by remember { mutableStateOf<String?>(null) } // "eleanor" for testing
    var isAutoModeRunning by remember { mutableStateOf(false) }
    var autoSecondsLeft by remember { mutableStateOf(5) }
    var showSpeakerSheet by remember { mutableStateOf(false) }
    
    val lazyListState = rememberLazyListState()

    // 模拟自动接龙效果
    LaunchedEffect(isAutoModeRunning) {
        if (isAutoModeRunning) {
            autoSecondsLeft = 5
            while (autoSecondsLeft > 0) {
                kotlinx.coroutines.delay(1000)
                autoSecondsLeft--
            }
            isAutoModeRunning = false
            typingSpeakerId = "eleanor"
            kotlinx.coroutines.delay(2500)
            typingSpeakerId = null
            threadMessages.add(
                DemoGroupMessage(
                    role = "assistant",
                    speaker = "eleanor",
                    mesId = threadMessages.size,
                    time = "21:04",
                    text = "*把稿子在桌上磕了磕，清了清嗓子。*\n\n那我开始读了啊……\"第一章。当夜幕降临在雾气昭昭的泰晤士河畔，她知道自己已无退路。\""
                )
            )
        }
    }

    // DEMO_PLACEHOLDER: 模拟点名发言后的打字动效及自动回复逻辑，防止 TypingRow 卡死。
    LaunchedEffect(typingSpeakerId) {
        val speaker = typingSpeakerId
        if (speaker != null) {
            kotlinx.coroutines.delay(2000)
            typingSpeakerId = null
            val replyText = when (speaker) {
                "aria" -> "*端着新鲜出炉的华夫饼走过来，在桌上放下一小碟蜂蜜。*\n\n那今天的可可多加些鲜奶油，算我请客！"
                "eleanor" -> "*轻轻翻开泛黄的手稿，眼神明亮。*\n\n多谢你的勇气。那我就先读一小段……\"第一章。伦敦的钟声敲响了十二下。\""
                "kael" -> "*取下腰间的短笛，微风穿过树影。*\n\n那我就用这支笛子给 Eleanor 的故事配乐，如何？"
                else -> "我听着呢。你说得对，群聊的氛围最棒了。"
            }
            threadMessages.add(
                DemoGroupMessage(
                    role = "assistant",
                    speaker = speaker,
                    mesId = threadMessages.size,
                    time = "21:04",
                    text = replyText
                )
            )
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            // 1. 群聊头部
            GroupChatHeader(
                group = groupState.value,
                members = membersList,
                onBack = onBack,
                onHeaderClick = onNavigateToSettings,
                onMembersIconClick = onNavigateToMembers,
                onNavigateToNewGroup = onNavigateToNewGroup
            )

            // 2. 自动回复横幅 (AutoMode Banner)
            if (isAutoModeRunning) {
                AutoModeBanner(
                    nextSpeakerName = "Kael",
                    nextSpeakerGrad = listOf(Color(0xFFC8E5B7), Color(0xFF3D6B3A)),
                    seconds = autoSecondsLeft,
                    onPause = { isAutoModeRunning = false }
                )
            }

            // 3. 消息滚动区 (Scroll Area)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        DateChipG(text = "今晚 20:55 · 雨")
                    }
                    itemsIndexed(threadMessages) { idx, msg ->
                        val isLast = idx == threadMessages.lastIndex
                        if (msg.role == "user") {
                            GroupMesUser(msg = msg)
                        } else {
                            val member = membersList.find { it.id == msg.speaker }
                            if (member != null) {
                                GroupMesAssistant(
                                    msg = msg,
                                    member = member,
                                    isLast = isLast,
                                    onSwipeLeft = { /* DEMO_PLACEHOLDER */ },
                                    onSwipeRight = { /* DEMO_PLACEHOLDER */ },
                                    onRegenerate = { typingSpeakerId = member.id },
                                    onContinue = { typingSpeakerId = member.id },
                                    onMore = { showSpeakerSheet = true }
                                )
                            }
                        }
                    }
                    if (typingSpeakerId != null) {
                        item {
                            val member = membersList.find { it.id == typingSpeakerId }
                            if (member != null) {
                                TypingRow(member = member)
                            }
                        }
                    }
                }
            }

            // 4. 下一位发言人控制条 (NextSpeakerBar)
            if (!isAutoModeRunning) {
                NextSpeakerBar(
                    strategy = groupState.value.strategy,
                    onBarClick = { showSpeakerSheet = true }
                )
            }

            // 5. 消息输入框
            GroupComposer(
                onSend = { text ->
                    threadMessages.add(
                        DemoGroupMessage(
                            role = "user",
                            speaker = null,
                            mesId = threadMessages.size,
                            time = "21:04",
                            text = text
                        )
                    )
                }
            )
        }

        // 6. 发言人选择底部弹窗 (SpeakerSheet)
        if (showSpeakerSheet) {
            SpeakerSheet(
                members = membersList,
                onDismiss = { showSpeakerSheet = false },
                onSelectSpeaker = { id ->
                    showSpeakerSheet = false
                    typingSpeakerId = id
                    // 模拟延迟回复效果
                    isAutoModeRunning = false
                    // 开启协程模拟生成
                },
                onToggleMute = { id ->
                    val idx = membersList.indexOfFirst { it.id == id }
                    if (idx >= 0) {
                        val m = membersList[idx]
                        membersList[idx] = m.copy(muted = !m.muted)
                    }
                },
                onTriggerAuto = {
                    showSpeakerSheet = false
                    isAutoModeRunning = true
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// GroupAvatar — 将群成员头像拼贴成圆角格栅
// ─────────────────────────────────────────────────────────────
@Composable
fun GroupAvatar(ids: List<String>, members: List<DemoGroupMember>, size: Dp, modifier: Modifier = Modifier) {
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(m.avatarGrad)),
                contentAlignment = Alignment.Center
            ) {
                Text(m.initial, color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
            }
        } else if (activeMembers.size == 3) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column - Full Height
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Brush.linearGradient(activeMembers[0].avatarGrad)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(activeMembers[0].initial, color = Color.White, fontSize = (size.value * 0.3f).sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(gap))
                // Right Column - Two Rows (top and bottom)
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Brush.linearGradient(activeMembers[1].avatarGrad)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(activeMembers[1].initial, color = Color.White, fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(gap))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Brush.linearGradient(activeMembers[2].avatarGrad)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(activeMembers[2].initial, color = Color.White, fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Top Left
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Brush.linearGradient(activeMembers[0].avatarGrad)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(activeMembers[0].initial, color = Color.White, fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(gap))
                    // Top Right
                    val topRight = activeMembers.getOrNull(1)
                    if (topRight != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Brush.linearGradient(topRight.avatarGrad)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(topRight.initial, color = Color.White, fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(gap))
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Bottom Left
                    val bottomLeft = activeMembers.getOrNull(2)
                    if (bottomLeft != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Brush.linearGradient(bottomLeft.avatarGrad)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(bottomLeft.initial, color = Color.White, fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.width(gap))
                    // Bottom Right
                    val bottomRight = activeMembers.getOrNull(3)
                    if (bottomRight != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Brush.linearGradient(bottomRight.avatarGrad)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(bottomRight.initial, color = Color.White, fontSize = (size.value * 0.22f).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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
    onBack: () -> Unit,
    onHeaderClick: () -> Unit,
    onMembersIconClick: () -> Unit,
    onNavigateToNewGroup: () -> Unit
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
                GroupAvatar(ids = group.members, members = members, size = 38.dp)
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
                            onHeaderClick()
                        }
                    )
                }
            }
        }
        
        // 横向滚动成员指示器
        MemberStrip(
            members = members,
            onAddMemberClick = onMembersIconClick,
            onMemberClick = { /* DEMO_PLACEHOLDER */ }
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
            val shape = CircleShape
            
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .clickable { onMemberClick(m.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.size(44.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(shape)
                            .alpha(alpha)
                            .background(Brush.linearGradient(m.avatarGrad))
                            .border(if (!m.muted) 2.dp else 0.dp, if (!m.muted) m.accent else Color.Transparent, shape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(m.initial, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    
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
                                imageVector = Icons.Filled.VolumeOff,
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
        
        // 快捷添加按钮
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable { onAddMemberClick() }
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
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
    msg: DemoGroupMessage,
    member: DemoGroupMember,
    isLast: Boolean,
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
        // 头像
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(member.avatarGrad)),
            contentAlignment = Alignment.Center
        ) {
            Text(member.initial, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        
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
                    text = msg.time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 文本气泡
            Box(
                modifier = Modifier
                    .widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.92f).dp)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        width = 2.dp,
                        brush = Brush.verticalGradient(listOf(member.accent, member.accent.copy(alpha = 0.5f))),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                GText(text = msg.text)
            }
            
            // 最后一发 Swipes 动作面板
            if (isLast && msg.swipes != null) {
                val (idx, total) = msg.swipes
                Row(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSwipeLeft, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "上个版本", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "${idx + 1} / $total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    IconButton(onClick = onSwipeRight, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "下个版本", modifier = Modifier.size(18.dp))
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    IconButton(onClick = onRegenerate, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重写", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onContinue, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "继续", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MoreHoriz, contentDescription = "更多选项", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// GroupMesUser — 消息气泡 (用户版)
// ─────────────────────────────────────────────────────────────
@Composable
fun GroupMesUser(msg: DemoGroupMessage) {
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
                text = "你 · ${msg.time}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = msg.text,
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
fun TypingRow(member: DemoGroupMember) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(member.avatarGrad)),
            contentAlignment = Alignment.Center
        ) {
            Text(member.initial, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, member.accent.copy(alpha = 0.4f), RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                0.0f at 0 with FastOutSlowInEasing
                                -6f at 300 with FastOutSlowInEasing
                                0.0f at 600 with FastOutSlowInEasing
                                0.0f at 1000 with FastOutSlowInEasing
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = {
                            textValue = it
                            composerHint = null
                        },
                        placeholder = { Text("发条消息，或 @ 点名某位角色", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                    )
                    
                    IconButton(
                        onClick = {
                            textValue = if (textValue.endsWith("@") || textValue.endsWith("@ ")) textValue else "$textValue @"
                            composerHint = "输入角色名即可点名接话"
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AlternateEmail,
                            contentDescription = "点名",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
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

// ─────────────────────────────────────────────────────────────
// SpeakerSheet — 原生 Compose M3 底部点名发言器
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerSheet(
    members: List<DemoGroupMember>,
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .alpha(if (m.muted) 0.5f else 1f)
                                .background(Brush.linearGradient(m.avatarGrad))
                                .border(2.dp, if (!m.muted) m.accent else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(m.initial, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
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
                            imageVector = if (m.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
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

// ─────────────────────────────────────────────────────────────
// DateChipG — 会话日期
// ─────────────────────────────────────────────────────────────
@Composable
fun DateChipG(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun getStrategyLabel(id: String): String {
    return when (id) {
        "manual" -> "手动"
        "natural" -> "自然顺序"
        "list" -> "列表顺序"
        "pooled" -> "池化顺序"
        else -> id
    }
}

private fun getStrategyActionLabel(id: String): String {
    return when (id) {
        "manual" -> "由你点名"
        "natural" -> "自动 · 自然顺序"
        "list" -> "自动 · 列表顺序"
        "pooled" -> "自动 · 池化顺序"
        else -> getStrategyLabel(id)
    }
}
