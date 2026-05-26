package io.github.sanitised.st.ui.navigation

import android.net.Uri

object STRoutes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val CHARACTERS = "characters"
    const val CHARACTER_NEW = "characters/new"
    const val CHARACTER_EDIT = "characters/edit/{avatar}"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"

    const val LOGS = "settings/logs"
    const val CONFIG = "settings/config"
    const val LEGAL = "settings/legal"
    const val LICENSE = "settings/legal/{assetPath}"
    const val MANAGE_ST = "settings/manage"

    fun characterEdit(avatar: String): String = "characters/edit/${Uri.encode(avatar)}"
}
