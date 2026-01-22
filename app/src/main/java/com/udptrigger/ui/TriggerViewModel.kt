package com.udptrigger.ui

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udptrigger.data.SettingsDataStore
import com.udptrigger.data.UdpConfig
import com.udptrigger.domain.UdpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TriggerState(
    val isConnected: Boolean = false,
    val lastTriggerTime: Long? = null,
    val lastTimestamp: Long? = null,
    val error: String? = null,
    val config: UdpConfig = UdpConfig(),
    val lastSendLatencyMs: Double = 0.0,
    val averageLatencyMs: Double = 0.0
)

class TriggerViewModel(
    private val context: Context,
    private val dataStore: SettingsDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(TriggerState())
    val state: StateFlow<TriggerState> = _state.asStateFlow()

    private val udpClient = UdpClient()
    private var lastTriggerNanoTime: Long = 0

    // Latency tracking
    private var latencySumMs: Double = 0.0
    private var latencyCount: Int = 0

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
        // Load saved configuration
        viewModelScope.launch {
            try {
                val savedConfig = dataStore.configFlow.first()
                _state.value = _state.value.copy(config = savedConfig)
            } catch (e: Exception) {
                // Use default config if loading fails
            }
        }
    }

    fun updateConfig(config: UdpConfig) {
        _state.value = _state.value.copy(config = config)
        viewModelScope.launch {
            dataStore.saveConfig(config)
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
        viewModelScope.launch {
            val timestamp = System.nanoTime()
            lastTriggerNanoTime = timestamp
            val message = buildPacketMessage(timestamp)
            val result = udpClient.send(message)
            result.fold(
                onSuccess = {
                    triggerHapticFeedback()
                    val latencyMs = (System.nanoTime() - timestamp) / 1_000_000.0
                    updateLatencyTracking(latencyMs)
                    _state.value = _state.value.copy(
                        lastTriggerTime = System.currentTimeMillis(),
                        lastTimestamp = timestamp,
                        lastSendLatencyMs = latencyMs,
                        averageLatencyMs = if (latencyCount > 0) latencySumMs / latencyCount else 0.0,
                        error = null
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        error = "Send failed: ${e.message}"
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
     * @return The send completion timestamp (nanos), or -1 if failed
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
                // Haptic feedback (non-critical)
                triggerHapticFeedback()

                // Update state asynchronously
                _state.value = _state.value.copy(
                    lastTriggerTime = System.currentTimeMillis(),
                    lastTimestamp = timestamp,
                    lastSendLatencyMs = latencyMs,
                    averageLatencyMs = if (latencyCount > 0) latencySumMs / latencyCount else 0.0,
                    error = null
                )
            } else {
                _state.value = _state.value.copy(error = "Send failed")
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

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun connect() {
        if (_state.value.isConnected) return

        viewModelScope.launch {
            val config = _state.value.config
            try {
                udpClient.initialize(config.host, config.port)
                _state.value = _state.value.copy(
                    isConnected = true,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Connection failed: ${e.message}"
                )
            }
        }
    }

    fun disconnect() {
        if (!_state.value.isConnected) return

        viewModelScope.launch {
            udpClient.close()
            _state.value = _state.value.copy(
                isConnected = false,
                error = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        udpClient.closeSync()
    }
}
