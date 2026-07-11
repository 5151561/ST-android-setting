package io.github.sanitised.st.ui.screens

import androidx.compose.runtime.Immutable
import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.ChatSummary

enum class PrototypeChatKind {
    DIRECT,
    GROUP
}

@Immutable
data class PrototypeChatItem(
    val id: String,
    val characterId: String,
    val chatFile: String?,
    val avatarUrl: String? = null,
    val title: String,
    val preview: String,
    val time: String,
    val initial: String,
    val favorite: Boolean,
    val kind: PrototypeChatKind = PrototypeChatKind.DIRECT
)

@Immutable
data class PrototypeCharacterCard(
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

@Immutable
data class PrototypeDrawerState(
    val personaName: String,
    val personaInitial: String,
    val connectionEyebrow: String,
    val connectionLabel: String,
    val connected: Boolean
) {
    companion object {
        fun from(
            status: NodeStatus,
            stLabel: String,
            nodeLabel: String
        ): PrototypeDrawerState {
            val running = status.state == NodeState.RUNNING
            return PrototypeDrawerState(
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
                connected = running
            )
        }
    }
}

fun ChatSummary.toPrototypeChatItem(): PrototypeChatItem {
    val parsedChatFile = id.substringAfter('/', missingDelimiterValue = "").ifBlank { null }
    return PrototypeChatItem(
        id = id,
        characterId = characterId,
        chatFile = parsedChatFile,
        avatarUrl = avatarUrl ?: characterId.takeIf { it.isNotBlank() },
        title = characterName.ifBlank { characterId.readableName() },
        preview = lastMessage?.trim().orEmpty().ifBlank { "还没有消息，点开开始一段新对话。" },
        time = prototypeRelativeTimeLabel(lastUpdated),
        initial = characterName.initial(),
        favorite = isPinned,
        kind = PrototypeChatKind.DIRECT
    )
}

fun prototypeCharacterTagFilters(
    characters: List<CharacterSummary>,
    limit: Int = 4
): List<String> {
    data class TagCount(val label: String, val count: Int, val firstIndex: Int)

    val counts = linkedMapOf<String, TagCount>()
    var index = 0
    characters.flatMap { it.tags }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filter { it.isVisiblePrototypeTagFilter() }
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

fun CharacterSummary.toPrototypeCharacterCard(index: Int): PrototypeCharacterCard {
    return PrototypeCharacterCard(
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
        gradient = prototypeGradientFor(index)
    )
}

fun CharacterDetail.toPrototypeCharacterCard(index: Int): PrototypeCharacterCard {
    return PrototypeCharacterCard(
        id = id,
        avatarUrl = avatarUrl,
        name = name.ifBlank { id.readableName() },
        subtitle = creatorNotes.ifBlank { description.linePreview().ifBlank { "SillyTavern 角色卡" } },
        tags = tags.filter { it.isNotBlank() }.take(2),
        initial = name.initial(),
        messageCount = 0,
        favorite = isFavorite,
        gradient = prototypeGradientFor(index)
    )
}

fun prototypeGradientFor(index: Int): List<Long> {
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

internal fun prototypeRelativeTimeLabel(
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

private fun String.isVisiblePrototypeTagFilter(): Boolean {
    val normalized = lowercase()
    if (normalized in hiddenPrototypeTagFilters) return false
    if (normalized.matches(Regex("v\\d+"))) return false
    if (normalized.startsWith("内部:") || normalized.startsWith("internal:")) return false
    return true
}

private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other

private val hiddenPrototypeTagFilters = setOf(
    "not_dead",
    "dead",
    "scenario",
    "character",
    "assistant",
    "user"
)
