package com.udptrigger.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import com.udptrigger.data.UdpConfig
import com.udptrigger.domain.UdpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Burst mode state
 */
data class BurstModeState(
    val isRunning: Boolean = false,
    val packetCount: Int = 10,
    val delayMs: Int = 100,
    val currentIndex: Int = 0,
    val sentCount: Int = 0,
    val failedCount: Int = 0
)

/**
 * Burst Mode ViewModel
 */
class BurstModeViewModel(
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(BurstModeState())
    val state: StateFlow<BurstModeState> = _state.asStateFlow()

    private val udpClient = UdpClient()

    fun updatePacketCount(count: Int) {
        _state.value = _state.value.copy(packetCount = count.coerceIn(1, 1000))
    }

    fun updateDelayMs(delay: Int) {
        _state.value = _state.value.copy(delayMs = delay.coerceIn(1, 5000))
    }

    fun startBurst(config: UdpConfig) {
        if (_state.value.isRunning) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isRunning = true, currentIndex = 0, sentCount = 0, failedCount = 0)

            val timestamp = System.nanoTime()
            repeat(_state.value.packetCount) { index ->
                if (!_state.value.isRunning) return@launch

                _state.value = _state.value.copy(currentIndex = index + 1)

                val message = buildPacketMessage(config, timestamp + index)
                val result = udpClient.send(message)

                result.fold(
                    onSuccess = {
                        _state.value = _state.value.copy(sentCount = _state.value.sentCount + 1)
                    },
                    onFailure = {
                        _state.value = _state.value.copy(failedCount = _state.value.failedCount + 1)
                    }
                )

                if (index < _state.value.packetCount - 1) {
                    delay(_state.value.delayMs.toLong())
                }
            }

            _state.value = _state.value.copy(isRunning = false)
        }
    }

    fun stopBurst() {
        _state.value = _state.value.copy(isRunning = false)
    }

    fun reset() {
        _state.value = BurstModeState()
    }

    private fun buildPacketMessage(config: UdpConfig, timestamp: Long): ByteArray {
        return if (config.includeTimestamp) {
            "${config.packetContent}:$timestamp".toByteArray(Charsets.UTF_8)
        } else {
            config.packetContent.toByteArray(Charsets.UTF_8)
        }
    }
}

/**
 * Burst mode dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BurstModeDialog(
    onDismiss: () -> Unit,
    config: UdpConfig,
    burstViewModel: BurstModeViewModel = viewModel(
        factory = BurstModeViewModelFactory(LocalContext.current)
    )
) {
    val state by burstViewModel.state.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_burst_mode),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }

                HorizontalDivider()

                // Packet count slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Packet Count",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${state.packetCount}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = state.packetCount.toFloat(),
                        onValueChange = { burstViewModel.updatePacketCount(it.toInt()) },
                        valueRange = 1f..100f,
                        steps = 98,
                        enabled = !state.isRunning
                    )
                }

                // Delay slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Delay",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${state.delayMs}ms",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = state.delayMs.toFloat(),
                        onValueChange = { burstViewModel.updateDelayMs(it.toInt()) },
                        valueRange = 10f..1000f,
                        steps = 98,
                        enabled = !state.isRunning
                    )
                }

                HorizontalDivider()

                // Progress indicator
                if (state.isRunning || state.currentIndex > 0) {
                    val progress by animateFloatAsState(
                        targetValue = if (state.packetCount > 0) {
                            state.currentIndex.toFloat() / state.packetCount
                        } else 0f,
                        label = "progress"
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${state.currentIndex} / ${state.packetCount}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Results
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ResultChip(
                            label = "Sent",
                            count = state.sentCount,
                            color = MaterialTheme.colorScheme.primary
                        )
                        ResultChip(
                            label = "Failed",
                            count = state.failedCount,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                HorizontalDivider()

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isRunning) {
                        OutlinedButton(
                            onClick = { burstViewModel.stopBurst() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { burstViewModel.reset() },
                            modifier = Modifier.weight(1f),
                            enabled = state.currentIndex > 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset")
                        }

                        Button(
                            onClick = { burstViewModel.startBurst(config) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultChip(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
        }
    }
}

// Factory for BurstModeViewModel
class BurstModeViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BurstModeViewModel(context) as T
    }
}
