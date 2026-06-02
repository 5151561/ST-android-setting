package io.github.sanitised.st.chat.prompt

import org.junit.Assert.assertEquals
import org.junit.Test

class StopStringBuilderTest {

    @Test
    fun buildsInstructAndContextStopStringsWithWrapAndMacros() {
        val stops = StopStringBuilder.build(
            instruct = InstructSettings(
                wrap = true,
                macro = true,
                stopSequence = "END\n\nEND",
                inputSequence = "<U {{name}}>",
                outputSequence = "<A {{name}}>",
                firstOutputSequence = "<FIRST {{name}}>",
                lastOutputSequence = "<LAST {{name}}>",
                systemSequence = "<SYS {{name}}>",
                lastSystemSequence = "<LASTSYS {{name}}>",
                sequencesAsStopStrings = true,
            ),
            context = ContextTemplateSettings(
                useStopStrings = true,
                namesAsStopStrings = false,
                chatStart = "<START {{char}}>",
                exampleSeparator = "*** {{user}}",
            ),
            userName = "Alex",
            charName = "Alice",
        )

        assertEquals(
            listOf(
                "\nEND",
                "\n<U Alex>",
                "\n<A Alice>",
                "\n<FIRST Alice>",
                "\n<LAST Alice>",
                "\n<SYS System>",
                "\n<LASTSYS System>",
                "\n<START Alice>",
                "\n*** Alex",
            ),
            stops
        )
    }

    @Test
    fun canDisableSequenceAndContextStops() {
        val stops = StopStringBuilder.build(
            instruct = InstructSettings(
                stopSequence = "END",
                inputSequence = "<U>",
                outputSequence = "<A>",
                sequencesAsStopStrings = false,
            ),
            context = ContextTemplateSettings(
                useStopStrings = false,
                namesAsStopStrings = false,
                chatStart = "<START>",
                exampleSeparator = "***",
            ),
            userName = "Alex",
            charName = "Alice",
        )

        assertEquals(listOf("END"), stops)
    }

    @Test
    fun includesUserNameStopWhenContextRequestsNameStops() {
        val stops = StopStringBuilder.build(
            instruct = InstructSettings(),
            context = ContextTemplateSettings(namesAsStopStrings = true),
            userName = "Alex",
            charName = "Alice",
        )

        assertEquals(listOf("\nAlex:"), stops)
    }
}
