package com.udptrigger.ui

import android.content.Context
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.Activity
import android.content.ContextWrapper

fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    throw IllegalStateException("Cannot find Activity from context")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerScreen(
    triggerViewModel: TriggerViewModel = viewModel(
        factory = TriggerViewModelFactory(LocalContext.current)
    ),
    pendingIntentAction: String? = null
) {
    val state by triggerViewModel.state.collectAsState()
    val context = LocalContext.current

    // Keep screen on with proper cleanup
    val activity = context.findActivity()
    DisposableEffect(state.keepScreenOn) {
        if (state.keepScreenOn) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var showConfig by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showListenMode by remember { mutableStateOf(false) }
    var showImportExport by remember { mutableStateOf(false) }
    var showCrashReports by remember { mutableStateOf(false) }
    var showAnalytics by remember { mutableStateOf(false) }
    var showNetworkDiscovery by remember { mutableStateOf(false) }
    var showAutomationManager by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UDP Trigger") },
                actions = {
                    // Network status indicator
                    Box(
                        modifier = Modifier
                            .size(40.dp, 24.dp)
                            .padding(4.dp)
                            .background(
                                color = if (state.isNetworkAvailable) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                shape = MaterialTheme.shapes.small
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (state.isNetworkAvailable) "NW" else "X",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { showHistory = !showHistory }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Packet History")
                    }
                    IconButton(onClick = { showListenMode = !showListenMode }) {
                        Icon(Icons.Default.Info, contentDescription = "Listen Mode")
                    }
                    IconButton(onClick = { showImportExport = !showImportExport }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Import/Export")
                    }
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                    TextButton(onClick = { showConfig = !showConfig }) {
                        Text(if (showConfig) "Hide Config" else "Config")
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
                    onApplyPreset = { presetName -> triggerViewModel.applyPreset(presetName) },
                    isConnected = state.isConnected,
                    onPacketOptionsChanged = { hexMode, includeTs, includeBurstIndex ->
                        triggerViewModel.updatePacketOptions(hexMode, includeTs, includeBurstIndex)
                    },
                    onSavePreset = { name, description -> triggerViewModel.saveAsPreset(name, description) },
                    onUpdatePreset = { oldName, newName, description -> triggerViewModel.updatePresetMetadata(oldName, newName, description) },
                    onDeletePreset = { name -> triggerViewModel.deletePreset(name) },
                    onIsCustomPreset = { name -> triggerViewModel.isCustomPreset(name) },
                    onGetPreset = { name -> triggerViewModel.getPreset(name) },
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            // Settings section
            if (showSettings) {
                SettingsSection(
                    hapticFeedbackEnabled = state.hapticFeedbackEnabled,
                    onHapticFeedbackChanged = { triggerViewModel.updateHapticFeedback(it) },
                    soundEnabled = state.soundEnabled,
                    onSoundEnabledChanged = { triggerViewModel.updateSoundEnabled(it) },
                    rateLimitEnabled = state.rateLimitEnabled,
                    rateLimitMs = state.rateLimitMs,
                    onRateLimitChanged = { enabled, ms -> triggerViewModel.updateRateLimit(enabled, ms) },
                    autoReconnect = state.autoReconnect,
                    onAutoReconnectChanged = { triggerViewModel.updateAutoReconnect(it) },
                    autoConnectOnStartup = state.autoConnectOnStartup,
                    onAutoConnectOnStartupChanged = { triggerViewModel.updateAutoConnectOnStartup(it) },
                    keepScreenOn = state.keepScreenOn,
                    onKeepScreenOnChanged = { triggerViewModel.updateKeepScreenOn(it) },
                    wakeLockEnabled = state.wakeLockEnabled,
                    isWakeLockActive = state.isWakeLockActive,
                    onWakeLockChanged = { triggerViewModel.updateWakeLockEnabled(it) },
                    foregroundServiceEnabled = state.foregroundServiceEnabled,
                    isForegroundServiceActive = state.isForegroundServiceActive,
                    onForegroundServiceChanged = { triggerViewModel.updateForegroundServiceEnabled(it) },
                    burstModeEnabled = state.burstMode.enabled,
                    burstPacketCount = state.burstMode.packetCount,
                    burstDelayMs = state.burstMode.delayMs,
                    burstIsSending = state.burstMode.isSending,
                    onBurstModeChanged = { enabled, count, delay -> triggerViewModel.updateBurstMode(enabled, count, delay) },
                    onShowAutomationManager = { showAutomationManager = true },
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            // Import/Export section
            if (showImportExport) {
                val coroutineScope = rememberCoroutineScope()
                ImportExportSection(
                    onExportConfig = { uri ->
                        coroutineScope.launch {
                            triggerViewModel.exportConfig(uri)
                        }
                    },
                    onImportConfig = { uri ->
                        coroutineScope.launch {
                            triggerViewModel.importConfig(uri)
                        }
                    },
                    onExportHistory = { uri ->
                        coroutineScope.launch {
                            triggerViewModel.exportHistoryToCsv(uri)
                        }
                    },
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            // Listen mode section
            if (showListenMode) {
                val deviceIp = triggerViewModel.getDeviceIpAddress()
                val coroutineScope = rememberCoroutineScope()
                ListenModeSection(
                    isListening = state.isListening,
                    listenPort = state.listenPort ?: 5000,
                    receivedPackets = state.receivedPackets,
                    deviceIpAddress = deviceIp,
                    onStartListening = { triggerViewModel.startListening(it) },
                    onStopListening = { triggerViewModel.stopListening() },
                    onClearReceived = { triggerViewModel.clearReceivedPackets() },
                    onReply = { address, port, data ->
                        coroutineScope.launch {
                            triggerViewModel.replyToPacket(address, port, data)
                        }
                    },
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            // Scheduled trigger section
            ScheduledTriggerSection(
                enabled = state.scheduledTrigger.enabled,
                intervalMs = state.scheduledTrigger.intervalMs,
                packetCount = state.scheduledTrigger.packetCount,
                packetsSent = state.scheduledTrigger.packetsSent,
                isRunning = state.scheduledTrigger.isRunning,
                onEnabledChanged = { enabled ->
                    triggerViewModel.updateScheduledTrigger(
                        enabled = enabled,
                        intervalMs = state.scheduledTrigger.intervalMs,
                        packetCount = state.scheduledTrigger.packetCount
                    )
                },
                onIntervalChanged = { intervalMs ->
                    triggerViewModel.updateScheduledTrigger(
                        enabled = state.scheduledTrigger.enabled,
                        intervalMs = intervalMs,
                        packetCount = state.scheduledTrigger.packetCount
                    )
                },
                onPacketCountChanged = { packetCount ->
                    triggerViewModel.updateScheduledTrigger(
                        enabled = state.scheduledTrigger.enabled,
                        intervalMs = state.scheduledTrigger.intervalMs,
                        packetCount = packetCount
                    )
                },
                onStartStop = {
                    if (state.scheduledTrigger.isRunning) {
                        triggerViewModel.stopScheduledTrigger()
                    } else {
                        triggerViewModel.startScheduledTrigger()
                    }
                },
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Status indicator
            StatusIndicator(isConnected = state.isConnected, isNetworkAvailable = state.isNetworkAvailable)

            // Latency indicator (only show when connected and has data)
            if (state.isConnected && (state.lastSendLatencyMs > 0 || state.averageLatencyMs > 0)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            state.averageLatencyMs < 1.0 -> MaterialTheme.colorScheme.primaryContainer
                            state.averageLatencyMs < 5.0 -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.tertiaryContainer
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Send Latency",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Last: ${"%.2f".format(state.lastSendLatencyMs)}ms",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Avg: ${"%.2f".format(state.averageLatencyMs)}ms",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Latency indicator",
                            tint = when {
                                state.averageLatencyMs < 1.0 -> MaterialTheme.colorScheme.primary
                                state.averageLatencyMs < 5.0 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                        )
                    }
                }
            }

            // Packet size indicator
            val packetSize = triggerViewModel.getPacketSizePreview()
            val sizeBreakdown = triggerViewModel.getPacketSizeBreakdown()
            var showSizeBreakdown by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clickable { showSizeBreakdown = !showSizeBreakdown },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Packet Size",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "${packetSize} bytes",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Icon(
                            imageVector = if (showSizeBreakdown) Icons.Default.Close else Icons.Default.Info,
                            contentDescription = if (showSizeBreakdown) "Hide breakdown" else "Show breakdown",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                        )
                    }

                    if (showSizeBreakdown) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Size Breakdown:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                            SizeBreakdownItem("Content", sizeBreakdown.contentSize, "Base data")
                            if (sizeBreakdown.separatorSize > 0) {
                                SizeBreakdownItem("Separators", sizeBreakdown.separatorSize, "Colons/dividers")
                            }
                            if (sizeBreakdown.timestampSize > 0) {
                                SizeBreakdownItem("Timestamp", sizeBreakdown.timestampSize, "Nano time")
                            }
                            if (sizeBreakdown.burstIndexSize > 0) {
                                SizeBreakdownItem("Burst Index", sizeBreakdown.burstIndexSize, "Packet number")
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "${sizeBreakdown.totalSize} bytes",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Trigger button - large and easy to press
            if (state.burstMode.enabled) {
                // Burst mode layout
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { triggerViewModel.triggerBurst() },
                            modifier = Modifier.size(160.dp),
                            enabled = state.isConnected && !state.burstMode.isSending,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (state.burstMode.isSending) "SENDING..." else "BURST",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(
                                    text = "×${state.burstMode.packetCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${state.burstMode.delayMs}ms delay between packets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                // Single trigger mode
                val isPressed = remember { mutableStateOf(false) }

                LaunchedEffect(state.lastTriggered) {
                    if (state.lastTriggered > 0) {
                        isPressed.value = true
                        kotlinx.coroutines.delay(150)
                        isPressed.value = false
                    }
                }

                Button(
                    onClick = { triggerViewModel.trigger() },
                    modifier = Modifier
                        .size(200.dp)
                        .scale(if (isPressed.value) 0.95f else 1f)
                        .semantics {
                            contentDescription = if (state.isConnected) {
                                "Send UDP trigger packet. Tap to send immediately."
                            } else {
                                "Trigger button - not connected to server"
                            }
                        },
                    enabled = state.isConnected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        } else {
                            Text(
                                text = "Not connected",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Stats section
            StatsCard(
                totalPacketsSent = state.totalPacketsSent,
                totalPacketsFailed = state.totalPacketsFailed,
                lastTriggerTime = state.lastTriggerTime,
                lastTimestamp = state.lastTimestamp,
                onResetStats = { triggerViewModel.clearHistory() }
            )

            // History section
            if (showHistory) {
                Spacer(modifier = Modifier.height(16.dp))
                HistorySection(
                    packetHistory = state.packetHistory,
                    onClearHistory = { triggerViewModel.clearHistory() }
                )
            }

            // Error display
            state.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                ErrorCard(error)
            }
        }
    }

    // About dialog
    if (showAbout) {
        AboutDialog(
            onDismiss = { showAbout = false },
            onShowCrashReports = { showCrashReports = true },
            onShowAnalytics = { showAnalytics = true },
            onShowNetworkDiscovery = { showNetworkDiscovery = true },
            onShowPrivacyPolicy = {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://yourdomain.com/privacy-policy"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Handle error - could show a toast
                }
            }
        )
    }

    if (showCrashReports) {
        CrashReportsDialog(
            onDismiss = { showCrashReports = false },
            context = context
        )
    }

    if (showAnalytics) {
        AnalyticsDialog(
            onDismiss = { showAnalytics = false },
            context = context
        )
    }

    if (showNetworkDiscovery) {
        NetworkDiscoveryDialog(
            onDismiss = { showNetworkDiscovery = false },
            context = context,
            onConnectToDevice = { host, port ->
                triggerViewModel.updateConfig(state.config.copy(host = host, port = port))
                triggerViewModel.connect()
                showNetworkDiscovery = false
            }
        )
    }

    if (showAutomationManager) {
        AutomationManagerDialog(
            onDismiss = { showAutomationManager = false },
            context = context
        )
    }

    // Transparent key event listener overlay
    KeyEventListener(
        onKeyPressed = object : KeyEventCallback {
            override fun onKeyPressed(keyCode: Int, timestamp: Long): Boolean {
                if (state.isConnected) {
                    triggerViewModel.triggerFast(timestamp)
                    return true
                }
                return false
            }
        }
    )
}

@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
    onShowCrashReports: () -> Unit = {},
    onShowAnalytics: () -> Unit = {},
    onShowNetworkDiscovery: () -> Unit = {},
    onShowPrivacyPolicy: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About UDP Trigger") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "UDP Trigger v1.0",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "A low-latency UDP packet sender for Android.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Features:",
                    style = MaterialTheme.typography.titleSmall
                )
                Text("• Ultra-low latency UDP transmission", style = MaterialTheme.typography.bodySmall)
                Text("• Button and key press triggers", style = MaterialTheme.typography.bodySmall)
                Text("• IPv4, IPv6, and broadcast support", style = MaterialTheme.typography.bodySmall)
                Text("• Configurable presets", style = MaterialTheme.typography.bodySmall)
                Text("• Haptic and sound feedback", style = MaterialTheme.typography.bodySmall)
                Text("• Auto-reconnect on network changes", style = MaterialTheme.typography.bodySmall)
                Text("• Packet history and statistics", style = MaterialTheme.typography.bodySmall)
                Text("• Crash reporting and error analytics", style = MaterialTheme.typography.bodySmall)
                Text("• Usage analytics and insights", style = MaterialTheme.typography.bodySmall)
                Text("• Network device discovery", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Perfect for show control, OBS triggering, and network testing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
            ) {
                TextButton(onClick = onShowPrivacyPolicy) {
                    Text("Privacy")
                }
                TextButton(onClick = onShowCrashReports) {
                    Text("Crash Reports")
                }
                TextButton(onClick = onShowAnalytics) {
                    Text("Analytics")
                }
                TextButton(onClick = onShowNetworkDiscovery) {
                    Text("Network Scan")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun SizeBreakdownItem(label: String, size: Int, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
            )
        }
        Text(
            text = "${size}B",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun StatusIndicator(isConnected: Boolean, isNetworkAvailable: Boolean) {
    val statusColor = when {
        !isNetworkAvailable -> MaterialTheme.colorScheme.error
        isConnected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    val statusText = when {
        !isNetworkAvailable -> "No Network"
        isConnected -> "Connected"
        else -> "Disconnected"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = statusColor,
                    shape = MaterialTheme.shapes.small
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor
        )
    }
}

@Composable
fun ConfigSection(
    config: UdpConfig,
    onConfigChange: (UdpConfig) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onApplyPreset: (String) -> Unit,
    isConnected: Boolean,
    onPacketOptionsChanged: (Boolean, Boolean, Boolean) -> Unit,
    onSavePreset: (String, String) -> Boolean,
    onUpdatePreset: (String, String, String) -> Boolean,
    onDeletePreset: (String) -> Boolean,
    onIsCustomPreset: (String) -> Boolean,
    onGetPreset: (String) -> com.udptrigger.data.CustomPreset?,
    modifier: Modifier = Modifier
) {
    var showPresetsMenu by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showEditPresetDialog by remember { mutableStateOf(false) }
    var editingPresetName by remember { mutableStateOf<String?>(null) }
    val presets = com.udptrigger.data.PresetsManager.presets
    var presetName by remember { mutableStateOf("") }
    var presetDescription by remember { mutableStateOf("") }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }

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
            Text(
                text = "Connection Configuration",
                style = MaterialTheme.typography.titleMedium
            )

            // Presets row with Load and Save buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Load Preset dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { showPresetsMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isConnected
                    ) {
                        Text("Load Preset")
                    }
                    DropdownMenu(
                        expanded = showPresetsMenu,
                        onDismissRequest = { showPresetsMenu = false }
                    ) {
                        presets.forEach { preset ->
                            val isCustom = onIsCustomPreset(preset.name)
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = preset.name,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                Text(
                                                    text = preset.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                            if (isCustom) {
                                                Row {
                                                    IconButton(
                                                        onClick = {
                                                            val presetToEdit = onGetPreset(preset.name)
                                                            if (presetToEdit != null) {
                                                                editingPresetName = preset.name
                                                                presetName = presetToEdit.name
                                                                presetDescription = presetToEdit.description
                                                                showPresetsMenu = false
                                                                showEditPresetDialog = true
                                                            }
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Info,
                                                            contentDescription = "Edit preset",
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            if (onDeletePreset(preset.name)) {
                                                                showPresetsMenu = false
                                                            }
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete preset",
                                                            tint = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    onApplyPreset(preset.name)
                                    showPresetsMenu = false
                                }
                            )
                        }
                    }
                }

                // Save as Preset button
                OutlinedButton(
                    onClick = {
                        presetName = ""
                        presetDescription = ""
                        saveErrorMessage = null
                        showSavePresetDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isConnected
                ) {
                    Text("Save Preset")
                }
            }

            OutlinedTextField(
                value = config.host,
                onValueChange = { onConfigChange(config.copy(host = it)) },
                label = { Text("Destination Host (IPv4/IPv6/hostname/broadcast)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isConnected
            )

            val isPortValid = config.port in 1..65535
            OutlinedTextField(
                value = config.port.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.let { port ->
                        if (port in 1..65535) {
                            onConfigChange(config.copy(port = port))
                        }
                    }
                },
                label = { Text("Port (1-65535)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isConnected,
                isError = !isPortValid,
                supportingText = if (!isPortValid) {
                    { Text("Port must be 1-65535") }
                } else null
            )

            OutlinedTextField(
                value = config.packetContent,
                onValueChange = { onConfigChange(config.copy(packetContent = it)) },
                label = { Text(if (config.hexMode) "Packet Content (Hex)" else "Packet Content (Text)") },
                placeholder = { Text(if (config.hexMode) "e.g., 48454C4F or Hello" else "e.g., TRIGGER or Hello World") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    val preview = if (config.hexMode) {
                        when {
                            config.packetContent.isEmpty() -> "Enter hex values (e.g., 48454C4F)"
                            config.packetContent.length % 2 != 0 -> "Invalid hex: odd number of characters"
                            !config.packetContent.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } -> "Invalid hex: use 0-9, A-F only"
                            else -> {
                                try {
                                    val bytes = config.packetContent.chunked(2).mapNotNull { it.toIntOrNull(16) }
                                    "Preview: ${bytes.take(8).joinToString(", ") }${if (bytes.size > 8) "..." else ""}"
                                } catch (e: Exception) {
                                    "Invalid hex format"
                                }
                            }
                        }
                    } else {
                        "Preview: ${config.packetContent.take(50)}${if (config.packetContent.length > 50) "..." else ""}"
                    }
                    Text(preview, style = MaterialTheme.typography.bodySmall)
                }
            )

            // Packet options
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Packet Options",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hex Mode",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Interpret as hex bytes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = config.hexMode,
                        onCheckedChange = { enabled ->
                            onPacketOptionsChanged(
                                enabled,
                                config.includeTimestamp,
                                config.includeBurstIndex
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Include Timestamp",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Add nano timestamp to packet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = config.includeTimestamp,
                        onCheckedChange = { enabled ->
                            onPacketOptionsChanged(
                                config.hexMode,
                                enabled,
                                config.includeBurstIndex
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Include Burst Index",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Add index in burst mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = config.includeBurstIndex,
                        onCheckedChange = { enabled ->
                            onPacketOptionsChanged(
                                config.hexMode,
                                config.includeTimestamp,
                                enabled
                            )
                        }
                    )
                }
            }

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

    // Save Preset Dialog
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save as Preset") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = {
                            presetName = it
                            saveErrorMessage = null
                        },
                        label = { Text("Preset Name") },
                        singleLine = true,
                        isError = saveErrorMessage != null
                    )
                    OutlinedTextField(
                        value = presetDescription,
                        onValueChange = { presetDescription = it },
                        label = { Text("Description (optional)") },
                        singleLine = true
                    )
                    if (saveErrorMessage != null) {
                        Text(
                            text = saveErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "Current config: ${config.host}:${config.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (presetName.isBlank()) {
                            saveErrorMessage = "Name cannot be empty"
                            return@Button
                        }
                        if (!onSavePreset(presetName, presetDescription)) {
                            saveErrorMessage = "Preset with this name already exists"
                            return@Button
                        }
                        showSavePresetDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditPresetDialog) {
        AlertDialog(
            onDismissRequest = {
                showEditPresetDialog = false
                editingPresetName = null
            },
            title = { Text("Edit Preset") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = {
                            presetName = it
                            saveErrorMessage = null
                        },
                        label = { Text("Preset Name") },
                        singleLine = true,
                        isError = saveErrorMessage != null
                    )
                    OutlinedTextField(
                        value = presetDescription,
                        onValueChange = { presetDescription = it },
                        label = { Text("Description (optional)") },
                        singleLine = true
                    )
                    if (saveErrorMessage != null) {
                        Text(
                            text = saveErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "Editing: ${editingPresetName ?: "Unknown"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (presetName.isBlank()) {
                            saveErrorMessage = "Name cannot be empty"
                            return@Button
                        }
                        val oldName = editingPresetName
                        if (oldName != null) {
                            if (!onUpdatePreset(oldName, presetName, presetDescription)) {
                                saveErrorMessage = "Failed to update preset"
                                return@Button
                            }
                        }
                        showEditPresetDialog = false
                        editingPresetName = null
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditPresetDialog = false
                        editingPresetName = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    hapticFeedbackEnabled: Boolean,
    onHapticFeedbackChanged: (Boolean) -> Unit,
    soundEnabled: Boolean,
    onSoundEnabledChanged: (Boolean) -> Unit,
    rateLimitEnabled: Boolean,
    rateLimitMs: Long,
    onRateLimitChanged: (Boolean, Long) -> Unit,
    autoReconnect: Boolean,
    onAutoReconnectChanged: (Boolean) -> Unit,
    autoConnectOnStartup: Boolean,
    onAutoConnectOnStartupChanged: (Boolean) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    wakeLockEnabled: Boolean,
    isWakeLockActive: Boolean,
    onWakeLockChanged: (Boolean) -> Unit,
    foregroundServiceEnabled: Boolean,
    isForegroundServiceActive: Boolean,
    onForegroundServiceChanged: (Boolean) -> Unit,
    burstModeEnabled: Boolean,
    burstPacketCount: Int,
    burstDelayMs: Long,
    @Suppress("UNUSED_PARAMETER") burstIsSending: Boolean,
    onBurstModeChanged: (Boolean, Int, Long) -> Unit,
    onShowAutomationManager: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium
            )

            // Haptic feedback toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Haptic Feedback",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Vibrate on successful trigger",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = hapticFeedbackEnabled,
                    onCheckedChange = onHapticFeedbackChanged
                )
            }

            HorizontalDivider()

            // Sound effects toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sound Effects",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Play sound on trigger",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = onSoundEnabledChanged
                )
            }

            HorizontalDivider()

            // Auto-reconnect toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Reconnect",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Automatically reconnect when network available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = autoReconnect,
                    onCheckedChange = onAutoReconnectChanged
                )
            }

            HorizontalDivider()

            // Auto connect on startup toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto Connect on Startup",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Automatically connect when app starts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = autoConnectOnStartup,
                    onCheckedChange = onAutoConnectOnStartupChanged
                )
            }

            HorizontalDivider()

            // Keep screen on toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Keep Screen On",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Prevent screen from turning off",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = onKeepScreenOnChanged
                )
            }

            HorizontalDivider()

            // Wake lock toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wake Lock (Low Latency)",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (isWakeLockActive) {
                            "Active - CPU stays on for minimal latency"
                        } else {
                            "Keep CPU running for lowest latency (uses more battery)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isWakeLockActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        }
                    )
                }
                Switch(
                    checked = wakeLockEnabled,
                    onCheckedChange = onWakeLockChanged
                )
            }

            HorizontalDivider()

            // Foreground service toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Background Service",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (isForegroundServiceActive) {
                            "Running - app stays alive in background"
                        } else {
                            "Keep app active when screen is off or app backgrounded"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isForegroundServiceActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        }
                    )
                }
                Switch(
                    checked = foregroundServiceEnabled,
                    onCheckedChange = onForegroundServiceChanged
                )
            }

            HorizontalDivider()

            // Rate limiting
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Rate Limiting",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Prevent rapid-fire triggers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = rateLimitEnabled,
                        onCheckedChange = { enabled ->
                            onRateLimitChanged(enabled, rateLimitMs)
                        }
                    )
                }

                if (rateLimitEnabled) {
                    var rateLimitText by remember { mutableStateOf(rateLimitMs.toString()) }
                    OutlinedTextField(
                        value = rateLimitText,
                        onValueChange = { value ->
                            value.toLongOrNull()?.let { ms ->
                                if (ms in 1..5000) {
                                    rateLimitText = value
                                    onRateLimitChanged(true, ms)
                                }
                            }
                        },
                        label = { Text("Minimum interval (ms)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            HorizontalDivider()

            // Burst mode toggle
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Burst Mode",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Send multiple packets with delay",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = burstModeEnabled,
                        onCheckedChange = { enabled ->
                            onBurstModeChanged(enabled, burstPacketCount, burstDelayMs)
                        }
                    )
                }

                if (burstModeEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = burstPacketCount.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let { count ->
                                    if (count in 1..100) {
                                        onBurstModeChanged(true, count, burstDelayMs)
                                    }
                                }
                            },
                            label = { Text("Packet Count") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = burstDelayMs.toString(),
                            onValueChange = { value ->
                                value.toLongOrNull()?.let { ms ->
                                    if (ms in 10..5000) {
                                        onBurstModeChanged(true, burstPacketCount, ms)
                                    }
                                }
                            },
                            label = { Text("Delay (ms)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }

            HorizontalDivider()

            // Automation Manager button
            OutlinedButton(
                onClick = onShowAutomationManager,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Automation Manager")
            }
        }
    }
}

/**
 * Import/Export Section - for data portability
 */
@Composable
fun ImportExportSection(
    onExportConfig: (Uri) -> Unit,
    onImportConfig: (Uri) -> Unit,
    onExportHistory: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    // File pickers
    val exportConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExportConfig(uri)
        }
    }

    val importConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportConfig(uri)
        }
    }

    val exportHistoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            onExportHistory(uri)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Import / Export",
                style = MaterialTheme.typography.titleMedium
            )

            HorizontalDivider()

            // Export Configuration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Export Configuration",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Save settings and presets to JSON",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
                Button(onClick = {
                    exportConfigLauncher.launch("udp_trigger_config_${System.currentTimeMillis()}.json")
                }) {
                    Text("Export")
                }
            }

            HorizontalDivider()

            // Import Configuration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Import Configuration",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Load settings and presets from JSON",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
                Button(onClick = {
                    importConfigLauncher.launch(arrayOf("*/*"))
                }) {
                    Text("Import")
                }
            }

            HorizontalDivider()

            // Export Packet History
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Export History",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Save packet history to CSV",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
                Button(onClick = {
                    exportHistoryLauncher.launch("udp_trigger_history_${System.currentTimeMillis()}.csv")
                }) {
                    Text("Export")
                }
            }
        }
    }
}

