package com.qrtonfc.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val lockEnabled: Boolean = false,
    val lockWarningSeen: Boolean = false,
    val autoLoop: Boolean = true,
    val rawText: Boolean = false,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val lock = booleanPreferencesKey("lock_enabled")
        val lockWarning = booleanPreferencesKey("lock_warning_seen")
        val autoLoop = booleanPreferencesKey("auto_loop")
        val rawText = booleanPreferencesKey("raw_text")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            lockEnabled = p[Keys.lock] ?: false,
            lockWarningSeen = p[Keys.lockWarning] ?: false,
            autoLoop = p[Keys.autoLoop] ?: true,
            rawText = p[Keys.rawText] ?: false,
        )
    }

    suspend fun setLock(enabled: Boolean) = set(Keys.lock, enabled)
    suspend fun markLockWarningSeen() = set(Keys.lockWarning, true)
    suspend fun setAutoLoop(enabled: Boolean) = set(Keys.autoLoop, enabled)
    suspend fun setRawText(enabled: Boolean) = set(Keys.rawText, enabled)

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }
}
