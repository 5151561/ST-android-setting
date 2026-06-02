package io.github.sanitised.st.chat.prompt

import org.junit.Assert.assertEquals
import org.junit.Test

class InstructTemplateTest {

    @Test
    fun formatsTurnsWithSequencesSuffixesWrapNamesAndMacros() {
        val template = InstructTemplate(
            settings = InstructSettings(
                wrap = true,
                macro = true,
                inputSequence = "<|user {{name}}|>",
                outputSequence = "<|assistant {{name}}|>",
                systemSequence = "<|system {{name}}|>",
                firstInputSequence = "<|first {{name}}|>",
                lastOutputSequence = "<|last {{name}}|>",
                inputSuffix = "<eou>",
                outputSuffix = "<eoa>",
                systemSuffix = "<eos>",
                namesBehavior = NamesBehavior.ALWAYS,
            ),
            userName = "Alex",
            charName = "Alice",
        )

        assertEquals(
            "<|first Alex|>\nAlex: Hello {{char}}<eou>",
            template.formatChat(
                name = "Alex",
                message = "Hello {{char}}",
                isUser = true,
                position = InstructTurnPosition.FIRST,
            )
        )
        assertEquals(
            "<|last Alice|>\nAlice: Hello {{user}}<eoa>",
            template.formatChat(
                name = "Alice",
                message = "Hello {{user}}",
                isUser = false,
                position = InstructTurnPosition.LAST,
            )
        )
        assertEquals(
            "<|system System|>\nSystem note<eos>",
            template.formatChat(
                name = "System",
                message = "System note",
                isUser = false,
                isNarrator = true,
            )
        )
    }

    @Test
    fun forceNamesOnlyWhenRequestedForOneToOneChats() {
        val template = InstructTemplate(
            settings = InstructSettings(
                inputSequence = "USER:",
                outputSequence = "BOT:",
                inputSuffix = "\n",
                outputSuffix = "\n",
                namesBehavior = NamesBehavior.FORCE,
            ),
            userName = "Alex",
            charName = "Alice",
        )

        assertEquals("USER:hello\n", template.formatChat("Alex", "hello", isUser = true))
        assertEquals("BOT:Alice: hello\n", template.formatChat("Alice", "hello", isUser = false, forceName = true))
    }

    @Test
    fun formatsGenerationPromptWithLastOutputSequenceAndNameFiller() {
        val template = InstructTemplate(
            settings = InstructSettings(
                wrap = true,
                outputSequence = "<A> ",
                lastOutputSequence = "<LAST>",
                namesBehavior = NamesBehavior.ALWAYS,
            ),
            userName = "Alex",
            charName = "Alice",
        )

        assertEquals("\n<LAST>\n Alice:", template.formatPrompt(name = "Alice"))
    }

    @Test
    fun doesNotSubstituteMacrosInsideMessageBody() {
        val template = InstructTemplate(
            settings = InstructSettings(
                macro = true,
                inputSequence = "<U {{name}}>",
                namesBehavior = NamesBehavior.ALWAYS,
            ),
            userName = "Alex",
            charName = "Alice",
        )

        assertEquals(
            "<U Alex>Alex: Literal {{char}} and {{user}}",
            template.formatChat("Alex", "Literal {{char}} and {{user}}", isUser = true)
        )
    }
}
