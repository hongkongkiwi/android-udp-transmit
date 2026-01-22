package com.udptrigger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udptrigger.data.AppSettings
import com.udptrigger.data.SettingsDataStore
import com.udptrigger.domain.NetworkMonitor
import com.udptrigger.domain.SoundManager
import com.udptrigger.domain.TcpClient
import com.udptrigger.domain.UdpClient
import com.udptrigger.domain.WakeLockManager
import com.udptrigger.service.UdpForegroundService
import com.udptrigger.util.ErrorHandler
import com.udptrigger.widget.updateWidgetConnectionState
import com.udptrigger.util.ErrorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PacketHistoryEntry(
    val timestamp: Long,
    val nanoTime: Long,
    val success: Boolean,
    val errorMessage: String? = null,
    val type: PacketType = PacketType.SENT,
    val sourceAddress: String? = null,
    val sourcePort: Int? = null,
    val data: String? = null
)

enum class PacketType {
    SENT,
    RECEIVED
}

data class ReceivedPacketInfo(
    val timestamp: Long,
    val sourceAddress: String,
    val sourcePort: Int,
    val data: String,
    val length: Int
)

data class PacketSizeBreakdown(
    val totalSize: Int,
    val contentSize: Int,
    val separatorSize: Int,
    val timestampSize: Int,
    val burstIndexSize: Int,
    val isHexMode: Boolean
)

/**
 * Connection health status for monitoring UDP connection quality
 */
enum class ConnectionHealth {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    DISCONNECTED;

    fun getDisplayLabel(): String = when (this) {
        EXCELLENT -> "Excellent"
        GOOD -> "Good"
        FAIR -> "Fair"
        POOR -> "Poor"
        DISCONNECTED -> "Disconnected"
    }

    fun getColor(): Long = when (this) {
        EXCELLENT -> 0xFF4CAF50  // Green
        GOOD -> 0xFF8BC34A       // Light Green
        FAIR -> 0xFFFFC107       // Amber
        POOR -> 0xFFFF5722       // Orange-Red
        DISCONNECTED -> 0xFF9E9E9E // Gray
    }
}

data class TriggerState(
    val isConnected: Boolean = false,
    val isListening: Boolean = false,
    val listenPort: Int? = null,
    val lastTriggerTime: Long? = null,
    val lastTimestamp: Long? = null,
    val error: String? = null,
    // User-friendly error information for display
    val userFacingError: ErrorInfo? = null,
    val config: UdpConfig = UdpConfig(),
    val packetHistory: List<PacketHistoryEntry> = emptyList(),
    val receivedPackets: List<ReceivedPacketInfo> = emptyList(),
    val totalPacketsSent: Int = 0,
    val totalPacketsFailed: Int = 0,
    val totalPacketsReceived: Int = 0,
    val hapticFeedbackEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val rateLimitMs: Long = 50,
    val rateLimitEnabled: Boolean = true,
    val autoReconnect: Boolean = false,
    val autoConnectOnStartup: Boolean = false,
    val keepScreenOn: Boolean = false,
    val wakeLockEnabled: Boolean = false,
    val isWakeLockActive: Boolean = false,
    val foregroundServiceEnabled: Boolean = false,
    val isForegroundServiceActive: Boolean = false,
    val isNetworkAvailable: Boolean = true,
    val burstMode: BurstMode = BurstMode(),
    val scheduledTrigger: ScheduledTrigger = ScheduledTrigger(),
    val lastTriggered: Long = 0,
    val lastSendLatencyMs: Double = 0.0,
    val averageLatencyMs: Double = 0.0,
    // Historical latency for visualization (last 30 measurements, in milliseconds)
    val recentLatencyHistory: List<Double> = emptyList(),
    // Connection health monitoring
    val connectionHealth: ConnectionHealth = ConnectionHealth.DISCONNECTED,
    val recentSuccessRate: Float = 1.0f, // 0.0 to 1.0 (last 20 packets)
    val lastSuccessfulSendTime: Long? = null,
    val healthCheckEnabled: Boolean = true,
    val historyLimit: Int = 1000,
    val shareCode: String? = null,
    // Multi-target configuration
    val multiTargetConfig: com.udptrigger.data.MultiTargetConfig = com.udptrigger.data.MultiTargetConfig(),
    val roundRobinIndex: Int = 0, // For round-robin send mode
    // Packet action rules
    val packetActionRules: List<com.udptrigger.data.PacketActionRule> = emptyList(),
    // New comprehensive settings
    val themeSettings: com.udptrigger.data.ThemeSettings = com.udptrigger.data.ThemeSettings(),
    val autoRetrySettings: com.udptrigger.data.AutoRetrySettings = com.udptrigger.data.AutoRetrySettings(),
    val qosSettings: com.udptrigger.data.QosSettings = com.udptrigger.data.QosSettings(),
    val multicastSettings: com.udptrigger.data.MulticastSettings = com.udptrigger.data.MulticastSettings()
)

data class ScheduledTrigger(
    val enabled: Boolean = false,
    val intervalMs: Long = 1000,
    val packetCount: Int = 0, // 0 = infinite
    val packetsSent: Int = 0,
    val isRunning: Boolean = false
)

data class BurstMode(
    val enabled: Boolean = false,
    val packetCount: Int = 5,
    val delayMs: Long = 100,
    val isSending: Boolean = false
)

data class UdpConfig(
    val host: String = "192.168.1.100",
    val port: Int = 5000,
    val packetContent: String = "TRIGGER",
    val hexMode: Boolean = false,
    val includeTimestamp: Boolean = true,
    val includeBurstIndex: Boolean = false
) {
    companion object {
        // IPv4 validation regex
        private val IPV4_PATTERN = Regex(
            """^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"""
        )

        // IPv6 validation regex
        private val IPV6_PATTERN = Regex(
            """^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]+|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\.){3}(25[0-5]|(2[0-4]|1?[0-9])?[0-9]))$"""
        )

        fun isValidHost(host: String): Boolean {
            if (host.isBlank()) return false
            // Check for IPv6
            if (host.contains(':')) {
                return IPV6_PATTERN.matches(host)
            }
            // Check for IPv4
            if (host.contains('.')) {
                return IPV4_PATTERN.matches(host)
            }
            // Hostname without dots or colons: alphanumeric and hyphens only
            return host.matches(Regex("""^[a-zA-Z0-9\-]+$"""))
        }
    }

    fun isValid(): Boolean = isValidHost(host) && port in 1..65535
}

