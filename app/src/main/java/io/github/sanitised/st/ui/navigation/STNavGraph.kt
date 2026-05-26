package io.github.sanitised.st.ui.navigation

import android.net.Uri

object STRoutes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val CHARACTERS = "characters"
    const val CHARACTER_NEW = "characters/new"
    const val CHARACTER_DETAIL = "characters/detail/{avatar}"
    const val CHARACTER_EDIT = "characters/edit/{avatar}"
    const val TOOLS = "tools"
    const val WORLD_INFO = "tools/world-info"
    const val PERSONA = "tools/persona"
    const val PRESETS = "tools/presets"
    const val CONNECTIONS = "tools/connections"
    const val CHAT_BACKUPS = "tools/chat-backups"
    const val SETTINGS = "settings"

    const val LOGS = "settings/logs"
    const val CONFIG = "settings/config"
    const val LEGAL = "settings/legal"
    const val LICENSE = "settings/legal/{assetPath}"
    const val MANAGE_ST = "settings/manage"

    fun characterDetail(avatar: String): String = "characters/detail/${Uri.encode(avatar)}"
    fun characterEdit(avatar: String): String = "characters/edit/${Uri.encode(avatar)}"
}