/**
 * Scheduled Trigger Section - for automated packet sending
 */
@Composable
fun ScheduledTriggerSection(
    enabled: Boolean,
    intervalMs: Long,
    packetCount: Int,
    packetsSent: Int,
    isRunning: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onIntervalChanged: (Long) -> Unit,
    onPacketCountChanged: (Int) -> Unit,
    onStartStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scheduled Trigger",
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChanged
                )
            }

            if (enabled) {
                HorizontalDivider()

                // Interval settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Interval",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${intervalMs}ms between packets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Slider(
                    value = intervalMs.toFloat(),
                    onValueChange = { onIntervalChanged(it.toLong()) },
                    valueRange = 100f..10000f,
                    modifier = Modifier.fillMaxWidth()
                )

                // Packet count settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Packet Count",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (packetCount == 0) "Infinite" else "$packetCount packets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Slider(
                    value = packetCount.toFloat(),
                    onValueChange = { onPacketCountChanged(it.toInt()) },
                    valueRange = 0f..1000f,
                    modifier = Modifier.fillMaxWidth(),
                    steps = 100
                )

                HorizontalDivider()

                // Status and controls
                if (isRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Running: $packetsSent sent",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = onStartStop,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Stop")
                        }
                    }
                } else {
                    Button(
                        onClick = onStartStop,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Scheduled Trigger")
                    }
                }
            }
        }
    }
}

