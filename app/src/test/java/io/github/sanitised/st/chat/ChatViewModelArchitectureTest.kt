package io.github.sanitised.st.chat

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatViewModelArchitectureTest {
    private fun mainActivityAndNavigationSources(): String = listOf(
        "src/main/java/io/github/sanitised/st/MainActivity.kt",
        "src/main/java/io/github/sanitised/st/ui/navigation/STAppChrome.kt",
        "src/main/java/io/github/sanitised/st/ui/navigation/STAppNavHost.kt",
        "src/main/java/io/github/sanitised/st/ui/navigation/STAppDialogs.kt"
    ).joinToString("\n") { path -> File(path).readText() }

    @Test
    fun ownsOneChatSessionInsteadOfCompositionOwningGeneration() {
        val viewModel = ChatViewModel { error("client should not be requested") }

        assertSame(viewModel.store, viewModel.store)
        assertSame(viewModel.loader, viewModel.loader)
        assertSame(viewModel.runtime, viewModel.runtime)
        assertSame(viewModel.engine, viewModel.engine)

        val activity = mainActivityAndNavigationSources()
        val source = File("src/main/java/io/github/sanitised/st/chat/ChatViewModel.kt").readText()
        assertTrue(activity.contains("val chatViewModel: ChatViewModel = viewModel("))
        assertFalse(activity.contains("val chatStore = remember { ChatStore() }"))
        assertFalse(activity.contains("val chatScope = rememberCoroutineScope()"))
        assertTrue(source.contains("scope = viewModelScope"))
    }
}
