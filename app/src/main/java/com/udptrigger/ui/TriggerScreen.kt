package com.udptrigger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udptrigger.data.UdpConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerScreen(
    triggerViewModel: TriggerViewModel = viewModel(
        factory = TriggerViewModelFactory(LocalContext.current)
    )
) {
    val state by triggerViewModel.state.collectAsState()

    var showConfig by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UDP Trigger") },
                actions = {
                    TextButton(onClick = { showConfig = !showConfig }) {
                        Text(if (showConfig) "Hide" else "Config")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (state.isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Config section (collapsible)
            if (showConfig) {
                ConfigSection(
                    config = state.config,
                    onConfigChange = { triggerViewModel.updateConfig(it) },
                    onConnect = { triggerViewModel.connect() },
                    onDisconnect = { triggerViewModel.disconnect() },
                    isConnected = state.isConnected,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            // Status indicator
            StatusIndicator(isConnected = state.isConnected)

            // Latency indicator (only show when connected)
            if (state.isConnected && state.lastSendLatencyMs > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Last: ${"%.2f".format(state.lastSendLatencyMs)}ms",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Avg: ${"%.2f".format(state.averageLatencyMs)}ms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Trigger button - large and easy to press
            Button(
                onClick = { triggerViewModel.trigger() },
                modifier = Modifier.size(200.dp),
                enabled = state.isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "TRIGGER",
                    style = MaterialTheme.typography.headlineLarge
                )
            }

            // Error message
            if (state.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Hardware keyboard event listener (LOW-LATENCY PATH)
        KeyEventListener(
            onKeyPressed = object : KeyEventCallback {
                override fun onKeyPressed(keyCode: Int, timestamp: Long): Boolean {
                    // Only trigger if connected
                    return if (state.isConnected) {
                        triggerViewModel.triggerFast(timestamp)
                        true // Consume the event
                    } else {
                        false // Let system handle it
                    }
                }
            }
        )
    }
}

@Composable
fun ConfigSection(
    config: UdpConfig,
    onConfigChange: (UdpConfig) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    var host by remember { mutableStateOf(config.host) }
    var port by remember { mutableStateOf(config.port.toString()) }
    var packetContent by remember { mutableStateOf(config.packetContent) }
    var includeTimestamp by remember { mutableStateOf(config.includeTimestamp) }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Configuration",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = host,
                onValueChange = {
                    host = it
                    onConfigChange(config.copy(host = it))
                },
                label = { Text("Host IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = port,
                onValueChange = {
                    port = it
                    onConfigChange(config.copy(port = it.toIntOrNull() ?: 5000))
                },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = packetContent,
                onValueChange = {
                    packetContent = it
                    onConfigChange(config.copy(packetContent = it))
                },
                label = { Text("Packet Content") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeTimestamp,
                    onCheckedChange = {
                        includeTimestamp = it
                        onConfigChange(config.copy(includeTimestamp = it))
                    }
                )
                Text("Include timestamp")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isConnected) {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Disconnect")
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                        enabled = config.isValid()
                    ) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(isConnected: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isConnected) "Connected" else "Disconnected",
                style = MaterialTheme.typography.titleMedium,
                color = if (isConnected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }
}
