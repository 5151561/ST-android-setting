package io.github.sanitised.st.ui.navigation

import android.net.Uri
import androidx.navigation3.runtime.NavKey
import java.net.URLDecoder
import kotlinx.serialization.Serializable

object STRoutes {
    const val HOME = "chats/home"
    const val CHAT = "chat"
    const val CHARACTERS = "characters"
    const val CHARACTER_NEW = "characters/new"
    const val CHARACTER_DETAIL = "characters/detail/{avatar}"
    const val CHARACTER_EDIT = "characters/edit/{avatar}"
    const val TOOLS = "tools"
    const val WORLD_INFO = "world-info"
    const val PERSONA = "personas"
    const val PRESETS = "ai-settings"
    const val CONNECTIONS = "api-connections"
    const val CHAT_BACKUPS = "memory"
    const val GROUP_CHAT_DETAIL = "group-chat/detail/{groupId}?chatId={chatId}"
    const val GROUP_SETTINGS = "group-chat/settings/{groupId}"
    const val GROUP_MEMBERS = "group-chat/members/{groupId}"
    const val NEW_GROUP_CHAT = "group-chat/new"
    const val SETTINGS = "me"

    const val LOGS = "settings/logs"
    const val CONFIG = "settings/config"
    const val LEGAL = "settings/legal"
    const val LICENSE = "settings/legal/{assetPath}"
    const val MANAGE_ST = "st-core"

    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"
    const val ACCOUNT = "settings/account"

    const val WORLD_INFO_MANAGE = "world-info/manage"
    const val WORLD_INFO_BOOK = "world-info/book/{name}"
    const val WORLD_INFO_ENTRY = "world-info/entry?uid={uid}&book={book}"
    const val WORLD_INFO_GLOBAL = "world-info/global"

    const val CHAR_FORM = "characters/form/{avatar}"
    const val CHAR_GREETINGS = "characters/greetings/{avatar}"
    const val CHAR_ADVANCED = "characters/advanced/{avatar}"

    const val BACKGROUNDS = "settings/backgrounds"
    const val CHAT_BEHAVIOR = "settings/chat-behavior"
    const val APPEARANCE_THEME_COLOR = "settings/appearance/theme-color"
    const val APPEARANCE_TYPOGRAPHY = "settings/appearance/typography"
    const val APPEARANCE_CHAT_STYLE = "settings/appearance/chat-style"

    const val SECRETS = "settings/secrets"
    const val EXTENSIONS = "settings/extensions"
    const val AUTHOR_NOTE = "settings/author-note"
    const val QUICK_REPLIES = "settings/quick-replies"
    const val APPEARANCE = "settings/appearance"
    const val PROVIDER_DETAIL = "api-connections/detail/{providerId}"
    const val PAST_CHATS = "characters/chats/{avatar}"

    fun characterDetail(avatar: String): String = "characters/detail/${Uri.encode(avatar)}"
    fun characterEdit(avatar: String): String = "characters/edit/${Uri.encode(avatar)}"
    fun characterForm(avatar: String): String = "characters/form/${Uri.encode(avatar)}"
    fun characterGreetings(avatar: String): String = "characters/greetings/${Uri.encode(avatar)}"
    fun characterAdvanced(avatar: String): String = "characters/advanced/${Uri.encode(avatar)}"
    fun worldInfoBook(name: String): String = "world-info/book/${Uri.encode(name)}"
    fun worldInfoEntry(uid: Int, book: String): String =
        "world-info/entry?uid=$uid&book=${Uri.encode(book)}"
    fun providerDetail(providerId: String): String = "api-connections/detail/${Uri.encode(providerId)}"
    fun pastChats(avatar: String): String = "characters/chats/${Uri.encode(avatar)}"
    fun groupChatDetail(groupId: String, chatId: String?): String =
        "group-chat/detail/${Uri.encode(groupId)}?chatId=${Uri.encode(chatId.orEmpty())}"
    fun groupSettings(groupId: String): String = "group-chat/settings/${Uri.encode(groupId)}"
    fun groupMembers(groupId: String): String = "group-chat/members/${Uri.encode(groupId)}"
}

@Serializable
sealed interface STNavKey : NavKey

object STKey {
    @Serializable data object Home : STNavKey
    @Serializable data object Characters : STNavKey
    @Serializable data object WorldInfo : STNavKey
    @Serializable data object Settings : STNavKey

    @Serializable
    data class Chat(
        val avatar: String = "",
        val chatFile: String? = null,
        val useCurrentStore: Boolean = false,
    ) : STNavKey

    @Serializable data class PastChats(val avatar: String) : STNavKey
    @Serializable data class GroupChat(val groupId: String, val chatId: String = "") : STNavKey
    @Serializable data object NewGroupChat : STNavKey
    @Serializable data class GroupSettings(val groupId: String) : STNavKey
    @Serializable data class GroupMembers(val groupId: String) : STNavKey

