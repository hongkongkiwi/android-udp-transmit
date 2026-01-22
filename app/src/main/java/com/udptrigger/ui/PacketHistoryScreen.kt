package com.udptrigger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udptrigger.R
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Packet history entry data class
 */
data class PacketHistoryEntry(
    val id: Long,
    val timestamp: Long,
    val content: String,
    val host: String,
    val port: Int,
    val success: Boolean,
    val latencyMs: Double = 0.0,
    val errorMessage: String? = null
)

/**
 * Packet history ViewModel
 */
class PacketHistoryViewModel : ViewModel() {
    private val _history = MutableStateFlow<List<PacketHistoryEntry>>(emptyList())
    val history: StateFlow<List<PacketHistoryEntry>> = _history.asStateFlow()

    private val _selectedEntry = MutableStateFlow<PacketHistoryEntry?>(null)
    val selectedEntry: StateFlow<PacketHistoryEntry?> = _selectedEntry.asStateFlow()

    fun addEntry(entry: PacketHistoryEntry) {
        _history.value = listOf(entry) + _history.value
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    fun selectEntry(entry: PacketHistoryEntry?) {
        _selectedEntry.value = entry
    }

    fun deleteEntry(entry: PacketHistoryEntry) {
        _history.value = _history.value.filter { it.id != entry.id }
    }
}

/**
 * Packet history screen with list of sent packets
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacketHistoryDialog(
    onDismiss: () -> Unit,
    historyViewModel: PacketHistoryViewModel = viewModel()
) {
    val history by historyViewModel.history.collectAsState()
    val selectedEntry by historyViewModel.selectedEntry.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.8f)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.nav_history),
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

                // Stats row
                if (history.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = stringResource(R.string.stats_packets_sent, history.count { it.success }),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.stats_packets_failed, history.count { !it.success }),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    HorizontalDivider()
                }

                // History list
                if (history.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "No packets sent yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history) { entry ->
                            PacketHistoryItem(
                                entry = entry,
                                onClick = { historyViewModel.selectEntry(entry) },
                                onDelete = { historyViewModel.deleteEntry(entry) }
                            )
                        }
                    }
                }

                // Footer with clear button
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (history.isNotEmpty()) {
                        TextButton(
                            onClick = { historyViewModel.clearHistory() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear History")
                        }
                    }
                }
            }
        }
    }

    // Detail dialog
    if (selectedEntry != null) {
        PacketDetailDialog(
            entry = selectedEntry!!,
            onDismiss = { historyViewModel.selectEntry(null) }
        )
    }
}

@Composable
fun PacketHistoryItem(
    entry: PacketHistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = dateFormat.format(Date(entry.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.success) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Icon(
                imageVector = if (entry.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = if (entry.success) "Success" else "Failed",
                tint = if (entry.success) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Entry details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry.success && entry.latencyMs > 0) {
                        Text(
                            text = "${String.format("%.2f", entry.latencyMs)}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = "${entry.host}:${entry.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete entry"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacketDetailDialog(
    entry: PacketHistoryEntry,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val fullTimeStr = dateFormat.format(Date(entry.timestamp))

    AlertDialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Packet Details",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )

                HorizontalDivider()

                DetailRow("Status", if (entry.success) "Success" else "Failed")
                DetailRow("Time", fullTimeStr)
                DetailRow("Host", "${entry.host}:${entry.port}")
                DetailRow("Content", entry.content)

                if (entry.success && entry.latencyMs > 0) {
                    DetailRow("Latency", "${String.format("%.2f", entry.latencyMs)}ms")
                }

                if (!entry.success && entry.errorMessage != null) {
                    DetailRow("Error", entry.errorMessage!!)
                }

                HorizontalDivider()

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
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
