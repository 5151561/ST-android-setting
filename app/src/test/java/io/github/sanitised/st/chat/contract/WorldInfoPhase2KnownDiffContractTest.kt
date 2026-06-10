package io.github.sanitised.st.chat.contract

import io.github.sanitised.st.api.WorldInfoEntry
import io.github.sanitised.st.chat.prompt.WorldInfoEngine
import org.junit.Test

class WorldInfoPhase2KnownDiffContractTest {

    @Test
    fun probabilityRandomnessDiffersOnlyAsRegistered() {
        ContractDiffs.assertContract(
            "wi.probability.randomness",
            "ST rolls Math.random() for every generation",
            "native uses a deterministic uid-derived roll",
        )
    }

    @Test
    fun inclusionGroupSelectionDiffersOnlyAsRegistered() {
        ContractDiffs.assertContract(
            "wi.inclusion-group.selection",
            "ST chooses one group winner with groupOverride/groupWeight/multi-group semantics",
            "native chooses order max per literal group and keeps groupOverride outside grouping",
        )
    }

    @Test
    fun nonPromptWorldInfoPositionsDifferOnlyAsRegistered() {
        val result = WorldInfoEngine.scan(
            entries = listOf(
                WorldInfoEntry(
                    uid = 1,
                    content = "Depth lore.",
                    constant = true,
                    position = 4,
                )
            ),
            history = listOf("Alex: hello"),
        )

        ContractDiffs.assertContract(
            "wi.position.non-prompt-injections",
            "ST keeps position 2-6 entries for AN/depth/EM injection",
            result.before + result.after,
        )
    }
}
