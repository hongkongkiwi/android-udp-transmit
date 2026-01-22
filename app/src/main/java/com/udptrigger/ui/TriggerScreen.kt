package com.udptrigger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udptrigger.R
import com.udptrigger.data.UdpConfig
import kotlinx.coroutines.launch

/**
 * Main trigger screen with UDP controls
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerScreen(
    triggerViewModel: TriggerViewModel = viewModel(
        factory = TriggerViewModelFactory(LocalContext.current)
    )
) {
    val state by triggerViewModel.state.collectAsState()

    var showConfig by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        modifier = Modifier.semantics { heading() }
                    )
                },
                actions = {
                    // Settings button
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    }

                    // Config toggle button
                    val configDesc = stringResource(R.string.nav_config)
                    val closeDesc = stringResource(R.string.close)
                    val disconnectedDesc = stringResource(R.string.status_disconnected)
                    TextButton(
                        onClick = { showConfig = !showConfig },
                        modifier = Modifier.semantics {
                            contentDescription = if (showConfig) {
                                configDesc
                            } else {
                                "$configDesc, $disconnectedDesc"
                            }
                        }
                    ) {
                        Text(if (showConfig) closeDesc else configDesc)
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
            AnimatedVisibility(
                visible = showConfig,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                ConfigSection(
                    config = state.config,
                    onConfigChange = { triggerViewModel.updateConfig(it) },
                    onConnect = { triggerViewModel.connect() },
                    onDisconnect = { triggerViewModel.disconnect() },
                    isConnected = state.isConnected,
                    isConnecting = state.isConnecting,
                    isDisconnecting = state.isDisconnecting,
                    portError = state.portError,
                    hostError = state.hostError,
                    onClearErrors = {
                        triggerViewModel.clearError()
                        triggerViewModel.clearValidationErrors()
                    }
                )
            }

            // Status indicator
            StatusIndicator(
                isConnected = state.isConnected,
                isConnecting = state.isConnecting,
                packetsSent = state.packetsSent,
                packetsFailed = state.packetsFailed
            )

            // Latency indicator (only show when connected)
            if (state.isConnected && state.lastSendLatencyMs > 0) {
                LatencyCard(
                    lastLatencyMs = state.lastSendLatencyMs,
                    averageLatencyMs = state.averageLatencyMs
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Trigger button - large and easy to press
            TriggerButton(
                onClick = { triggerViewModel.trigger() },
                isEnabled = state.isConnected,
                isConnecting = state.isConnecting
            )

            // Error message with dismiss
            if (state.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ErrorCard(
                    error = state.error!!,
                    onDismiss = { triggerViewModel.clearError() }
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

    // Settings dialog
    if (showSettings) {
        SettingsScreen(onDismiss = { showSettings = false })
    }

    // History dialog
    if (showHistory) {
        PacketHistoryDialog(onDismiss = { showHistory = false })
    }
}

/**
 * Configuration section with host, port, and packet content fields
 */