    @Serializable data class CharacterDetail(val avatar: String) : STNavKey
    @Serializable data object CharacterCreate : STNavKey
    @Serializable data class CharacterEdit(val avatar: String) : STNavKey
    @Serializable data class CharacterForm(val avatar: String) : STNavKey
    @Serializable data class CharacterGreetings(val avatar: String) : STNavKey
    @Serializable data class CharacterAdvanced(val avatar: String) : STNavKey

    @Serializable data object WorldBookManage : STNavKey
    @Serializable data class LorebookDetail(val bookName: String) : STNavKey
    @Serializable data class WorldEntryEdit(val uid: Int = -1, val bookName: String = "") : STNavKey
    @Serializable data object WiGlobalSettings : STNavKey

    @Serializable data object Persona : STNavKey
    @Serializable data object Connections : STNavKey
    @Serializable data class ProviderDetail(val providerId: String) : STNavKey
    @Serializable data object Presets : STNavKey

    @Serializable data object Appearance : STNavKey
    @Serializable data object AppearanceThemeColor : STNavKey
    @Serializable data object AppearanceTypography : STNavKey
    @Serializable data object AppearanceBackgrounds : STNavKey
    @Serializable data object AppearanceChatStyle : STNavKey
    @Serializable data object AppearanceChatBehavior : STNavKey

    @Serializable data object ManageSt : STNavKey
    @Serializable data object ChatBackups : STNavKey
    @Serializable data object AuthorNote : STNavKey
    @Serializable data object Extensions : STNavKey
    @Serializable data object QuickReplies : STNavKey
    @Serializable data object Secrets : STNavKey
    @Serializable data object Logs : STNavKey
    @Serializable data object Config : STNavKey
    @Serializable data object Legal : STNavKey
    @Serializable data class License(val assetPath: String) : STNavKey
    @Serializable data object Login : STNavKey
    @Serializable data object Onboarding : STNavKey
    @Serializable data object Account : STNavKey
}

val STNavKey.routeId: String
    get() = when (this) {
        STKey.Home -> STRoutes.HOME
        STKey.Characters -> STRoutes.CHARACTERS
        STKey.WorldInfo -> STRoutes.WORLD_INFO
        STKey.Settings -> STRoutes.SETTINGS
        is STKey.Chat -> STRoutes.CHAT
        is STKey.PastChats -> STRoutes.PAST_CHATS
        is STKey.GroupChat -> STRoutes.GROUP_CHAT_DETAIL
        STKey.NewGroupChat -> STRoutes.NEW_GROUP_CHAT
        is STKey.GroupSettings -> STRoutes.GROUP_SETTINGS
        is STKey.GroupMembers -> STRoutes.GROUP_MEMBERS
        is STKey.CharacterDetail -> STRoutes.CHARACTER_DETAIL
        STKey.CharacterCreate -> STRoutes.CHARACTER_NEW
        is STKey.CharacterEdit -> STRoutes.CHARACTER_EDIT
        is STKey.CharacterForm -> STRoutes.CHAR_FORM
        is STKey.CharacterGreetings -> STRoutes.CHAR_GREETINGS
        is STKey.CharacterAdvanced -> STRoutes.CHAR_ADVANCED
        STKey.WorldBookManage -> STRoutes.WORLD_INFO_MANAGE
        is STKey.LorebookDetail -> STRoutes.WORLD_INFO_BOOK
        is STKey.WorldEntryEdit -> STRoutes.WORLD_INFO_ENTRY
        STKey.WiGlobalSettings -> STRoutes.WORLD_INFO_GLOBAL
        STKey.Persona -> STRoutes.PERSONA
        STKey.Connections -> STRoutes.CONNECTIONS
        is STKey.ProviderDetail -> STRoutes.PROVIDER_DETAIL
        STKey.Presets -> STRoutes.PRESETS
        STKey.Appearance -> STRoutes.APPEARANCE
        STKey.AppearanceThemeColor -> STRoutes.APPEARANCE_THEME_COLOR
        STKey.AppearanceTypography -> STRoutes.APPEARANCE_TYPOGRAPHY
        STKey.AppearanceBackgrounds -> STRoutes.BACKGROUNDS
        STKey.AppearanceChatStyle -> STRoutes.APPEARANCE_CHAT_STYLE
        STKey.AppearanceChatBehavior -> STRoutes.CHAT_BEHAVIOR
        STKey.ManageSt -> STRoutes.MANAGE_ST
        STKey.ChatBackups -> STRoutes.CHAT_BACKUPS
        STKey.AuthorNote -> STRoutes.AUTHOR_NOTE
        STKey.Extensions -> STRoutes.EXTENSIONS
        STKey.QuickReplies -> STRoutes.QUICK_REPLIES
        STKey.Secrets -> STRoutes.SECRETS
        STKey.Logs -> STRoutes.LOGS
        STKey.Config -> STRoutes.CONFIG
        STKey.Legal -> STRoutes.LEGAL
        is STKey.License -> STRoutes.LICENSE
        STKey.Login -> STRoutes.LOGIN
        STKey.Onboarding -> STRoutes.ONBOARDING
        STKey.Account -> STRoutes.ACCOUNT
    }

