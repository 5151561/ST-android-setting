package io.github.sanitised.st.ui.screens

import kotlin.math.ceil

internal data class CharacterTokenInput(
    val description: String = "",
    val firstMessage: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val depthPrompt: String = "",
    val creatorNotes: String = "",
    val personality: String = "",
    val scenario: String = "",
    val messageExample: String = ""
)

internal data class CharacterTokenStats(
    val description: Int,
    val greetings: Int,
    val promptAndNote: Int,
    val metadataAndExamples: Int,
    val total: Int
)

internal object CharacterEditTools {
    private const val BYTES_PER_TOKEN = 3.35
    private val originalImageExtensions = setOf("png", "jpg", "jpeg")

    fun estimateTokenCount(text: String): Int {
        if (text.isBlank()) return 0
        return ceil(text.toByteArray(Charsets.UTF_8).size / BYTES_PER_TOKEN).toInt()
    }

    fun tokenStats(input: CharacterTokenInput): CharacterTokenStats {
        val description = estimateTokenCount(input.description)
        val greetings = estimateTokenCount(
            (listOf(input.firstMessage) + input.alternateGreetings)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        )
        val promptAndNote = estimateTokenCount(
            listOf(input.systemPrompt, input.postHistoryInstructions, input.depthPrompt)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        )
        val metadataAndExamples = estimateTokenCount(
            listOf(input.creatorNotes, input.personality, input.scenario, input.messageExample)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        )
        return CharacterTokenStats(
            description = description,
            greetings = greetings,
            promptAndNote = promptAndNote,
            metadataAndExamples = metadataAndExamples,
            total = description + greetings + promptAndNote + metadataAndExamples
        )
    }

    fun shouldTranscodeAvatar(fileName: String): Boolean {
        return fileName.extension() !in originalImageExtensions
    }

    fun avatarOutputFileName(fileName: String): String {
        return if (shouldTranscodeAvatar(fileName)) {
            fileName.substringBeforeLast('.', fileName).ifBlank { "avatar" } + ".png"
        } else {
            fileName
        }
    }

    private fun String.extension(): String = substringAfterLast('.', "").lowercase()
}