@Composable
fun StatsCard(
    totalPacketsSent: Int,
    totalPacketsFailed: Int,
    lastTriggerTime: Long?,
    lastTimestamp: Long?,
    onResetStats: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Statistics",
                    style = MaterialTheme.typography.titleMedium
                )
                if (totalPacketsSent > 0 || totalPacketsFailed > 0) {
                    TextButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Reset")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = totalPacketsSent.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Sent",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (totalPacketsFailed > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = totalPacketsFailed.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Failed",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (lastTriggerTime != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                Text(
                    text = "Last: ${dateFormat.format(Date(lastTriggerTime))}",
                    style = MaterialTheme.typography.bodyMedium
                )
                lastTimestamp?.let { ts ->
                    Text(
                        text = "Timestamp: $ts ns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Statistics") },
            text = { Text("Are you sure you want to reset all statistics? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetStats()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun HistorySection(
    packetHistory: List<PacketHistoryEntry>,
    onClearHistory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Packet History (${packetHistory.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                if (packetHistory.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text("Clear")
                    }
                }
            }

            if (packetHistory.isEmpty()) {
                Text(
                    text = "No packets sent yet",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(packetHistory) { entry ->
                        HistoryItem(entry)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(entry: PacketHistoryEntry) {
    val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (entry.success) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                },
                shape = MaterialTheme.shapes.small
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.success) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (entry.success) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateFormat.format(Date(entry.timestamp)),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Nano: ${entry.nanoTime}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            if (!entry.success) {
                entry.errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = error,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * Listen Mode Section - for receiving UDP packets
 */
@Composable
fun ListenModeSection(
    isListening: Boolean,
    listenPort: Int,
    receivedPackets: List<com.udptrigger.ui.ReceivedPacketInfo>,
    deviceIpAddress: String?,
    onStartListening: (Int) -> Unit,
    onStopListening: () -> Unit,
    onClearReceived: () -> Unit,
    onReply: (String, Int, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var portInput by remember { mutableStateOf(listenPort.toString()) }
    var showHexView by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isListening) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Filled.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isListening) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "UDP Listen Mode",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (isListening) {
                    Text(
                        text = "Listening",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Device IP Address Display
            deviceIpAddress?.let { ip ->
                val clipboardManager = LocalClipboardManager.current
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "This Device IP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = ip,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(ip))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Copy IP address",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Port Input and Control
            if (!isListening) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { input ->
                            portInput = input.filter { it.isDigit() }
                        },
                        label = { Text("Listen Port") },
                        placeholder = { Text("e.g., 5000") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Button(
                        onClick = {
                            val port = portInput.toIntOrNull()
                            if (port != null && port in 1024..65535) {
                                onStartListening(port)
                            }
                        },
                        enabled = portInput.toIntOrNull()?.let { it in 1024..65535 } == true
                    ) {
                        Text("Start Listening")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Listening on port $listenPort",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = onStopListening,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Stop")
                    }
                }
            }

            // Received Packets Section
            if (receivedPackets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Received Packets (${receivedPackets.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = showHexView,
                                onClick = { showHexView = !showHexView },
                                label = { Text(if (showHexView) "Hex" else "Text") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (showHexView) Icons.Outlined.Share else Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            TextButton(onClick = onClearReceived) {
                                Text("Clear")
                            }
                        }
                    }

                    // Received packets list
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(receivedPackets.take(20)) { packet ->
                                ReceivedPacketItem(packet, showHexView, onReply)
                            }
                        }
                    }

                    if (receivedPackets.size > 20) {
                        Text(
                            text = "Showing 20 of ${receivedPackets.size} packets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Display item for a received UDP packet
 */
@Composable
fun ReceivedPacketItem(
    packet: com.udptrigger.ui.ReceivedPacketInfo,
    showHexView: Boolean,
    onReply: (String, Int, String) -> Unit
) {
    var showReplyDialog by remember { mutableStateOf(false) }
    var replyData by remember { mutableStateOf(packet.data) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = packet.sourceAddress,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = ":${packet.sourcePort}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${packet.length}B",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            val displayData = if (showHexView) {
                // Convert to hex
                packet.data.toByteArray().joinToString(" ") { byte ->
                    String.format("%02X", byte)
                }
            } else {
                packet.data
            }

            Text(
                text = displayData,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (showHexView) FontFamily.Monospace else null,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date(packet.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                IconButton(
                    onClick = { showReplyDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Reply",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showReplyDialog) {
        AlertDialog(
            onDismissRequest = { showReplyDialog = false },
            title = { Text("Reply to ${packet.sourceAddress}:${packet.sourcePort}") },
            text = {
                OutlinedTextField(
                    value = replyData,
                    onValueChange = { replyData = it },
                    label = { Text("Response Data") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReply(packet.sourceAddress, packet.sourcePort, replyData)
                        showReplyDialog = false
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
fun CrashReportsDialog(
    onDismiss: () -> Unit,
    context: Context
) {
    val crashReporter = remember { com.udptrigger.util.CrashReporterSingleton.get() }
    val crashReports = remember { mutableStateOf(crashReporter?.getCrashReports() ?: emptyList()) }
    val selectedReport = remember { mutableStateOf<com.udptrigger.util.CrashReportInfo?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crash Reports") },
        text = {
            if (crashReports.value.isEmpty()) {
                Text(
                    "No crash reports found.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(crashReports.value.size) { index ->
                        val report = crashReports.value[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReport.value = report },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = report.exceptionType,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = report.fileName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (report.timestamp > 0) {
                                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                            .format(java.util.Date(report.timestamp))
                                    } else {
                                        "Unknown time"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (crashReports.value.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            crashReporter?.clearAllCrashReports()
                            crashReports.value = emptyList()
                        }
                    ) {
                        Text("Clear All")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )

    // Show detailed crash report
    selectedReport.value?.let { report ->
        AlertDialog(
            onDismissRequest = { selectedReport.value = null },
            title = { Text(report.exceptionType) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = report.content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            // Share crash report
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, report.content)
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Crash Report: ${report.exceptionType}")
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share Crash Report"))
                        }
                    ) {
                        Text("Share")
                    }
                    TextButton(
                        onClick = {
                            crashReporter?.deleteCrashReport(report.fileName)
                            crashReports.value = crashReporter?.getCrashReports() ?: emptyList()
                            selectedReport.value = null
                        }
                    ) {
                        Text("Delete")
                    }
                    TextButton(onClick = { selectedReport.value = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@Composable
fun AnalyticsDialog(
    onDismiss: () -> Unit,
    context: Context
) {
    val statisticsDataStore = remember { com.udptrigger.data.StatisticsDataStore(context) }
    var statistics by remember { mutableStateOf<com.udptrigger.data.UsageStatistics?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        statistics = statisticsDataStore.statisticsFlow.first()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Usage Analytics") },
        text = {
            if (statistics == null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val stats = statistics!!
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Session Statistics",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                StatRow("Total Sessions", stats.totalSessions.toString())
                                StatRow(
                                    "Last Session",
                                    if (stats.lastSessionTime > 0) {
                                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                                            .format(java.util.Date(stats.lastSessionTime))
                                    } else {
                                        "Never"
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Packet Statistics",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                StatRow("Sent", stats.totalPacketsSent.toString())
                                StatRow("Received", stats.totalPacketsReceived.toString())
                                StatRow("Failed", stats.totalPacketsFailed.toString())
                                val successRate = if (stats.totalPacketsSent + stats.totalPacketsReceived > 0) {
                                    (stats.totalPacketsSent + stats.totalPacketsReceived - stats.totalPacketsFailed).toFloat() /
                                            (stats.totalPacketsSent + stats.totalPacketsReceived).toFloat() * 100
                                } else 0f
                                StatRow("Success Rate", "%.1f%%".format(successRate))
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Usage Time",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                val hours = stats.totalConnectionTimeMs / 3600000
                                val minutes = (stats.totalConnectionTimeMs % 3600000) / 60000
                                val seconds = (stats.totalConnectionTimeMs % 60000) / 1000
                                StatRow("Total Time", "%dh %dm %ds".format(hours, minutes, seconds))
                            }
                        }
                    }

                    if (stats.mostUsedPreset.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Most Used Preset",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    StatRow("Name", stats.mostUsedPreset)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
            ) {
                TextButton(
                    onClick = {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            statisticsDataStore.clearAllStatistics()
                            statistics = com.udptrigger.data.UsageStatistics()
                        }
                    }
                ) {
                    Text("Reset Stats")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun NetworkDiscoveryDialog(
    onDismiss: () -> Unit,
    context: Context,
    onConnectToDevice: (String, Int) -> Unit
) {
    val networkScanner = remember { com.udptrigger.domain.NetworkScanner(context) }
    val discoveredDevices = remember { mutableStateOf<List<com.udptrigger.domain.DiscoveredDevice>>(emptyList()) }
    val isScanning = remember { mutableStateOf(false) }
    val scanProgress = remember { mutableStateOf(0 to 254) }
    val localIp = remember { mutableStateOf(networkScanner.getLocalIpAddress()) }
    val ssid = remember { mutableStateOf(networkScanner.getNetworkSSID()) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Network Discovery") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Network info
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Current Network",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "SSID: ${ssid ?: "Unknown"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Local IP: ${localIp ?: "Unknown"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Scan button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isScanning.value = true
                            discoveredDevices.value = emptyList()
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val devices = networkScanner.scanCommonPorts(
                                    onProgress = { current, total ->
                                        scanProgress.value = current to total
                                    },
                                    onDeviceFound = { device ->
                                        val current = discoveredDevices.value.toMutableList()
                                        current.add(device)
                                        discoveredDevices.value = current
                                    }
                                )
                                discoveredDevices.value = devices
                                isScanning.value = false
                            }
                        },
                        enabled = !isScanning.value,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isScanning.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Scan Network")
                        }
                    }
                }

                // Progress
                if (isScanning.value) {
                    Column {
                        Text(
                            text = "Scanning... ${scanProgress.value.first}/${scanProgress.value.second}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = { scanProgress.value.first.toFloat() / scanProgress.value.second.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Discovered devices
                if (discoveredDevices.value.isNotEmpty()) {
                    Text(
                        text = "Found ${discoveredDevices.value.size} device(s):",
                        style = MaterialTheme.typography.titleSmall
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(discoveredDevices.value.size) { index ->
                            val device = discoveredDevices.value[index]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (device.latencyMs >= 0) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${device.address}:${device.port}",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            device.hostName?.let {
                                                Text(
                                                    text = it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = if (device.latencyMs >= 0) {
                                                    "Latency: ${device.latencyMs}ms"
                                                } else {
                                                    "Latency: Unknown"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                onConnectToDevice(device.address, device.port)
                                            },
                                            modifier = Modifier.size(height = 36.dp, width = 80.dp)
                                        ) {
                                            Text("Connect", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (!isScanning.value) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No devices found. Tap 'Scan Network' to discover UDP devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AutomationManagerDialog(
    onDismiss: () -> Unit,
    context: Context
) {
    val automationManager = remember { com.udptrigger.data.AutomationManager(context) }
    val automations by automationManager.automations.collectAsState()
    val activeAutomations by automationManager.activeAutomations.collectAsState()
    val executionLogs by automationManager.executionLogs.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingAutomation by remember { mutableStateOf<com.udptrigger.data.AutomationManager.Automation?>(null) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showVariablesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        automationManager.loadAutomations()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Automation Manager") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Automations") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Logs") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Variables") }
                    )
                }

                // Tab content
                when (selectedTab) {
                    0 -> AutomationsTabContent(
                        automations = automations,
                        activeAutomations = activeAutomations,
                        onToggle = { id, enabled ->
                            coroutineScope.launch {
                                automationManager.toggleAutomation(id, enabled)
                            }
                        },
                        onEdit = { automation ->
                            editingAutomation = automation
                            showEditDialog = true
                        },
                        onDelete = { id ->
                            coroutineScope.launch {
                                automationManager.deleteAutomation(id)
                            }
                        },
                        onExecute = { automation ->
                            coroutineScope.launch {
                                automationManager.executeAutomation(automation)
                            }
                        }
                    )
                    1 -> LogsTabContent(
                        logs = executionLogs,
                        onClearLogs = {
                            automationManager.clearExecutionLogs()
                        }
                    )
                    2 -> VariablesTabContent(
                        variables = automationManager.getVariables()
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                Button(
                    onClick = { showAddDialog = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Automation")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )

    // Add Automation Dialog
    if (showAddDialog) {
        AutomationEditDialog(
            onDismiss = { showAddDialog = false },
            onSave = { automation ->
                coroutineScope.launch {
                    automationManager.addAutomation(automation)
                }
                showAddDialog = false
            },
            context = context
        )
    }

    // Edit Automation Dialog
    if (showEditDialog && editingAutomation != null) {
        AutomationEditDialog(
            onDismiss = {
                showEditDialog = false
                editingAutomation = null
            },
            onSave = { automation ->
                coroutineScope.launch {
                    automationManager.updateAutomation(automation)
                }
                showEditDialog = false
                editingAutomation = null
            },
            context = context,
            existingAutomation = editingAutomation
        )
    }
}

@Composable
private fun AutomationsTabContent(
    automations: List<com.udptrigger.data.AutomationManager.Automation>,
    activeAutomations: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (com.udptrigger.data.AutomationManager.Automation) -> Unit,
    onDelete: (String) -> Unit,
    onExecute: (com.udptrigger.data.AutomationManager.Automation) -> Unit
) {
    if (automations.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "No automations yet",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Create your first automation to get started",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(automations.size) { index ->
                val automation = automations[index]
                val isActive = activeAutomations.contains(automation.id)

                AutomationCard(
                    automation = automation,
                    isActive = isActive,
                    onToggle = { onToggle(automation.id, !automation.enabled) },
                    onEdit = { onEdit(automation) },
                    onDelete = { onDelete(automation.id) },
                    onExecute = { onExecute(automation) }
                )
            }
        }
    }
}

@Composable
private fun AutomationCard(
    automation: com.udptrigger.data.AutomationManager.Automation,
    isActive: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExecute: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = automation.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (isActive) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "Running",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    if (automation.description.isNotEmpty()) {
                        Text(
                            text = automation.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Actions: ${automation.actions.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Runs: ${automation.executionCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (automation.cooldownMs > 0) {
                            Text(
                                text = "Cooldown: ${automation.cooldownMs / 1000}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Switch(
                    checked = automation.enabled,
                    onCheckedChange = { onToggle() }
                )
            }

            // Trigger and action preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExecute,
                    enabled = automation.enabled && !isActive,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run Now")
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Create, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.size(width = 60.dp, height = 40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LogsTabContent(
    logs: List<com.udptrigger.data.AutomationManager.ExecutionLog>,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.titleSmall
            )
            if (logs.isNotEmpty()) {
                TextButton(onClick = onClearLogs) {
                    Text("Clear")
                }
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No execution logs yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs.size) { index ->
                    val log = logs[index]
                    LogEntryCard(log)
                }
            }
        }
    }
}

@Composable
private fun LogEntryCard(log: com.udptrigger.data.AutomationManager.ExecutionLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (log.status) {
                com.udptrigger.data.AutomationManager.ExecutionStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                com.udptrigger.data.AutomationManager.ExecutionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = log.automationName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = formatDate(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Status: ${log.status}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when (log.status) {
                        com.udptrigger.data.AutomationManager.ExecutionStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                        com.udptrigger.data.AutomationManager.ExecutionStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (log.actionsExecuted > 0) {
                    Text(
                        text = "Actions: ${log.actionsExecuted}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (log.durationMs > 0) {
                    Text(
                        text = "${log.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
private fun VariablesTabContent(
    variables: Map<String, String>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Automation Variables",
            style = MaterialTheme.typography.titleSmall
        )

        if (variables.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No variables set",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(variables.keys.toList().size) { index ->
                    val key = variables.keys.toList()[index]
                    val value = variables[key] ?: ""
                    VariableCard(key, value)
                }
            }
        }
    }
}

@Composable
private fun VariableCard(name: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AutomationEditDialog(
    onDismiss: () -> Unit,
    onSave: (com.udptrigger.data.AutomationManager.Automation) -> Unit,
    context: Context,
    existingAutomation: com.udptrigger.data.AutomationManager.Automation? = null
) {
    var name by remember { mutableStateOf(existingAutomation?.name ?: "") }
    var description by remember { mutableStateOf(existingAutomation?.description ?: "") }
    var enabled by remember { mutableStateOf(existingAutomation?.enabled ?: true) }
    var priority by remember { mutableStateOf(existingAutomation?.priority ?: 0) }
    var cooldownMs by remember { mutableStateOf(existingAutomation?.cooldownMs?.toString() ?: "0") }

    var selectedTriggerType by remember { mutableStateOf("Packet Received") }
    var showTriggerConfig by remember { mutableStateOf(false) }

    var actions by remember { mutableStateOf(existingAutomation?.actions?.toMutableList() ?: mutableListOf()) }
    var showActionPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingAutomation != null) "Edit Automation" else "New Automation") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Basic info
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = priority.toString(),
                        onValueChange = { priority = it.toIntOrNull() ?: 0 },
                        label = { Text("Priority") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cooldownMs,
                        onValueChange = { cooldownMs = it },
                        label = { Text("Cooldown (ms)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Enabled", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                // Trigger section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Trigger",
                            style = MaterialTheme.typography.titleSmall
                        )
                        OutlinedButton(
                            onClick = { showTriggerConfig = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedTriggerType)
                        }
                    }
                }

                // Actions section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Actions (${actions.size})",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Button(onClick = { showActionPicker = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add")
                            }
                        }

                        if (actions.isEmpty()) {
                            Text(
                                text = "No actions added",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            actions.forEachIndexed { index, action ->
                                ActionItemCard(
                                    action = action,
                                    onRemove = { actions.removeAt(index) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trigger = com.udptrigger.data.AutomationManager.TriggerCondition.PacketReceived(
                        pattern = ".*"
                    )
                    val automation = com.udptrigger.data.AutomationManager.Automation(
                        id = existingAutomation?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name,
                        description = description,
                        trigger = trigger,
                        actions = actions,
                        enabled = enabled,
                        priority = priority,
                        cooldownMs = cooldownMs.toLongOrNull() ?: 0,
                        lastExecuted = existingAutomation?.lastExecuted ?: 0,
                        executionCount = existingAutomation?.executionCount ?: 0,
                        createdAt = existingAutomation?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(automation)
                },
                enabled = name.isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Trigger configuration dialog
    if (showTriggerConfig) {
        TriggerConfigDialog(
            onDismiss = { showTriggerConfig = false },
            onTriggerSelected = { triggerType ->
                selectedTriggerType = triggerType
                showTriggerConfig = false
            }
        )
    }
}

@Composable
private fun ActionItemCard(
    action: com.udptrigger.data.AutomationManager.AutomationAction,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getActionDescription(action),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    }
}

private fun getActionDescription(action: com.udptrigger.data.AutomationManager.AutomationAction): String {
    return when (action) {
        is com.udptrigger.data.AutomationManager.AutomationAction.SendUdp ->
            "Send UDP to ${action.host}:${action.port}"
        is com.udptrigger.data.AutomationManager.AutomationAction.SendTcp ->
            "Send TCP to ${action.host}:${action.port}"
        is com.udptrigger.data.AutomationManager.AutomationAction.Delay ->
            "Delay ${action.durationMs}ms"
        is com.udptrigger.data.AutomationManager.AutomationAction.SetVariable ->
            "Set ${action.name} = ${action.value}"
        is com.udptrigger.data.AutomationManager.AutomationAction.IncrementVariable ->
            "Increment ${action.name} by ${action.by}"
        is com.udptrigger.data.AutomationManager.AutomationAction.ShowNotification ->
            "Notify: ${action.title}"
        is com.udptrigger.data.AutomationManager.AutomationAction.Log ->
            "Log: ${action.message}"
        else -> action::class.simpleName ?: "Unknown Action"
    }
}

@Composable
private fun TriggerConfigDialog(
    onDismiss: () -> Unit,
    onTriggerSelected: (String) -> Unit
) {
    val triggers = listOf(
        "Packet Received",
        "Button Pressed",
        "Interval",
        "Gesture",
        "Time Range",
        "Network State",
        "Variable Changed"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Trigger Type") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                triggers.forEach { trigger ->
                    OutlinedButton(
                        onClick = {
                            onTriggerSelected(trigger)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(trigger)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
