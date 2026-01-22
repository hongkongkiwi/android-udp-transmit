package com.udptrigger.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udptrigger.R
import com.udptrigger.data.SettingsDataStore
import com.udptrigger.data.UdpConfig
import com.udptrigger.domain.SoundManager
import com.udptrigger.domain.UdpClient
import com.udptrigger.domain.WakeLockManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Validation result for configuration input
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
}

/**
 * Main UI state for the trigger screen
 */
data class TriggerState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isDisconnecting: Boolean = false,
    val lastTriggerTime: Long? = null,
    val lastTimestamp: Long? = null,
    val error: String? = null,
    val config: UdpConfig = UdpConfig(),
    val portError: String? = null,
    val hostError: String? = null,
    val lastSendLatencyMs: Double = 0.0,
    val averageLatencyMs: Double = 0.0,
    val packetsSent: Int = 0,
    val packetsFailed: Int = 0,
    val rateLimitMs: Int = 100,
    val hapticEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val wakeLockEnabled: Boolean = false
)

class TriggerViewModel(
    private val context: Context,
    private val dataStore: SettingsDataStore
) : ViewModel() {

    // Use application context to avoid memory leaks
    private val appContext: Context = context.applicationContext

    private val _state = MutableStateFlow(TriggerState())
    val state: StateFlow<TriggerState> = _state.asStateFlow()

    private val udpClient = UdpClient()
    private var lastTriggerNanoTime: Long = 0

    // Latency tracking
    private var latencySumMs: Double = 0.0
    private var latencyCount: Int = 0

    // Mutex to prevent concurrent connect/disconnect operations
    private val connectionMutex = Mutex()

    // Rate limiting
    private var lastSendTimeMs: Long = 0

    // Feedback managers
    private val soundManager: SoundManager by lazy { SoundManager(appContext) }
    private val wakeLockManager: WakeLockManager by lazy { WakeLockManager(appContext) }

    private val vibrator: Vibrator? by lazy {
        when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            }
            else -> @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        // Load saved configuration and settings
        viewModelScope.launch {
            try {
                val savedConfig = dataStore.configFlow.first()
                val rateLimit = dataStore.rateLimitMsFlow.first()
                val haptic = dataStore.hapticEnabledFlow.first()
                val sound = dataStore.soundFeedbackFlow.first()
                val wakeLock = dataStore.wakeLockFlow.first()

                _state.value = _state.value.copy(
                    config = savedConfig,
                    rateLimitMs = rateLimit,
                    hapticEnabled = haptic,
                    soundEnabled = sound,
                    wakeLockEnabled = wakeLock
                )

                // Apply wake lock if enabled
                if (wakeLock) {
                    wakeLockManager.acquire()
                }
            } catch (e: Exception) {
                // Use default config if loading fails
                _state.value = _state.value.copy(
                    error = appContext.getString(R.string.restore_failed, e.message)
                )
            }
        }
    }

    /**
     * Update rate limit setting
     */
    fun updateRateLimit(ms: Int) {
        _state.value = _state.value.copy(rateLimitMs = ms)
        viewModelScope.launch {
            dataStore.setRateLimitMs(ms)
        }
    }

    /**
     * Update haptic feedback setting
     */
    fun updateHapticEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(hapticEnabled = enabled)
        viewModelScope.launch {
            dataStore.setHapticEnabled(enabled)
        }
    }

    /**
     * Update sound feedback setting
     */
    fun updateSoundEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(soundEnabled = enabled)
        viewModelScope.launch {
            dataStore.setSoundFeedback(enabled)
        }
    }

    /**
     * Update wake lock setting
     */
    fun updateWakeLockEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(wakeLockEnabled = enabled)
        viewModelScope.launch {
            dataStore.setWakeLock(enabled)
            if (enabled) {
                wakeLockManager.acquire()
            } else {
                wakeLockManager.release()
            }
        }
    }

    /**
     * Validate port number
     */
    fun validatePort(port: String): ValidationResult {
        val portInt = port.toIntOrNull()
        return when {
            port.isBlank() -> ValidationResult.Invalid(appContext.getString(R.string.error_invalid_port))
            portInt == null -> ValidationResult.Invalid(appContext.getString(R.string.error_invalid_port))
            portInt < 1 || portInt > 65535 -> ValidationResult.Invalid(appContext.getString(R.string.error_invalid_port))
            else -> ValidationResult.Valid
        }
    }

    /**
     * Validate host IP address
     */
    fun validateHost(host: String): ValidationResult {
        return when {
            host.isBlank() -> ValidationResult.Invalid(appContext.getString(R.string.error_invalid_ip))
            isValidIpAddress(host) -> ValidationResult.Valid
            else -> ValidationResult.Invalid(appContext.getString(R.string.error_invalid_ip))
        }
    }

    private fun isValidIpAddress(host: String): Boolean {
        val ipPattern = Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$")
        return ipPattern.matches(host) || host == "localhost" || host.startsWith("192.168.") ||
                host.startsWith("10.") || host.startsWith("172.")
    }

    /**
     * Update configuration with validation
     */
    fun updateConfig(config: UdpConfig) {
        // Validate port
        val portError = when (val result = validatePort(config.port.toString())) {
            is ValidationResult.Invalid -> result.message
            else -> null
        }

        // Validate host
        val hostError = when (val result = validateHost(config.host)) {
            is ValidationResult.Invalid -> result.message
            else -> null
        }

        _state.value = _state.value.copy(
            config = config,
            portError = portError,
            hostError = hostError
        )

        viewModelScope.launch {
            try {
                dataStore.saveConfig(config)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = appContext.getString(R.string.error_send_failed, e.message)
                )
            }
        }
    }

    /**
     * Connect to the UDP endpoint
     */
    fun connect() {
        val config = _state.value.config
        val portResult = validatePort(config.port.toString())
        val hostResult = validateHost(config.host)

        if (portResult is ValidationResult.Invalid || hostResult is ValidationResult.Invalid) {
            _state.value = _state.value.copy(
                error = appContext.getString(R.string.error_invalid_config)
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isConnecting = true, error = null)
            try {
                udpClient.initialize(config.host, config.port)
                _state.value = _state.value.copy(
                    isConnected = true,
                    isConnecting = false,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isConnected = false,
                    isConnecting = false,
                    error = appContext.getString(R.string.error_connection_failed, e.message)
                )
            }
        }
    }

    /**
     * Disconnect from the UDP endpoint
     */
    fun disconnect() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isDisconnecting = true)
            try {
                udpClient.close()
                _state.value = _state.value.copy(
                    isConnected = false,
                    isDisconnecting = false,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isConnected = false,
                    isDisconnecting = false,
                    error = null
                )
            }
        }
    }

    /**
     * Clear the error state
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /**
     * Clear validation errors
     */
    fun clearValidationErrors() {
        _state.value = _state.value.copy(portError = null, hostError = null)
    }

    private fun triggerFeedback() {
        if (_state.value.hapticEnabled) {
            triggerHapticFeedback()
        }
        if (_state.value.soundEnabled) {
            soundManager.playFeedbackSound()
        }
    }

    private fun triggerHapticFeedback() {
        vibrator?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(50)
            }
        }
    }

    /**
     * Build the packet message with optional timestamp.
     */
    private fun buildPacketMessage(timestamp: Long): ByteArray {
        val config = _state.value.config
        return if (config.includeTimestamp) {
            "$config.packetContent:$timestamp".toByteArray(Charsets.UTF_8)
        } else {
            config.packetContent.toByteArray(Charsets.UTF_8)
        }
    }

    /**
     * Standard trigger (for button tap).
     */
    fun trigger() {
        if (!_state.value.isConnected) {
            _state.value = _state.value.copy(
                error = appContext.getString(R.string.error_not_connected)
            )
            return
        }

        // Check rate limiting
        val now = System.currentTimeMillis()
        val rateLimitMs = _state.value.rateLimitMs
        if (now - lastSendTimeMs < rateLimitMs) {
            return // Rate limit exceeded
        }
        lastSendTimeMs = now

        viewModelScope.launch {
            val timestamp = System.nanoTime()
            lastTriggerNanoTime = timestamp
            val message = buildPacketMessage(timestamp)
            val result = udpClient.send(message)
            result.fold(
                onSuccess = {
                    triggerFeedback()
                    val latencyMs = (System.nanoTime() - timestamp) / 1_000_000.0
                    updateLatencyTracking(latencyMs)
                    _state.value = _state.value.copy(
                        lastTriggerTime = System.currentTimeMillis(),
                        lastTimestamp = timestamp,
                        lastSendLatencyMs = latencyMs,
                        averageLatencyMs = if (latencyCount > 0) latencySumMs / latencyCount else 0.0,
                        packetsSent = _state.value.packetsSent + 1,
                        error = null
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        error = appContext.getString(R.string.error_send_failed, e.message),
                        packetsFailed = _state.value.packetsFailed + 1
                    )
                }
            )
        }
    }

    /**
     * LOW-LATENCY trigger path.
     * This bypasses coroutine dispatch overhead for minimal delay.
     * Called from key event handler to send UDP packet as fast as possible.
     */
    fun triggerFast(timestamp: Long): Long {
        // Update last trigger time
        lastTriggerNanoTime = timestamp

        // Build packet message
        val message = buildPacketMessage(timestamp)

        // SEND IMMEDIATELY without coroutine overhead
        val sendCompleteTime = udpClient.sendFast(message)
        val success = sendCompleteTime > 0

        // Calculate latency in milliseconds
        val latencyMs = if (success) {
            (sendCompleteTime - timestamp) / 1_000_000.0
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
                // Feedback (non-critical, deferred)
                triggerFeedback()

                // Update state asynchronously
                _state.value = _state.value.copy(
                    lastTriggerTime = System.currentTimeMillis(),
                    lastTimestamp = timestamp,
                    lastSendLatencyMs = latencyMs,
                    averageLatencyMs = if (latencyCount > 0) latencySumMs / latencyCount else 0.0,
                    packetsSent = _state.value.packetsSent + 1,
                    error = null
                )
            } else {
                _state.value = _state.value.copy(
                    error = appContext.getString(R.string.error_send_failed, "Unknown error"),
                    packetsFailed = _state.value.packetsFailed + 1
                )
            }
        }

        return sendCompleteTime
    }

    private fun updateLatencyTracking(latencyMs: Double) {
        if (latencyMs > 0) {
            latencySumMs += latencyMs
            latencyCount++
        }
    }
}
