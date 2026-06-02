package io.github.sanitised.st.chat.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenerationDeltaParserTest {

    @Test
    fun extractsChatCompletionDeltaShapes() {
        assertEquals(
            "hello",
            GenerationDeltaParser.extract("""{"choices":[{"delta":{"content":"hello"}}]}""")
        )
        assertEquals(
            "anthropic",
            GenerationDeltaParser.extract("""{"delta":{"text":"anthropic"}}""")
        )
        assertEquals(
            "gemini",
            GenerationDeltaParser.extract("""{"candidates":[{"content":{"parts":[{"text":"ge"},{"text":"mini"}]}}]}""")
        )
    }

    @Test
    fun extractsTextCompletionDeltaShapes() {
        assertEquals(
            "text",
            GenerationDeltaParser.extract("""{"choices":[{"text":"text"}]}""")
        )
        assertEquals(
            "llama",
            GenerationDeltaParser.extract("""{"content":"llama"}""")
        )
    }

    @Test
    fun returnsNullWhenNoKnownDeltaExists() {
        assertNull(GenerationDeltaParser.extract("""{"choices":[{"finish_reason":"stop"}]}"""))
        assertNull(GenerationDeltaParser.extract("not json"))
    }
}
