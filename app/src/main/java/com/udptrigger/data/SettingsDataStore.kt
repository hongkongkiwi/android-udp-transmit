package com.udptrigger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "udp_settings")

class SettingsDataStore(private val context: Context) {

    private object PreferencesKeys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val PACKET_CONTENT = stringPreferencesKey("packet_content")
        val INCLUDE_TIMESTAMP = booleanPreferencesKey("include_timestamp")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val RATE_LIMIT_MS = intPreferencesKey("rate_limit_ms")
    }

    val configFlow: Flow<UdpConfig> = context.dataStore.data.map { preferences ->
        UdpConfig(
            host = preferences[PreferencesKeys.HOST] ?: "192.168.1.100",
            port = preferences[PreferencesKeys.PORT] ?: 5000,
            packetContent = preferences[PreferencesKeys.PACKET_CONTENT] ?: "TRIGGER",
            includeTimestamp = preferences[PreferencesKeys.INCLUDE_TIMESTAMP] ?: true
        )
    }

    val hapticEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HAPTIC_ENABLED] ?: true
    }

    val rateLimitMsFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.RATE_LIMIT_MS] ?: 100
    }

    suspend fun saveConfig(config: UdpConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HOST] = config.host
            preferences[PreferencesKeys.PORT] = config.port
            preferences[PreferencesKeys.PACKET_CONTENT] = config.packetContent
            preferences[PreferencesKeys.INCLUDE_TIMESTAMP] = config.includeTimestamp
        }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_ENABLED] = enabled
        }
    }

    suspend fun setRateLimitMs(ms: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RATE_LIMIT_MS] = ms
        }
    }
}

/**
 * Core UDP configuration data class.
 */
data class UdpConfig(
    val host: String = "192.168.1.100",
    val port: Int = 5000,
    val packetContent: String = "TRIGGER",
    val includeTimestamp: Boolean = true
) {
    companion object {
        private val IPV4_PATTERN = Regex(
            """^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"""
        )

        fun isValidHost(host: String): Boolean {
            if (host.isBlank()) return false
            if (host.contains('.')) {
                return IPV4_PATTERN.matches(host)
            }
            return host.matches(Regex("""^[a-zA-Z0-9\-]+$"""))
        }
    }

    fun isValid(): Boolean = isValidHost(host) && port in 1..65535
}
