package io.github.sanitised.st.chat.prompt

object StopStringBuilder {

    fun build(
        instruct: InstructSettings,
        context: ContextTemplateSettings,
        userName: String,
        charName: String,
    ): List<String> {
        val result = LinkedHashSet<String>()

        if (context.namesAsStopStrings && userName.isNotBlank()) {
            result += "\n$userName:"
        }

        if (instruct.enabled) {
            val sequences = mutableListOf(instruct.stopSequence)
            if (instruct.sequencesAsStopStrings) {
                sequences += instruct.inputSequence.replaceName(userName)
                sequences += instruct.outputSequence.replaceName(charName)
                sequences += instruct.firstOutputSequence.replaceName(charName)
                sequences += instruct.lastOutputSequence.replaceName(charName)
                sequences += instruct.systemSequence.replaceName("System")
                sequences += instruct.lastSystemSequence.replaceName("System")
            }
            sequences
                .joinToString("\n")
                .split('\n')
                .forEach { addInstructSequence(result, it, instruct, userName, charName) }
        }

        if (context.useStopStrings) {
            context.chatStart.takeIf { it.isNotBlank() }?.let {
                result += "\n" + substituteMacros(it, userName, charName, charName)
            }
            context.exampleSeparator.takeIf { it.isNotBlank() }?.let {
                result += "\n" + substituteMacros(it, userName, charName, charName)
            }
        }

        return result.toList()
    }

    private fun addInstructSequence(
        result: MutableSet<String>,
        sequence: String,
        instruct: InstructSettings,
        userName: String,
        charName: String,
    ) {
        if (sequence.isEmpty() || sequence.trim().isEmpty()) return
        val wrapped = if (instruct.wrap) "\n$sequence" else sequence
        result += if (instruct.macro) substituteMacros(wrapped, userName, charName, charName) else wrapped
    }

    private fun String.replaceName(name: String): String =
        replace(Regex("\\{\\{name\\}\\}", RegexOption.IGNORE_CASE), name)

    private fun substituteMacros(text: String, userName: String, charName: String, name: String): String =
        text
            .replace(Regex("\\{\\{name\\}\\}", RegexOption.IGNORE_CASE), name)
            .replace(Regex("\\{\\{char\\}\\}", RegexOption.IGNORE_CASE), charName)
            .replace(Regex("\\{\\{user\\}\\}", RegexOption.IGNORE_CASE), userName)
}