private fun decodeRouteSegment(raw: String): String =
    runCatching { Uri.decode(raw) }.getOrNull()
        ?: runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrDefault(raw)

fun routeToNavKey(route: String): STNavKey = when {
    route == STRoutes.HOME -> STKey.Home
    route == STRoutes.CHARACTERS -> STKey.Characters
    route == STRoutes.WORLD_INFO -> STKey.WorldInfo
    route == STRoutes.SETTINGS -> STKey.Settings
    route == STRoutes.CHAT -> STKey.Chat(useCurrentStore = true)
    route == STRoutes.CHARACTER_NEW -> STKey.CharacterCreate
    route == STRoutes.NEW_GROUP_CHAT -> STKey.NewGroupChat
    route == STRoutes.WORLD_INFO_MANAGE -> STKey.WorldBookManage
    route == STRoutes.WORLD_INFO_GLOBAL -> STKey.WiGlobalSettings
    route == STRoutes.PERSONA -> STKey.Persona
    route == STRoutes.CONNECTIONS -> STKey.Connections
    route == STRoutes.PRESETS -> STKey.Presets
    route == STRoutes.APPEARANCE -> STKey.Appearance
    route == STRoutes.APPEARANCE_THEME_COLOR -> STKey.AppearanceThemeColor
    route == STRoutes.APPEARANCE_TYPOGRAPHY -> STKey.AppearanceTypography
    route == STRoutes.BACKGROUNDS -> STKey.AppearanceBackgrounds
    route == STRoutes.APPEARANCE_CHAT_STYLE -> STKey.AppearanceChatStyle
    route == STRoutes.CHAT_BEHAVIOR -> STKey.AppearanceChatBehavior
    route == STRoutes.MANAGE_ST -> STKey.ManageSt
    route == STRoutes.CHAT_BACKUPS -> STKey.ChatBackups
    route == STRoutes.AUTHOR_NOTE -> STKey.AuthorNote
    route == STRoutes.EXTENSIONS -> STKey.Extensions
    route == STRoutes.QUICK_REPLIES -> STKey.QuickReplies
    route == STRoutes.SECRETS -> STKey.Secrets
    route == STRoutes.LOGS -> STKey.Logs
    route == STRoutes.CONFIG -> STKey.Config
    route == STRoutes.LEGAL -> STKey.Legal
    route == STRoutes.LOGIN -> STKey.Login
    route == STRoutes.ONBOARDING -> STKey.Onboarding
    route == STRoutes.ACCOUNT -> STKey.Account
    route.startsWith("characters/detail/") ->
        STKey.CharacterDetail(decodeRouteSegment(route.removePrefix("characters/detail/")))
    route.startsWith("characters/edit/") ->
        STKey.CharacterEdit(decodeRouteSegment(route.removePrefix("characters/edit/")))
    route.startsWith("characters/form/") ->
        STKey.CharacterForm(decodeRouteSegment(route.removePrefix("characters/form/")))
    route.startsWith("characters/greetings/") ->
        STKey.CharacterGreetings(decodeRouteSegment(route.removePrefix("characters/greetings/")))
    route.startsWith("characters/advanced/") ->
        STKey.CharacterAdvanced(decodeRouteSegment(route.removePrefix("characters/advanced/")))
    route.startsWith("characters/chats/") ->
        STKey.PastChats(decodeRouteSegment(route.removePrefix("characters/chats/")))
    route.startsWith("world-info/book/") ->
        STKey.LorebookDetail(decodeRouteSegment(route.removePrefix("world-info/book/")))
    route.startsWith("api-connections/detail/") ->
        STKey.ProviderDetail(decodeRouteSegment(route.removePrefix("api-connections/detail/")))
    route.startsWith("group-chat/detail/") -> {
        val afterPrefix = route.removePrefix("group-chat/detail/")
        val groupPart = afterPrefix.substringBefore("?chatId=")
        val chatPart = afterPrefix.substringAfter("?chatId=", "")
        STKey.GroupChat(
            groupId = decodeRouteSegment(groupPart),
            chatId = decodeRouteSegment(chatPart)
        )
    }
    route.startsWith("group-chat/settings/") ->
        STKey.GroupSettings(decodeRouteSegment(route.removePrefix("group-chat/settings/")))
    route.startsWith("group-chat/members/") ->
        STKey.GroupMembers(decodeRouteSegment(route.removePrefix("group-chat/members/")))
    route.startsWith("settings/legal/") ->
        STKey.License(decodeRouteSegment(route.removePrefix("settings/legal/")))
    else -> STKey.Home
}
