package io.github.sanitised.st.ui.screens

import androidx.compose.runtime.Immutable
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.ChatSummary
import io.github.sanitised.st.api.GroupSummary
import io.github.sanitised.st.api.SecretProviderState

enum class STChatKind {
    DIRECT,
    GROUP
}

@Immutable
data class STChatItem(
    val id: String,
    val characterId: String,
    val chatFile: String?,
    val avatarUrl: String? = null,
    val title: String,
    val preview: String,
    val time: String,
    val initial: String,
    val favorite: Boolean,
    val kind: STChatKind = STChatKind.DIRECT,
    val memberCount: Int = 0,
    val memberAvatars: List<String> = emptyList(),
    val unread: Boolean = false,
    val inProgress: Boolean = false,
    val isCheckpoint: Boolean = false
)

@Immutable
data class STCharacterCard(
    val id: String,
    val avatarUrl: String? = null,
    val name: String,
    val subtitle: String,
    val tags: List<String>,
    val initial: String,
    val messageCount: Int,
    val favorite: Boolean,
    val gradient: List<Long>
)

/** 抽屉头部的真实账户信息:当前扮演者 + 当前 API 连接,由 settings/secrets 拉取。 */
@Immutable
data class STDrawerAccount(
    val personaName: String,
    val providerLabel: String,
    val model: String,
    val secretConfigured: Boolean
)

fun buildSTDrawerAccount(
    settings: Map<String, Any?>,
    secrets: List<SecretProviderState>
): STDrawerAccount {
    val userAvatar = settings["user_avatar"] as? String ?: ""
    val personas = ((settings["power_user"] as? Map<*, *>)?.get("personas") as? Map<*, *>)
    val personaName = (personas?.get(userAvatar) as? String)?.trim()?.ifBlank { null }
        ?: userAvatar.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { "我" }
    val connection = buildApiConnectionUiState(
        settings = settings,
        secrets = secrets,
        serviceRunning = true
    )
    return STDrawerAccount(
        personaName = personaName,
        providerLabel = connection.activeProvider.label,
        model = connection.activeModel,
        secretConfigured = connection.activeProvider.hasConfiguredSecret
    )
}

@Immutable
data class STDrawerState(
    val personaName: String,
    val personaInitial: String,
    val connectionEyebrow: String,
    val connectionLabel: String,
    val connectionInitial: String,
    val connected: Boolean
) {
    companion object {
        fun from(
            status: NodeStatus,
            stLabel: String,
            nodeLabel: String,
            account: STDrawerAccount? = null
        ): STDrawerState {
            val running = status.state == NodeState.RUNNING
            if (running && account != null) {
                return STDrawerState(
                    personaName = account.personaName,
                    personaInitial = account.personaName.trim().firstOrNull()?.toString() ?: "我",
                    connectionEyebrow = if (account.secretConfigured) {
                        "已连接 · ${account.providerLabel}"
                    } else {
                        "未配置密钥 · ${account.providerLabel}"
                    },
                    connectionLabel = account.model,
                    connectionInitial = account.providerLabel.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C",
                    connected = account.secretConfigured
                )
            }
            return STDrawerState(
                personaName = "我（默认）",
                personaInitial = "我",
                connectionEyebrow = if (running) "已连接" else "本地服务",
                connectionLabel = if (running) {
                    "$stLabel · $nodeLabel"
                } else {
                    when (status.state) {
                        NodeState.STARTING -> "正在启动 · :${status.port}"
                        NodeState.ERROR -> "启动异常 · :${status.port}"
                        else -> "未运行 · :${status.port}"
                    }
                },
                connectionInitial = "ST",
                connected = running
            )
        }
    }
}

fun ChatSummary.toSTChatItem(): STChatItem {
    val parsedChatFile = id.substringAfter('/', missingDelimiterValue = "").ifBlank { null }
    return STChatItem(
        id = id,
        characterId = characterId,
        chatFile = parsedChatFile,
        avatarUrl = avatarUrl ?: characterId.takeIf { it.isNotBlank() },
        title = characterName.ifBlank { characterId.readableName() },
        preview = lastMessage?.trim().orEmpty().ifBlank { "还没有消息，点开开始一段新对话。" },
        time = stRelativeTimeLabel(lastUpdated),
        initial = characterName.initial(),
        favorite = isPinned,
        kind = STChatKind.DIRECT,
        isCheckpoint = parsedChatFile?.contains("checkpoint", ignoreCase = true) == true
    )
}

fun GroupSummary.toSTChatItem(): STChatItem {
    return STChatItem(
        id = "group:$id",
        characterId = id,
        chatFile = chatId.ifBlank { null },
        avatarUrl = avatarUrl.takeIf { it.isNotBlank() },
        title = name,
        preview = lastMessage?.trim().orEmpty().ifBlank { "还没有消息，点开开始群聊。" },
        time = stRelativeTimeLabel(lastUpdated),
        initial = name.initial(),
        favorite = isFavorite,
        kind = STChatKind.GROUP,
        memberCount = members.size,
        memberAvatars = members.take(3)
    )
}

/** 未读打点的键:单聊按「角色 + 聊天文件」,群聊按群 id。 */
fun chatSeenKey(characterId: String, chatFile: String?): String =
    "char:$characterId:${chatFile.orEmpty().removeSuffix(".jsonl")}"

