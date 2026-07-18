package io.github.sanitised.st.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelArchitectureTest {
    @Test
    fun ownsOneChatSessionInsteadOfCompositionOwningGeneration() {
        val viewModel = ChatViewModel { error("client should not be requested") }

        assertSame(viewModel.store, viewModel.store)
        assertSame(viewModel.loader, viewModel.loader)
        assertSame(viewModel.runtime, viewModel.runtime)
        assertSame(viewModel.engine, viewModel.engine)

        val activity = File("src/main/java/io/github/sanitised/st/MainActivity.kt").readText()
        val source = File("src/main/java/io/github/sanitised/st/chat/ChatViewModel.kt").readText()
        assertTrue(activity.contains("val chatViewModel: ChatViewModel = viewModel("))
        assertFalse(activity.contains("val chatStore = remember { ChatStore() }"))
        assertFalse(activity.contains("val chatScope = rememberCoroutineScope()"))
        assertTrue(source.contains("scope = viewModelScope"))
    }
}
