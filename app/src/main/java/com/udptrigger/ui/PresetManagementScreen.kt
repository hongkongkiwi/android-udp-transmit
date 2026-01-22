package com.udptrigger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udptrigger.R
import com.udptrigger.data.UdpConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Preset data class
 */
data class Preset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val config: UdpConfig,
    val isDefault: Boolean = false
)

/**
 * Preset Manager ViewModel
 */
class PresetViewModel : ViewModel() {
    private val _presets = MutableStateFlow<List<Preset>>(getDefaultPresets())
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    private val _selectedPreset = MutableStateFlow<Preset?>(null)
    val selectedPreset: StateFlow<Preset?> = _selectedPreset.asStateFlow()

    private val _showSaveDialog = MutableStateFlow(false)
    val showSaveDialog: StateFlow<Boolean> = _showSaveDialog.asStateFlow()

    private val _currentConfig = MutableStateFlow(UdpConfig())
    val currentConfig: StateFlow<UdpConfig> = _currentConfig.asStateFlow()

    fun setCurrentConfig(config: UdpConfig) {
        _currentConfig.value = config
    }

    fun selectPreset(preset: Preset?) {
        _selectedPreset.value = preset
    }

    fun showSaveDialog(show: Boolean) {
        _showSaveDialog.value = show
    }

    fun addPreset(preset: Preset) {
        _presets.value = _presets.value + preset
    }

    fun updatePreset(preset: Preset) {
        _presets.value = _presets.value.map {
            if (it.id == preset.id) preset else it
        }
    }

    fun deletePreset(preset: Preset) {
        if (!preset.isDefault) {
            _presets.value = _presets.value.filter { it.id != preset.id }
        }
    }

    fun loadPreset(preset: Preset): UdpConfig {
        return preset.config
    }

    private fun getDefaultPresets(): List<Preset> {
        return listOf(
            Preset(
                id = "default_1",
                name = "Default",
                description = "Default UDP trigger",
                config = UdpConfig(host = "192.168.1.100", port = 5000, packetContent = "TRIGGER"),
                isDefault = true
            ),
            Preset(
                id = "default_2",
                name = "Test Server",
                description = "Local test server",
                config = UdpConfig(host = "localhost", port = 5000, packetContent = "TEST")
            ),
            Preset(
                id = "default_3",
                name = "Broadcast",
                description = "Broadcast to local network",
                config = UdpConfig(host = "255.255.255.255", port = 5000, packetContent = "DISCOVER")
            )
        )
    }
}

/**
 * Preset management dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetManagementDialog(
    onDismiss: () -> Unit,
    onPresetSelected: (UdpConfig) -> Unit,
    presetViewModel: PresetViewModel = viewModel()
) {
    val presets by presetViewModel.presets.collectAsState()
    val showSaveDialog by presetViewModel.showSaveDialog.collectAsState()
    val currentConfig by presetViewModel.currentConfig.collectAsState()

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
                        text = stringResource(R.string.presets),
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

                // Save current as preset button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = { presetViewModel.showSaveDialog(true) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Current")
                    }
                }

                // Presets list
                if (presets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No presets saved",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presets) { preset ->
                            PresetItem(
                                preset = preset,
                                onClick = { presetViewModel.selectPreset(preset) },
                                onLoad = {
                                    onPresetSelected(presetViewModel.loadPreset(preset))
                                    onDismiss()
                                },
                                onDelete = { presetViewModel.deletePreset(preset) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Save preset dialog
    if (showSaveDialog) {
        SavePresetDialog(
            config = currentConfig,
            onDismiss = { presetViewModel.showSaveDialog(false) },
            onSave = { name, description ->
                val newPreset = Preset(
                    name = name,
                    description = description,
                    config = currentConfig
                )
                presetViewModel.addPreset(newPreset)
                presetViewModel.showSaveDialog(false)
            }
        )
    }
}

@Composable
fun PresetItem(
    preset: Preset,
    onClick: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
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
                            text = preset.name,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (preset.isDefault) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Default", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    if (preset.description.isNotEmpty()) {
                        Text(
                            text = preset.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onLoad) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Load preset"
                        )
                    }
                    if (!preset.isDefault) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete preset"
                            )
                        }
                    }
                }
            }

            // Show config preview
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "${preset.config.host}:${preset.config.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\"${preset.config.packetContent}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavePresetDialog(
    config: UdpConfig,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preset_save_as)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.preset_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.preset_description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Preview
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${config.host}:${config.port}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = config.packetContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, description) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