class TriggerViewModel(
    private val context: Context,
    private val dataStore: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(TriggerState())
    val state: StateFlow<TriggerState> = _state.asStateFlow()

    private val udpClient = UdpClient()
    private val tcpClient = TcpClient()
    private var lastTriggerNanoTime: Long = 0
    private val soundManager = SoundManager(context)
    private val networkMonitor = NetworkMonitor(context)
    private val dataManager = com.udptrigger.data.DataManager(context)
    private val wakeLockManager = WakeLockManager(context)
    private val packetRulesDataStore = com.udptrigger.data.PacketRulesDataStore(context)

    // Variable counter for sequences
    private var packetSequence: Int = 0
    private var sessionPacketsSent: Int = 0

    // Latency tracking
    private var latencySumMs: Double = 0.0
    private var latencyCount: Int = 0

    // Health tracking - track recent send results for connection quality
    private val recentSendResults = ArrayDeque<Boolean>()
    private val maxHealthTrackingSize = 20

    private val vibrator: Vibrator? by lazy {
        when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            }
            else -> @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        // Load custom presets
        com.udptrigger.data.PresetsManager.loadCustomPresets(context)

        // Load saved configuration
        viewModelScope.launch {
            try {
                val savedConfig = dataStore.configFlow.first()
                _state.value = _state.value.copy(config = savedConfig)
            } catch (e: Exception) {
                // Use default config if loading fails
            }
        }

        // Load saved settings
        viewModelScope.launch {
            try {
                val savedSettings = dataStore.settingsFlow.first()
                _state.value = _state.value.copy(
                    hapticFeedbackEnabled = savedSettings.hapticFeedbackEnabled,
                    soundEnabled = savedSettings.soundEnabled,
                    rateLimitEnabled = savedSettings.rateLimitEnabled,
                    rateLimitMs = savedSettings.rateLimitMs,
                    autoReconnect = savedSettings.autoReconnect,
                    autoConnectOnStartup = savedSettings.autoConnectOnStartup,
                    keepScreenOn = savedSettings.keepScreenOn,
                    wakeLockEnabled = savedSettings.wakeLockEnabled,
                    foregroundServiceEnabled = savedSettings.foregroundServiceEnabled,
                    healthCheckEnabled = savedSettings.healthCheckEnabled,
                    historyLimit = savedSettings.historyLimit
                )

                // Auto-connect on startup if enabled OR if there was a previous connection
                if (networkMonitor.isCurrentlyConnected()) {
                    // Check if there was a previous connection
                    val lastConnection = dataStore.lastConnectionFlow.first()

                    if (savedSettings.autoConnectOnStartup || lastConnection != null) {
                        kotlinx.coroutines.delay(500) // Small delay to ensure network is ready

                        // If we have a last connection, use that; otherwise use current config
                        if (lastConnection != null) {
                            // Load the last connection config
                            _state.value = _state.value.copy(
                                config = _state.value.config.copy(
                                    host = lastConnection.host,
                                    port = lastConnection.port
                                )
                            )
                        }
                        connect()
                    }
                }
            } catch (e: Exception) {
                // Use defaults if loading fails
            }
        }

        // Monitor network state for auto-reconnect
        networkMonitor.isNetworkAvailableFlow
            .onEach { isAvailable ->
                _state.value = _state.value.copy(isNetworkAvailable = isAvailable)
                if (isAvailable && _state.value.autoReconnect && !_state.value.isConnected) {
                    // Attempt auto-reconnect
                    connect()
                }
            }
            .launchIn(viewModelScope)

        // Load multi-target configuration
        viewModelScope.launch {
            try {
                val multiTargetConfig = dataStore.multiTargetConfigFlow.first()
                _state.value = _state.value.copy(multiTargetConfig = multiTargetConfig)
            } catch (e: Exception) {
                // Use defaults if loading fails
            }
        }

        // Load packet action rules
        viewModelScope.launch {
            packetRulesDataStore.rulesFlow.collect { rules ->
                _state.value = _state.value.copy(packetActionRules = rules)
            }
        }

        // Load theme settings
        viewModelScope.launch {
            try {
                val themeSettings = dataStore.themeSettingsFlow.first()
                _state.value = _state.value.copy(themeSettings = themeSettings)
            } catch (e: Exception) {
                // Use defaults if loading fails
            }
        }

        // Load auto-retry settings
        viewModelScope.launch {
            try {
                val autoRetrySettings = dataStore.autoRetrySettingsFlow.first()
                _state.value = _state.value.copy(autoRetrySettings = autoRetrySettings)
            } catch (e: Exception) {
                // Use defaults if loading fails
            }
        }

        // Load QoS settings
        viewModelScope.launch {
            try {
                val qosSettings = dataStore.qosSettingsFlow.first()
                _state.value = _state.value.copy(qosSettings = qosSettings)
            } catch (e: Exception) {
                // Use defaults if loading fails
            }
        }

        // Load multicast settings
        viewModelScope.launch {
            try {
                val multicastSettings = dataStore.multicastSettingsFlow.first()
                _state.value = _state.value.copy(multicastSettings = multicastSettings)
            } catch (e: Exception) {
                // Use defaults if loading fails
            }
        }
    }

    fun updateConfig(config: UdpConfig) {
        _state.value = _state.value.copy(config = config)
        viewModelScope.launch {
            dataStore.saveConfig(config)
        }
    }

    fun updatePacketOptions(hexMode: Boolean, includeTimestamp: Boolean, includeBurstIndex: Boolean) {
        _state.value = _state.value.copy(
            config = _state.value.config.copy(
                hexMode = hexMode,
                includeTimestamp = includeTimestamp,
                includeBurstIndex = includeBurstIndex
            )
        )
        viewModelScope.launch {
            dataStore.saveConfig(_state.value.config)
        }
    }

    fun getPacketSizePreview(): Int {
        return buildPacketMessage(System.nanoTime(), 0).size
    }

    /**
     * Get a detailed breakdown of the packet size
     */
    fun getPacketSizeBreakdown(): PacketSizeBreakdown {
        val config = _state.value.config
        val baseContent = config.packetContent.toByteArray(Charsets.UTF_8)

        var size = baseContent.size
        var separatorSize = 0
        var timestampSize = 0
        var burstIndexSize = 0

        if (config.hexMode) {
            // Hex mode: bytes depend on hex string length
            val hexBytes = hexStringToBytes(config.packetContent)
            size = hexBytes.size

            if (config.includeTimestamp) {
                // ":" + timestamp in hex
                val timestampHex = System.nanoTime().toString(16)
                separatorSize += 1 // ":"
                timestampSize = timestampHex.toByteArray(Charsets.UTF_8).size
                size += separatorSize + timestampSize
            }
        } else {
            // Text mode
            if (config.includeTimestamp && config.includeBurstIndex) {
                // ":timestamp:index"
                separatorSize = 2 // two colons
                timestampSize = System.nanoTime().toString().toByteArray(Charsets.UTF_8).size
                burstIndexSize = ":0".toByteArray(Charsets.UTF_8).size
                size += separatorSize + timestampSize + burstIndexSize
            } else if (config.includeTimestamp) {
                // ":timestamp"
                separatorSize = 1
                timestampSize = System.nanoTime().toString().toByteArray(Charsets.UTF_8).size
                size += separatorSize + timestampSize
            } else if (config.includeBurstIndex) {
                // ":index"
                separatorSize = 1
                burstIndexSize = ":0".toByteArray(Charsets.UTF_8).size
                size += separatorSize + burstIndexSize
            }
        }

        return PacketSizeBreakdown(
            totalSize = size,
            contentSize = if (config.hexMode) hexStringToBytes(config.packetContent).size else baseContent.size,
            separatorSize = separatorSize,
            timestampSize = timestampSize,
            burstIndexSize = burstIndexSize,
            isHexMode = config.hexMode
        )
    }

    fun updateHapticFeedback(enabled: Boolean) {
        _state.value = _state.value.copy(hapticFeedbackEnabled = enabled)
        viewModelScope.launch {
            dataStore.saveHapticFeedback(enabled)
        }
    }

    fun updateSoundEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(soundEnabled = enabled)
        viewModelScope.launch {
            dataStore.saveSoundEnabled(enabled)
        }
    }

    fun updateRateLimit(enabled: Boolean, ms: Long) {
        _state.value = _state.value.copy(
            rateLimitEnabled = enabled,
            rateLimitMs = ms
        )
        viewModelScope.launch {
            dataStore.saveRateLimit(enabled, ms)
        }
    }

    fun updateAutoReconnect(enabled: Boolean) {
        _state.value = _state.value.copy(autoReconnect = enabled)
        viewModelScope.launch {
            dataStore.saveAutoReconnect(enabled)
        }
    }

    fun updateAutoConnectOnStartup(enabled: Boolean) {
        _state.value = _state.value.copy(autoConnectOnStartup = enabled)
        viewModelScope.launch {
            dataStore.saveAutoConnectOnStartup(enabled)
        }
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        _state.value = _state.value.copy(keepScreenOn = enabled)
        viewModelScope.launch {
            dataStore.saveKeepScreenOn(enabled)
        }
    }

    fun updateWakeLockEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(wakeLockEnabled = enabled)
        viewModelScope.launch {
            dataStore.saveWakeLockEnabled(enabled)
            // Auto-acquire/release when connected/disconnected
            if (enabled && _state.value.isConnected) {
                acquireWakeLock()
            } else if (!enabled) {
                releaseWakeLock()
            }
        }
    }

    fun updateForegroundServiceEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(foregroundServiceEnabled = enabled)
        viewModelScope.launch {
            dataStore.saveForegroundServiceEnabled(enabled)
            // Auto-start/stop when connected/disconnected
            if (enabled && _state.value.isConnected) {
                startForegroundService()
            } else if (!enabled) {
                stopForegroundService()
            }
        }
    }

    fun updateHealthCheckEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(healthCheckEnabled = enabled)
        viewModelScope.launch {
            dataStore.saveHealthCheckEnabled(enabled)
            // Reset health tracking when toggling
            if (!enabled) {
                recentSendResults.clear()
                _state.value = _state.value.copy(
                    connectionHealth = if (_state.value.isConnected) ConnectionHealth.GOOD else ConnectionHealth.DISCONNECTED,
                    recentSuccessRate = 1.0f
                )
            }
        }
    }

    // Theme settings updates
    fun updateDarkTheme(darkTheme: Boolean?) {
        _state.value = _state.value.copy(
            themeSettings = _state.value.themeSettings.copy(darkTheme = darkTheme)
        )
        viewModelScope.launch {
            dataStore.updateDarkTheme(darkTheme)
        }
    }

    fun updateUseDynamicColors(enabled: Boolean) {
        _state.value = _state.value.copy(
            themeSettings = _state.value.themeSettings.copy(useDynamicColors = enabled)
        )
        viewModelScope.launch {
            dataStore.updateUseDynamicColors(enabled)
        }
    }

    // Auto-retry settings updates
    fun updateAutoRetryEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(
            autoRetrySettings = _state.value.autoRetrySettings.copy(enabled = enabled)
        )
        viewModelScope.launch {
            dataStore.updateAutoRetryEnabled(enabled)
        }
    }

    fun updateAutoRetryMaxAttempts(attempts: Int) {
        _state.value = _state.value.copy(
            autoRetrySettings = _state.value.autoRetrySettings.copy(maxAttempts = attempts.coerceIn(1, 10))
        )
        viewModelScope.launch {
            dataStore.updateAutoRetryMaxAttempts(attempts.coerceIn(1, 10))
        }
    }

    fun updateAutoRetryDelayMs(delayMs: Long) {
        _state.value = _state.value.copy(
            autoRetrySettings = _state.value.autoRetrySettings.copy(delayMs = delayMs.coerceIn(10, 5000))
        )
        viewModelScope.launch {
            dataStore.updateAutoRetryDelayMs(delayMs.coerceIn(10, 5000))
        }
    }

    // QoS settings updates
    fun updateQosDscpValue(value: Int) {
        _state.value = _state.value.copy(
            qosSettings = _state.value.qosSettings.copy(dscpValue = value.coerceIn(0, 63))
        )
        viewModelScope.launch {
            dataStore.updateQosDscpValue(value.coerceIn(0, 63))
        }
    }

    fun updateTransportProtocol(protocol: com.udptrigger.data.TransportProtocol) {
        _state.value = _state.value.copy(
            qosSettings = _state.value.qosSettings.copy(transportProtocol = protocol)
        )
        viewModelScope.launch {
            dataStore.updateTransportProtocol(protocol)
        }
    }

    // Multicast settings updates
    fun updateMulticastAddress(address: String) {
        _state.value = _state.value.copy(
            multicastSettings = _state.value.multicastSettings.copy(address = address)
        )
        viewModelScope.launch {
            dataStore.updateMulticastAddress(address)
        }
    }

    fun updateMulticastTtl(ttl: Int) {
        _state.value = _state.value.copy(
            multicastSettings = _state.value.multicastSettings.copy(ttl = ttl.coerceIn(0, 255))
        )
        viewModelScope.launch {
            dataStore.updateMulticastTtl(ttl.coerceIn(0, 255))
        }
    }

    private fun acquireWakeLock() {
        if (_state.value.wakeLockEnabled && !_state.value.isWakeLockActive) {
            if (wakeLockManager.acquire()) {
                _state.value = _state.value.copy(isWakeLockActive = true)
            }
        }
    }

    private fun releaseWakeLock() {
        if (_state.value.isWakeLockActive) {
            wakeLockManager.release()
            _state.value = _state.value.copy(isWakeLockActive = false)
        }
    }

    private fun startForegroundService() {
        if (_state.value.foregroundServiceEnabled && !_state.value.isForegroundServiceActive) {
            val intent = Intent(context, UdpForegroundService::class.java).apply {
                action = UdpForegroundService.ACTION_START
                putExtra(UdpForegroundService.EXTRA_IS_CONNECTED, _state.value.isConnected)
                putExtra(UdpForegroundService.EXTRA_TARGET_HOST, _state.value.config.host)
                putExtra(UdpForegroundService.EXTRA_TARGET_PORT, _state.value.config.port)
            }
            context.startService(intent)
            _state.value = _state.value.copy(isForegroundServiceActive = true)
        }
    }

    private fun stopForegroundService() {
        if (_state.value.isForegroundServiceActive) {
            val intent = Intent(context, UdpForegroundService::class.java).apply {
                action = UdpForegroundService.ACTION_STOP
            }
            context.startService(intent)
            _state.value = _state.value.copy(isForegroundServiceActive = false)
        }
    }

    private fun updateForegroundServiceNotification() {
        if (_state.value.isForegroundServiceActive) {
            val intent = Intent(context, UdpForegroundService::class.java).apply {
                action = UdpForegroundService.ACTION_UPDATE_STATUS
                putExtra(UdpForegroundService.EXTRA_IS_CONNECTED, _state.value.isConnected)
                putExtra(UdpForegroundService.EXTRA_TARGET_HOST, _state.value.config.host)
                putExtra(UdpForegroundService.EXTRA_TARGET_PORT, _state.value.config.port)
            }
            context.startService(intent)
        }
    }

    fun clearHistory() {
        _state.value = _state.value.copy(
            packetHistory = emptyList(),
            totalPacketsSent = 0,
            totalPacketsFailed = 0,
            lastSendLatencyMs = 0.0,
            averageLatencyMs = 0.0
        )
        // Reset latency tracking
        latencySumMs = 0.0
        latencyCount = 0
    }

    fun applyPreset(presetName: String) {
        val preset = com.udptrigger.data.PresetsManager.getPreset(presetName)
        preset?.let {
            updateConfig(it.config)
        }
    }

    fun updateBurstMode(enabled: Boolean, packetCount: Int, delayMs: Long) {
        _state.value = _state.value.copy(
            burstMode = _state.value.burstMode.copy(
                enabled = enabled,
                packetCount = packetCount.coerceIn(1, 100),
                delayMs = delayMs.coerceIn(10, 5000)
            )
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(
            error = null,
            userFacingError = null
        )
    }

    fun triggerBurst() {
        if (_state.value.burstMode.isSending) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                burstMode = _state.value.burstMode.copy(isSending = true)
            )

            val burstConfig = _state.value.burstMode
            repeat(burstConfig.packetCount) { index ->
                val timestamp = System.nanoTime()
                val message = buildPacketMessage(timestamp, index)
                val result = withContext(Dispatchers.IO) {
                    udpClient.send(message)
                }
                result.fold(
                    onSuccess = {
                        triggerHapticFeedback()
                        if (index == 0) playSoundEffect() // Only play sound once for burst
                        val newEntry = PacketHistoryEntry(
                            timestamp = System.currentTimeMillis(),
                            nanoTime = timestamp,
                            success = true,
                            type = PacketType.SENT
                        )
                        val updatedHistory = (listOf(newEntry) + _state.value.packetHistory).take(100)
                        _state.value = _state.value.copy(
                            lastTriggerTime = System.currentTimeMillis(),
                            lastTimestamp = timestamp,
                            error = null,
                            packetHistory = updatedHistory,
                            totalPacketsSent = _state.value.totalPacketsSent + 1
                        )
                    },
                    onFailure = { e ->
                        val newEntry = PacketHistoryEntry(
                            timestamp = System.currentTimeMillis(),
                            nanoTime = timestamp,
                            success = false,
                            errorMessage = e.message,
                            type = PacketType.SENT
                        )
                        val updatedHistory = (listOf(newEntry) + _state.value.packetHistory).take(100)
                        _state.value = _state.value.copy(
                            error = "Send failed: ${e.message}",
                            packetHistory = updatedHistory,
                            totalPacketsFailed = _state.value.totalPacketsFailed + 1
                        )
                    }
                )

                // Delay between packets (except after last one)
                if (index < burstConfig.packetCount - 1) {
                    kotlinx.coroutines.delay(burstConfig.delayMs)
                }
            }

            _state.value = _state.value.copy(
                burstMode = _state.value.burstMode.copy(isSending = false)
            )
        }
    }

    private fun triggerHapticFeedback() {
        if (!_state.value.hapticFeedbackEnabled) return

        vibrator?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(50)
            }
        }
    }

    private fun playSoundEffect() {
        if (_state.value.soundEnabled) {
            soundManager.playClickSound()
        }
    }

    private fun checkRateLimit(): Boolean {
        if (!_state.value.rateLimitEnabled) return true

        val currentTime = SystemClock.elapsedRealtimeNanos()
        val timeSinceLastTrigger = (currentTime - lastTriggerNanoTime) / 1_000_000

        if (timeSinceLastTrigger < _state.value.rateLimitMs) {
            return false
        }
        return true
    }

    /**
     * Send packet with auto-retry support
     */
    private suspend fun sendWithRetry(message: ByteArray): Result<Unit> {
        val retrySettings = _state.value.autoRetrySettings

        // First attempt
        val firstResult = withContext(Dispatchers.IO) {
            udpClient.send(message)
        }

        if (firstResult.isSuccess) {
            return firstResult
        }

        // Auto-retry if enabled
        if (!retrySettings.enabled) {
            return firstResult
        }

        // Retry loop
        repeat(retrySettings.maxAttempts - 1) { attempt ->
            delay(retrySettings.delayMs)
            val retryResult = withContext(Dispatchers.IO) {
                udpClient.send(message)
            }
            if (retryResult.isSuccess) {
                return retryResult
            }
        }

        return firstResult
    }

    fun trigger() {
        if (!checkRateLimit()) {
            _state.value = _state.value.copy(
                error = "Rate limit: please wait ${_state.value.rateLimitMs}ms between triggers"
            )
            return
        }

        // Set trigger time for animation
        _state.value = _state.value.copy(lastTriggered = System.currentTimeMillis())

        viewModelScope.launch {
            val timestamp = System.nanoTime()
            lastTriggerNanoTime = timestamp
            val message = buildPacketMessage(timestamp, 0)
            val result = sendWithRetry(message)
            result.fold(
                onSuccess = {
                    triggerHapticFeedback()
                    playSoundEffect()
                    val newEntry = PacketHistoryEntry(
                        timestamp = System.currentTimeMillis(),
                        nanoTime = timestamp,
                        success = true,
                        type = PacketType.SENT
                    )
                    val updatedHistory = (listOf(newEntry) + _state.value.packetHistory).take(100)
                    _state.value = _state.value.copy(
                        lastTriggerTime = System.currentTimeMillis(),
                        lastTimestamp = timestamp,
                        error = null,
                        packetHistory = updatedHistory,
                        totalPacketsSent = _state.value.totalPacketsSent + 1
                    )
                    updateHealthTracking(success = true)
                },
                onFailure = { e ->
                    val newEntry = PacketHistoryEntry(
                        timestamp = System.currentTimeMillis(),
                        nanoTime = timestamp,
                        success = false,
                        errorMessage = e.message,
                        type = PacketType.SENT
                    )
                    val updatedHistory = (listOf(newEntry) + _state.value.packetHistory).take(100)
                    _state.value = _state.value.copy(
                        error = "Send failed: ${e.message}",
                        packetHistory = updatedHistory,
                        totalPacketsFailed = _state.value.totalPacketsFailed + 1
                    )
                    updateHealthTracking(success = false)
                }
            )
        }
    }

    private fun buildPacketMessage(timestamp: Long, burstIndex: Int): ByteArray {
        val config = _state.value.config
        var baseContent = config.packetContent

        // Apply variable replacements
        baseContent = replaceVariables(baseContent, timestamp, burstIndex)

        return when {
            config.hexMode -> {
                // Hex mode: interpret packetContent as hex bytes
                val hexContent = if (config.includeTimestamp) {
                    "$baseContent:${timestamp.toString(16)}"
                } else {
                    baseContent
                }
                hexStringToBytes(hexContent)
            }
            config.includeTimestamp && config.includeBurstIndex && burstIndex >= 0 -> {
                "$baseContent:$timestamp:$burstIndex".toByteArray(Charsets.UTF_8)
            }
            config.includeTimestamp -> {
                "$baseContent:$timestamp".toByteArray(Charsets.UTF_8)
            }
            config.includeBurstIndex && burstIndex >= 0 -> {
                "$baseContent:$burstIndex".toByteArray(Charsets.UTF_8)
            }
            else -> {
                baseContent.toByteArray(Charsets.UTF_8)
            }
        }
    }

    /**
     * Replace variables in the packet content with dynamic values
     * Supported variables:
     * {sequence} - Incrementing sequence number
     * {session_count} - Total packets sent this session
     * {timestamp_ms} - Current timestamp in milliseconds
     * {timestamp_s} - Current timestamp in seconds
     * {random} - Random number 0-9999
     * {device_id} - Device identifier (first 8 chars of Android ID)
     * {burst_index} - Current burst index
     */
    private fun replaceVariables(content: String, timestamp: Long, burstIndex: Int): String {
        var result = content

        // Increment sequence for this packet
        packetSequence++
        sessionPacketsSent++

        // Sequence number
        result = result.replace("{sequence}", packetSequence.toString())

        // Session packet count
        result = result.replace("{session_count}", sessionPacketsSent.toString())

        // Timestamp in milliseconds
        result = result.replace("{timestamp_ms}", timestamp.toString())

        // Timestamp in seconds
        result = result.replace("{timestamp_s}", (timestamp / 1_000_000_000).toString())

        // Random number 0-9999
        result = result.replace("{random}", (0..9999).random().toString())

        // Device ID (first 8 chars of Android ID for privacy)
        val deviceId = try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ).take(8)
        } catch (e: Exception) {
            "UNKNOWN"
        }
        result = result.replace("{device_id}", deviceId)

        // Burst index
        result = result.replace("{burst_index}", burstIndex.toString())

        return result
    }

    /**
     * Reset the sequence counter
     */
    fun resetSequence() {
        packetSequence = 0
    }

    /**
     * Get current sequence number
     */
    fun getSequence(): Int = packetSequence

    /**
     * Get the device's IP address
     */
    fun getDeviceIpAddress(): String? {
        return networkMonitor.getDeviceIpAddress()
    }

    /**
     * Get all IP addresses of the device
     */
    fun getAllIpAddresses(): List<String> {
        return networkMonitor.getAllIpAddresses()
    }

    private fun hexStringToBytes(hex: String): ByteArray {
        // Remove any spaces or newlines
        val cleanHex = hex.replace("\\s".toRegex(), "")
        // Pad to even length if necessary
        val paddedHex = if (cleanHex.length % 2 != 0) "0$cleanHex" else cleanHex

        return paddedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    fun triggerWithTimestamp(timestamp: Long) {
        if (!checkRateLimit()) {
            return
        }

        viewModelScope.launch {
            lastTriggerNanoTime = timestamp
            val message = buildPacketMessage(timestamp, 0)
            val result = withContext(Dispatchers.IO) {
                udpClient.send(message)
            }
            result.fold(
                onSuccess = {
                    triggerHapticFeedback()
                    playSoundEffect()
                    val newEntry = PacketHistoryEntry(
                        timestamp = System.currentTimeMillis(),
                        nanoTime = timestamp,
                        success = true,
                        type = PacketType.SENT
                    )
                    val updatedHistory = (listOf(newEntry) + _state.value.packetHistory).take(100)
                    _state.value = _state.value.copy(
                        lastTriggerTime = System.currentTimeMillis(),
                        lastTimestamp = timestamp,
                        error = null,
                        packetHistory = updatedHistory,
                        totalPacketsSent = _state.value.totalPacketsSent + 1
                    )
                },
                onFailure = { e ->
                    val newEntry = PacketHistoryEntry(
                        timestamp = System.currentTimeMillis(),
                        nanoTime = timestamp,
                        success = false,
                        errorMessage = e.message,
                        type = PacketType.SENT
                    )
                    val updatedHistory = (listOf(newEntry) + _state.value.packetHistory).take(100)
                    _state.value = _state.value.copy(
                        error = "Send failed: ${e.message}",
                        packetHistory = updatedHistory,
                        totalPacketsFailed = _state.value.totalPacketsFailed + 1
                    )
                }
            )
        }
    }

    /**
     * LOW-LATENCY trigger path.
     * This bypasses coroutine dispatch overhead for minimal delay.
     * Called from key event handler to send UDP packet as fast as possible.
     *
     * Optimizations:
     * - No coroutine launch before send (synchronous call)
     * - No mutex lock (using pre-allocated buffer)
     * - State updates deferred to background coroutine after send
     *
     * @param timestamp The nanosecond timestamp from the key event
     * @return The send completion timestamp (nanos), or -1 if rate limited
     */
    fun triggerFast(timestamp: Long): Long {
        // Rate limit check (synchronous, very fast)
        if (!checkRateLimit()) {
            return -1L
        }

        // Update last trigger time for rate limiting
        lastTriggerNanoTime = timestamp

        // Build packet message
        val message = buildPacketMessage(timestamp, 0)

        // Check if multi-target mode is active
        val isMultiTarget = _state.value.multiTargetConfig.isActive()

        // SEND IMMEDIATELY without coroutine overhead
        val sendCompleteTime = if (isMultiTarget) {
            // Multi-target send
            val results = sendToMultipleTargetsFast(message)
            // Use the first successful timestamp, or -1 if all failed
            results.values.firstOrNull { it > 0 } ?: -1L
        } else {
            // Single target send
            udpClient.sendFast(message)
        }
        val success = sendCompleteTime > 0

        // For multi-target, calculate packets sent count
        val packetsSent = if (isMultiTarget) {
            _state.value.multiTargetConfig.getEnabledTargetCount()
        } else {
            1
        }

        // Calculate latency in milliseconds
        val latencyMs = if (success) {
            (sendCompleteTime - timestamp) / 1_000_000.0 // Convert nanos to millis
        } else {
            0.0
        }

        // Update latency tracking
        if (success && latencyMs > 0) {
            latencySumMs += latencyMs
            latencyCount++
        }

        // Defer all state updates to background coroutine
        viewModelScope.launch {
            if (success) {
                // Haptic and sound feedback (non-critical)
                triggerHapticFeedback()
                playSoundEffect()

                // Update state asynchronously
                val newEntry = PacketHistoryEntry(
                    timestamp = System.currentTimeMillis(),
                    nanoTime = timestamp,
                    success = true,
                    type = PacketType.SENT
                )
                val updatedHistory = (listOf(newEntry) + _state.value.packetHistory).take(100)
                // Update latency history (keep last 30 values)
                val updatedLatencyHistory = (listOf(latencyMs) + _state.value.recentLatencyHistory).take(30)
                _state.value = _state.value.copy(
                    lastTriggerTime = System.currentTimeMillis(),
                    lastTimestamp = timestamp,
                    lastTriggered = System.currentTimeMillis(),
                    lastSendLatencyMs = latencyMs,
                    averageLatencyMs = if (latencyCount > 0) latencySumMs / latencyCount else 0.0,
                    recentLatencyHistory = updatedLatencyHistory,
                    error = null,
                    userFacingError = null,
                    packetHistory = updatedHistory,
                    totalPacketsSent = _state.value.totalPacketsSent + packetsSent
                )
                updateHealthTracking(success = true)
            } else {
                val newEntry = PacketHistoryEntry(
                    timestamp = System.currentTimeMillis(),
                    nanoTime = timestamp,
                    success = false,
                    errorMessage = "Send failed",
                    type = PacketType.SENT
                )
                val updatedHistory = (listOf(newEntry) + _state.value.packetHistory).take(100)

                // Create a generic send error (no exception details available in fast path)
                val sendError = ErrorInfo(
                    title = "Send Failed",
                    message = "Failed to send UDP packet. The destination may be unreachable.",
                    recoverySuggestion = "Check:\n• Connection is active\n• Destination address and port are correct\n• Network is available"
                )

                _state.value = _state.value.copy(
                    error = "Send failed",
                    userFacingError = sendError,
                    packetHistory = updatedHistory,
                    totalPacketsFailed = _state.value.totalPacketsFailed + 1
                )
                updateHealthTracking(success = false)
            }
        }

        return sendCompleteTime
    }

    /**
     * Update connection health tracking after a send attempt
     * @param success Whether the send was successful
     */
    private fun updateHealthTracking(success: Boolean) {
        if (!_state.value.healthCheckEnabled) return

        // Add result to recent history (keeps last 20)
        recentSendResults.addLast(success)

        // Manually manage size - remove oldest if exceeds max
        if (recentSendResults.size > maxHealthTrackingSize) {
            recentSendResults.removeFirst()
        }

        // Calculate success rate
        val successRate = if (recentSendResults.isNotEmpty()) {
            recentSendResults.count { it }.toFloat() / recentSendResults.size
        } else {
            1.0f
        }

        // Calculate health based on multiple factors
        val health = when {
            !_state.value.isConnected -> ConnectionHealth.DISCONNECTED
            recentSendResults.isEmpty() -> ConnectionHealth.GOOD
            successRate >= 0.95f && _state.value.averageLatencyMs < 50.0 -> ConnectionHealth.EXCELLENT
            successRate >= 0.85f && _state.value.averageLatencyMs < 100.0 -> ConnectionHealth.GOOD
            successRate >= 0.70f -> ConnectionHealth.FAIR
            else -> ConnectionHealth.POOR
        }

        val now = System.currentTimeMillis()

        // Update state with new health info
        _state.value = _state.value.copy(
            connectionHealth = health,
            recentSuccessRate = successRate,
            lastSuccessfulSendTime = if (success) now else _state.value.lastSuccessfulSendTime
        )
    }

    /**
     * Reset health tracking when connection state changes
     */
    private fun resetHealthTracking() {
        recentSendResults.clear()
        _state.value = _state.value.copy(
            connectionHealth = if (_state.value.isConnected) ConnectionHealth.GOOD else ConnectionHealth.DISCONNECTED,
            recentSuccessRate = 1.0f,
            lastSuccessfulSendTime = null,
            recentLatencyHistory = emptyList()
        )
    }

    fun connect() {
        if (_state.value.isConnected) return

        viewModelScope.launch {
            val config = _state.value.config
            try {
                udpClient.initialize(config.host, config.port)
                _state.value = _state.value.copy(
                    isConnected = true,
                    error = null,
                    userFacingError = null
                )
                // Update widget connection state
                updateWidgetConnectionState(context, true)
                // Save last successful connection
                dataStore.saveLastConnection(config.host, config.port)
                // Reset and initialize health tracking
                resetHealthTracking()
                // Acquire wake lock if enabled
                acquireWakeLock()
                // Start foreground service if enabled
                startForegroundService()
            } catch (e: Exception) {
                val errorInfo = ErrorHandler.getConnectionErrorInfo(e, config.host, config.port)
                _state.value = _state.value.copy(
                    error = "Connection failed: ${e.message}",
                    userFacingError = errorInfo
                )
            }
        }
    }

    fun disconnect() {
        if (!_state.value.isConnected) return

        viewModelScope.launch {
            // Stop foreground service
            stopForegroundService()
            // Release wake lock before disconnecting
            releaseWakeLock()
            udpClient.close()
            _state.value = _state.value.copy(
                isConnected = false,
                error = null,
                userFacingError = null
            )
            // Update widget connection state
            updateWidgetConnectionState(context, false)
            // Clear last connection state when user manually disconnects
            dataStore.clearLastConnection()
            // Reset health tracking on disconnect
            resetHealthTracking()
        }
    }

    // Listen Mode for Receiving Packets

    /**
     * Start listening for UDP packets on the specified port
     */
    fun startListening(port: Int) {
        if (_state.value.isConnected || _state.value.isListening) return

        viewModelScope.launch {
            try {
                udpClient.initializeListen(port)
                _state.value = _state.value.copy(
                    isListening = true,
                    listenPort = port,
                    error = null
                )

                // Start receiving packets
                udpClient.startListening().collect { packet ->
                    val dataString = packet.data.toString(Charsets.UTF_8)
                    val receivedInfo = ReceivedPacketInfo(
                        timestamp = packet.timestamp,
                        sourceAddress = packet.sourceAddress.hostAddress ?: "Unknown",
                        sourcePort = packet.sourcePort,
                        data = dataString,
                        length = packet.length
                    )

                    // Add to received packets list (keep last 100)
                    val updatedReceived = (listOf(receivedInfo) + _state.value.receivedPackets).take(100)

                    // Also add to history
                    val historyEntry = PacketHistoryEntry(
                        timestamp = packet.timestamp,
                        nanoTime = System.nanoTime(),
                        success = true,
                        type = PacketType.RECEIVED,
                        sourceAddress = packet.sourceAddress.hostAddress,
                        sourcePort = packet.sourcePort,
                        data = dataString
                    )
                    val updatedHistory = (listOf(historyEntry) + _state.value.packetHistory).take(100)

                    _state.value = _state.value.copy(
                        receivedPackets = updatedReceived,
                        packetHistory = updatedHistory,
                        totalPacketsReceived = _state.value.totalPacketsReceived + 1
                    )

                    // Play sound/haptic for received packets
                    triggerHapticFeedback()
                    playSoundEffect()

                    // Check packet action rules
                    checkPacketActionRules(dataString, receivedInfo.sourceAddress, receivedInfo.sourcePort)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Listen failed: ${e.message}",
                    isListening = false
                )
            }
        }
    }

    /**
     * Stop listening for UDP packets
     */
    fun stopListening() {
        if (!_state.value.isListening) return

        viewModelScope.launch {
            udpClient.stopListening()
            udpClient.close()
            _state.value = _state.value.copy(
                isListening = false,
                listenPort = null,
                error = null
            )
        }
    }

    /**
     * Clear received packets history
     */
    fun clearReceivedPackets() {
        _state.value = _state.value.copy(
            receivedPackets = emptyList(),
            totalPacketsReceived = 0
        )
    }

    /**
     * Update the history limit setting
     */
    fun updateHistoryLimit(limit: Int) {
        val validLimit = limit.coerceIn(100, 100000) // Between 100 and 100000
        _state.value = _state.value.copy(historyLimit = validLimit)
        viewModelScope.launch {
            dataStore.saveHistoryLimit(validLimit)
        }
    }

    /**
     * Clear all data (both sent and received packet history)
     */
    fun clearAllData() {
        _state.value = _state.value.copy(
            packetHistory = emptyList(),
            receivedPackets = emptyList(),
            totalPacketsSent = 0,
            totalPacketsFailed = 0,
            totalPacketsReceived = 0,
            lastSendLatencyMs = 0.0,
            averageLatencyMs = 0.0,
            recentLatencyHistory = emptyList(),
            connectionHealth = ConnectionHealth.DISCONNECTED,
            recentSuccessRate = 1.0f,
            lastSuccessfulSendTime = null
        )
    }

    /**
     * Check received packet against action rules and execute matching rules
     */
    private fun checkPacketActionRules(packetContent: String, sourceAddress: String, sourcePort: Int) {
        viewModelScope.launch {
            try {
                val rules = packetRulesDataStore.rulesFlow.first()
                val enabledRules = rules.filter { it.enabled }

                for (rule in enabledRules) {
                    val matches = if (rule.useRegex) {
                        try {
                            Regex(rule.matchPattern).containsMatchIn(packetContent)
                        } catch (e: Exception) {
                            false // Invalid regex
                        }
                    } else {
                        packetContent.contains(rule.matchPattern, ignoreCase = false)
                    }

                    if (matches) {
                        com.udptrigger.data.executePacketAction(
                            context,
                            rule,
                            packetContent,
                            sourceAddress,
                            sourcePort
                        )
                    }
                }
            } catch (e: Exception) {
                // Silently fail on rule processing errors
            }
        }
    }

    // Scheduled Trigger Mode

    private var scheduledTriggerJob: kotlinx.coroutines.Job? = null

    /**
     * Update scheduled trigger settings
     */
    fun updateScheduledTrigger(enabled: Boolean, intervalMs: Long, packetCount: Int) {
        _state.value = _state.value.copy(
            scheduledTrigger = _state.value.scheduledTrigger.copy(
                enabled = enabled,
                intervalMs = intervalMs.coerceIn(100, 3600000), // 100ms to 1 hour
                packetCount = packetCount.coerceIn(0, 10000) // 0 = infinite, max 10000
            )
        )

        // Update running job if settings changed while running
        if (_state.value.scheduledTrigger.isRunning) {
            stopScheduledTrigger()
            startScheduledTrigger()
        }
    }

    /**
     * Start scheduled trigger mode
     */
    fun startScheduledTrigger() {
        if (!_state.value.isConnected) {
            _state.value = _state.value.copy(error = "Cannot start scheduled trigger: not connected")
            return
        }

        if (_state.value.scheduledTrigger.isRunning) return

        _state.value = _state.value.copy(
            scheduledTrigger = _state.value.scheduledTrigger.copy(
                isRunning = true,
                packetsSent = 0
            )
        )

        scheduledTriggerJob = viewModelScope.launch {
            while (_state.value.scheduledTrigger.isRunning) {
                // Check if we've reached the packet count limit
                if (_state.value.scheduledTrigger.packetCount > 0 &&
                    _state.value.scheduledTrigger.packetsSent >= _state.value.scheduledTrigger.packetCount) {
                    stopScheduledTrigger()
                    break
                }

                // Send the trigger
                triggerInternal()

                // Update packets sent counter
                _state.value = _state.value.copy(
                    scheduledTrigger = _state.value.scheduledTrigger.copy(
                        packetsSent = _state.value.scheduledTrigger.packetsSent + 1
                    )
                )

                // Wait for the interval (or stop if cancelled)
                kotlinx.coroutines.delay(_state.value.scheduledTrigger.intervalMs)
            }
        }
    }

    /**
     * Stop scheduled trigger mode
     */
    fun stopScheduledTrigger() {
        if (!_state.value.scheduledTrigger.isRunning) return

        scheduledTriggerJob?.cancel()
        scheduledTriggerJob = null

        _state.value = _state.value.copy(
            scheduledTrigger = _state.value.scheduledTrigger.copy(
                isRunning = false,
                packetsSent = 0
            )
        )
    }

    /**
     * Internal trigger method that bypasses rate limiting for scheduled triggers
     */
    private fun triggerInternal() {
        viewModelScope.launch {
            val timestamp = System.nanoTime()
            val message = buildPacketMessage(timestamp, 0)
            val result = withContext(Dispatchers.IO) {
                udpClient.send(message)
            }
            result.fold(
                onSuccess = {
                    triggerHapticFeedback()
                    playSoundEffect()
                    val newEntry = PacketHistoryEntry(
                        timestamp = System.currentTimeMillis(),
                        nanoTime = timestamp,
                        success = true,
                        type = PacketType.SENT
                    )
                    val updatedHistory = (listOf(newEntry) + _state.value.packetHistory).take(100)
                    _state.value = _state.value.copy(
                        lastTriggerTime = System.currentTimeMillis(),
                        lastTimestamp = timestamp,
                        error = null,
                        packetHistory = updatedHistory,
                        totalPacketsSent = _state.value.totalPacketsSent + 1
                    )
                },
                onFailure = { e ->
                    val newEntry = PacketHistoryEntry(
                        timestamp = System.currentTimeMillis(),
                        nanoTime = timestamp,
                        success = false,
                        errorMessage = e.message,
                        type = PacketType.SENT
                    )
                    val updatedHistory = (listOf(newEntry) + _state.value.packetHistory).take(100)
                    _state.value = _state.value.copy(
                        error = "Send failed: ${e.message}",
                        packetHistory = updatedHistory,
                        totalPacketsFailed = _state.value.totalPacketsFailed + 1
                    )
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        scheduledTriggerJob?.cancel()
        udpClient.closeSync()
        soundManager.release()
        wakeLockManager.cleanup()
        // Stop foreground service when ViewModel is cleared
        stopForegroundService()
    }

    // Custom Preset Management

    /**
     * Add a new custom preset from the current configuration
     * @param name Name for the new preset
     * @param description Optional description
     * @return true if added successfully, false if name already exists
     */
    fun saveAsPreset(name: String, description: String = ""): Boolean {
        val preset = com.udptrigger.data.CustomPreset(
            name = name,
            host = _state.value.config.host,
            port = _state.value.config.port,
            packetContent = _state.value.config.packetContent,
            hexMode = _state.value.config.hexMode,
            includeTimestamp = _state.value.config.includeTimestamp,
            includeBurstIndex = _state.value.config.includeBurstIndex,
            description = description
        )
        return com.udptrigger.data.PresetsManager.addCustomPreset(context, preset)
    }

    /**
     * Update an existing custom preset (with current config)
     */
    fun updatePreset(oldName: String, name: String, description: String = ""): Boolean {
        val preset = com.udptrigger.data.CustomPreset(
            name = name,
            host = _state.value.config.host,
            port = _state.value.config.port,
            packetContent = _state.value.config.packetContent,
            hexMode = _state.value.config.hexMode,
            includeTimestamp = _state.value.config.includeTimestamp,
            includeBurstIndex = _state.value.config.includeBurstIndex,
            description = description
        )
        return com.udptrigger.data.PresetsManager.updateCustomPreset(context, oldName, preset)
    }

    /**
     * Update name and description of an existing custom preset (keeping preset values)
     */
    fun updatePresetMetadata(oldName: String, newName: String, description: String): Boolean {
        val oldPreset = com.udptrigger.data.PresetsManager.customPresets.value.find { it.name == oldName }
            ?: return false

        val updatedPreset = com.udptrigger.data.CustomPreset(
            name = newName,
            description = description,
            host = oldPreset.host,
            port = oldPreset.port,
            packetContent = oldPreset.packetContent,
            hexMode = oldPreset.hexMode,
            includeTimestamp = oldPreset.includeTimestamp,
            includeBurstIndex = oldPreset.includeBurstIndex
        )
        return com.udptrigger.data.PresetsManager.updateCustomPreset(context, oldName, updatedPreset)
    }

    /**
     * Delete a custom preset
     */
    fun deletePreset(name: String): Boolean {
        return com.udptrigger.data.PresetsManager.deleteCustomPreset(context, name)
    }

    /**
     * Get a specific preset by name
     */
    fun getPreset(name: String): com.udptrigger.data.CustomPreset? {
        return com.udptrigger.data.PresetsManager.customPresets.value.find { it.name == name }
    }

    /**
     * Get all custom presets
     */
    fun getCustomPresets(): List<com.udptrigger.data.CustomPreset> {
        return com.udptrigger.data.PresetsManager.customPresets.value
    }

    /**
     * Check if a preset is custom (can be deleted)
     */
    fun isCustomPreset(name: String): Boolean {
        return com.udptrigger.data.PresetsManager.isCustomPreset(name)
    }

    // Import/Export Functionality

    /**
     * Export all app data to JSON
     */
    suspend fun exportData(outputStream: java.io.OutputStream): Result<String> {
        val settings = com.udptrigger.data.AppSettings(
            hapticFeedbackEnabled = _state.value.hapticFeedbackEnabled,
            soundEnabled = _state.value.soundEnabled,
            rateLimitEnabled = _state.value.rateLimitEnabled,
            rateLimitMs = _state.value.rateLimitMs,
            autoReconnect = _state.value.autoReconnect,
            keepScreenOn = _state.value.keepScreenOn
        )

        return dataManager.exportData(
            config = _state.value.config,
            settings = settings,
            customPresets = com.udptrigger.data.PresetsManager.customPresets.value,
            outputStream = outputStream
        )
    }

    /**
     * Import app data from JSON and apply it
     */
    suspend fun importData(inputStream: java.io.InputStream): Result<String> {
        return dataManager.importData(inputStream).fold(
            onSuccess = { import ->
                // Apply imported settings
                _state.value = _state.value.copy(
                    config = import.config,
                    hapticFeedbackEnabled = import.settings.hapticFeedbackEnabled,
                    soundEnabled = import.settings.soundEnabled,
                    rateLimitEnabled = import.settings.rateLimitEnabled,
                    rateLimitMs = import.settings.rateLimitMs,
                    autoReconnect = import.settings.autoReconnect,
                    keepScreenOn = import.settings.keepScreenOn
                )

                // Save to DataStore
                dataStore.saveConfig(import.config)
                dataStore.saveHapticFeedback(import.settings.hapticFeedbackEnabled)
                dataStore.saveSoundEnabled(import.settings.soundEnabled)
                dataStore.saveRateLimit(import.settings.rateLimitEnabled, import.settings.rateLimitMs)
                dataStore.saveAutoReconnect(import.settings.autoReconnect)
                dataStore.saveKeepScreenOn(import.settings.keepScreenOn)

                // Import custom presets
                import.customPresets.forEach { preset ->
                    com.udptrigger.data.PresetsManager.addCustomPreset(context, preset)
                }

                Result.success("Import successful: ${import.customPresets.size} presets loaded")
            },
            onFailure = { e ->
                Result.failure(e)
            }
        )
    }

    /**
     * Export packet history to CSV
     */
    suspend fun exportHistoryToCsv(outputStream: java.io.OutputStream): Result<String> {
        return dataManager.exportHistoryToCsv(
            history = _state.value.packetHistory,
            outputStream = outputStream
        )
    }

    /**
     * Export configuration to Uri (for file picker)
     */
    suspend fun exportConfig(uri: Uri) {
        try {
            val outputStream = context.contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                exportData(outputStream).fold(
                    onSuccess = {
                        _state.value = _state.value.copy(error = null)
                    },
                    onFailure = { e ->
                        _state.value = _state.value.copy(
                            error = "Export failed: ${e.message}"
                        )
                    }
                )
            } else {
                _state.value = _state.value.copy(
                    error = "Failed to open output stream"
                )
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Export failed: ${e.message}"
            )
        }
    }

    /**
     * Import configuration from Uri (for file picker)
     */
    suspend fun importConfig(uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                importData(inputStream).fold(
                    onSuccess = {
                        _state.value = _state.value.copy(error = null)
                    },
                    onFailure = { e ->
                        _state.value = _state.value.copy(
                            error = "Import failed: ${e.message}"
                        )
                    }
                )
            } else {
                _state.value = _state.value.copy(
                    error = "Failed to open input stream"
                )
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Import failed: ${e.message}"
            )
        }
    }

    /**
     * Export packet history to CSV via Uri (for file picker)
     */
    suspend fun exportHistoryToCsv(uri: Uri) {
        try {
            val outputStream = context.contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                exportHistoryToCsv(outputStream).fold(
                    onSuccess = {
                        _state.value = _state.value.copy(error = null)
                    },
                    onFailure = { e ->
                        _state.value = _state.value.copy(
                            error = "Export failed: ${e.message}"
                        )
                    }
                )
            } else {
                _state.value = _state.value.copy(
                    error = "Failed to open output stream"
                )
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Export failed: ${e.message}"
            )
        }
    }

    /**
     * Generate a share code for the current configuration
     */
    suspend fun generateShareCode() {
        com.udptrigger.data.generateShareCode(context).fold(
            onSuccess = { shareCode ->
                _state.value = _state.value.copy(
                    error = null,
                    shareCode = shareCode
                )
            },
            onFailure = { e ->
                _state.value = _state.value.copy(
                    error = "Failed to generate share code: ${e.message}"
                )
            }
        )
    }

    /**
     * Import configuration from a share code
     */
    suspend fun importShareCode(code: String) {
        com.udptrigger.data.importShareCode(context, code).fold(
            onSuccess = { config ->
                updateConfig(config)
                _state.value = _state.value.copy(
                    error = null,
                    shareCode = null
                )
            },
            onFailure = { e ->
                _state.value = _state.value.copy(
                    error = "Invalid share code: ${e.message}"
                )
            }
        )
    }

    /**
     * Reply to a received packet
     */
    suspend fun replyToPacket(
        sourceAddress: String,
        sourcePort: Int,
        data: String
    ) {
        if (!_state.value.isListening) {
            _state.value = _state.value.copy(
                error = "Cannot reply: not in listen mode"
            )
            return
        }

        val address = java.net.InetAddress.getByName(sourceAddress)
        val packetData = data.toByteArray()

        udpClient.sendTo(packetData, address, sourcePort).fold(
            onSuccess = {
                _state.value = _state.value.copy(error = null)
            },
            onFailure = { e ->
                _state.value = _state.value.copy(
                    error = "Reply failed: ${e.message}"
                )
            }
        )
    }

    // Multi-Target Management

    /**
     * Add a new target to the multi-target configuration
     */
    fun addTarget(name: String, host: String, port: Int) {
        val target = com.udptrigger.data.UdpTarget.create(name, host, port)
        val updatedConfig = _state.value.multiTargetConfig.addTarget(target)
        _state.value = _state.value.copy(multiTargetConfig = updatedConfig)
        viewModelScope.launch {
            dataStore.saveMultiTargetConfig(updatedConfig)
        }
    }

    /**
     * Remove a target from the multi-target configuration
     */
    fun removeTarget(id: String) {
        val updatedConfig = _state.value.multiTargetConfig.removeTarget(id)
        _state.value = _state.value.copy(multiTargetConfig = updatedConfig)
        viewModelScope.launch {
            dataStore.saveMultiTargetConfig(updatedConfig)
        }
    }

    /**
     * Update an existing target
     */
    fun updateTarget(id: String, name: String, host: String, port: Int) {
        val existingTarget = _state.value.multiTargetConfig.targets.find { it.id == id }
        if (existingTarget != null) {
            val updatedTarget = existingTarget.copy(
                name = name,
                host = host,
                port = port
            )
            val updatedConfig = _state.value.multiTargetConfig.updateTarget(id, updatedTarget)
            _state.value = _state.value.copy(multiTargetConfig = updatedConfig)
            viewModelScope.launch {
                dataStore.saveMultiTargetConfig(updatedConfig)
            }
        }
    }

    /**
     * Toggle a target's enabled state
     */
    fun toggleTargetEnabled(id: String) {
        val updatedConfig = _state.value.multiTargetConfig.toggleTarget(id)
        _state.value = _state.value.copy(multiTargetConfig = updatedConfig)
        viewModelScope.launch {
            dataStore.saveMultiTargetConfig(updatedConfig)
        }
    }

    /**
     * Enable or disable multi-target mode
     */
    fun setMultiTargetEnabled(enabled: Boolean) {
        val updatedConfig = _state.value.multiTargetConfig.copy(enabled = enabled)
        _state.value = _state.value.copy(multiTargetConfig = updatedConfig)
        viewModelScope.launch {
            dataStore.saveMultiTargetConfig(updatedConfig)
        }
    }

    /**
     * Update the send mode for multi-target
     */
    fun setMultiTargetMode(mode: com.udptrigger.data.SendMode) {
        val updatedConfig = _state.value.multiTargetConfig.copy(sendMode = mode)
        _state.value = _state.value.copy(multiTargetConfig = updatedConfig)
        viewModelScope.launch {
            dataStore.saveMultiTargetConfig(updatedConfig)
        }
    }

    /**
     * Update the sequential delay for multi-target
     */
    fun setMultiTargetDelay(delayMs: Long) {
        val updatedConfig = _state.value.multiTargetConfig.copy(sequentialDelayMs = delayMs.coerceIn(0, 5000))
        _state.value = _state.value.copy(multiTargetConfig = updatedConfig)
        viewModelScope.launch {
            dataStore.saveMultiTargetConfig(updatedConfig)
        }
    }

    /**
     * Reorder targets
     */
    fun reorderTargets(newOrder: List<String>) {
        val updatedConfig = _state.value.multiTargetConfig.reorderTargets(newOrder)
        _state.value = _state.value.copy(multiTargetConfig = updatedConfig)
        viewModelScope.launch {
            dataStore.saveMultiTargetConfig(updatedConfig)
        }
    }

    /**
     * Update target status after a send attempt
     */
    private fun updateTargetStatus(targetId: String, success: Boolean) {
        val target = _state.value.multiTargetConfig.targets.find { it.id == targetId } ?: return
        val updatedTarget = target.withStatus(success)
        val updatedConfig = _state.value.multiTargetConfig.updateTarget(targetId, updatedTarget)
        _state.value = _state.value.copy(multiTargetConfig = updatedConfig)
    }

    /**
     * Send to multiple targets (used in triggerFast)
     */
    private fun sendToMultipleTargetsFast(data: ByteArray): Map<String, Long> {
        val config = _state.value.multiTargetConfig
        if (!config.isActive()) return emptyMap()

        val enabledTargets = config.getEnabledTargets()
        if (enabledTargets.isEmpty()) return emptyMap()

        return when (config.sendMode) {
            com.udptrigger.data.SendMode.SEQUENTIAL, com.udptrigger.data.SendMode.PARALLEL -> {
                // Prepare target addresses
                val targets = enabledTargets.map { target ->
                    try {
                        Pair(java.net.InetAddress.getByName(target.host), target.port) to target.id
                    } catch (e: Exception) {
                        null
                    }
                }.filterNotNull().map { it.first to it.second }

                // Send and collect results
                val targetPairs = targets.map { it.first }
                val results = udpClient.sendFastMultiple(data, targetPairs)

                // Update target statuses
                val targetIds = targets.associate { "${it.first.first.hostAddress}:${it.first.second}" to it.second }
                results.forEach { (targetKey, timestamp) ->
                    val targetId = targetIds[targetKey]
                    if (targetId != null) {
                        updateTargetStatus(targetId, timestamp > 0)
                    }
                }

                results
            }
            com.udptrigger.data.SendMode.ROUND_ROBIN -> {
                // Send to next target in rotation
                val targetIndex = _state.value.roundRobinIndex % enabledTargets.size
                val target = enabledTargets[targetIndex]

                try {
                    val address = java.net.InetAddress.getByName(target.host)
                    val result = udpClient.sendFast(data)
                    updateTargetStatus(target.id, result > 0)

                    // Move to next target
                    val nextIndex = (targetIndex + 1) % enabledTargets.size
                    _state.value = _state.value.copy(roundRobinIndex = nextIndex)

                    if (result > 0) {
                        mapOf("${address.hostAddress}:${target.port}" to result)
                    } else {
                        emptyMap()
                    }
                } catch (e: Exception) {
                    updateTargetStatus(target.id, false)
                    emptyMap()
                }
            }
        }
    }

    // Packet Action Rules Management

    /**
     * Add a new packet action rule
     */
    fun addPacketActionRule(
        name: String,
        matchPattern: String,
        useRegex: Boolean,
        actionType: com.udptrigger.data.PacketActionType,
        actionData: String,
        replyHost: String,
        replyPort: Int
    ) {
        viewModelScope.launch {
            val rule = com.udptrigger.data.PacketActionRule(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                enabled = true,
                matchPattern = matchPattern,
                useRegex = useRegex,
                actionType = actionType,
                actionData = actionData,
                replyHost = replyHost,
                replyPort = replyPort
            )
            packetRulesDataStore.addRule(rule)
        }
    }

    /**
     * Update an existing packet action rule
     */
    fun updatePacketActionRule(
        id: String,
        name: String,
        matchPattern: String,
        useRegex: Boolean,
        actionType: com.udptrigger.data.PacketActionType,
        actionData: String,
        replyHost: String,
        replyPort: Int,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            val rule = com.udptrigger.data.PacketActionRule(
                id = id,
                name = name,
                enabled = enabled,
                matchPattern = matchPattern,
                useRegex = useRegex,
                actionType = actionType,
                actionData = actionData,
                replyHost = replyHost,
                replyPort = replyPort
            )
            packetRulesDataStore.updateRule(rule)
        }
    }

    /**
     * Delete a packet action rule
     */
    fun deletePacketActionRule(ruleId: String) {
        viewModelScope.launch {
            packetRulesDataStore.deleteRule(ruleId)
        }
    }

    /**
     * Toggle a packet action rule's enabled state
     */
    fun togglePacketActionRule(ruleId: String, enabled: Boolean) {
        viewModelScope.launch {
            packetRulesDataStore.toggleRule(ruleId, enabled)
        }
    }

    /**
     * Test a packet action rule (for validation)
     */
    fun testPacketActionRule(
        matchPattern: String,
        useRegex: Boolean,
        packetContent: String
    ): Boolean {
        return if (useRegex) {
            try {
                Regex(matchPattern).containsMatchIn(packetContent)
            } catch (e: Exception) {
                false
            }
        } else {
            packetContent.contains(matchPattern)
        }
    }
}
