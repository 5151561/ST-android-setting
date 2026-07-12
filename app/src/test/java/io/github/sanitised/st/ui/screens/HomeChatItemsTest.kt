package io.github.sanitised.st.ui.screens

import io.github.sanitised.st.api.CharacterSummary
import io.github.sanitised.st.api.ChatSummary
import io.github.sanitised.st.api.GroupSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeChatItemsTest {

    private fun chat(
        id: String,
        characterId: String = "Aria.png",
        lastUpdated: Long,
        pinned: Boolean = false
    ) = ChatSummary(
        id = id,
        characterId = characterId,
        characterName = "Aria",
        lastMessage = "hello",
        lastUpdated = lastUpdated,
        isPinned = pinned
    )

    private fun group(
        id: String,
        lastUpdated: Long,
        fav: Boolean = false
    ) = GroupSummary(
        id = id,
        name = "冒险小队",
        members = listOf("Aria.png", "Vex.png", "Mira.png", "Zed.png"),
        chatId = "chat-$id",
        lastUpdated = lastUpdated,
        isFavorite = fav,
        lastMessage = "group hello"
    )

    @Test
    fun mixesGroupsAndChatsSortedByLastUpdated() {
        val snapshot = LocalTavernLibrarySnapshot(
            recentChats = listOf(chat("Aria/old", lastUpdated = 100), chat("Aria/new", lastUpdated = 300)),
            groups = listOf(group("g1", lastUpdated = 200))
        )
        val items = buildHomeChatItems(snapshot)
        assertEquals(listOf("Aria/new", "group:g1", "Aria/old"), items.map { it.id })
        val groupItem = items[1]
        assertEquals(STChatKind.GROUP, groupItem.kind)
        assertEquals(4, groupItem.memberCount)
        assertEquals(3, groupItem.memberAvatars.size)
        assertEquals("chat-g1", groupItem.chatFile)
        assertEquals("group hello", groupItem.preview)
    }

    @Test
    fun unreadOnlyWhenSeenBeforeAndUpdatedAfter() {
        val snapshot = LocalTavernLibrarySnapshot(
            recentChats = listOf(chat("Aria/a", lastUpdated = 500)),
            groups = listOf(group("g1", lastUpdated = 500))
        )
        val neverSeen = buildHomeChatItems(snapshot, lastSeen = { 0L })
        assertFalse(neverSeen.any { it.unread })

        val seenEarlier = buildHomeChatItems(snapshot, lastSeen = { 400L })
        assertTrue(seenEarlier.all { it.unread })

        val seenLater = buildHomeChatItems(snapshot, lastSeen = { 600L })
        assertFalse(seenLater.any { it.unread })
    }

    @Test
    fun generatingChatShowsInProgressInsteadOfUnread() {
        val snapshot = LocalTavernLibrarySnapshot(
            recentChats = listOf(chat("Aria/a", lastUpdated = 500))
        )
        val key = chatSeenKey("Aria.png", "a")
        val items = buildHomeChatItems(snapshot, generatingKey = key, lastSeen = { 400L })
        assertTrue(items.single().inProgress)
        assertFalse(items.single().unread)
    }

    @Test
    fun favoriteComesFromCharacterOrGroupFlag() {
        val snapshot = LocalTavernLibrarySnapshot(
            characters = listOf(CharacterSummary(id = "Aria.png", name = "Aria", isFavorite = true)),
            recentChats = listOf(chat("Aria/a", lastUpdated = 100)),
            groups = listOf(group("g1", lastUpdated = 200, fav = true))
        )
        val items = buildHomeChatItems(snapshot)
        assertTrue(items.all { it.favorite })
    }

    @Test
    fun checkpointDetectedFromChatFileName() {
        val snapshot = LocalTavernLibrarySnapshot(
            recentChats = listOf(
                chat("Aria/Aria Checkpoint 2026-07-01", lastUpdated = 100),
                chat("Aria/plain", lastUpdated = 200)
            )
        )
        val items = buildHomeChatItems(snapshot)
        assertEquals(1, items.count { it.isCheckpoint })
    }

    @Test
    fun seenKeyNormalizesJsonlSuffix() {
        assertEquals(chatSeenKey("Aria.png", "a.jsonl"), chatSeenKey("Aria.png", "a"))
    }
}
