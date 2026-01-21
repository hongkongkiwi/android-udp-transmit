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
    fun isValid(): Boolean = host.isNotBlank() && port in 1..65535
}

class TriggerViewModel : ViewModel() {

    private val _state = MutableStateFlow(TriggerState())
    val state: StateFlow<TriggerState> = _state.asStateFlow()

    private val udpClient = UdpClient()

    fun updateConfig(config: UdpConfig) {
        _state.value = _state.value.copy(config = config)
    }

    fun connect() {
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
            val result = withContext(Dispatchers.IO) {
                udpClient.sendWithTimestamp()
            }
            result.fold(
                onSuccess = { timestamp ->
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
            val message = "${_state.value.config.packetContent}:$timestamp".toByteArray()
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

    fun setError(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun disconnect() {
        viewModelScope.launch {
            udpClient.close()
            _state.value = _state.value.copy(isConnected = false, error = null)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            udpClient.close()
        }
    }
}
