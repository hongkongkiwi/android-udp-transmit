package com.udptrigger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.udptrigger.ui.UdpConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "udp_settings")

class SettingsDataStore(private val context: Context) {

    private object PreferencesKeys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val PACKET_CONTENT = stringPreferencesKey("packet_content")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val RATE_LIMIT_ENABLED = booleanPreferencesKey("rate_limit_enabled")
        val RATE_LIMIT_MS = intPreferencesKey("rate_limit_ms")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }

    val configFlow: Flow<UdpConfig> = context.dataStore.data.map { preferences ->
        UdpConfig(
            host = preferences[PreferencesKeys.HOST] ?: "192.168.1.100",
            port = preferences[PreferencesKeys.PORT] ?: 5000,
            packetContent = preferences[PreferencesKeys.PACKET_CONTENT] ?: "TRIGGER"
        )
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            hapticFeedbackEnabled = preferences[PreferencesKeys.HAPTIC_FEEDBACK] ?: true,
            soundEnabled = preferences[PreferencesKeys.SOUND_ENABLED] ?: false,
            rateLimitEnabled = preferences[PreferencesKeys.RATE_LIMIT_ENABLED] ?: true,
            rateLimitMs = (preferences[PreferencesKeys.RATE_LIMIT_MS] ?: 50).toLong(),
            autoReconnect = preferences[PreferencesKeys.AUTO_RECONNECT] ?: false,
            keepScreenOn = preferences[PreferencesKeys.KEEP_SCREEN_ON] ?: false
        )
    }

    suspend fun saveConfig(config: UdpConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HOST] = config.host
            preferences[PreferencesKeys.PORT] = config.port
            preferences[PreferencesKeys.PACKET_CONTENT] = config.packetContent
        }
    }

    suspend fun saveHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_FEEDBACK] = enabled
        }
    }

    suspend fun saveSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SOUND_ENABLED] = enabled
        }
    }

    suspend fun saveRateLimit(enabled: Boolean, ms: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RATE_LIMIT_ENABLED] = enabled
            preferences[PreferencesKeys.RATE_LIMIT_MS] = ms.toInt()
        }
    }

    suspend fun saveAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_RECONNECT] = enabled
        }
    }

    suspend fun saveKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = enabled
        }
    }
}

data class AppSettings(
    val hapticFeedbackEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val rateLimitEnabled: Boolean = true,
    val rateLimitMs: Long = 50,
    val autoReconnect: Boolean = false,
    val keepScreenOn: Boolean = false
)
