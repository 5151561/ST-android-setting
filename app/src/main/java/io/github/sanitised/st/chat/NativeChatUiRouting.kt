package io.github.sanitised.st.chat

object NativeChatUiRouting {
    fun shouldActivateHiddenWebViewForChatEntry(nativeGenerationEnabled: Boolean): Boolean =
        !nativeGenerationEnabled

    fun <T> selectNativeSingleChatRuntime(
        nativeChatRuntime: T?,
        nativeChatLoadingEnabled: Boolean,
        targetMatched: Boolean,
        isGroupMode: Boolean,
    ): T? =
        nativeChatRuntime?.takeIf {
            nativeChatLoadingEnabled && targetMatched && !isGroupMode
        }
}
