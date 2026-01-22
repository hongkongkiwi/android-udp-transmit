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
        val HEX_MODE = booleanPreferencesKey("hex_mode")
        val INCLUDE_TIMESTAMP = booleanPreferencesKey("include_timestamp")
        val INCLUDE_BURST_INDEX = booleanPreferencesKey("include_burst_index")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val RATE_LIMIT_ENABLED = booleanPreferencesKey("rate_limit_enabled")
        val RATE_LIMIT_MS = intPreferencesKey("rate_limit_ms")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val AUTO_CONNECT_ON_STARTUP = booleanPreferencesKey("auto_connect_on_startup")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val WAKE_LOCK_ENABLED = booleanPreferencesKey("wake_lock_enabled")
        val FOREGROUND_SERVICE_ENABLED = booleanPreferencesKey("foreground_service_enabled")
        val HEALTH_CHECK_ENABLED = booleanPreferencesKey("health_check_enabled")
        val HISTORY_LIMIT = intPreferencesKey("history_limit")
        val LAST_WAS_CONNECTED = booleanPreferencesKey("last_was_connected")
        val LAST_CONNECTED_HOST = stringPreferencesKey("last_connected_host")
        val LAST_CONNECTED_PORT = intPreferencesKey("last_connected_port")
    }

    val configFlow: Flow<UdpConfig> = context.dataStore.data.map { preferences ->
        UdpConfig(
            host = preferences[PreferencesKeys.HOST] ?: "192.168.1.100",
            port = preferences[PreferencesKeys.PORT] ?: 5000,
            packetContent = preferences[PreferencesKeys.PACKET_CONTENT] ?: "TRIGGER",
            hexMode = preferences[PreferencesKeys.HEX_MODE] ?: false,
            includeTimestamp = preferences[PreferencesKeys.INCLUDE_TIMESTAMP] ?: true,
            includeBurstIndex = preferences[PreferencesKeys.INCLUDE_BURST_INDEX] ?: false
        )
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            hapticFeedbackEnabled = preferences[PreferencesKeys.HAPTIC_FEEDBACK] ?: true,
            soundEnabled = preferences[PreferencesKeys.SOUND_ENABLED] ?: false,
            rateLimitEnabled = preferences[PreferencesKeys.RATE_LIMIT_ENABLED] ?: true,
            rateLimitMs = (preferences[PreferencesKeys.RATE_LIMIT_MS] ?: 50).toLong(),
            autoReconnect = preferences[PreferencesKeys.AUTO_RECONNECT] ?: false,
            autoConnectOnStartup = preferences[PreferencesKeys.AUTO_CONNECT_ON_STARTUP] ?: false,
            keepScreenOn = preferences[PreferencesKeys.KEEP_SCREEN_ON] ?: false,
            wakeLockEnabled = preferences[PreferencesKeys.WAKE_LOCK_ENABLED] ?: false,
            foregroundServiceEnabled = preferences[PreferencesKeys.FOREGROUND_SERVICE_ENABLED] ?: true,
            healthCheckEnabled = preferences[PreferencesKeys.HEALTH_CHECK_ENABLED] ?: true,
            historyLimit = preferences[PreferencesKeys.HISTORY_LIMIT] ?: 1000
        )
    }

    suspend fun saveConfig(config: UdpConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HOST] = config.host
            preferences[PreferencesKeys.PORT] = config.port
            preferences[PreferencesKeys.PACKET_CONTENT] = config.packetContent
            preferences[PreferencesKeys.HEX_MODE] = config.hexMode
            preferences[PreferencesKeys.INCLUDE_TIMESTAMP] = config.includeTimestamp
            preferences[PreferencesKeys.INCLUDE_BURST_INDEX] = config.includeBurstIndex
        }
    }

    suspend fun savePacketOptions(hexMode: Boolean, includeTimestamp: Boolean, includeBurstIndex: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HEX_MODE] = hexMode
            preferences[PreferencesKeys.INCLUDE_TIMESTAMP] = includeTimestamp
            preferences[PreferencesKeys.INCLUDE_BURST_INDEX] = includeBurstIndex
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

    suspend fun saveAutoConnectOnStartup(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CONNECT_ON_STARTUP] = enabled
        }
    }

    suspend fun saveKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun saveWakeLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WAKE_LOCK_ENABLED] = enabled
        }
    }

    suspend fun saveForegroundServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FOREGROUND_SERVICE_ENABLED] = enabled
        }
    }

    suspend fun saveHealthCheckEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HEALTH_CHECK_ENABLED] = enabled
        }
    }

    suspend fun saveHistoryLimit(limit: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HISTORY_LIMIT] = limit
        }
    }

    /**
     * Save the last successful connection state
     */
    suspend fun saveLastConnection(host: String, port: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_WAS_CONNECTED] = true
            preferences[PreferencesKeys.LAST_CONNECTED_HOST] = host
            preferences[PreferencesKeys.LAST_CONNECTED_PORT] = port
        }
    }

    /**
     * Clear the last connection state (when user disconnects)
     */
    suspend fun clearLastConnection() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_WAS_CONNECTED] = false
        }
    }

    /**
     * Flow for last connection state
     */
    val lastConnectionFlow: Flow<LastConnectionInfo?> = context.dataStore.data.map { preferences ->
        val wasConnected = preferences[PreferencesKeys.LAST_WAS_CONNECTED] ?: false
        if (wasConnected) {
            LastConnectionInfo(
                host = preferences[PreferencesKeys.LAST_CONNECTED_HOST] ?: "192.168.1.100",
                port = preferences[PreferencesKeys.LAST_CONNECTED_PORT] ?: 5000
            )
        } else {
            null
        }
    }
}

/**
 * Information about the last successful connection
 */
data class LastConnectionInfo(
    val host: String,
    val port: Int
)

data class AppSettings(
    val hapticFeedbackEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val rateLimitEnabled: Boolean = true,
    val rateLimitMs: Long = 50,
    val autoReconnect: Boolean = false,
    val autoConnectOnStartup: Boolean = false,
    val keepScreenOn: Boolean = false,
    val wakeLockEnabled: Boolean = false,
    val foregroundServiceEnabled: Boolean = true,
    val healthCheckEnabled: Boolean = true,
    val historyLimit: Int = 1000
)
