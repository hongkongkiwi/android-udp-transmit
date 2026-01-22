package com.udptrigger.ui

import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Discovered device data class
 */
data class DiscoveredDevice(
    val ip: String,
    val port: Int = 0,
    val latencyMs: Long = 0,
    val isReachable: Boolean = false
)

/**
 * Network Scanner ViewModel
 */
class NetworkScannerViewModel(
    private val context: Context
) : ViewModel() {

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    enum class ScanRange { LOCAL_NETWORK, CUSTOM_IP }
    private val _scanRange = MutableStateFlow(ScanRange.LOCAL_NETWORK)
    val scanRange: StateFlow<ScanRange> = _scanRange.asStateFlow()

    private var customIp: String = ""
    private var customPort: Int = 5000

    fun setScanRange(range: ScanRange) {
        _scanRange.value = range
    }

    fun setCustomIp(ip: String) {
        customIp = ip
    }

    fun setCustomPort(port: Int) {
        customPort = port
    }

    fun startScan() {
        if (_isScanning.value) return

        viewModelScope.launch {
            _isScanning.value = true
            _devices.value = emptyList()
            _progress.value = 0f

            try {
                when (_scanRange.value) {
                    ScanRange.LOCAL_NETWORK -> scanLocalNetwork()
                    ScanRange.CUSTOM_IP -> scanCustomIp()
                }
            } finally {
                _isScanning.value = false
                _progress.value = 1f
            }
        }
    }

    fun stopScan() {
        _isScanning.value = false
    }

    fun clearDevices() {
        _devices.value = emptyList()
        _progress.value = 0f
    }

    private suspend fun scanLocalNetwork() {
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ipAddress = wifiManager.connectionInfo.ipAddress
            val baseIp = String.format("%d.%d.%d.",
                ipAddress and 0xff,
                (ipAddress shr 8) and 0xff,
                (ipAddress shr 16) and 0xff
            )

            val commonPorts = listOf(80, 443, 5000, 8080, 8888)

            for (i in 1..50) { // Limit to first 50 for speed
                if (!_isScanning.value) return@withContext
                val host = "$baseIp$i"
                _progress.value = i / 50f

                for (port in commonPorts) {
                    if (!_isScanning.value) return@withContext
                    if (checkPort(host, port, 50)) {
                        val latency = measureLatency(host)
                        _devices.value = _devices.value + DiscoveredDevice(
                            ip = host, port = port, latencyMs = latency, isReachable = true
                        )
                    }
                }
            }
        }
    }

    private suspend fun scanCustomIp() {
        withContext(Dispatchers.IO) {
            val host = customIp
            _progress.value = 0.5f

            if (checkPort(host, customPort, 100)) {
                val latency = measureLatency(host)
                _devices.value = listOf(DiscoveredDevice(ip = host, port = customPort, latencyMs = latency, isReachable = true))
            }
            _progress.value = 1f
        }
    }

    private fun checkPort(host: String, port: Int, timeout: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeout)
                true
            }
        } catch (e: Exception) { false }
    }

    private fun measureLatency(host: String): Long {
        val start = System.currentTimeMillis()
        try { java.net.InetAddress.getByName(host).isReachable(100) } catch (e: Exception) {}
        return System.currentTimeMillis() - start
    }
}

/**
 * Network scanner dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScannerDialog(
    onDismiss: () -> Unit,
    onDeviceSelected: (String, Int) -> Unit,
    scannerViewModel: NetworkScannerViewModel = viewModel(
        factory = NetworkScannerViewModelFactory(LocalContext.current)
    )
) {
    val devices by scannerViewModel.devices.collectAsState()
    val isScanning by scannerViewModel.isScanning.collectAsState()
    val progress by scannerViewModel.progress.collectAsState()
    val scanRange by scannerViewModel.scanRange.collectAsState()

    var customIp by remember { mutableStateOf("") }
    var customPort by remember { mutableStateOf("5000") }

    BasicAlertDialog(
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
                    Text("Network Scanner", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                HorizontalDivider()

                // Scan options
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scan Target", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = scanRange == NetworkScannerViewModel.ScanRange.LOCAL_NETWORK,
                            onClick = { scannerViewModel.setScanRange(NetworkScannerViewModel.ScanRange.LOCAL_NETWORK) },
                            label = { Text("Local Network") },
                            enabled = !isScanning
                        )
                        FilterChip(
                            selected = scanRange == NetworkScannerViewModel.ScanRange.CUSTOM_IP,
                            onClick = { scannerViewModel.setScanRange(NetworkScannerViewModel.ScanRange.CUSTOM_IP) },
                            label = { Text("Custom IP") },
                            enabled = !isScanning
                        )
                    }

                    if (scanRange == NetworkScannerViewModel.ScanRange.CUSTOM_IP) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = customIp, onValueChange = { customIp = it; scannerViewModel.setCustomIp(it) },
                                label = { Text("IP Address") }, singleLine = true,
                                modifier = Modifier.weight(1f), enabled = !isScanning
                            )
                            OutlinedTextField(
                                value = customPort, onValueChange = { customPort = it; it.toIntOrNull()?.let { p -> scannerViewModel.setCustomPort(p) } },
                                label = { Text("Port") }, singleLine = true,
                                modifier = Modifier.width(100.dp), enabled = !isScanning
                            )
                        }
                    }
                }

                // Progress
                if (isScanning) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("Scanning... ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Devices list
                if (devices.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Found ${devices.size} device(s)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp))
                }

                if (devices.isEmpty() && !isScanning) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                            Text("No devices found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devices) { device ->
                            DeviceItem(device = device, onClick = { onDeviceSelected(device.ip, device.port) })
                        }
                    }
                }

                HorizontalDivider()

                // Action buttons
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isScanning) {
                        OutlinedButton(
                            onClick = { scannerViewModel.stopScan() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop")
                        }
                    } else {
                        OutlinedButton(onClick = { scannerViewModel.clearDevices() }, enabled = devices.isNotEmpty()) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear")
                        }
                        Button(onClick = { scannerViewModel.startScan() }) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: DiscoveredDevice, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.ip, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (device.port > 0) {
                        Text("Port: ${device.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (device.latencyMs > 0) {
                        Text("${device.latencyMs}ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Icon(Icons.Default.Check, contentDescription = "Select", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Factory
class NetworkScannerViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = NetworkScannerViewModel(context) as T
}
