package io.github.sanitised.st.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterEditToolsTest {
    @Test
    fun tokenStatsUseSillyTavernByteEstimateAndGroupEditableFields() {
        val stats = CharacterEditTools.tokenStats(
            CharacterTokenInput(
                description = "hello world",
                firstMessage = "Hi!",
                alternateGreetings = listOf("A second hello"),
                systemPrompt = "Follow the card.",
                postHistoryInstructions = "Stay in character.",
                depthPrompt = "Remember the scene.",
                creatorNotes = "private notes",
                personality = "bright",
                scenario = "at the station",
                messageExample = "<START>"
            )
        )

        assertEquals(CharacterEditTools.estimateTokenCount("hello world"), stats.description)
        assertEquals(
            CharacterEditTools.estimateTokenCount("Hi!\nA second hello"),
            stats.greetings
        )
        assertTrue(stats.promptAndNote > 0)
        assertTrue(stats.metadataAndExamples > 0)
        assertEquals(
            stats.description + stats.greetings + stats.promptAndNote + stats.metadataAndExamples,
            stats.total
        )
    }

    @Test
    fun avatarProcessingKeepsSupportedOriginalsButNormalizesConvertedUploadsToPng() {
        assertFalse(
            CharacterEditTools.shouldTranscodeAvatar(
                "Seraphina.jpg",
                CharacterAvatarProcessingMode.ORIGINAL
            )
        )
        assertTrue(
            CharacterEditTools.shouldTranscodeAvatar(
                "Seraphina.heic",
                CharacterAvatarProcessingMode.ORIGINAL
            )
        )
        assertTrue(
            CharacterEditTools.shouldTranscodeAvatar(
                "Seraphina.jpg",
                CharacterAvatarProcessingMode.CENTER_CROP_PNG
            )
        )
        assertEquals(
            "Seraphina.png",
            CharacterEditTools.avatarOutputFileName("Seraphina.heic", CharacterAvatarProcessingMode.ORIGINAL)
        )
        assertEquals(
            "Seraphina.png",
            CharacterEditTools.avatarOutputFileName("Seraphina.jpg", CharacterAvatarProcessingMode.PNG)
        )
    }
}
