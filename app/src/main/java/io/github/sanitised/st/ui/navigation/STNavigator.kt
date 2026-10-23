package io.github.sanitised.st.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.json.Json

private val rootTabKeys: Set<STNavKey> = setOf(
    STKey.Home,
    STKey.Characters,
    STKey.WorldInfo,
    STKey.Settings,
)

@Stable
class STNavigator(
    val backStack: SnapshotStateList<STNavKey>,
) {
    val currentKey: STNavKey
        get() = backStack.lastOrNull() ?: STKey.Home

    val currentRoute: String
        get() = currentKey.routeId

    val canGoBack: Boolean
        get() = backStack.size > 1

    fun navigate(key: STNavKey, singleTop: Boolean = true) {
        if (singleTop && backStack.lastOrNull() == key) return
        backStack.add(key)
    }

    /**
     * Switches between top-level root tabs (Home / Characters / WorldInfo / Settings).
     * Collapses sub-pages back to Home and pushes the selected root tab above Home.
     */
    fun switchRootTab(tab: STNavKey) {
        when (tab) {
            STKey.Home -> {
                backStack.clear()
                backStack.add(STKey.Home)
            }
            STKey.Characters, STKey.WorldInfo, STKey.Settings -> {
                if (currentKey == tab && backStack.size == 2 && backStack.firstOrNull() == STKey.Home) {
                    return
                }
                backStack.clear()
                backStack.add(STKey.Home)
                backStack.add(tab)
            }
            else -> navigate(tab)
        }
    }

    /**
     * Handles drawer or legacy string route selection.
     * Top-level tabs reset the stack above Home; secondary drawer destinations push onto the
     * active root tab so pressing Back returns directly to the root tab the user came from.
     */
    fun selectTopLevelOrDrawerRoute(route: String) {
        when (val key = routeToNavKey(route)) {
            STKey.Home, STKey.Characters, STKey.WorldInfo, STKey.Settings -> switchRootTab(key)
            else -> {
                val activeRootTab = backStack.lastOrNull { it in rootTabKeys } ?: STKey.Home
                backStack.clear()
                backStack.add(STKey.Home)
                if (activeRootTab != STKey.Home) {
                    backStack.add(activeRootTab)
                }
                if (key != activeRootTab) {
                    backStack.add(key)
                }
            }
        }
    }

    fun openChat(
        avatar: String = "",
        chatFile: String? = null,
        useCurrentStore: Boolean = false,
    ) {
        val chatKey = STKey.Chat(
            avatar = avatar,
            chatFile = chatFile,
            useCurrentStore = useCurrentStore,
        )
        val existingChatIndex = backStack.indexOfLast { it is STKey.Chat }
        if (existingChatIndex >= 0) {
            while (backStack.lastIndex >= existingChatIndex) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
        if (backStack.isEmpty()) {
            backStack.add(STKey.Home)
        }
        backStack.add(chatKey)
    }

    fun goBack(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}

private val stNavBackStackSaver: Saver<SnapshotStateList<STNavKey>, ArrayList<String>> = Saver(
    save = { list -> ArrayList(list.map { Json.encodeToString(STNavKey.serializer(), it) }) },
    restore = { saved ->
        val restored = saved.mapNotNull {
            runCatching { Json.decodeFromString(STNavKey.serializer(), it) }.getOrNull()
        }
        mutableStateListOf<STNavKey>().apply {
            if (restored.isEmpty()) add(STKey.Home) else addAll(restored)
        }
    },
)

@Composable
fun rememberSTNavigator(startKey: STNavKey = STKey.Home): STNavigator {
    val typedStack = rememberSaveable(saver = stNavBackStackSaver) {
        mutableStateListOf(startKey)
    }
    return remember(typedStack) { STNavigator(typedStack) }
}
