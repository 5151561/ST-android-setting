package io.github.sanitised.st.ui.screens

import io.github.sanitised.st.NodeState
import io.github.sanitised.st.NodeStatus
import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.ChatSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class STModelsTest {
    @Test
    fun chatSummaryMapsToSTConversationRow() {
        val row = ChatSummary(
            id = "aria/session",
            characterId = "aria.png",
            characterName = "Aria",
            avatarUrl = "aria.png",
            lastMessage = "那我多加了一份饼干哦，别告诉店长。",
            lastUpdated = 1_700_000_000_000L,
            isPinned = true
        ).toSTChatItem()

        assertEquals("aria/session", row.id)
        assertEquals("aria.png", row.avatarUrl)
        assertEquals("Aria", row.title)
        assertEquals("那我多加了一份饼干哦，别告诉店长。", row.preview)
        assertEquals("A", row.initial)
        assertTrue(row.favorite)
        assertEquals(STChatKind.DIRECT, row.kind)
    }

    @Test
    fun chatSummaryWithoutTimestampDoesNotInventRelativeTime() {
        val row = ChatSummary(
            id = "aria/session",
            characterId = "aria.png",
            characterName = "Aria",
            lastUpdated = 0
        ).toSTChatItem()

        assertEquals("未知时间", row.time)
    }

    @Test
    fun relativeTimeLabelCoversSTListRows() {
        val now = 1_700_000_000_000L

        assertEquals("未知时间", stRelativeTimeLabel(timestampMs = 0L, nowMs = now))
        assertEquals("刚才", stRelativeTimeLabel(timestampMs = now - 30_000L, nowMs = now))
        assertEquals("12 分钟前", stRelativeTimeLabel(timestampMs = now - 12 * 60_000L, nowMs = now))
        assertEquals("今天", stRelativeTimeLabel(timestampMs = now - 2 * 60 * 60_000L, nowMs = now))
        assertEquals("昨天", stRelativeTimeLabel(timestampMs = now - 25 * 60 * 60_000L, nowMs = now))
        assertEquals("3 天前", stRelativeTimeLabel(timestampMs = now - 3 * 24 * 60 * 60_000L, nowMs = now))
    }

    @Test
    fun characterSummaryMapsToSTCard() {
        val card = CharacterSummary(
            id = "vex.png",
            name = "Captain Vex",
            avatarUrl = "vex.png",
            tags = listOf("科幻", "反英雄", "冒险"),
            creatorNotes = "银河走私船 Wraith 号船长",
            isFavorite = true,
            chatSize = 89
        ).toSTCharacterCard(index = 1)

        assertEquals("vex.png", card.id)
        assertEquals("vex.png", card.avatarUrl)
        assertEquals("Captain Vex", card.name)
        assertEquals("银河走私船 Wraith 号船长", card.subtitle)
        assertEquals(listOf("科幻", "反英雄"), card.tags)
        assertEquals("C", card.initial)
        assertEquals(89, card.messageCount)
        assertTrue(card.favorite)
    }

    @Test
    fun avatarImageUrlResolvesSillyTavernCharacterAndStaticPaths() {
        val baseUrl = "http://127.0.0.1:8000/"

        assertEquals(
            "http://127.0.0.1:8000/thumbnail?type=avatar&file=Aria.png",
            stAvatarImageUrl(baseUrl, "Aria.png")
        )
        assertEquals(
            "http://127.0.0.1:8000/img/ai4.png",
            stAvatarImageUrl(baseUrl, "img/ai4.png")
        )
        assertEquals(
            "file:/tmp/Aria.png",
            stAvatarImageUrl(baseUrl, "file:/tmp/Aria.png")
        )
    }

    @Test
    fun characterTagFiltersPreferFrequentUserFacingTags() {
        val filters = stCharacterTagFilters(
            listOf(
                CharacterSummary(id = "a.png", name = "A", tags = listOf("v2", "科幻", "日常")),
                CharacterSummary(id = "b.png", name = "B", tags = listOf("not_dead", "科幻", "奇幻")),
                CharacterSummary(id = "c.png", name = "C", tags = listOf("日常", "科幻", "内部:debug", "  "))
            )
        )

        assertEquals(listOf("科幻", "日常", "奇幻"), filters)
    }

    @Test
    fun drawerStateReflectsNodeStatus() {
        val state = STDrawerState.from(
            status = NodeStatus(NodeState.RUNNING, "Running", port = 8000),
            stLabel = "SillyTavern 1.13.0",
            nodeLabel = "Node 23.10"
        )

        assertEquals("我（默认）", state.personaName)
        assertEquals("已连接", state.connectionEyebrow)
        assertEquals("SillyTavern 1.13.0 · Node 23.10", state.connectionLabel)
        assertTrue(state.connected)
    }
}
