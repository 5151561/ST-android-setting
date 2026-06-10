package io.github.sanitised.st.chat.prompt

enum class NamesBehavior(val id: String) {
    NONE("none"),
    FORCE("force"),
    ALWAYS("always");

    companion object {
        fun from(value: Any?): NamesBehavior =
            entries.firstOrNull { it.id == value?.toString() } ?: FORCE
    }
}

enum class InstructTurnPosition {
    NORMAL,
    FIRST,
    LAST,
}

data class InstructSettings(
    val enabled: Boolean = true,
    val wrap: Boolean = false,
    val macro: Boolean = false,
    val storyStringPrefix: String = "",
    val storyStringSuffix: String = "",
    val inputSequence: String = "",
    val inputSuffix: String = "",
    val outputSequence: String = "",
    val outputSuffix: String = "",
    val systemSequence: String = "",
    val systemSuffix: String = "",
    val lastSystemSequence: String = "",
    val firstInputSequence: String = "",
    val lastInputSequence: String = "",
    val firstOutputSequence: String = "",
    val lastOutputSequence: String = "",
    val stopSequence: String = "",
    val namesBehavior: NamesBehavior = NamesBehavior.FORCE,
    val systemSameAsUser: Boolean = false,
    val sequencesAsStopStrings: Boolean = true,
    val activationRegex: String = "",
    val skipExamples: Boolean = false,
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): InstructSettings = InstructSettings(
            enabled = map.booleanValue("enabled", true),
            wrap = map.booleanValue("wrap", false),
            macro = map.booleanValue("macro", false),
            storyStringPrefix = map.stringValue("story_string_prefix"),
            storyStringSuffix = map.stringValue("story_string_suffix"),
            inputSequence = map.stringValue("input_sequence"),
            inputSuffix = map.stringValue("input_suffix"),
            outputSequence = map.stringValue("output_sequence"),
            outputSuffix = map.stringValue("output_suffix"),
            systemSequence = map.stringValue("system_sequence"),
            systemSuffix = map.stringValue("system_suffix"),
            lastSystemSequence = map.stringValue("last_system_sequence"),
            firstInputSequence = map.stringValue("first_input_sequence"),
            lastInputSequence = map.stringValue("last_input_sequence"),
            firstOutputSequence = map.stringValue("first_output_sequence"),
            lastOutputSequence = map.stringValue("last_output_sequence"),
            stopSequence = map.stringValue("stop_sequence"),
            namesBehavior = NamesBehavior.from(map["names_behavior"]),
            systemSameAsUser = map.booleanValue("system_same_as_user", false),
            sequencesAsStopStrings = map.booleanValue("sequences_as_stop_strings", true),
            activationRegex = map.stringValue("activation_regex"),
            skipExamples = map.booleanValue("skip_examples", false),
        )
    }
}

data class ContextTemplateSettings(
    val storyString: String = DEFAULT_STORY_STRING,
    val storyStringPrefix: String = "",
    val storyStringSuffix: String = "",
    val storyStringPosition: Int = 0,
    val chatStart: String = "",
    val exampleSeparator: String = "",
    val useStopStrings: Boolean = true,
    val namesAsStopStrings: Boolean = true,
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): ContextTemplateSettings = ContextTemplateSettings(
            storyString = map.stringValue("story_string").ifBlank { DEFAULT_STORY_STRING },
            storyStringPrefix = map.stringValue("story_string_prefix"),
            storyStringSuffix = map.stringValue("story_string_suffix"),
            storyStringPosition = map.intValue("story_string_position", 0),
            chatStart = map.stringValue("chat_start"),
            exampleSeparator = map.stringValue("example_separator"),
            useStopStrings = map.booleanValue("use_stop_strings", true),
            namesAsStopStrings = map.booleanValue("names_as_stop_strings", true),
        )
    }
}

