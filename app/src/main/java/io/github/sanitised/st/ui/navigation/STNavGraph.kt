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
    const val SETTINGS = "me"

    const val LOGS = "settings/logs"
    const val CONFIG = "settings/config"
    const val LEGAL = "settings/legal"
    const val LICENSE = "settings/legal/{assetPath}"
    const val MANAGE_ST = "st-core"

    const val SECRETS = "settings/secrets"
    const val EXTENSIONS = "settings/extensions"
    const val AUTHOR_NOTE = "settings/author-note"
    const val QUICK_REPLIES = "settings/quick-replies"
    const val APPEARANCE = "settings/appearance"
    const val PROVIDER_DETAIL = "api-connections/detail/{providerId}"

    fun characterDetail(avatar: String): String = "characters/detail/${Uri.encode(avatar)}"
    fun characterEdit(avatar: String): String = "characters/edit/${Uri.encode(avatar)}"
    fun providerDetail(providerId: String): String = "api-connections/detail/${Uri.encode(providerId)}"
}
