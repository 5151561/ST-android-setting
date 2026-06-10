package io.github.sanitised.st.chat.prompt

enum class RegexScope {
    INPUT,
    OUTPUT,
    REASONING,
    DISPLAY,
}

data class RegexRule(
    val id: String,
    val pattern: String,
    val replacement: String,
    val scopes: Set<RegexScope>,
    val order: Int = 0,
    val enabled: Boolean = true,
    val options: Set<RegexOption> = emptySet(),
)

object RegexEngine {

    fun apply(text: String, scope: RegexScope, rules: List<RegexRule>): String =
        rules
            .asSequence()
            .filter { it.enabled && scope in it.scopes && it.pattern.isNotBlank() }
            .sortedWith(compareBy<RegexRule> { it.order }.thenBy { it.id })
            .fold(text) { current, rule ->
                runCatching { Regex(rule.pattern, rule.options).replace(current, rule.replacement) }
                    .getOrElse { current }
            }
}