fun groupSeenKey(groupId: String): String = "group:$groupId"

/**
 * 首页对话列表:单聊 + 群聊按最后更新时间混排(对齐设计稿 ChatList 的单一 feed),
 * 并标注未读(上次离开后有更新)与进行中(后台仍在生成)。
 */
fun buildHomeChatItems(
    snapshot: LocalTavernLibrarySnapshot,
    generatingKey: String? = null,
    lastSeen: (String) -> Long = { 0L }
): List<STChatItem> {
    val favoriteCharacters = snapshot.characters
        .filter { it.isFavorite }
        .map { it.id }
        .toSet()

    fun decorate(item: STChatItem, key: String, lastUpdated: Long): STChatItem {
        val inProgress = key == generatingKey
        val seenAt = lastSeen(key)
        return item.copy(
            inProgress = inProgress,
            // 从未打开过的会话不算未读,避免首启全列表点亮
            unread = !inProgress && seenAt > 0L && lastUpdated > seenAt
        )
    }

    val chatEntries = snapshot.recentChats.map { chat ->
        val item = chat.toSTChatItem()
        val decorated = decorate(item, chatSeenKey(chat.characterId, item.chatFile), chat.lastUpdated)
        chat.lastUpdated to decorated.copy(
            favorite = decorated.favorite || chat.characterId in favoriteCharacters
        )
    }
    val groupEntries = snapshot.groups.map { group ->
        group.lastUpdated to decorate(group.toSTChatItem(), groupSeenKey(group.id), group.lastUpdated)
    }

    return (chatEntries + groupEntries)
        .sortedByDescending { (lastUpdated, _) -> lastUpdated }
        .map { (_, item) -> item }
}

fun stCharacterTagFilters(
    characters: List<CharacterSummary>,
    limit: Int = 4
): List<String> {
    data class TagCount(val label: String, val count: Int, val firstIndex: Int)

    val counts = linkedMapOf<String, TagCount>()
    var index = 0
    characters.flatMap { it.tags }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filter { it.isVisibleSTTagFilter() }
        .forEach { tag ->
            val key = tag.lowercase()
            val current = counts[key]
            counts[key] = if (current == null) {
                TagCount(label = tag, count = 1, firstIndex = index)
            } else {
                current.copy(count = current.count + 1)
            }
            index++
        }

    return counts.values
        .sortedWith(
            compareByDescending<TagCount> { it.count }
                .thenBy { it.firstIndex }
        )
        .map { it.label }
        .take(limit)
}

fun CharacterSummary.toSTCharacterCard(index: Int): STCharacterCard {
    return STCharacterCard(
        id = id,
        avatarUrl = avatarUrl,
        name = name.ifBlank { id.readableName() },
        subtitle = creatorNotes.ifBlank {
            when {
                tags.isNotEmpty() -> tags.take(3).joinToString(" · ")
                characterVersion.isNotBlank() -> characterVersion
                else -> "SillyTavern 角色卡"
            }
        },
        tags = tags.filter { it.isNotBlank() }.take(2),
        initial = name.initial(),
        messageCount = chatSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        favorite = isFavorite,
        gradient = stGradientFor(index)
    )
}

fun CharacterDetail.toSTCharacterCard(index: Int): STCharacterCard {
    return STCharacterCard(
        id = id,
        avatarUrl = avatarUrl,
        name = name.ifBlank { id.readableName() },
        subtitle = creatorNotes.ifBlank { description.linePreview().ifBlank { "SillyTavern 角色卡" } },
        tags = tags.filter { it.isNotBlank() }.take(2),
        initial = name.initial(),
        messageCount = 0,
        favorite = isFavorite,
        gradient = stGradientFor(index)
    )
}

fun stGradientFor(index: Int): List<Long> {
    val gradients = listOf(
        listOf(0xFFFFD7B0, 0xFFA55A2A),
        listOf(0xFF8FB6C6, 0xFF2F5567),
        listOf(0xFFD8C4A3, 0xFF6B4E2B),
        listOf(0xFFC8E5B7, 0xFF3D6B3A),
        listOf(0xFFF5B0C8, 0xFFA8366A),
        listOf(0xFFB8B2A4, 0xFF46443B)
    )
    return gradients[index.floorMod(gradients.size)]
}

private fun String.initial(): String =
    trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

private fun String.readableName(): String =
    substringBeforeLast('.').replace('_', ' ').trim().ifBlank { this }

private fun String.linePreview(): String =
    lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

internal fun stRelativeTimeLabel(
    timestampMs: Long,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (timestampMs <= 0L) {
        return "未知时间"
    }
    val age = (nowMs - timestampMs).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = minute * 60
    val day = hour * 24
    return when {
        age < minute -> "刚才"
        age < hour -> "${age / minute} 分钟前"
        age < day -> "今天"
        age < day * 2 -> "昨天"
        else -> "${(age / day).coerceAtLeast(1)} 天前"
    }
}

private fun String.isVisibleSTTagFilter(): Boolean {
    val normalized = lowercase()
    if (normalized in hiddenSTTagFilters) return false
    if (normalized.matches(Regex("v\\d+"))) return false
    if (normalized.startsWith("内部:") || normalized.startsWith("internal:")) return false
    return true
}

private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other

private val hiddenSTTagFilters = setOf(
    "not_dead",
    "dead",
    "scenario",
    "character",
    "assistant",
    "user"
)
