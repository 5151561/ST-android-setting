package io.github.sanitised.st.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class STNavigatorTest {
    private fun createNavigator(startKey: STNavKey = STKey.Home): STNavigator =
        STNavigator(mutableStateListOf(startKey))

    @Test
    fun initialStackStartsAtHomeAndCannotPopLastEntry() {
        val navigator = createNavigator()

        assertEquals(STKey.Home, navigator.currentKey)
        assertEquals(STRoutes.HOME, navigator.currentRoute)
        assertFalse(navigator.canGoBack)
        assertFalse(navigator.goBack())
        assertEquals(listOf(STKey.Home), navigator.backStack.toList())
    }

    @Test
    fun navigateSingleTopPreventsDuplicateAdjacentKeys() {
        val navigator = createNavigator()

        navigator.navigate(STKey.CharacterDetail("alice.png"))
        navigator.navigate(STKey.CharacterDetail("alice.png"))

        assertEquals(
            listOf(STKey.Home, STKey.CharacterDetail("alice.png")),
            navigator.backStack.toList()
        )
        assertTrue(navigator.canGoBack)
        assertTrue(navigator.goBack())
        assertEquals(STKey.Home, navigator.currentKey)
    }

    @Test
    fun switchRootTabCollapsesSubPagesAndReturnsToHomeOnBack() {
        val navigator = createNavigator()

        navigator.switchRootTab(STKey.Characters)
        navigator.navigate(STKey.CharacterDetail("alice.png"))
        navigator.navigate(STKey.CharacterForm("alice.png"))

        navigator.switchRootTab(STKey.Settings)
        assertEquals(listOf(STKey.Home, STKey.Settings), navigator.backStack.toList())
        assertEquals(STRoutes.SETTINGS, navigator.currentRoute)

        assertTrue(navigator.goBack())
        assertEquals(listOf(STKey.Home), navigator.backStack.toList())
    }

    @Test
    fun selectTopLevelOrDrawerRouteHandlesRootTabsAndSecondaryDrawerRoutes() {
        val navigator = createNavigator()

        navigator.selectTopLevelOrDrawerRoute(STRoutes.WORLD_INFO)
        assertEquals(listOf(STKey.Home, STKey.WorldInfo), navigator.backStack.toList())

        navigator.selectTopLevelOrDrawerRoute(STRoutes.CONNECTIONS)
        assertEquals(listOf(STKey.Home, STKey.WorldInfo, STKey.Connections), navigator.backStack.toList())

        navigator.navigate(STKey.ProviderDetail("openai"))
        assertEquals(
            listOf(STKey.Home, STKey.WorldInfo, STKey.Connections, STKey.ProviderDetail("openai")),
            navigator.backStack.toList()
        )

        // Pressing back from Connections returns directly to the active root tab (WorldInfo)
        assertTrue(navigator.goBack())
        assertEquals(STKey.Connections, navigator.currentKey)
        assertTrue(navigator.goBack())
        assertEquals(STKey.WorldInfo, navigator.currentKey)

        navigator.selectTopLevelOrDrawerRoute(STRoutes.HOME)
        assertEquals(listOf(STKey.Home), navigator.backStack.toList())
    }

    @Test
    fun openChatDeduplicatesExistingChatEntriesInStack() {
        val navigator = createNavigator()

        navigator.openChat(avatar = "alice.png", chatFile = "chat-1.jsonl")
        navigator.navigate(STKey.PastChats("alice.png"))
        navigator.openChat(avatar = "alice.png", chatFile = "chat-2.jsonl")

        assertEquals(
            listOf(
                STKey.Home,
                STKey.Chat(avatar = "alice.png", chatFile = "chat-2.jsonl", useCurrentStore = false)
            ),
            navigator.backStack.toList()
        )
    }

    @Test
    fun routeToNavKeyAndRouteIdRoundTrip() {
        val keys = listOf(
            STKey.Home to STRoutes.HOME,
            STKey.Characters to STRoutes.CHARACTERS,
            STKey.WorldInfo to STRoutes.WORLD_INFO,
            STKey.Settings to STRoutes.SETTINGS,
            STKey.Persona to STRoutes.PERSONA,
            STKey.Connections to STRoutes.CONNECTIONS,
            STKey.Presets to STRoutes.PRESETS,
            STKey.Appearance to STRoutes.APPEARANCE,
            STKey.AppearanceThemeColor to STRoutes.APPEARANCE_THEME_COLOR,
            STKey.AppearanceTypography to STRoutes.APPEARANCE_TYPOGRAPHY,
            STKey.AppearanceBackgrounds to STRoutes.BACKGROUNDS,
            STKey.AppearanceChatStyle to STRoutes.APPEARANCE_CHAT_STYLE,
            STKey.AppearanceChatBehavior to STRoutes.CHAT_BEHAVIOR,
            STKey.ManageSt to STRoutes.MANAGE_ST,
            STKey.ChatBackups to STRoutes.CHAT_BACKUPS,
            STKey.AuthorNote to STRoutes.AUTHOR_NOTE,
            STKey.Extensions to STRoutes.EXTENSIONS,
            STKey.QuickReplies to STRoutes.QUICK_REPLIES,
            STKey.Secrets to STRoutes.SECRETS,
            STKey.Logs to STRoutes.LOGS,
            STKey.Config to STRoutes.CONFIG,
            STKey.Legal to STRoutes.LEGAL,
            STKey.Login to STRoutes.LOGIN,
            STKey.Onboarding to STRoutes.ONBOARDING,
            STKey.Account to STRoutes.ACCOUNT,
        )

        for ((expectedKey, route) in keys) {
            assertEquals(route, expectedKey.routeId)
            assertEquals(expectedKey, routeToNavKey(route))
        }

        assertEquals(
            STKey.GroupChat(groupId = "g1", chatId = "c1"),
            routeToNavKey("group-chat/detail/g1?chatId=c1")
        )
        assertEquals(
            STKey.CharacterDetail("alice.png"),
            routeToNavKey("characters/detail/alice.png")
        )
    }

    @Test
    fun navigateWithSingleTopFalseAllowsDuplicateAdjacentKeys() {
        val navigator = createNavigator()

        navigator.navigate(STKey.CharacterDetail("alice.png"), singleTop = false)
        navigator.navigate(STKey.CharacterDetail("alice.png"), singleTop = false)

        assertEquals(
            listOf(
                STKey.Home,
                STKey.CharacterDetail("alice.png"),
                STKey.CharacterDetail("alice.png"),
            ),
            navigator.backStack.toList()
        )
    }

    @Test
    fun openChatPreservesPrecedingNonChatBackStackEntries() {
        val navigator = createNavigator()

        navigator.switchRootTab(STKey.Characters)
        navigator.navigate(STKey.CharacterDetail("alice.png"))
        navigator.openChat(avatar = "alice.png", chatFile = "chat-1.jsonl")
        navigator.navigate(STKey.PastChats("alice.png"))
        navigator.openChat(avatar = "alice.png", chatFile = "chat-2.jsonl")

        assertEquals(
            listOf(
                STKey.Home,
                STKey.Characters,
                STKey.CharacterDetail("alice.png"),
                STKey.Chat(avatar = "alice.png", chatFile = "chat-2.jsonl", useCurrentStore = false),
            ),
            navigator.backStack.toList()
        )
    }

    @Test
    fun switchRootTabToSameRootTabWithoutSubPagesIsNoOp() {
        val navigator = createNavigator()

        navigator.switchRootTab(STKey.Characters)
        assertEquals(listOf(STKey.Home, STKey.Characters), navigator.backStack.toList())

        navigator.switchRootTab(STKey.Characters)
        assertEquals(listOf(STKey.Home, STKey.Characters), navigator.backStack.toList())
    }
}
