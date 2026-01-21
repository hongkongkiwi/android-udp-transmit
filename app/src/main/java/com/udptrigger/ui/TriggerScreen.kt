package com.udptrigger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerScreen(
    triggerViewModel: TriggerViewModel = viewModel()
) {
    val state by triggerViewModel.state.collectAsState()
    var showConfig by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UDP Trigger") },
                actions = {
                    TextButton(onClick = { showConfig = !showConfig }) {
                        Text(if (showConfig) "Hide Config" else "Config")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (state.isConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primaryContainer
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

            Spacer(modifier = Modifier.height(32.dp))

            // Trigger button - large and easy to press
            Button(
                onClick = { triggerViewModel.trigger() },
                modifier = Modifier
                    .size(200.dp),
                enabled = state.isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TRIGGER",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    if (state.isConnected) {
                        Text(
                            text = "Press any key",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Last trigger info
            state.lastTriggerTime?.let { triggerTime ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Last Trigger",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Time: ${triggerTime}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        state.lastTimestamp?.let { ts ->
                            Text(
                                text = "Timestamp: $ts ns",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Error display
            state.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    // Transparent key event listener overlay
    KeyEventListener(
        onKeyPressed = object : KeyEventCallback {
            override fun onKeyPressed(keyCode: Int, timestamp: Long) {
                if (state.isConnected) {
                    triggerViewModel.triggerWithTimestamp(timestamp)
                }
            }
        }
    )
}

@Composable
fun StatusIndicator(isConnected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFE53935),
                    shape = MaterialTheme.shapes.small
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isConnected) "Connected" else "Disconnected",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFE53935)
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
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = config.host,
                onValueChange = { onConfigChange(config.copy(host = it)) },
                label = { Text("Destination IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isConnected
            )

            val isPortValid = config.port in 1..65535
            OutlinedTextField(
                value = config.port.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.let { port ->
                        if (port in 0..65535) {
                            onConfigChange(config.copy(port = port))
                        }
                    }
                },
                label = { Text("Port (1-65535)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isConnected,
                isError = !isPortValid && config.port != 0,
                supportingText = if (!isPortValid && config.port != 0) {
                    { Text("Port must be 1-65535") }
                } else null
            )

            OutlinedTextField(
                value = config.packetContent,
                onValueChange = { onConfigChange(config.copy(packetContent = it)) },
                label = { Text("Packet Content") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.weight(1f),
                    enabled = !isConnected && config.isValid()
                ) {
                    Text("Connect")
                }

                if (isConnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }
    }
}
