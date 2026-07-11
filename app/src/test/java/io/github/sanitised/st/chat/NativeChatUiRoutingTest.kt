package io.github.sanitised.st.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NativeChatUiRoutingTest {

    @Test
    fun nativeRuntimeIsSelectedOnlyForMatchedSingleCharacterChat() {
        val runtime = Any()

        assertSame(
            runtime,
            NativeChatUiRouting.selectNativeSingleChatRuntime(
                nativeChatRuntime = runtime,
                nativeChatLoadingEnabled = true,
                targetMatched = true,
                isGroupMode = false,
            )
        )
        assertNull(
            NativeChatUiRouting.selectNativeSingleChatRuntime(
                nativeChatRuntime = runtime,
                nativeChatLoadingEnabled = false,
                targetMatched = true,
                isGroupMode = false,
            )
        )
        assertNull(
            NativeChatUiRouting.selectNativeSingleChatRuntime(
                nativeChatRuntime = runtime,
                nativeChatLoadingEnabled = true,
                targetMatched = false,
                isGroupMode = false,
            )
        )
        assertNull(
            NativeChatUiRouting.selectNativeSingleChatRuntime(
                nativeChatRuntime = runtime,
                nativeChatLoadingEnabled = true,
                targetMatched = true,
                isGroupMode = true,
            )
        )
    }
}
