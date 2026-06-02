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
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// 群聊 UI 的数据载体。名称沿用 Demo* 前缀，但已由真实 ST API 数据填充
// （群信息 / 成员 / 历史消息见 GroupChatScreen.reload()）。
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
    val swipes: Pair<Int, Int>? = null, // (current_index, total)
    val swipeTexts: List<String>? = null // 各 swipe 版本文本，与 swipes.first 对应
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    chatId: String?,
    baseUrl: String,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onNavigateToNewGroup: () -> Unit,
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    // Real group state, loaded from the local SillyTavern API by [groupId]/[chatId].
    val groupState = remember { mutableStateOf(emptyDemoGroup(groupId)) }
    val membersList = remember { mutableStateListOf<DemoGroupMember>() }
    val threadMessages = remember { mutableStateListOf<DemoGroupMessage>() }
    var activeChatId by remember { mutableStateOf(chatId?.takeIf { it.isNotBlank() } ?: "") }
    var userName by remember { mutableStateOf("User") }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        val client = TavernCoreClient(baseUrl)
        val group = runCatching { client.listGroups().find { it.id == groupId } }.getOrNull()
        if (group == null) {
            loadError = "找不到群聊"
            loading = false
            return
        }
        val chatToLoad = activeChatId.ifBlank { group.chatId.ifBlank { group.id } }
        activeChatId = chatToLoad
        userName = runCatching { client.getSettings()["username"] as? String }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "User"
        val byId = runCatching { client.listCharacters() }.getOrDefault(emptyList())
            .associateBy { it.id }
        val members = group.members.mapIndexed { index, avatar ->
            val character = byId[avatar]
            val name = character?.name ?: avatar.removeSuffix(".png")
            DemoGroupMember(
                id = avatar,
                name = name,
                subtitle = character?.creatorNotes?.lineSequence()?.firstOrNull()?.take(24) ?: "",
                accent = gradientFor(avatar).last(),
                role = "",
                queue = index + 1,
                muted = avatar in group.disabledMembers,
                avatarGrad = gradientFor(avatar),
                initial = memberInitial(name)
            )
        }
        val nameToId = members.associate { it.name to it.id }
        val jsonl = runCatching { client.getGroupChatJsonl(chatToLoad) }.getOrDefault(mutableListOf())
        val messages = jsonl.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            if (!map.containsKey("mes")) return@mapNotNull null // skip the JSONL header line
            val isUser = map["is_user"] == true
            val name = map["name"]?.toString() ?: ""
            val swipeTexts = (map["swipes"] as? List<*>)?.map { it?.toString() ?: "" }
            val swipeId = (map["swipe_id"] as? Number)?.toInt() ?: 0
            val hasSwipes = swipeTexts != null && swipeTexts.size > 1
            DemoGroupMessage(
                role = if (isUser) "user" else "assistant",
                speaker = if (isUser) null else (nameToId[name] ?: name),
                mesId = 0,
                time = formatGroupTime(map["send_date"]?.toString()),
                text = map["mes"]?.toString() ?: "",
                swipes = if (hasSwipes) Pair(swipeId.coerceIn(0, swipeTexts!!.size - 1), swipeTexts.size) else null,
                swipeTexts = if (hasSwipes) swipeTexts else null
            )
        }.mapIndexed { index, message -> message.copy(mesId = index) }

        groupState.value = group.toDemoGroup()
        membersList.clear(); membersList.addAll(members)
        threadMessages.clear(); threadMessages.addAll(messages)
        loadError = null
        loading = false
    }

    LaunchedEffect(groupId, chatId) {
        loading = true
        reload()
    }

    var typingSpeakerId by remember { mutableStateOf<String?>(null) }
    var isAutoModeRunning by remember { mutableStateOf(false) }
    var autoSecondsLeft by remember { mutableStateOf(groupState.value.autoDelay) }
    var showSpeakerSheet by remember { mutableStateOf(false) }
    var showConversationSwitcher by remember { mutableStateOf(false) }

    // 下一位发言者：取第一位未静音成员（自然顺序近似）
    val autoNextSpeaker = membersList.firstOrNull { !it.muted } ?: membersList.firstOrNull()

    val lazyListState = rememberLazyListState()

    val generator = remember { NativeGroupGenerator { TavernCoreClient(baseUrl) } }
    var isGenerating by remember { mutableStateOf(false) }

    // 发送用户消息：追加到本地并真实落库（群聊 JSONL）。
    fun sendUserMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || activeChatId.isBlank()) return
        if (isGenerating) {
            onShowMessage("正在生成回复，请稍候")
            return
        }
        val date = groupSendDate()
        threadMessages.add(
            DemoGroupMessage(
                role = "user",
                speaker = null,
                mesId = threadMessages.size,
                time = formatGroupTime(date),
                text = trimmed
            )
        )
        scope.launch {
            runCatching {
                val client = TavernCoreClient(baseUrl)
                val chat = client.getGroupChatJsonl(activeChatId)
                ensureGroupHeader(chat, userName, groupState.value.name, date)
                chat.add(groupUserMessageMap(userName, trimmed, date))
                client.saveGroupChatJsonl(activeChatId, chat)
            }.onFailure { error -> onShowMessage(error.message ?: "保存消息失败") }
        }
    }

    // AI 回复生成：原生群聊生成（NativeGroupGenerator）。
    // memberId 非空 = 点名/重写/继续指定成员；为空 = 按 strategy 自动选下一位。
    fun requestGroupReply(memberId: String?) {
        if (isGenerating || activeChatId.isBlank()) return
        val strategyInt = activationStrategyId(groupState.value.strategy)
        val disabled = membersList.filter { it.muted }.map { it.id }.toSet()
        val lastSpeaker = threadMessages.lastOrNull { it.role == "assistant" }?.speaker
        val speakerAvatar = memberId
            ?: pickGroupSpeaker(membersList.map { it.id }, disabled, lastSpeaker, strategyInt)
        if (speakerAvatar == null) {
            onShowMessage(if (groupState.value.strategy == "manual") "请先点名一位发言者" else "没有可发言的成员")
            return
        }
        val member = membersList.find { it.id == speakerAvatar }
        if (member == null) {
            onShowMessage("找不到该成员")
            return
        }

        // 提示词历史：把当前线程映射为 ChatMessage（assistant 用成员真实名）。
        val promptHistory = threadMessages.map { m ->
            val name = if (m.role == "user") userName
            else membersList.find { it.id == m.speaker }?.name ?: (m.speaker ?: "")
            ChatMessage(
                id = m.mesId,
                name = name,
                mes = m.text,
                isUser = m.role == "user",
                isSystem = false,
                sendDate = "",
                swipeId = 0,
                swipes = emptyList(),
                extra = JSONObject()
            )
        }

        // 乐观空气泡（流式期间输入被禁用，占位始终保持在末尾）。
        threadMessages.add(
            DemoGroupMessage(
                role = "assistant",
                speaker = member.id,
                mesId = threadMessages.size,
                time = formatGroupTime(groupSendDate()),
                text = ""
            )
        )
        isGenerating = true
        scope.launch {
            try {
                val reply = generator.generate(
                    speakerAvatar = member.id,
                    userName = userName,
                    history = promptHistory,
                    authorsNote = "",
                    worldInfoName = "",
                    onToken = { cumulative ->
                        val idx = threadMessages.lastIndex
                        if (idx >= 0 && threadMessages[idx].role == "assistant") {
                            threadMessages[idx] = threadMessages[idx].copy(text = cumulative)
                        }
                    }
                )
                if (reply.text.isBlank()) {
                    val idx = threadMessages.lastIndex
                    if (idx >= 0 && threadMessages[idx].role == "assistant" && threadMessages[idx].text.isBlank()) {
                        threadMessages.removeAt(idx)
                    }
                } else {
                    val date = groupSendDate()
                    val client = TavernCoreClient(baseUrl)
                    val chat = client.getGroupChatJsonl(activeChatId)
                    ensureGroupHeader(chat, userName, groupState.value.name, date)
                    chat.add(groupAssistantMessageMap(reply, date))
                    client.saveGroupChatJsonl(activeChatId, chat)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val idx = threadMessages.lastIndex
                if (idx >= 0 && threadMessages[idx].role == "assistant" && threadMessages[idx].text.isBlank()) {
                    threadMessages.removeAt(idx)
                }
                onShowMessage(e.message ?: "群聊生成失败")
            } finally {
                isGenerating = false
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            // 1. 群聊头部
            GroupChatHeader(
                group = groupState.value,
                members = membersList,
                onBack = onBack,
                onHeaderClick = { showConversationSwitcher = true },
                onMembersIconClick = onNavigateToMembers,
                onNavigateToNewGroup = onNavigateToNewGroup,
                onOpenSettings = onNavigateToSettings,
                onMemberClick = { id -> requestGroupReply(id) }
            )

            // 2. 自动回复横幅 (AutoMode Banner) —— 显示与实际发言一致的下一位
            if (isAutoModeRunning && autoNextSpeaker != null) {
                AutoModeBanner(
                    nextSpeakerName = autoNextSpeaker.name,
                    nextSpeakerGrad = autoNextSpeaker.avatarGrad,
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
                                    onSwipeLeft = {
                                        val i = threadMessages.indexOf(msg)
                                        val sw = msg.swipes
                                        if (i >= 0 && sw != null && sw.first > 0) {
                                            threadMessages[i] = msg.copy(swipes = Pair(sw.first - 1, sw.second))
                                        }
                                    },
                                    onSwipeRight = {
                                        val i = threadMessages.indexOf(msg)
                                        val sw = msg.swipes
                                        if (i >= 0 && sw != null && sw.first < sw.second - 1) {
                                            threadMessages[i] = msg.copy(swipes = Pair(sw.first + 1, sw.second))
                                        }
                                    },
                                    // 重写 / 继续：交给原生群聊生成（下一阶段接入）
                                    onRegenerate = { requestGroupReply(member.id) },
                                    onContinue = { requestGroupReply(member.id) },
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
                onSend = { text -> sendUserMessage(text) }
            )
        }

        // 6. 发言人选择底部弹窗 (SpeakerSheet)
        if (showSpeakerSheet) {
            SpeakerSheet(
                members = membersList,
                onDismiss = { showSpeakerSheet = false },
                onSelectSpeaker = { id ->
                    showSpeakerSheet = false
                    requestGroupReply(id)
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
                    requestGroupReply(null)
                }
            )
        }

        // 7. 切换对话下拉面板 (ConversationSwitcher) —— 点群名触发
        if (showConversationSwitcher) {
            ConversationSwitcherSheet(
                group = groupState.value,
                members = membersList,
                onDismiss = { showConversationSwitcher = false },
                onSelectConversation = { showConversationSwitcher = false },
                onNewConversation = { showConversationSwitcher = false },
                onManageAll = { showConversationSwitcher = false }
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
                            onOpenSettings()
                        }
                    )
                }
            }
        }
        
        // 横向滚动成员指示器
        MemberStrip(
            members = members,
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
            
            // 文本气泡 —— 仅左侧一条 accent 强调竖条（随圆角裁剪），对齐设计稿 border-left
            val bubbleShape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            Box(
                modifier = Modifier
                    .widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.92f).dp)
                    .clip(bubbleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                // 左侧强调条
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(member.accent)
                )
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    val displayText = msg.swipeTexts?.getOrNull(msg.swipes?.first ?: 0) ?: msg.text
                    GText(text = displayText)
                }
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
    onDismiss: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onManageAll: () -> Unit
) {
    // DEMO_PLACEHOLDER: 历史对话与检查点/分支模拟数据。
    val conversations = remember {
        listOf(
            DemoConversation("rainynight", "雨夜小聚", DemoConvKind.CHAT, 48, "Eleanor：带了。说实话我写了三个版本的结尾…", "今天 21:03 · 36 KB", active = true),
            DemoConversation("boardgame", "周末桌游夜", DemoConvKind.CHAT, 132, "Kael：这把我赌 Aria 在虚张声势。", "3 天前 · 94 KB"),
            DemoConversation("bookshop", "深夜书店打烊后", DemoConvKind.CHAT, 76, "你：所以那本书到底是谁落下的？", "上周 · 58 KB"),
            DemoConversation("firstmeet", "初次见面", DemoConvKind.CHAT, 24, "Aria：欢迎光临～三位是一起的吗？", "142 天前 · 17 KB")
        )
    }
    val checkpoints = remember {
        listOf(
            DemoConversation("cp-reading", "结尾朗读 · 检查点", DemoConvKind.CHECKPOINT, 41, "从 Eleanor 读第二版结尾那刻保存", "今天 21:02 · 31 KB"),
            DemoConversation("branch-rain", "如果当晚没下雨", DemoConvKind.BRANCH, 19, "岔开的支线：改约在天台", "今天 20:40 · 14 KB")
        )
    }

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
                GroupAvatar(ids = group.members, members = members, size = 40.dp)
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
private fun ConvSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ConversationRow(c: DemoConversation, onClick: () -> Unit) {
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
// 真实数据映射与群聊 JSONL 持久化辅助
// ─────────────────────────────────────────────────────────────

/** 加载完成前的占位群（避免 UI 读到 demo 数据）。 */
private fun emptyDemoGroup(id: String): DemoGroup = DemoGroup(
    id = id,
    name = "",
    members = emptyList(),
    strategy = "natural",
    genMode = "swap",
    autoMode = false,
    autoDelay = 5,
    selfResponses = false,
    hideMutedSprites = false,
    fav = false,
    lorebook = "",
    tags = emptyList()
)

// SillyTavern group_activation_strategy: 0=自然 1=列表 2=手动 3=池化。
private fun groupStrategyName(value: Int): String = when (value) {
    1 -> "list"
    2 -> "manual"
    3 -> "pooled"
    else -> "natural"
}

private fun GroupSummary.toDemoGroup(): DemoGroup = DemoGroup(
    id = id,
    name = name,
    members = members,
    strategy = groupStrategyName(activationStrategy),
    genMode = if (generationMode == 0) "swap" else "append",
    autoMode = false,
    autoDelay = autoModeDelay,
    selfResponses = allowSelfResponses,
    hideMutedSprites = false,
    fav = isFavorite,
    lorebook = "",
    tags = emptyList()
)

/** ST 的 send_date 多为 "May 26, 2026 12:00pm" 风格；尽力提取 HH:mm，否则原样回退。 */
private fun formatGroupTime(sendDate: String?): String {
    val raw = sendDate?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    val match = Regex("(\\d{1,2}:\\d{2})").find(raw)
    return match?.groupValues?.get(1) ?: raw.take(16)
}

private fun groupSendDate(): String =
    SimpleDateFormat("MMMM d, yyyy h:mma", Locale.ENGLISH).format(Date()).lowercase(Locale.ENGLISH)

/** 群聊 JSONL 为空时写入首行 header（与 NativeChatEngine 1v1 语义一致）。 */
private fun ensureGroupHeader(chat: MutableList<Any?>, userName: String, groupName: String, date: String) {
    if (chat.isNotEmpty()) return
    chat.add(
        linkedMapOf<String, Any?>(
            "user_name" to userName,
            "character_name" to groupName,
            "create_date" to date,
            "chat_metadata" to linkedMapOf<String, Any?>("integrity" to UUID.randomUUID().toString())
        )
    )
}

private fun groupUserMessageMap(userName: String, text: String, date: String): Map<String, Any?> =
    linkedMapOf(
        "name" to userName,
        "is_user" to true,
        "is_system" to false,
        "send_date" to date,
        "mes" to text,
        "extra" to emptyMap<String, Any?>()
    )

private fun groupAssistantMessageMap(reply: GroupReply, date: String): Map<String, Any?> =
    linkedMapOf(
        "name" to reply.speakerName,
        "is_user" to false,
        "is_system" to false,
        "send_date" to date,
        "mes" to reply.text,
        "swipes" to listOf(reply.text),
        "swipe_id" to 0,
        "extra" to linkedMapOf<String, Any?>("api" to reply.api, "model" to reply.model)
    )
