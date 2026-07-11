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
import io.github.sanitised.st.ui.screens.PrototypeAvatar
import io.github.sanitised.st.ui.screens.prototypeAvatarImageUrl
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
// 真实数据映射与群聊 JSONL 持久化辅助
// ─────────────────────────────────────────────────────────────

/** 加载完成前的占位群（避免 UI 读到 demo 数据）。 */
internal fun emptyDemoGroup(id: String): DemoGroup = DemoGroup(
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
internal fun groupStrategyName(value: Int): String = when (value) {
    1 -> "list"
    2 -> "manual"
    3 -> "pooled"
    else -> "natural"
}

internal fun GroupSummary.toDemoGroup(): DemoGroup = DemoGroup(
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
    tags = emptyList(),
    chats = chats
)

/** ST 的 send_date 多为 "May 26, 2026 12:00pm" 风格；尽力提取 HH:mm，否则原样回退。 */
internal fun formatGroupTime(sendDate: String?): String {
    val raw = sendDate?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    val match = Regex("(\\d{1,2}:\\d{2})").find(raw)
    return match?.groupValues?.get(1) ?: raw.take(16)
}

internal fun groupSendDate(): String =
    SimpleDateFormat("MMMM d, yyyy h:mma", Locale.ENGLISH).format(Date()).lowercase(Locale.ENGLISH)

/** 群聊 JSONL 为空时写入首行 header（与 NativeChatEngine 1v1 语义一致）。 */
internal fun ensureGroupHeader(chat: MutableList<Any?>, userName: String, groupName: String, date: String) {
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

internal fun groupUserMessageMap(userName: String, text: String, date: String): Map<String, Any?> =
    linkedMapOf(
        "name" to userName,
        "is_user" to true,
        "is_system" to false,
        "send_date" to date,
        "mes" to text,
        "extra" to emptyMap<String, Any?>()
    )

internal fun groupAssistantMessageMap(reply: GroupReply, date: String): Map<String, Any?> =
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
