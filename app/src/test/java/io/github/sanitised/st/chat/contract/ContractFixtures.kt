package io.github.sanitised.st.chat.contract

import io.github.sanitised.st.api.CharacterDetail
import io.github.sanitised.st.api.WorldInfoEntry
import io.github.sanitised.st.chat.ChatMessage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.yaml.snakeyaml.Yaml

/**
 * Loader for the Phase 0 chat contract fixtures (`src/test/resources/chat-contract-fixtures`).
 *
 * The fixture set freezes "native vs SillyTavern" contract inputs: character cards,
 * persona, world books, settings (including verbatim upstream presets), chat JSONL,
 * group chat, and extension settings. Golden files under `goldens/` carry the expected
 * ST-side products, each annotated with the ST source (`provenance`) it was derived from.
 */
object ContractFixtures {
    private const val ROOT = "chat-contract-fixtures"

    fun text(path: String): String =
        requireNotNull(ContractFixtures::class.java.classLoader.getResourceAsStream("$ROOT/$path")) {
            "Missing contract fixture: $ROOT/$path"
        }.bufferedReader().use { it.readText() }

    @Suppress("UNCHECKED_CAST")
    fun json(path: String): Map<String, Any?> =
        Yaml().load<Any?>(text(path)).asStringKeyMap()

    fun jsonl(path: String): MutableList<Any?> =
        text(path).lineSequence()
            .filter { it.isNotBlank() }
            .map { Yaml().load<Any?>(it) }
            .toMutableList()

    fun character(file: String): CharacterDetail {
        val map = json("characters/$file")
        return CharacterDetail(
            id = map.str("id"),
            name = map.str("name"),
            description = map.str("description"),
            personality = map.str("personality"),
            scenario = map.str("scenario"),
            firstMessage = map.str("first_mes"),
            messageExample = map.str("mes_example"),
            systemPrompt = map.str("system_prompt"),
            world = map.str("world"),
        )
    }

    fun worldBook(file: String): List<WorldInfoEntry> {
        val map = json("worldinfo/$file")
        val entries = (map["entries"] as Map<*, *>).values
        return entries.map { raw ->
            val entry = raw.asStringKeyMap()
            WorldInfoEntry(
                uid = entry.int("uid"),
                keys = entry.strList("key"),
                secondaryKeys = entry.strList("keysecondary"),
                comment = entry.str("comment"),
                content = entry.str("content"),
                order = entry.int("order"),
                position = entry.int("position"),
                constant = entry["constant"] == true,
                selective = entry["selective"] == true,
                disabled = entry["disable"] == true,
                raw = entry,
            )
        }.sortedBy { it.uid }
    }

    /** Visible chat history (header skipped) as the engine-facing [ChatMessage] list. */
    fun chatHistory(file: String): List<ChatMessage> {
        val rows = jsonl("chats/$file")
        return rows
            .drop(if (rows.firstOrNull().isChatHeader()) 1 else 0)
            .mapIndexed { index, raw ->
                val row = raw.asStringKeyMap()
                ChatMessage(
                    id = index,
                    name = row.str("name"),
                    mes = row.str("mes"),
                    isUser = row["is_user"] == true,
                    isSystem = row["is_system"] == true,
                    sendDate = row.str("send_date"),
                    swipeId = row.int("swipe_id"),
                    swipes = row.strList("swipes"),
                    extra = JSONObject(row["extra"].asStringKeyMap()),
                )
            }
    }

    private fun Any?.isChatHeader(): Boolean =
        (this as? Map<*, *>)?.containsKey("chat_metadata") == true

    fun Any?.asStringKeyMap(): Map<String, Any?> =
        (this as? Map<*, *>)?.entries?.associate { (k, v) -> k.toString() to v } ?: emptyMap()

    fun Map<String, Any?>.str(key: String): String = (this[key] as? String).orEmpty()

    fun Map<String, Any?>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: 0

    fun Map<String, Any?>.strList(key: String): List<String> =
        (this[key] as? List<*>)?.map { it?.toString() ?: "" } ?: emptyList()
}

/**
 * Machine-enforced known-diff matrix (`chat-contract-fixtures/known-diffs.json`).
 *
 * Every native-vs-ST comparison goes through [assertContract]:
 *  - values equal + id NOT in the matrix -> pass (contract holds);
 *  - values equal + id in the matrix -> FAIL (stale entry, must be removed);
 *  - values differ + id in the matrix -> pass (known, documented diff);
 *  - values differ + id NOT in the matrix -> FAIL (undocumented divergence).
 */
object ContractDiffs {
    private val matrix: Map<String, Any?> by lazy { ContractFixtures.json("known-diffs.json") }

    fun <T> assertContract(id: String, stExpected: T, native: T) {
        val registered = matrix.containsKey(id)
        val equal = normalize(stExpected) == normalize(native)
        when {
            equal && registered -> fail(
                "known-diff '$id' 的 native 与 ST 产物已经一致，请把它从 known-diffs.json 中移除"
            )
            !equal && !registered -> fail(
                "native 与 ST 产物不一致，且未在 known-diffs.json 登记 '$id'：\nST  =$stExpected\nnative=$native"
            )
        }
    }

    fun assertAllEntriesDocumented() {
        assertTrue("known-diffs.json 不应为空（空了请直接删除矩阵相关断言）", matrix.isNotEmpty())
        matrix.forEach { (id, value) ->
            val entry = (value as? Map<*, *>) ?: fail("known-diff '$id' 必须是对象") as Nothing
            assertTrue("known-diff '$id' 缺少非空 reason", (entry["reason"] as? String).orEmpty().isNotBlank())
            assertTrue("known-diff '$id' 缺少非空 st_source", (entry["st_source"] as? String).orEmpty().isNotBlank())
            assertTrue("known-diff '$id' 缺少非空 closes_in", (entry["closes_in"] as? String).orEmpty().isNotBlank())
        }
    }

    /** 矩阵不允许孤儿条目：登记的 id 必须正好是契约测试行使的 id 集合。 */
    fun assertMatrixIdsAreExactly(exercisedIds: Set<String>) {
        assertEquals(exercisedIds.sorted(), matrix.keys.sorted())
    }

    private fun normalize(value: Any?): Any? =
        when (value) {
            is Number -> value.toDouble()
            is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to normalize(v) }
            is List<*> -> value.map { normalize(it) }
            is Set<*> -> value.map { normalize(it) }.toSet()
            else -> value
        }
}
