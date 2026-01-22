package com.udptrigger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.udptrigger.ui.AccessibilitySettings
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
        val MULTI_TARGET_ENABLED = booleanPreferencesKey("multi_target_enabled")
        val MULTI_TARGET_CONFIG = stringPreferencesKey("multi_target_config")
        val MULTI_TARGET_MODE = stringPreferencesKey("multi_target_mode")
        val MULTI_TARGET_DELAY = intPreferencesKey("multi_target_delay")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val USE_DYNAMIC_COLORS = booleanPreferencesKey("use_dynamic_colors")
        val AUTO_RETRY_ENABLED = booleanPreferencesKey("auto_retry_enabled")
        val AUTO_RETRY_MAX_ATTEMPTS = intPreferencesKey("auto_retry_max_attempts")
        val AUTO_RETRY_DELAY_MS = intPreferencesKey("auto_retry_delay_ms")
        val QOS_DSCP_VALUE = intPreferencesKey("qos_dscp_value")
        val TRANSPORT_PROTOCOL = stringPreferencesKey("transport_protocol") // "UDP" or "TCP"
        val MULTICAST_ADDRESS = stringPreferencesKey("multicast_address")
        val MULTICAST_TTL = intPreferencesKey("multicast_ttl")
        // Accessibility settings
        val HIGH_CONTRAST_MODE = booleanPreferencesKey("high_contrast_mode")
        val LARGE_TEXT = booleanPreferencesKey("large_text")
        val ANNOUNCE_ERRORS = booleanPreferencesKey("announce_errors")
        val ANNOUNCE_PACKET_STATUS = booleanPreferencesKey("announce_packet_status")
        val ANNOUNCE_CONNECTION_CHANGES = booleanPreferencesKey("announce_connection_changes")
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

    val multiTargetConfigFlow: Flow<MultiTargetConfig> = context.dataStore.data.map { preferences ->
        val enabled = preferences[PreferencesKeys.MULTI_TARGET_ENABLED] ?: false
        val configJson = preferences[PreferencesKeys.MULTI_TARGET_CONFIG]
        val modeString = preferences[PreferencesKeys.MULTI_TARGET_MODE] ?: SendMode.SEQUENTIAL.name
        val delay = preferences[PreferencesKeys.MULTI_TARGET_DELAY] ?: 10

        val targets = if (configJson != null) {
            MultiTargetConfig.fromJson(configJson)?.targets ?: emptyList()
        } else {
            emptyList()
        }

        val mode = try {
            SendMode.valueOf(modeString)
        } catch (e: Exception) {
            SendMode.SEQUENTIAL
        }

        MultiTargetConfig(
            targets = targets,
            enabled = enabled,
            sendMode = mode,
            sequentialDelayMs = delay.toLong()
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

    suspend fun saveLastConnection(host: String, port: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_WAS_CONNECTED] = true
            preferences[PreferencesKeys.LAST_CONNECTED_HOST] = host
            preferences[PreferencesKeys.LAST_CONNECTED_PORT] = port
        }
    }

    suspend fun clearLastConnection() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_WAS_CONNECTED] = false
        }
    }

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

    // Multi-target configuration methods

    suspend fun saveMultiTargetConfig(config: MultiTargetConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MULTI_TARGET_ENABLED] = config.enabled
            preferences[PreferencesKeys.MULTI_TARGET_CONFIG] = MultiTargetConfig.toJson(config)
            preferences[PreferencesKeys.MULTI_TARGET_MODE] = config.sendMode.name
            preferences[PreferencesKeys.MULTI_TARGET_DELAY] = config.sequentialDelayMs.toInt()
        }
    }

    suspend fun updateMultiTargetEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MULTI_TARGET_ENABLED] = enabled
        }
    }

    suspend fun updateMultiTargetMode(mode: SendMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MULTI_TARGET_MODE] = mode.name
        }
    }

    suspend fun updateMultiTargetDelay(delayMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MULTI_TARGET_DELAY] = delayMs.toInt()
        }
    }

    // Theme settings
    val themeSettingsFlow: Flow<ThemeSettings> = context.dataStore.data.map { preferences ->
        ThemeSettings(
            darkTheme = preferences[PreferencesKeys.DARK_THEME] ?: null, // null = follow system
            useDynamicColors = preferences[PreferencesKeys.USE_DYNAMIC_COLORS] ?: true
        )
    }

    suspend fun saveThemeSettings(settings: ThemeSettings) {
        context.dataStore.edit { preferences ->
            settings.darkTheme?.let { preferences[PreferencesKeys.DARK_THEME] = it }
            preferences[PreferencesKeys.USE_DYNAMIC_COLORS] = settings.useDynamicColors
        }
    }

    suspend fun updateDarkTheme(darkTheme: Boolean?) {
        context.dataStore.edit { preferences ->
            darkTheme?.let { preferences[PreferencesKeys.DARK_THEME] = it } ?: preferences.remove(PreferencesKeys.DARK_THEME)
        }
    }

    suspend fun updateUseDynamicColors(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLORS] = enabled
        }
    }

    // Auto-retry settings
    val autoRetrySettingsFlow: Flow<AutoRetrySettings> = context.dataStore.data.map { preferences ->
        AutoRetrySettings(
            enabled = preferences[PreferencesKeys.AUTO_RETRY_ENABLED] ?: false,
            maxAttempts = preferences[PreferencesKeys.AUTO_RETRY_MAX_ATTEMPTS] ?: 3,
            delayMs = (preferences[PreferencesKeys.AUTO_RETRY_DELAY_MS] ?: 100).toLong()
        )
    }

    suspend fun saveAutoRetrySettings(settings: AutoRetrySettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_RETRY_ENABLED] = settings.enabled
            preferences[PreferencesKeys.AUTO_RETRY_MAX_ATTEMPTS] = settings.maxAttempts
            preferences[PreferencesKeys.AUTO_RETRY_DELAY_MS] = settings.delayMs.toInt()
        }
    }

    suspend fun updateAutoRetryEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_RETRY_ENABLED] = enabled
        }
    }

    suspend fun updateAutoRetryMaxAttempts(attempts: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_RETRY_MAX_ATTEMPTS] = attempts
        }
    }

    suspend fun updateAutoRetryDelayMs(delayMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_RETRY_DELAY_MS] = delayMs.toInt()
        }
    }

    // QoS/DSCP settings
    val qosSettingsFlow: Flow<QosSettings> = context.dataStore.data.map { preferences ->
        QosSettings(
            dscpValue = preferences[PreferencesKeys.QOS_DSCP_VALUE] ?: 0,
            transportProtocol = TransportProtocol.valueOf(
                preferences[PreferencesKeys.TRANSPORT_PROTOCOL] ?: "UDP"
            )
        )
    }

    suspend fun saveQosSettings(settings: QosSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.QOS_DSCP_VALUE] = settings.dscpValue
            preferences[PreferencesKeys.TRANSPORT_PROTOCOL] = settings.transportProtocol.name
        }
    }

    suspend fun updateQosDscpValue(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.QOS_DSCP_VALUE] = value.coerceIn(0, 63)
        }
    }

    suspend fun updateTransportProtocol(protocol: TransportProtocol) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRANSPORT_PROTOCOL] = protocol.name
        }
    }

    // Multicast settings
    val multicastSettingsFlow: Flow<MulticastSettings> = context.dataStore.data.map { preferences ->
        MulticastSettings(
            address = preferences[PreferencesKeys.MULTICAST_ADDRESS] ?: "230.0.0.1",
            ttl = preferences[PreferencesKeys.MULTICAST_TTL] ?: 1
        )
    }

    suspend fun saveMulticastSettings(settings: MulticastSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MULTICAST_ADDRESS] = settings.address
            preferences[PreferencesKeys.MULTICAST_TTL] = settings.ttl
        }
    }

    suspend fun updateMulticastAddress(address: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MULTICAST_ADDRESS] = address
        }
    }

    suspend fun updateMulticastTtl(ttl: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MULTICAST_TTL] = ttl.coerceIn(0, 255)
        }
    }

    // Accessibility settings flow
    val accessibilitySettingsFlow: Flow<AccessibilitySettings> = context.dataStore.data.map { preferences ->
        AccessibilitySettings(
            highContrastMode = preferences[PreferencesKeys.HIGH_CONTRAST_MODE] ?: false,
            largeText = preferences[PreferencesKeys.LARGE_TEXT] ?: false,
            announceErrors = preferences[PreferencesKeys.ANNOUNCE_ERRORS] ?: true,
            announcePacketStatus = preferences[PreferencesKeys.ANNOUNCE_PACKET_STATUS] ?: true,
            announceConnectionChanges = preferences[PreferencesKeys.ANNOUNCE_CONNECTION_CHANGES] ?: true,
            hapticFeedback = preferences[PreferencesKeys.HAPTIC_FEEDBACK] ?: true,
            soundFeedback = preferences[PreferencesKeys.SOUND_ENABLED] ?: false
        )
    }

    suspend fun saveAccessibilitySettings(settings: AccessibilitySettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIGH_CONTRAST_MODE] = settings.highContrastMode
            preferences[PreferencesKeys.LARGE_TEXT] = settings.largeText
            preferences[PreferencesKeys.ANNOUNCE_ERRORS] = settings.announceErrors
            preferences[PreferencesKeys.ANNOUNCE_PACKET_STATUS] = settings.announcePacketStatus
            preferences[PreferencesKeys.ANNOUNCE_CONNECTION_CHANGES] = settings.announceConnectionChanges
            preferences[PreferencesKeys.HAPTIC_FEEDBACK] = settings.hapticFeedback
            preferences[PreferencesKeys.SOUND_ENABLED] = settings.soundFeedback
        }
    }

    suspend fun updateHighContrastMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIGH_CONTRAST_MODE] = enabled
        }
    }

    suspend fun updateLargeText(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LARGE_TEXT] = enabled
        }
    }

    suspend fun updateAnnounceErrors(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ANNOUNCE_ERRORS] = enabled
        }
    }

    suspend fun updateAnnouncePacketStatus(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ANNOUNCE_PACKET_STATUS] = enabled
        }
    }

    suspend fun updateAnnounceConnectionChanges(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ANNOUNCE_CONNECTION_CHANGES] = enabled
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

/**
 * Theme settings for dark/light mode control
 */
data class ThemeSettings(
    val darkTheme: Boolean? = null, // null = follow system, true = dark, false = light
    val useDynamicColors: Boolean = true
)

/**
 * Auto-retry settings for failed packet sends
 */
data class AutoRetrySettings(
    val enabled: Boolean = false,
    val maxAttempts: Int = 3,
    val delayMs: Long = 100
)

/**
 * QoS and transport protocol settings
 */
data class QosSettings(
    val dscpValue: Int = 0, // 0-63, DSCP value for Type of Service
    val transportProtocol: TransportProtocol = TransportProtocol.UDP
)

/**
 * Transport protocol options
 */
enum class TransportProtocol {
    UDP,
    TCP,
    MULTICAST
}

/**
 * Multicast settings
 */
data class MulticastSettings(
    val address: String = "230.0.0.1",
    val ttl: Int = 1 // Time-to-live for multicast packets
)
