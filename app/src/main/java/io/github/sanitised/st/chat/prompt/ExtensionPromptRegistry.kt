package io.github.sanitised.st.chat.prompt

enum class ExtensionPromptPosition {
    BEFORE_PROMPT,
    IN_PROMPT,
    AFTER_PROMPT,
    IN_CHAT,
}

data class ExtensionPrompt(
    val id: String,
    val content: String,
    val position: ExtensionPromptPosition,
    val role: String = "system",
    val order: Int = 0,
    val depth: Int = 4,
    val enabled: Boolean = true,
    val triggers: Set<String> = emptySet(),
)

data class ExtensionPromptInterceptor(
    val id: String,
    val order: Int = 0,
    val enabled: Boolean = true,
    val intercept: (Map<String, Any?>) -> Map<String, Any?>,
)

class ExtensionPromptRegistry(
    private val prompts: List<ExtensionPrompt> = emptyList(),
    private val interceptors: List<ExtensionPromptInterceptor> = emptyList(),
) {
    fun register(prompt: ExtensionPrompt): ExtensionPromptRegistry =
        ExtensionPromptRegistry(prompts + prompt, interceptors)

    fun registerInterceptor(interceptor: ExtensionPromptInterceptor): ExtensionPromptRegistry =
        ExtensionPromptRegistry(prompts, interceptors + interceptor)

    fun collect(generationType: String): List<ExtensionPrompt> =
        prompts
            .filter { prompt ->
                prompt.enabled &&
                    prompt.content.isNotBlank() &&
                    (prompt.triggers.isEmpty() || generationType in prompt.triggers)
            }
            .sortedWith(
                compareBy<ExtensionPrompt> { it.position.ordinal }
                    .thenBy { it.order }
                    .thenBy { it.id }
            )

    fun intercept(payload: Map<String, Any?>): Map<String, Any?> =
        interceptors
            .asSequence()
            .filter { it.enabled }
            .sortedWith(compareBy<ExtensionPromptInterceptor> { it.order }.thenBy { it.id })
            .fold(payload) { current, interceptor -> interceptor.intercept(current) }
}
