package io.github.sanitised.st.chat.contract

import io.github.sanitised.st.chat.NativeChatJsonOps
import io.github.sanitised.st.chat.contract.ContractFixtures.asStringKeyMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 0 交付 5：消息操作 vs ST 的保存结果契约。
 *
 * ST 侧产物推导依据：
 * - 编辑：`script.js` messageEditDone——同步 `mes` 与 `swipes[swipe_id]`。
 * - 隐藏：`scripts/chats.js` hideChatMessageRange——置 `is_system`。
 * - swipe 删除：`script.js` `deleteSwipe`——splice swipes/swipe_info，按删除位与当前位调整 swipe_id。
 * - swipe 新建：`scripts/slash-commands.js` `addSwipeCallback`——默认追加但不切换。
 * - checkpoint / branch：`scripts/bookmarks.js` `createNewBookmark` / `createBranch`——
 *   截断复制 + `extra.bookmark_link` / `extra.branches` + `chat_metadata.main_chat`。
 */
class MessageOpsContractTest {

    private fun chat() = ContractFixtures.jsonl("chats/seraphina-main.jsonl")

    private fun message(chat: List<Any?>, messageId: Int): Map<String, Any?> =
        chat[messageId + 1].asStringKeyMap() // +1: header row

    @Test
    fun editUpdatesMesAndActiveSwipeLikeSt() {
        val chat = chat()
        NativeChatJsonOps.editMessage(chat, 1, "Edited reply.")
        val row = message(chat, 1)
        assertEquals("Edited reply.", row["mes"])
        val swipes = row["swipes"] as List<*>
        assertEquals("Edited reply.", swipes[0]) // swipe_id=0 是活动 swipe
        assertEquals("Rest now, traveler.", swipes[1]) // 非活动 swipe 不动
    }

    @Test
    fun deleteRemovesRowLikeSt() {
        val chat = chat()
        val before = chat.size
        NativeChatJsonOps.deleteMessage(chat, 2)
        assertEquals(before - 1, chat.size)
        assertFalse(chat.any { it.asStringKeyMap()["mes"] == "Can you guide me home?" })
    }

    @Test
    fun hideTogglesIsSystemLikeSt() {
        val chat = chat()
        NativeChatJsonOps.setHidden(chat, 0, true)
        assertEquals(true, message(chat, 0)["is_system"])
        NativeChatJsonOps.setHidden(chat, 0, false)
        assertEquals(false, message(chat, 0)["is_system"])
    }

    @Test
    fun swipeRightWithinExistingSwipesMatchesSt() {
        val chat = chat()
        NativeChatJsonOps.switchSwipe(chat, 1, delta = 1)
        val row = message(chat, 1)
        assertEquals(1, row["swipe_id"])
        assertEquals("Rest now, traveler.", row["mes"])
    }

    @Test
    fun swipeLeftAtZeroDiffersOnlyAsRegistered() {
        val chat = chat()
        NativeChatJsonOps.switchSwipe(chat, 1, delta = -1)
        // ST 在 swipe_id=0 时左滑不动作；native 环绕到最后一个 swipe。
        ContractDiffs.assertContract(
            "ops.swipe.wrap-around",
            0,
            message(chat, 1)["swipe_id"],
        )
    }

    @Test
    fun deleteSwipeAdjustsSelectionLikeSt() {
        val chat = chat()
        // 先切到 swipe 1，再删除 swipe 0：ST deleteSwipe 规则 swipeId < current → current-1。
        NativeChatJsonOps.switchSwipe(chat, 1, delta = 1)
        assertTrue(NativeChatJsonOps.deleteSwipe(chat, 1, swipeId = 0))
        val row = message(chat, 1)
        assertEquals(0, row["swipe_id"])
        assertEquals(listOf("Rest now, traveler."), row["swipes"])
        assertEquals("Rest now, traveler.", row["mes"])
    }

    @Test
    fun createSwipeSwitchBehaviorDiffersOnlyAsRegistered() {
        val chat = chat()
        NativeChatJsonOps.createSwipe(chat, 3, "An alternate farewell.")
        val row = message(chat, 3)
        val swipes = row["swipes"] as List<*>
        assertEquals(2, swipes.size) // 追加本身与 ST /addswipe 一致
        assertEquals("An alternate farewell.", swipes[1])
        // ST /addswipe 默认不切换（swipe_id 不变）；native 切换到新 swipe。
        ContractDiffs.assertContract("ops.create-swipe.switch-and-info", 0, row["swipe_id"])
    }

    @Test
    fun checkpointCopiesThroughMessageAndLinksLikeSt() {
        val chat = chat()
        val copy = NativeChatJsonOps.createCheckpoint(
            chat = chat,
            currentChatName = "seraphina-main.jsonl",
            messageId = 1,
            name = "seraphina-main - Checkpoint #1",
        )
        assertEquals("seraphina-main - Checkpoint #1", copy.linkedName)
        assertEquals(3, copy.chatCopy.size) // header + 消息 0..1
        val metadata = copy.chatCopy.first().asStringKeyMap()["chat_metadata"].asStringKeyMap()
        assertEquals("seraphina-main", metadata["main_chat"])
        val extra = message(chat, 1)["extra"].asStringKeyMap()
        assertEquals("seraphina-main - Checkpoint #1", extra["bookmark_link"])
    }

    @Test
    fun branchAppendsBranchListLikeSt() {
        val chat = chat()
        val copy = NativeChatJsonOps.createBranch(
            chat = chat,
            currentChatName = "seraphina-main.jsonl",
            messageId = 2,
            name = "seraphina-main - Branch #1",
        )
        assertEquals(4, copy.chatCopy.size) // header + 消息 0..2
        val extra = message(chat, 2)["extra"].asStringKeyMap()
        assertEquals(listOf("seraphina-main - Branch #1"), extra["branches"])
        val metadata = copy.chatCopy.first().asStringKeyMap()["chat_metadata"].asStringKeyMap()
        assertEquals("seraphina-main", metadata["main_chat"])
    }

    @Test
    fun reasoningEditAndDeleteKeepExtraShape() {
        val chat = chat()
        NativeChatJsonOps.setReasoning(chat, 1, "Revised chain of thought.")
        assertEquals(
            "Revised chain of thought.",
            message(chat, 1)["extra"].asStringKeyMap()["reasoning"],
        )
        NativeChatJsonOps.setReasoning(chat, 1, null)
        assertFalse(message(chat, 1)["extra"].asStringKeyMap().containsKey("reasoning"))
    }
}
