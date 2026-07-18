package io.github.sanitised.st.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.sanitised.st.api.TavernCoreApi
import io.github.sanitised.st.chat.engine.ChatEngine
import io.github.sanitised.st.chat.engine.NativeChatEngine

/**
 * 聊天目的地的业务状态容器。
 *
 * [ChatStore] 暂时作为 Compose 兼容层保留；它和所有长任务都由 ViewModel 持有，
 * 因而不会在 Activity 配置重建时被重新创建或取消。后续切片会把公开可变字段
 * 收敛为不可变 ChatUiState/StateFlow。
 */
class ChatViewModel(
    clientProvider: () -> TavernCoreApi,
) : ViewModel() {
    val store = ChatStore()
    val loader = NativeChatLoader(store, clientProvider)
    val runtime = NativeChatRuntime(store) {
        TavernNativeChatDataSource(clientProvider())
    }
    val engine: ChatEngine = NativeChatEngine(
        scope = viewModelScope,
        store = store,
        clientProvider = clientProvider,
    )

    companion object {
        fun factory(clientProvider: () -> TavernCoreApi): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                        "Unknown ViewModel class: ${modelClass.name}"
                    }
                    return ChatViewModel(clientProvider) as T
                }
            }
    }
}
