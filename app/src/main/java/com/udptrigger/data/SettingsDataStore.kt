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
        val SOUND_FEEDBACK = booleanPreferencesKey("sound_feedback")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val WAKE_LOCK = booleanPreferencesKey("wake_lock")
        val PRESETS = stringPreferencesKey("presets")
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

    val soundFeedbackFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SOUND_FEEDBACK] ?: false
    }

    val autoReconnectFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AUTO_RECONNECT] ?: false
    }

    val keepScreenOnFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.KEEP_SCREEN_ON] ?: false
    }

    val wakeLockFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.WAKE_LOCK] ?: false
    }

    val presetsFlow: Flow<List<PresetData>> = context.dataStore.data.map { preferences ->
        val presetsJson = preferences[PreferencesKeys.PRESETS]
        if (presetsJson.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                parsePresetsJson(presetsJson)
            } catch (e: Exception) {
                emptyList()
            }
        }
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

    suspend fun setSoundFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SOUND_FEEDBACK] = enabled
        }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_RECONNECT] = enabled
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun setWakeLock(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WAKE_LOCK] = enabled
        }
    }

    suspend fun savePresets(presets: List<PresetData>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRESETS] = serializePresets(presets)
        }
    }

    private fun parsePresetsJson(json: String): List<PresetData> {
        // Simple JSON parsing for presets
        // Format: [{"id":"...","name":"...","description":"...","host":"...","port":5000,"packetContent":"...","includeTimestamp":true}]
        val result = mutableListOf<PresetData>()
        if (json.isEmpty() || json == "[]") return result

        try {
            // Basic parsing - split by entries
            val entries = json.removePrefix("[").removeSuffix("]").split("},{")
            for (entry in entries) {
                val cleanEntry = entry.removePrefix("{").removeSuffix("}")
                val props = cleanEntry.split(",").associate { prop ->
                    val (key, value) = prop.split(":", limit = 2)
                    key.trim().removeSurrounding("\"") to value.trim().removeSurrounding("\"")
                }

                result.add(
                    PresetData(
                        id = props["id"] ?: java.util.UUID.randomUUID().toString(),
                        name = props["name"] ?: "Unknown",
                        description = props["description"] ?: "",
                        config = UdpConfig(
                            host = props["host"] ?: "192.168.1.100",
                            port = props["port"]?.toIntOrNull() ?: 5000,
                            packetContent = props["packetContent"] ?: "TRIGGER",
                            includeTimestamp = props["includeTimestamp"]?.toBooleanStrictOrNull() ?: true
                        )
                    )
                )
            }
        } catch (e: Exception) {
            // Return empty list on parse error
        }
        return result
    }

    private fun serializePresets(presets: List<PresetData>): String {
        return presets.joinToString(",", "[", "]") { preset ->
            """{"id":"${preset.id}","name":"${preset.name}","description":"${preset.description}","host":"${preset.config.host}","port":${preset.config.port},"packetContent":"${preset.config.packetContent}","includeTimestamp":${preset.config.includeTimestamp}}"""
        }
    }
}

/**
 * Serializable preset data for storage
 */
data class PresetData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val config: UdpConfig
)

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
