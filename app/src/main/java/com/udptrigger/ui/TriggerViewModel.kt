package com.udptrigger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udptrigger.domain.UdpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TriggerState(
    val isConnected: Boolean = false,
    val lastTriggerTime: Long? = null,
    val lastTimestamp: Long? = null,
    val error: String? = null,
    val config: UdpConfig = UdpConfig()
)

data class UdpConfig(
    val host: String = "192.168.1.100",
    val port: Int = 5000,
    val packetContent: String = "TRIGGER"
) {
    companion object {
        // Simple IPv4 validation regex
        private val IPV4_PATTERN = Regex(
            """^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"""
        )

        fun isValidHost(host: String): Boolean {
            if (host.isBlank()) return false
            // If it contains dots, it must be a valid IPv4
            if (host.contains('.')) {
                return IPV4_PATTERN.matches(host)
            }
            // Hostname without dots: alphanumeric and hyphens only
            return host.matches(Regex("""^[a-zA-Z0-9\-]+$"""))
        }
    }

    fun isValid(): Boolean = UdpConfig.isValidHost(host) && port in 1..65535
}

class TriggerViewModel : ViewModel() {

    private val _state = MutableStateFlow(TriggerState())
    val state: StateFlow<TriggerState> = _state.asStateFlow()

    private val udpClient = UdpClient()

    fun updateConfig(config: UdpConfig) {
        _state.value = _state.value.copy(config = config)
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

    fun trigger() {
        viewModelScope.launch {
            val timestamp = System.nanoTime()
            val message = "${_state.value.config.packetContent}:$timestamp".toByteArray(Charsets.UTF_8)
            val result = withContext(Dispatchers.IO) {
                udpClient.send(message)
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        lastTriggerTime = System.currentTimeMillis(),
                        lastTimestamp = timestamp,
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

    fun triggerWithTimestamp(timestamp: Long) {
        viewModelScope.launch {
            val message = "${_state.value.config.packetContent}:$timestamp".toByteArray(Charsets.UTF_8)
            val result = withContext(Dispatchers.IO) {
                udpClient.send(message)
            }
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        lastTriggerTime = System.currentTimeMillis(),
                        lastTimestamp = timestamp,
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

    fun disconnect() {
        if (!_state.value.isConnected) return

        viewModelScope.launch {
            udpClient.close()
            _state.value = _state.value.copy(isConnected = false, error = null)
        }
    }

    override fun onCleared() {
        super.onCleared()
        udpClient.closeSync()
    }
}