@Composable
fun ConfigSection(
    config: UdpConfig,
    onConfigChange: (UdpConfig) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    isConnected: Boolean,
    isConnecting: Boolean,
    isDisconnecting: Boolean,
    portError: String?,
    hostError: String?,
    onClearErrors: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Local state that syncs with config using LaunchedEffect
    var host by remember { mutableStateOf(config.host) }
    var port by remember { mutableStateOf(config.port.toString()) }
    var packetContent by remember { mutableStateOf(config.packetContent) }
    var includeTimestamp by remember { mutableStateOf(config.includeTimestamp) }

    // Sync local state when config changes externally (fixes state synchronization bug)
    LaunchedEffect(config) {
        host = config.host
        port = config.port.toString()
        packetContent = config.packetContent
        includeTimestamp = config.includeTimestamp
    }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.nav_config),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )

            // Host input
            OutlinedTextField(
                value = host,
                onValueChange = {
                    host = it
                    onConfigChange(config.copy(host = it))
                    // Clear error when user starts typing
                    if (hostError != null) onClearErrors()
                },
                label = { Text(stringResource(R.string.destination_ip)) },
                singleLine = true,
                isError = hostError != null,
                supportingText = if (hostError != null) {
                    { Text(hostError, color = MaterialTheme.colorScheme.error) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        if (hostError != null) error(hostError)
                    }
            )

            // Port input with validation
            OutlinedTextField(
                value = port,
                onValueChange = {
                    port = it
                    // Only update if valid port
                    it.toIntOrNull()?.let { portInt ->
                        if (portInt in 1..65535) {
                            onConfigChange(config.copy(port = portInt))
                        }
                    }
                    // Clear error when user starts typing
                    if (portError != null) onClearErrors()
                },
                label = { Text(stringResource(R.string.destination_port)) },
                singleLine = true,
                isError = portError != null,
                supportingText = if (portError != null) {
                    { Text(portError, color = MaterialTheme.colorScheme.error) }
                } else {
                    { Text(stringResource(R.string.error_invalid_port)) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        if (portError != null) error(portError)
                    }
            )

            // Packet content input
            OutlinedTextField(
                value = packetContent,
                onValueChange = {
                    packetContent = it
                    onConfigChange(config.copy(packetContent = it))
                },
                label = { Text(stringResource(R.string.packet_content)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Timestamp checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics {
                    contentDescription = if (includeTimestamp) {
                        "Timestamp included"
                    } else {
                        "Timestamp not included"
                    }
                }
            ) {
                Checkbox(
                    checked = includeTimestamp,
                    onCheckedChange = {
                        includeTimestamp = it
                        onConfigChange(config.copy(includeTimestamp = it))
                    }
                )
                Text(stringResource(R.string.include_timestamp))
            }

            // Connection buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isConnected) {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = "Disconnect from server"
                            },
                        enabled = !isDisconnecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        if (isDisconnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.disconnect))
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = "Connect to server"
                            },
                        enabled = !isConnecting && hostError == null && portError == null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hostError == null && portError == null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.connect))
                    }
                }
            }
        }
    }
}

/**
 * Status indicator showing connection state and packet statistics
 */
@Composable
fun StatusIndicator(
    isConnected: Boolean,
    isConnecting: Boolean,
    packetsSent: Int,
    packetsFailed: Int
) {
    val statusText = when {
        isConnecting -> stringResource(R.string.status_connecting)
        isConnected -> stringResource(R.string.status_connected)
        else -> stringResource(R.string.status_disconnected)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .semantics {
                heading()
                contentDescription = "Connection status: $statusText"
                stateDescription = if (isConnected) "Connected" else "Disconnected"
            },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isConnecting -> MaterialTheme.colorScheme.secondaryContainer
                isConnected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    isConnecting -> MaterialTheme.colorScheme.onSecondaryContainer
                    isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
                }
            )

            // Show packet stats if any packets sent
            if (packetsSent > 0 || packetsFailed > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = stringResource(R.string.stats_packets_sent, packetsSent),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics {
                            contentDescription = "$packetsSent packets sent successfully"
                        }
                    )
                    if (packetsFailed > 0) {
                        Text(
                            text = stringResource(R.string.stats_packets_failed, packetsFailed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics {
                                contentDescription = "$packetsFailed packets failed"
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Latency display card
 */
@Composable
fun LatencyCard(
    lastLatencyMs: Double,
    averageLatencyMs: Double
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "Latency: ${String.format("%.2f", lastLatencyMs)} milliseconds, Average: ${String.format("%.2f", averageLatencyMs)} milliseconds"
            }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.stats_latency, lastLatencyMs),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.stats_avg_latency, averageLatencyMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Large trigger button for sending UDP packets
 */
@Composable
fun TriggerButton(
    onClick: () -> Unit,
    isEnabled: Boolean,
    isConnecting: Boolean
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(200.dp)
            .semantics {
                contentDescription = "Send UDP packet"
                stateDescription = if (isConnecting) {
                    "Connecting"
                } else if (isEnabled) {
                    "Enabled"
                } else {
                    "Disabled, not connected"
                }
            },
        enabled = isEnabled && !isConnecting,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = stringResource(R.string.trigger),
            style = MaterialTheme.typography.displaySmall
        )
    }
}

/**
 * Error card with dismiss button
 */
@Composable
fun ErrorCard(
    error: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .semantics {
                error(error)
                contentDescription = "Error: $error"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
