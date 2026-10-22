package io.github.sanitised.st.data.preferences

import android.content.SharedPreferences
import io.github.sanitised.st.ThemeColorSource
import io.github.sanitised.st.ThemeMode
import io.github.sanitised.st.UpdateChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesRepositoryTest {

    @Test
    fun readsDefaultPreferencesAndUpdatesStateFlowAndStorage() {
        val prefs = InMemorySharedPreferences()
        val repo = UserPreferencesRepository(prefs)

        val initial = repo.preferences.value
        assertFalse(initial.autoCheckUpdates)
        assertTrue(initial.autoStartService)
        assertEquals(ThemeMode.DARK, initial.themeMode)
        assertEquals(ThemeColorSource.BRAND, initial.themeColorSource)
        assertEquals(14f, initial.fontSize, 0.001f)

        repo.setThemeMode(ThemeMode.LIGHT)
        repo.setThemeColorSource(ThemeColorSource.DYNAMIC)
        repo.setUpdateChannel(UpdateChannel.PRERELEASE)
        repo.setFontSize(16.5f)
        repo.setBubbleStyle(false)
        repo.setChatBackground("bg_forest.png")

        val updated = repo.preferences.value
        assertEquals(ThemeMode.LIGHT, updated.themeMode)
        assertEquals(ThemeColorSource.DYNAMIC, updated.themeColorSource)
        assertEquals(UpdateChannel.PRERELEASE, updated.updateChannel)
        assertEquals(16.5f, updated.fontSize, 0.001f)
        assertFalse(updated.bubbleStyle)
        assertEquals("bg_forest.png", updated.chatBackground)

        // Verify persisted values survive re-instantiation
        val reloaded = UserPreferencesRepository(prefs).preferences.value
        assertEquals(updated, reloaded)
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { pending[key] = values?.toSet() }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }
            override fun remove(key: String): SharedPreferences.Editor = apply { pending[key] = null }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) data.clear()
                for ((k, v) in pending) {
                    if (v == null) data.remove(k) else data[k] = v
                    listeners.forEach { it.onSharedPreferenceChanged(this@InMemorySharedPreferences, k) }
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            listeners.add(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            listeners.remove(listener)
        }
    }
}