class InstructTemplate(
    private val settings: InstructSettings,
    private val userName: String,
    private val charName: String,
) {
    fun formatChat(
        name: String,
        message: String,
        isUser: Boolean,
        isNarrator: Boolean = false,
        forceName: Boolean = false,
        position: InstructTurnPosition = InstructTurnPosition.NORMAL,
    ): String {
        val includeNames = !isNarrator && when (settings.namesBehavior) {
            NamesBehavior.ALWAYS -> true
            NamesBehavior.FORCE -> forceName
            NamesBehavior.NONE -> false
        }
        var prefix = prefixFor(isUser, isNarrator, position)
        var suffix = suffixFor(isUser, isNarrator)

        if (settings.macro) {
            val sequenceName = if (isNarrator) "System" else name
            prefix = substituteMacros(prefix, sequenceName)
            suffix = substituteMacros(suffix, sequenceName)
        }
        if (suffix.isEmpty() && settings.wrap) suffix = "\n"

        val separator = if (settings.wrap) "\n" else ""
        val line = if (includeNames && name.isNotBlank()) "$name: $message$suffix" else "$message$suffix"
        return listOf(prefix, line).filter { it.isNotEmpty() }.joinToString(separator)
    }

    fun formatPrompt(
        name: String,
        isImpersonate: Boolean = false,
        promptBias: String = "",
        isQuiet: Boolean = false,
        isQuietToLoud: Boolean = false,
    ): String {
        val includeNames = name.isNotBlank() &&
            settings.namesBehavior == NamesBehavior.ALWAYS &&
            !(isQuiet && !isQuietToLoud)
        var sequence = when {
            isImpersonate -> settings.inputSequence
            isQuiet && !isQuietToLoud -> settings.lastSystemSequence.ifBlank { settings.outputSequence }
            isQuiet && isQuietToLoud -> settings.lastOutputSequence.ifBlank { settings.outputSequence }
            else -> settings.lastOutputSequence.ifBlank { settings.outputSequence }
        }
        var nameFiller = ""
        if (
            includeNames &&
            settings.lastOutputSequence.isNotBlank() &&
            settings.outputSequence.isNotBlank() &&
            sequence == settings.lastOutputSequence &&
            settings.outputSequence.last().isWhitespace() &&
            !settings.lastOutputSequence.last().isWhitespace()
        ) {
            nameFiller = settings.outputSequence.takeLast(1)
        }
        if (settings.macro) sequence = substituteMacros(sequence, name.ifBlank { "System" })

        val separator = if (settings.wrap) "\n" else ""
        var text = if (includeNames) {
            separator + sequence + separator + nameFiller + "$name:"
        } else {
            separator + sequence
        }
        if (isQuiet && separator.isNotEmpty()) text = text.removePrefix(separator)
        if (!isImpersonate && promptBias.isNotBlank()) {
            text += if (includeNames) promptBias else separator + promptBias.trimStart()
        }
        return (if (settings.wrap) text.trimEnd() else text) + if (includeNames) "" else separator
    }

    private fun prefixFor(isUser: Boolean, isNarrator: Boolean, position: InstructTurnPosition): String =
        when {
            isNarrator -> if (settings.systemSameAsUser) settings.inputSequence else settings.systemSequence
            isUser && position == InstructTurnPosition.FIRST -> settings.firstInputSequence.ifBlank { settings.inputSequence }
            isUser && position == InstructTurnPosition.LAST -> settings.lastInputSequence.ifBlank { settings.inputSequence }
            isUser -> settings.inputSequence
            position == InstructTurnPosition.FIRST -> settings.firstOutputSequence.ifBlank { settings.outputSequence }
            position == InstructTurnPosition.LAST -> settings.lastOutputSequence.ifBlank { settings.outputSequence }
            else -> settings.outputSequence
        }

    private fun suffixFor(isUser: Boolean, isNarrator: Boolean): String =
        when {
            isNarrator -> if (settings.systemSameAsUser) settings.inputSuffix else settings.systemSuffix
            isUser -> settings.inputSuffix
            else -> settings.outputSuffix
        }

    private fun substituteMacros(text: String, name: String): String =
        text
            .replace(NAME_MACRO, name)
            .replace(CHAR_MACRO, charName)
            .replace(USER_MACRO, userName)

    companion object {
        private val NAME_MACRO = Regex("\\{\\{name\\}\\}", RegexOption.IGNORE_CASE)
        private val CHAR_MACRO = Regex("\\{\\{char\\}\\}", RegexOption.IGNORE_CASE)
        private val USER_MACRO = Regex("\\{\\{user\\}\\}", RegexOption.IGNORE_CASE)
    }
}

internal const val DEFAULT_STORY_STRING =
    "{{system}}\n{{description}}\n{{personality}}\n{{scenario}}\n{{persona}}\n"

internal fun Map<String, Any?>.stringValue(key: String): String =
    (this[key] as? String).orEmpty()

internal fun Map<String, Any?>.booleanValue(key: String, default: Boolean): Boolean =
    when (val value = this[key]) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        else -> default
    }

internal fun Map<String, Any?>.intValue(key: String, default: Int): Int =
    when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }
