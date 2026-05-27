package io.github.sanitised.st.ui.prototype

import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.ChatSummary

data class PrototypeChatItem(
    val id: String,
    val characterId: String,
    val chatFile: String?,
    val title: String,
    val preview: String,
    val time: String,
    val initial: String,
    val favorite: Boolean,
    val unread: Int = 0,
    val streaming: Boolean = false
)

data class PrototypeCharacterCard(
    val id: String,
    val name: String,
    val subtitle: String,
    val tags: List<String>,
    val initial: String,
    val messageCount: Int,
    val favorite: Boolean,
    val gradient: List<Long>
)

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

fun ChatSummary.toPrototypeChatItem(index: Int): PrototypeChatItem {
    val parsedChatFile = id.substringAfter('/', missingDelimiterValue = "").ifBlank { null }
    return PrototypeChatItem(
        id = id,
        characterId = characterId,
        chatFile = parsedChatFile,
        title = characterName.ifBlank { characterId.readableName() },
        preview = lastMessage?.trim().orEmpty().ifBlank { "还没有消息，点开开始一段新对话。" },
        time = lastUpdated.toPrototypeTime(index),
        initial = characterName.initial(),
        favorite = isPinned,
        unread = 0,
        streaming = false
    )
}

fun CharacterSummary.toPrototypeCharacterCard(index: Int): PrototypeCharacterCard {
    return PrototypeCharacterCard(
        id = id,
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
        name = name.ifBlank { id.readableName() },
        subtitle = creatorNotes.ifBlank { description.linePreview().ifBlank { "SillyTavern 角色卡" } },
        tags = tags.filter { it.isNotBlank() }.take(2),
        initial = name.initial(),
        messageCount = 0,
        favorite = isFavorite,
        gradient = prototypeGradientFor(index)
    )
}

fun prototypeFallbackChats(): List<PrototypeChatItem> = listOf(
    PrototypeChatItem("aria/demo", "aria", null, "Aria", "那我多加了一份饼干哦，别告诉店长。", "刚才", "A", true, unread = 2),
    PrototypeChatItem("zoey/demo", "zoey", null, "Zoey", "你：等等，所以她真的把那个发到群里了？？", "12 分钟前", "Z", false),
    PrototypeChatItem("vex/demo", "vex", null, "Captain Vex", "*她的目光扫过控制台上闪烁的红色警示灯，没有移开。*", "今天 14:02", "V", true, streaming = true),
    PrototypeChatItem("eleanor/demo", "eleanor", null, "Eleanor Wright", "那一章的结尾，我想了三个版本。你来听听看？", "昨天", "E", false)
)

fun prototypeFallbackCharacters(): List<PrototypeCharacterCard> = listOf(
    PrototypeCharacterCard("aria", "Aria", "咖啡馆的女店员", listOf("女性", "日常"), "A", 247, true, prototypeGradientFor(0)),
    PrototypeCharacterCard("vex", "Captain Vex", "银河走私船 Wraith 号船长", listOf("科幻", "反英雄"), "V", 89, true, prototypeGradientFor(1)),
    PrototypeCharacterCard("eleanor", "Eleanor Wright", "维多利亚时代小说家", listOf("历史", "文学"), "E", 412, false, prototypeGradientFor(2)),
    PrototypeCharacterCard("kael", "Kael", "吟游精灵", listOf("奇幻", "精灵"), "K", 56, false, prototypeGradientFor(3)),
    PrototypeCharacterCard("zoey", "Zoey", "高中同桌 / 闺蜜", listOf("现代", "青春"), "Z", 1241, true, prototypeGradientFor(4)),
    PrototypeCharacterCard("archive", "档案室", "神秘档案员", listOf("悬疑", "非人"), "档", 12, false, prototypeGradientFor(5))
)

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

private fun Long.toPrototypeTime(index: Int): String {
    if (this <= 0L) {
        return listOf("刚才", "12 分钟前", "今天 14:02", "昨天", "3 天前")[index.floorMod(5)]
    }
    val age = System.currentTimeMillis() - this
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

private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other
