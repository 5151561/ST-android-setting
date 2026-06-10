package io.github.sanitised.st.chat.prompt

import org.junit.Assert.assertEquals
import org.junit.Test

class RegexEngineTest {

    @Test
    fun appliesRulesOnlyForRequestedScopeInPriorityOrder() {
        val rules = listOf(
            RegexRule(
                id = "strip-ooc",
                pattern = "\\[ooc:.*?]",
                replacement = "",
                scopes = setOf(RegexScope.INPUT),
                order = 20,
            ),
            RegexRule(
                id = "normalize-space",
                pattern = "\\s{2,}",
                replacement = " ",
                scopes = setOf(RegexScope.INPUT, RegexScope.OUTPUT),
                order = 30,
            ),
            RegexRule(
                id = "output-only",
                pattern = "dragon",
                replacement = "wyrm",
                scopes = setOf(RegexScope.OUTPUT),
                order = 10,
            ),
        )

        assertEquals(
            "Hello there",
            RegexEngine.apply("Hello  [ooc:secret]  there", RegexScope.INPUT, rules).trim(),
        )
        assertEquals(
            "A wyrm appears",
            RegexEngine.apply("A dragon   appears", RegexScope.OUTPUT, rules),
        )
    }

    @Test
    fun supportsReasoningAndDisplayRegexWithOptions() {
        val rules = listOf(
            RegexRule(
                id = "hide-thinking",
                pattern = "<think>.*?</think>",
                replacement = "",
                scopes = setOf(RegexScope.REASONING),
                options = setOf(RegexOption.DOT_MATCHES_ALL),
            ),
            RegexRule(
                id = "strip-markdown-bold",
                pattern = "\\*\\*(.*?)\\*\\*",
                replacement = "$1",
                scopes = setOf(RegexScope.DISPLAY),
            ),
        )

        assertEquals(
            "Visible",
            RegexEngine.apply("<think>one\ntwo</think>Visible", RegexScope.REASONING, rules),
        )
        assertEquals(
            "Bold text",
            RegexEngine.apply("**Bold** text", RegexScope.DISPLAY, rules),
        )
    }
}
