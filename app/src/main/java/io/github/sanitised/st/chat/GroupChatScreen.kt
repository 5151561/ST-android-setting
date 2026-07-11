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
import io.github.sanitised.st.chat.ui.ChatDateChip
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
    val tags: List<String>,
    val chats: List<String> = emptyList()
)

data class DemoGroupMember(
    val id: String,
    val name: String,
    val subtitle: String,
    val accent: Color,
    val role: String,
    val queue: Int,
    val muted: Boolean,
    val avatarUrl: String?,
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
                avatarUrl = character?.avatarUrl ?: avatar,
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
                swipes = if (hasSwipes) Pair(swipeId.coerceIn(0, swipeTexts.size - 1), swipeTexts.size) else null,
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
    // Serializes every read-modify-write of the group JSONL so the user-message
    // save and the generation save can never interleave and drop each other.
    val saveMutex = remember { Mutex() }
    // Tracks the most recent (possibly in-flight) user-message save so generation
    // can wait for it before persisting its reply.
    var pendingUserSave by remember { mutableStateOf<Job?>(null) }

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
        pendingUserSave = scope.launch {
            runCatching {
                saveMutex.withLock {
                    val client = TavernCoreClient(baseUrl)
                    val chat = client.getGroupChatJsonl(activeChatId)
                    ensureGroupHeader(chat, userName, groupState.value.name, date)
                    chat.add(groupUserMessageMap(userName, trimmed, date))
                    client.saveGroupChatJsonl(activeChatId, chat)
                }
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
            ?: pickGroupSpeaker(
                memberAvatars = membersList.map { it.id },
                disabledMembers = disabled,
                lastSpeakerAvatar = lastSpeaker,
                activationStrategy = strategyInt,
                allowSelfResponses = groupState.value.selfResponses
            )
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
                // Make sure the just-sent user message is on disk before we read
                // the chat for our own append, so neither save clobbers the other.
                pendingUserSave?.join()
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
                    saveMutex.withLock {
                        val client = TavernCoreClient(baseUrl)
                        val chat = client.getGroupChatJsonl(activeChatId)
                        ensureGroupHeader(chat, userName, groupState.value.name, date)
                        chat.add(groupAssistantMessageMap(reply, date))
                        client.saveGroupChatJsonl(activeChatId, chat)
                    }
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

    // swipe 切换：更新显示文本并把 swipe_id + mes 落库（按消息序号定位 JSONL 行）。
    fun applySwipe(uiIndex: Int, newSwipeId: Int) {
        val msg = threadMessages.getOrNull(uiIndex) ?: return
        val texts = msg.swipeTexts ?: return
        if (newSwipeId !in texts.indices) return
        val newText = texts[newSwipeId]
        threadMessages[uiIndex] = msg.copy(swipes = Pair(newSwipeId, texts.size), text = newText)
        scope.launch {
            runCatching {
                saveMutex.withLock {
                    val client = TavernCoreClient(baseUrl)
                    val chat = client.getGroupChatJsonl(activeChatId)
                    var seen = -1
                    for (j in chat.indices) {
                        val line = chat[j] as? Map<*, *> ?: continue
                        if (!line.containsKey("mes")) continue
                        seen++
                        if (seen == uiIndex) {
                            val updated = LinkedHashMap<String, Any?>()
                            line.forEach { (k, v) -> updated[k.toString()] = v }
                            updated["swipe_id"] = newSwipeId
                            updated["mes"] = newText
                            chat[j] = updated
                            break
                        }
                    }
                    client.saveGroupChatJsonl(activeChatId, chat)
                }
            }.onFailure { onShowMessage(it.message ?: "保存 swipe 失败") }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            // 1. 群聊头部
            GroupChatHeader(
                group = groupState.value,
                members = membersList,
                baseUrl = baseUrl,
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
                        ChatDateChip(text = "今晚 20:55 · 雨", verticalPadding = 12.dp, bold = true)
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
                                    baseUrl = baseUrl,
                                    isLast = isLast,
                                    onSwipeLeft = {
                                        val i = threadMessages.indexOf(msg)
                                        val sw = msg.swipes
                                        if (i >= 0 && sw != null && sw.first > 0) applySwipe(i, sw.first - 1)
                                    },
                                    onSwipeRight = {
                                        val i = threadMessages.indexOf(msg)
                                        val sw = msg.swipes
                                        if (i >= 0 && sw != null && sw.first < sw.second - 1) applySwipe(i, sw.first + 1)
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
                                TypingRow(member = member, baseUrl = baseUrl)
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
                baseUrl = baseUrl,
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

        // 7. 切换对话下拉面板 (ConversationSwitcher) —— 点群名触发，列出真实群聊存档
        if (showConversationSwitcher) {
            val chatIds = groupState.value.chats.ifEmpty { listOf(activeChatId).filter { it.isNotBlank() } }
            val conversations = chatIds.map { cid ->
                DemoConversation(
                    id = cid,
                    title = cid,
                    kind = DemoConvKind.CHAT,
                    messageCount = 0,
                    preview = "",
                    timeInfo = "",
                    active = cid == activeChatId
                )
            }
            ConversationSwitcherSheet(
                group = groupState.value,
                members = membersList,
                baseUrl = baseUrl,
                conversations = conversations,
                onDismiss = { showConversationSwitcher = false },
                onSelectConversation = { cid ->
                    showConversationSwitcher = false
                    if (cid != activeChatId && !isGenerating) {
                        activeChatId = cid
                        scope.launch { loading = true; reload() }
                    }
                },
                onNewConversation = {
                    showConversationSwitcher = false
                    if (!isGenerating) {
                        scope.launch {
                            val newId = System.currentTimeMillis().toString()
                            val client = TavernCoreClient(baseUrl)
                            val group = runCatching { client.listGroups().find { it.id == groupId } }.getOrNull()
                            if (group != null) {
                                val updated = group.copy(chatId = newId, chats = group.chats + newId)
                                runCatching { client.editGroup(updated) }
                                    .onSuccess {
                                        activeChatId = newId
                                        loading = true
                                        reload()
                                    }
                                    .onFailure { onShowMessage(it.message ?: "新建对话失败") }
                            }
                        }
                    }
                },
                onManageAll = { showConversationSwitcher = false }
            )
        }
    }
}

