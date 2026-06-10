package io.github.sanitised.st.chat.contract

import io.github.sanitised.st.api.WorldInfoEntry
import io.github.sanitised.st.chat.contract.ContractFixtures.asStringKeyMap
import io.github.sanitised.st.chat.contract.ContractFixtures.str
import io.github.sanitised.st.chat.prompt.WorldInfoScanner
import org.junit.Test

/**
 * Phase 0 交付 4 的世界书部分：同一输入下对照"激活条目与顺序"。
 *
 * ST 侧产物推导依据（goldens 内 provenance 同步记录）：
 * - 激活与 key 匹配：`SillyTavern/public/scripts/world-info.js` `matchKeys` / `getScore`。
 * - 插入顺序：同文件 `checkWorldInfo`——`sort((a,b)=>b.order-a.order)` 后 `unshift`，
 *   等价于最终按 order **升序** 拼接，`join('\n')`。
 */
class WorldInfoActivationContractTest {

    @Test
    fun eldoriaActivationAndOrderMatchSt() {
        val entries = ContractFixtures.worldBook("eldoria.json")
        val golden = ContractFixtures.json("goldens/worldinfo-eldoria.json")
        val result = WorldInfoScanner.scan(entries, golden.str("scan_text"))

        ContractDiffs.assertContract("wi.eldoria.before", golden.str("st_before"), result.before)
        ContractDiffs.assertContract("wi.eldoria.after", golden.str("st_after"), result.after)
    }

    @Test
    fun frontierFeatureSemanticsDivergeOnlyWhereRegistered() {
        val entries = ContractFixtures.worldBook("frontier-station.json").associateBy { it.uid }
        val golden = ContractFixtures.json("goldens/worldinfo-frontier-station.json")
        val scanText = golden.str("scan_text")

        @Suppress("UNCHECKED_CAST")
        val cases = golden["cases"] as List<Any?>
        cases.forEach { raw ->
            val case = raw.asStringKeyMap()
            val entry = entries.getValue((case["uid"] as Number).toInt())
            ContractDiffs.assertContract(
                case.str("id"),
                case["st_active"] as Boolean,
                nativeActivates(entry, scanText),
            )
        }
    }

    private fun nativeActivates(entry: WorldInfoEntry, scanText: String): Boolean {
        val result = WorldInfoScanner.scan(listOf(entry), scanText)
        return (result.before + result.after).isNotBlank()
    }
}
