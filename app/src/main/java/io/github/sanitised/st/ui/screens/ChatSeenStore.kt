package io.github.sanitised.st.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 未读打点:记录每个会话最后一次「看过」的时刻(进入/离开聊天屏时打点)。
 * 列表把 lastUpdated > lastSeen 的会话标为未读;从未打开过的会话不算未读。
 * revision 是 Compose state,打点后驱动列表重组。
 */
class ChatSeenStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("st_chat_seen", Context.MODE_PRIVATE)

    var revision by mutableIntStateOf(0)
        private set

    fun lastSeen(key: String): Long = prefs.getLong(key, 0L)

    fun markSeen(key: String, timestampMs: Long = System.currentTimeMillis()) {
        if (key.isBlank()) return
        prefs.edit().putLong(key, timestampMs).apply()
        revision++
    }
}
