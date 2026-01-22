package com.udptrigger.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udptrigger.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.text.SimpleDateFormat
import java.util.*

/**
 * Received packet data class
 */
data class ReceivedPacket(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val data: String,
    val sourceIp: String,
    val sourcePort: Int
)

/**
 * Listen Mode ViewModel
 */
class ListenModeViewModel(
    private val context: Context
) : ViewModel() {

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _receivedPackets = MutableStateFlow<List<ReceivedPacket>>(emptyList())
    val receivedPackets: StateFlow<List<ReceivedPacket>> = _receivedPackets.asStateFlow()

    private val _listenPort = MutableStateFlow(5001)
    val listenPort: StateFlow<Int> = _listenPort.asStateFlow()

    private val _localIp = MutableStateFlow("")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private var listenSocket: DatagramSocket? = null

    init {
        viewModelScope.launch {
            _localIp.value = getLocalIpAddress()
        }
    }

    fun updateListenPort(port: Int) {
        _listenPort.value = port.coerceIn(1024, 65535)
    }

    fun startListening() {
        if (_isListening.value) return

        viewModelScope.launch {
            _isListening.value = true

            withContext(Dispatchers.IO) {
                try {
                    listenSocket = DatagramSocket(_listenPort.value).also { socket ->
                        socket.broadcast = true
                    }

                    val buffer = ByteArray(1024)

                    while (_isListening.value && listenSocket != null && !listenSocket!!.isClosed) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            listenSocket!!.receive(packet)

                            val data = String(packet.data, 0, packet.length).trim()
                            if (data.isNotEmpty()) {
                                val receivedPacket = ReceivedPacket(
                                    data = data,
                                    sourceIp = packet.address.hostAddress ?: "Unknown",
                                    sourcePort = packet.port
                                )
                                _receivedPackets.value = listOf(receivedPacket) + _receivedPackets.value
                            }
                        } catch (e: Exception) {
                            if (_isListening.value) {
                                // Continue listening
                            }
                        }
                    }
                } catch (e: Exception) {
                    _isListening.value = false
                }
            }
        }
    }

    fun stopListening() {
        _isListening.value = false
        listenSocket?.close()
        listenSocket = null
    }

    fun clearPackets() {
        _receivedPackets.value = emptyList()
    }

    private fun getLocalIpAddress(): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            String.format("%d.%d.%d.%d",
                ipAddress and 0xff,
                (ipAddress shr 8) and 0xff,
                (ipAddress shr 16) and 0xff,
                (ipAddress shr 24) and 0xff
            )
        } catch (e: Exception) {
            "Unknown"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}

/**
 * Listen mode dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenModeDialog(
    onDismiss: () -> Unit,
    listenViewModel: ListenModeViewModel = viewModel(
        factory = ListenModeViewModelFactory(LocalContext.current)
    )
) {
    val isListening by listenViewModel.isListening.collectAsState()
    val receivedPackets by listenViewModel.receivedPackets.collectAsState()
    val localIp by listenViewModel.localIp.collectAsState()

    var portInput by remember { mutableStateOf("5001") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.8f)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.listen_mode_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                HorizontalDivider()

                // Connection info
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.listen_mode_device_ip, localIp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Listening on port: ${listenViewModel.listenPort.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                // Port configuration
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it },
                    label = { Text("Listen Port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    enabled = !isListening
                )

                HorizontalDivider()

                // Received packets
                Text(
                    text = "Received Packets (${receivedPackets.size})",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(16.dp)
                )

                if (receivedPackets.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = if (isListening) "Waiting for packets..." else "Not listening",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(receivedPackets) { packet ->
                            ReceivedPacketItem(packet = packet)
                        }
                    }
                }

                HorizontalDivider()

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isListening) {
                        OutlinedButton(
                            onClick = { listenViewModel.stopListening() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.listen_mode_stop))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { listenViewModel.clearPackets() },
                            enabled = receivedPackets.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.listen_mode_clear))
                        }

                        Button(
                            onClick = {
                                portInput.toIntOrNull()?.let { listenViewModel.updateListenPort(it) }
                                listenViewModel.startListening()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.listen_mode_start))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReceivedPacketItem(packet: ReceivedPacket) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(packet.timestamp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text("${packet.sourceIp}:${packet.sourcePort}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(packet.data, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Factory for ListenModeViewModel
class ListenModeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ListenModeViewModel(context) as T
}
