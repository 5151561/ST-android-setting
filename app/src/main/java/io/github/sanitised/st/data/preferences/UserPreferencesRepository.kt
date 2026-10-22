package io.github.sanitised.st.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import io.github.sanitised.st.ThemeColorSource
import io.github.sanitised.st.ThemeMode
import io.github.sanitised.st.UpdateChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class UserPreferences(
    val autoCheckUpdates: Boolean = false,
    val autoOpenBrowser: Boolean = false,
    val autoStartService: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val themeColorSource: ThemeColorSource = ThemeColorSource.BRAND,
    val updateChannel: UpdateChannel = UpdateChannel.RELEASE,
    val bubbleStyle: Boolean = true,
    val vibrationFeedback: Boolean = false,
    val secondConfirmation: Boolean = true,
    val swipeDrawer: Boolean = true,
    val developerMode: Boolean = false,
    val fontSize: Float = 14f,
    val reduceMotion: Boolean = false,
    val chatBackground: String = "",
)

class UserPreferencesRepository(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val _preferences = MutableStateFlow(readPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _preferences.value = readPreferences()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
    }

    fun refresh() {
        _preferences.value = readPreferences()
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply()
        _preferences.value = _preferences.value.copy(autoCheckUpdates = enabled)
    }

    fun setAutoOpenBrowser(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_OPEN_BROWSER, enabled).apply()
        _preferences.value = _preferences.value.copy(autoOpenBrowser = enabled)
    }

    fun setAutoStartService(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_SERVICE, enabled).apply()
        _preferences.value = _preferences.value.copy(autoStartService = enabled)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.storageValue).apply()
        _preferences.value = _preferences.value.copy(themeMode = mode)
    }

    fun setThemeColorSource(source: ThemeColorSource) {
        prefs.edit().putString(KEY_THEME_COLOR_SOURCE, source.storageValue).apply()
        _preferences.value = _preferences.value.copy(themeColorSource = source)
    }

    fun setUpdateChannel(channel: UpdateChannel) {
        prefs.edit().putString(KEY_CHANNEL, channel.storageValue).apply()
        _preferences.value = _preferences.value.copy(updateChannel = channel)
    }

    fun setBubbleStyle(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUBBLE_STYLE, enabled).apply()
        _preferences.value = _preferences.value.copy(bubbleStyle = enabled)
    }

    fun setVibrationFeedback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION_FEEDBACK, enabled).apply()
        _preferences.value = _preferences.value.copy(vibrationFeedback = enabled)
    }

    fun setSecondConfirmation(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SECOND_CONFIRMATION, enabled).apply()
        _preferences.value = _preferences.value.copy(secondConfirmation = enabled)
    }

    fun setSwipeDrawer(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SWIPE_DRAWER, enabled).apply()
        _preferences.value = _preferences.value.copy(swipeDrawer = enabled)
    }

    fun setDeveloperMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply()
        _preferences.value = _preferences.value.copy(developerMode = enabled)
    }

    fun setFontSize(size: Float) {
        prefs.edit().putFloat(KEY_FONT_SIZE, size).apply()
        _preferences.value = _preferences.value.copy(fontSize = size)
    }

    fun setReduceMotion(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCE_MOTION, enabled).apply()
        _preferences.value = _preferences.value.copy(reduceMotion = enabled)
    }

    fun setChatBackground(background: String) {
        prefs.edit().putString(KEY_CHAT_BACKGROUND, background).apply()
        _preferences.value = _preferences.value.copy(chatBackground = background)
    }

    private fun readPreferences(): UserPreferences {
        return UserPreferences(
            autoCheckUpdates = prefs.getBoolean(KEY_AUTO_CHECK, false),
            autoOpenBrowser = prefs.getBoolean(KEY_AUTO_OPEN_BROWSER, false),
            autoStartService = prefs.getBoolean(KEY_AUTO_START_SERVICE, true),
            themeMode = ThemeMode.fromStorage(
                prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.storageValue)
            ),
            themeColorSource = ThemeColorSource.fromStorage(
                prefs.getString(KEY_THEME_COLOR_SOURCE, ThemeColorSource.BRAND.storageValue)
            ),
            updateChannel = UpdateChannel.fromStorage(
                prefs.getString(KEY_CHANNEL, UpdateChannel.RELEASE.storageValue)
            ),
            bubbleStyle = prefs.getBoolean(KEY_BUBBLE_STYLE, true),
            vibrationFeedback = prefs.getBoolean(KEY_VIBRATION_FEEDBACK, false),
            secondConfirmation = prefs.getBoolean(KEY_SECOND_CONFIRMATION, true),
            swipeDrawer = prefs.getBoolean(KEY_SWIPE_DRAWER, true),
            developerMode = prefs.getBoolean(KEY_DEVELOPER_MODE, false),
            fontSize = prefs.getFloat(KEY_FONT_SIZE, 14f),
            reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, false),
            chatBackground = prefs.getString(KEY_CHAT_BACKGROUND, "") ?: "",
        )
    }

    companion object {
        const val APP_PREFS_NAME = "updates"
        const val KEY_AUTO_CHECK = "auto_check"
        const val KEY_AUTO_OPEN_BROWSER = "auto_open_browser"
        const val KEY_AUTO_START_SERVICE = "auto_start_service"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_COLOR_SOURCE = "theme_color_source"
        const val KEY_CHANNEL = "channel"
        const val KEY_BUBBLE_STYLE = "bubble_style"
        const val KEY_VIBRATION_FEEDBACK = "vibration_feedback"
        const val KEY_SECOND_CONFIRMATION = "second_confirmation"
        const val KEY_SWIPE_DRAWER = "swipe_drawer"
        const val KEY_DEVELOPER_MODE = "developer_mode"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_REDUCE_MOTION = "reduce_motion"
        const val KEY_CHAT_BACKGROUND = "chat_background"
    }
}
