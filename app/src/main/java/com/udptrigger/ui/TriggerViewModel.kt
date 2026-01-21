package com.udptrigger.ui

import android.content.Context
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
import com.udptrigger.domain.UdpClient
import kotlinx.coroutines.Dispatchers
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

data class TriggerState(
    val isConnected: Boolean = false,
    val isListening: Boolean = false,
    val listenPort: Int? = null,
    val lastTriggerTime: Long? = null,
    val lastTimestamp: Long? = null,
    val error: String? = null,
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
    val keepScreenOn: Boolean = false,
    val isNetworkAvailable: Boolean = true,
    val burstMode: BurstMode = BurstMode()
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
    private var lastTriggerNanoTime: Long = 0
    private val soundManager = SoundManager(context)
    private val networkMonitor = NetworkMonitor(context)
    private val dataManager = com.udptrigger.data.DataManager(context)

    // Variable counter for sequences
    private var packetSequence: Int = 0
    private var sessionPacketsSent: Int = 0

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
                    keepScreenOn = savedSettings.keepScreenOn
                )
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

    fun updateKeepScreenOn(enabled: Boolean) {
        _state.value = _state.value.copy(keepScreenOn = enabled)
        viewModelScope.launch {
            dataStore.saveKeepScreenOn(enabled)
        }
    }

    fun clearHistory() {
        _state.value = _state.value.copy(
            packetHistory = emptyList(),
            totalPacketsSent = 0,
            totalPacketsFailed = 0
        )
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

    fun trigger() {
        if (!checkRateLimit()) {
            _state.value = _state.value.copy(
                error = "Rate limit: please wait ${_state.value.rateLimitMs}ms between triggers"
            )
            return
        }

        viewModelScope.launch {
            val timestamp = System.nanoTime()
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

    fun connect() {
        if (_state.value.isConnected) return

        viewModelScope.launch {
            val config = _state.value.config
            try {
                udpClient.initialize(config.host, config.port)
                _state.value = _state.value.copy(isConnected = true, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Connection failed: ${e.message}")
            }
        }
    }

    fun disconnect() {
        if (!_state.value.isConnected) return

        viewModelScope.launch {
            udpClient.close()
            _state.value = _state.value.copy(isConnected = false, error = null)
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

    override fun onCleared() {
        super.onCleared()
        udpClient.closeSync()
        soundManager.release()
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
     * Update an existing custom preset
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
     * Delete a custom preset
     */
    fun deletePreset(name: String): Boolean {
        return com.udptrigger.data.PresetsManager.deleteCustomPreset(context, name)
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
}
