package com.udptrigger.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udptrigger.R
import com.udptrigger.data.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application settings data class
 */
data class AppSettings(
    val hapticFeedback: Boolean = true,
    val soundFeedback: Boolean = false,
    val rateLimitMs: Int = 100,
    val autoReconnect: Boolean = false,
    val autoConnectOnStartup: Boolean = false,
    val keepScreenOn: Boolean = false,
    val wakeLockEnabled: Boolean = false
)

/**
 * Settings ViewModel
 */
class SettingsViewModel(
    private val context: Context,
    private val dataStore: SettingsDataStore
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                // Load settings from DataStore
                val haptic = dataStore.hapticEnabledFlow.first()
                val rateLimit = dataStore.rateLimitMsFlow.first()

                _settings.value = AppSettings(
                    hapticFeedback = haptic,
                    soundFeedback = false, // Not yet implemented
                    rateLimitMs = rateLimit,
                    autoReconnect = false, // Not yet implemented
                    autoConnectOnStartup = false, // Not yet implemented
                    keepScreenOn = false, // Not yet implemented
                    wakeLockEnabled = false // Not yet implemented
                )
            } catch (e: Exception) {
                // Use defaults
            }
        }
    }

    fun updateHapticFeedback(enabled: Boolean) {
        _settings.value = _settings.value.copy(hapticFeedback = enabled)
        viewModelScope.launch {
            dataStore.setHapticEnabled(enabled)
        }
    }

    fun updateRateLimit(ms: Int) {
        _settings.value = _settings.value.copy(rateLimitMs = ms)
        viewModelScope.launch {
            dataStore.setRateLimitMs(ms)
        }
    }
}

/**
 * Settings screen with Material 3 design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(LocalContext.current) as ViewModelProvider.Factory
    )
) {
    val settings by settingsViewModel.settings.collectAsState()
    val scope = rememberCoroutineScope()

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
                    .verticalScroll(rememberScrollState())
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
                        text = stringResource(R.string.nav_settings),
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

                // Settings sections
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Feedback Settings
                    SettingsSection(title = "Feedback") {
                        SwitchSetting(
                            title = stringResource(R.string.settings_haptic),
                            description = "Vibrate when sending packets",
                            checked = settings.hapticFeedback,
                            onCheckedChange = { settingsViewModel.updateHapticFeedback(it) }
                        )

                        SwitchSetting(
                            title = stringResource(R.string.settings_sound),
                            description = "Play sound when sending packets",
                            checked = settings.soundFeedback,
                            onCheckedChange = { /* TODO */ },
                            enabled = false
                        )
                    }

                    // Performance Settings
                    SettingsSection(title = "Performance") {
                        SliderSetting(
                            title = stringResource(R.string.settings_rate_limit),
                            description = "Minimum delay between packets: ${settings.rateLimitMs}ms",
                            value = settings.rateLimitMs.toFloat(),
                            valueRange = 0f..1000f,
                            onValueChange = {
                                settingsViewModel.updateRateLimit(it.toInt())
                            },
                            steps = 20
                        )

                        SwitchSetting(
                            title = stringResource(R.string.settings_wake_lock),
                            description = "Reduce latency by keeping CPU awake",
                            checked = settings.wakeLockEnabled,
                            onCheckedChange = { /* TODO */ },
                            enabled = false
                        )
                    }

                    // Connection Settings
                    SettingsSection(title = "Connection") {
                        SwitchSetting(
                            title = stringResource(R.string.settings_auto_reconnect),
                            description = "Automatically reconnect on disconnect",
                            checked = settings.autoReconnect,
                            onCheckedChange = { /* TODO */ },
                            enabled = false
                        )

                        SwitchSetting(
                            title = stringResource(R.string.settings_auto_connect_startup),
                            description = "Connect to last host on app start",
                            checked = settings.autoConnectOnStartup,
                            onCheckedChange = { /* TODO */ },
                            enabled = false
                        )
                    }

                    // Display Settings
                    SettingsSection(title = "Display") {
                        SwitchSetting(
                            title = stringResource(R.string.settings_keep_screen_on),
                            description = "Keep screen awake while connected",
                            checked = settings.keepScreenOn,
                            onCheckedChange = { /* TODO */ },
                            enabled = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )
        Card {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun SliderSetting(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
            Text(
                text = "${value.toInt()}ms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            }
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = "$description, current value ${value.toInt()} milliseconds"
            }
        )
    }
}

// Factory for creating SettingsViewModel
class SettingsViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(
            context,
            SettingsDataStore(context)
        ) as T
    }
}
