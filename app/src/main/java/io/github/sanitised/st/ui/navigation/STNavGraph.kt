package io.github.sanitised.st.ui.navigation

import android.net.Uri

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
    const val GROUP_CHAT = "group-chat"
    const val GROUP_CHAT_DETAIL = "group-chat/detail/{groupId}?chatId={chatId}"
    const val GROUP_SETTINGS = "group-chat/settings/{groupId}"
    const val GROUP_MEMBERS = "group-chat/members/{groupId}"
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
    const val THEME = "settings/theme"
    const val CHAT_BEHAVIOR = "settings/chat-behavior"

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
