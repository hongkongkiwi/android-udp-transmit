package com.udptrigger.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.udptrigger.data.SettingsDataStore
import com.udptrigger.ui.theme.UdpTriggerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.widgetConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_config")

/**
 * Widget configuration activity.
 * Allows users to configure the host and port for the widget trigger.
 */
class WidgetConfigurationActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the widget ID from the intent
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        // If the ID is invalid, close the activity
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            UdpTriggerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WidgetConfigScreen(
                        appWidgetId = appWidgetId,
                        onSave = { host, port, content ->
                            saveWidgetConfig(host, port, content)
                            val resultValue = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(RESULT_OK, resultValue)
                            finish()
                        },
                        onCancel = {
                            val resultValue = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(RESULT_CANCELED, resultValue)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun saveWidgetConfig(host: String, port: Int, content: String) {
        lifecycleScope.launch {
            widgetConfigDataStore.edit { preferences ->
                preferences[widgetConfigKey("host", appWidgetId)] = host
                preferences[widgetConfigKey("port", appWidgetId)] = port.toString()
                preferences[widgetConfigKey("content", appWidgetId)] = content
            }
        }
    }

    private fun widgetConfigKey(key: String, widgetId: Int): Preferences.Key<String> {
        return stringPreferencesKey("widget_${key}_$widgetId")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    appWidgetId: Int,
    onSave: (String, Int, String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()

    // Load current config
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5000") }
    var content by remember { mutableStateOf("TRIGGER") }
    var hostError by remember { mutableStateOf<String?>(null) }
    var portError by remember { mutableStateOf<String?>(null) }

    // Load existing config if available
    LaunchedEffect(appWidgetId) {
        val dataStore = context.widgetConfigDataStore
        val prefs = dataStore.data.first()
        val hostKey = stringPreferencesKey("widget_host_$appWidgetId")
        val portKey = stringPreferencesKey("widget_port_$appWidgetId")
        val contentKey = stringPreferencesKey("widget_content_$appWidgetId")
        val savedHost = prefs[hostKey]
        val savedPort = prefs[portKey]
        val savedContent = prefs[contentKey]

        if (savedHost != null) host = savedHost
        if (savedPort != null) port = savedPort
        if (savedContent != null) content = savedContent
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Widget") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Check, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Validate
                        var isValid = true
                        if (host.isBlank()) {
                            hostError = "Host cannot be empty"
                            isValid = false
                        }
                        val portNum = port.toIntOrNull()
                        if (portNum == null || portNum < 1 || portNum > 65535) {
                            portError = "Port must be 1-65535"
                            isValid = false
                        }
                        if (isValid) {
                            onSave(host.trim(), portNum!!, content.trim())
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "Configure the host and port that the widget will send UDP packets to. Tap the widget button to instantly trigger without opening the app.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Host input
            OutlinedTextField(
                value = host,
                onValueChange = {
                    host = it
                    hostError = null
                },
                label = { Text("Host Address") },
                placeholder = { Text("e.g., 192.168.1.100 or hostname") },
                isError = hostError != null,
                supportingText = hostError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Port input
            OutlinedTextField(
                value = port,
                onValueChange = {
                    port = it.filter { c -> c.isDigit() }
                    portError = null
                },
                label = { Text("Port") },
                isError = portError != null,
                supportingText = portError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // Content input
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Packet Content") },
                placeholder = { Text("e.g., TRIGGER") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Presets section
            Text(
                text = "Or use saved configuration:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Load from current app config button
            OutlinedButton(
                onClick = {
                    val dataStore = SettingsDataStore(context)
                    lifecycleOwner.lifecycleScope.launch {
                        dataStore.configFlow.collect { config ->
                            host = config.host
                            port = config.port.toString()
                            content = config.packetContent
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use Current App Config")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save button
            Button(
                onClick = {
                    var isValid = true
                    if (host.isBlank()) {
                        hostError = "Host cannot be empty"
                        isValid = false
                    }
                    val portNum = port.toIntOrNull()
                    if (portNum == null || portNum < 1 || portNum > 65535) {
                        portError = "Port must be 1-65535"
                        isValid = false
                    }
                    if (isValid) {
                        onSave(host.trim(), portNum!!, content.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Configuration")
            }

            // Cancel button
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
