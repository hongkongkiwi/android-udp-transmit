package com.udptrigger.widget

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.udptrigger.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DataStore for enhanced widget preferences
 */
private val Context.enhancedWidgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "enhanced_widget_preferences")

/**
 * Enhanced widget preference keys
 */
object EnhancedWidgetKeys {
    val KEY_CONNECTED = booleanPreferencesKey("enhanced_widget_connected")
    val KEY_WIDGET_SIZE = stringPreferencesKey("enhanced_widget_size") // small, medium, large
    val KEY_LAST_TRIGGER_TIME = longPreferencesKey("enhanced_widget_last_trigger")
    val KEY_PACKETS_SENT = intPreferencesKey("enhanced_widget_packets_sent")
    val KEY_CURRENT_PRESET = stringPreferencesKey("enhanced_widget_preset")
    val KEY_SHOW_STATS = booleanPreferencesKey("enhanced_widget_show_stats")
}

// Long preferences key helper
private fun longPreferencesKey(name: String): androidx.datastore.preferences.core.Key<Long> =
    androidx.datastore.preferences.core.longPreferencesKey(name)

/**
 * Widget size enumeration
 */
enum class WidgetSize(val displayName: String, val minWidth: Int, val minHeight: Int) {
    SMALL("Small", 2, 2),
    MEDIUM("Medium", 4, 2),
    LARGE("Large", 4, 4);

    companion object {
        fun fromString(value: String?): WidgetSize = values().find { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
    }
}

/**
 * Update enhanced widget state from the app
 */
fun updateEnhancedWidgetState(
    context: Context,
    isConnected: Boolean,
    lastTriggerTime: Long = System.currentTimeMillis(),
    packetsSent: Int = 0,
    currentPreset: String = ""
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scope.launch {
        context.enhancedWidgetDataStore.edit { preferences ->
            preferences[EnhancedWidgetKeys.KEY_CONNECTED] = isConnected
            preferences[EnhancedWidgetKeys.KEY_LAST_TRIGGER_TIME] = lastTriggerTime
            preferences[EnhancedWidgetKeys.KEY_PACKETS_SENT] = packetsSent
            if (currentPreset.isNotEmpty()) {
                preferences[EnhancedWidgetKeys.KEY_CURRENT_PRESET] = currentPreset
            }
        }
        EnhancedTriggerWidget().updateAll(context)
    }
}

/**
 * Enhanced Glance App Widget with multiple sizes and features
 * - Small: Single trigger button with status indicator
 * - Medium: Quick connect/disconnect + trigger button + preset name
 * - Large: Full stats + trigger + last trigger time + preset info
 */
class EnhancedTriggerWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext ?: context

        // Read current widget state
        val prefs = runBlocking {
            appContext.enhancedWidgetDataStore.data.first()
        }

        val isConnected = prefs[EnhancedWidgetKeys.KEY_CONNECTED] ?: false
        val widgetSize = WidgetSize.fromString(prefs[EnhancedWidgetKeys.KEY_WIDGET_SIZE])
        val lastTriggerTime = prefs[EnhancedWidgetKeys.KEY_LAST_TRIGGER_TIME] ?: 0
        val packetsSent = prefs[EnhancedWidgetKeys.KEY_PACKETS_SENT] ?: 0
        val currentPreset = prefs[EnhancedWidgetKeys.KEY_CURRENT_PRESET] ?: ""
        val showStats = prefs[EnhancedWidgetKeys.KEY_SHOW_STATS] ?: true

        val statusColor = if (isConnected) {
            ColorProvider(0xFF4CAF50.toInt()) // Green
        } else {
            ColorProvider(0xFF1A73E8.toInt()) // Blue
        }

        val statusText = if (isConnected) "● Connected" else "○ Ready"
        val lastTriggerText = if (lastTriggerTime > 0) {
            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastTriggerTime))
        } else "--:--:--"

        // Intent to open app with trigger action
        val triggerIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = ACTION_TRIGGER_UDP
        }

        // Intent to open app settings (for configuration)
        val configIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = ACTION_CONFIG_WIDGET
        }

        provideContent {
            GlanceTheme {
                when (widgetSize) {
                    WidgetSize.SMALL -> SmallWidgetContent(
                        statusText = statusText,
                        statusColor = statusColor,
                        triggerIntent = triggerIntent
                    )
                    WidgetSize.MEDIUM -> MediumWidgetContent(
                        statusText = statusText,
                        statusColor = statusColor,
                        currentPreset = currentPreset,
                        triggerIntent = triggerIntent,
                        configIntent = configIntent
                    )
                    WidgetSize.LARGE -> LargeWidgetContent(
                        statusText = statusText,
                        statusColor = statusColor,
                        currentPreset = currentPreset,
                        lastTriggerTime = lastTriggerText,
                        packetsSent = packetsSent,
                        showStats = showStats,
                        triggerIntent = triggerIntent,
                        configIntent = configIntent
                    )
                }
            }
        }
    }
}

/**
 * Small widget: Status indicator + trigger button
 */
@Composable
private fun SmallWidgetContent(
    statusText: String,
    statusColor: ColorProvider,
    triggerIntent: Intent
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(0xFF1E1E1E.toInt()))
            .padding(8.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // Status indicator
        Text(
            text = statusText,
            style = TextStyle(
                color = statusColor,
                fontSize = 12.sp
            )
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Trigger button
        androidx.glance.Button(
            text = "TRIGGER",
            onClick = actionStartActivity(triggerIntent),
            modifier = GlanceModifier.size(60.dp, 40.dp)
        )
    }
}

/**
 * Medium widget: Status + preset + trigger + config
 */
@Composable
private fun MediumWidgetContent(
    statusText: String,
    statusColor: ColorProvider,
    currentPreset: String,
    triggerIntent: Intent,
    configIntent: Intent
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(0xFF1E1E1E.toInt()))
            .padding(12.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // Status row
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = statusText,
                style = TextStyle(
                    color = statusColor,
                    fontSize = 14.sp
                )
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Preset name (truncated)
            if (currentPreset.isNotEmpty()) {
                Text(
                    text = currentPreset.take(10),
                    style = TextStyle(
                        color = ColorProvider(0xFFBBBBBB.toInt()),
                        fontSize = 12.sp
                    )
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(12.dp))

        // Trigger button
        androidx.glance.Button(
            text = "SEND UDP",
            onClick = actionStartActivity(triggerIntent),
            modifier = GlanceModifier.fillMaxSize()
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Config button (small)
        androidx.glance.Button(
            text = "Config",
            onClick = actionStartActivity(configIntent),
            modifier = GlanceModifier.fillMaxSize()
        )
    }
}

/**
 * Large widget: Full stats display
 */
@Composable
private fun LargeWidgetContent(
    statusText: String,
    statusColor: ColorProvider,
    currentPreset: String,
    lastTriggerTime: String,
    packetsSent: Int,
    showStats: Boolean,
    triggerIntent: Intent,
    configIntent: Intent
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(0xFF1E1E1E.toInt()))
            .padding(16.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        // Header row
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "UDP Trigger",
                style = TextStyle(
                    color = ColorProvider(0xFFFFFFFF.toInt()),
                    fontSize = 16.sp,
                    fontWeight = androidx.glance.text.FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.defaultWeight())

            Text(
                text = statusText,
                style = TextStyle(
                    color = statusColor,
                    fontSize = 14.sp
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(12.dp))

        // Current preset
        if (currentPreset.isNotEmpty()) {
            Text(
                text = "Preset: $currentPreset",
                style = TextStyle(
                    color = ColorProvider(0xFFBBBBBB.toInt()),
                    fontSize = 12.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
        }

        // Stats row
        if (showStats) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.Horizontal.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Last:",
                        style = TextStyle(
                            color = ColorProvider(0xFF888888.toInt()),
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = lastTriggerTime,
                        style = TextStyle(
                            color = ColorProvider(0xFFFFFFFF.toInt()),
                            fontSize = 14.sp
                        )
                    )
                }

                Column {
                    Text(
                        text = "Sent:",
                        style = TextStyle(
                            color = ColorProvider(0xFF888888.toInt()),
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = packetsSent.toString(),
                        style = TextStyle(
                            color = ColorProvider(0xFFFFFFFF.toInt()),
                            fontSize = 14.sp
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(12.dp))
        }

        // Large trigger button
        androidx.glance.Button(
            text = "TRIGGER UDP",
            onClick = actionStartActivity(triggerIntent),
            modifier = GlanceModifier.fillMaxSize()
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Config button
        androidx.glance.Button(
            text = "Configure",
            onClick = actionStartActivity(configIntent),
            modifier = GlanceModifier.fillMaxSize()
        )
    }
}

/**
 * Receiver for enhanced widget updates
 */
class EnhancedTriggerReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = EnhancedTriggerWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_UPDATE_CONNECTION -> {
                val isConnected = intent.getBooleanExtra(EXTRA_CONNECTION_STATE, false)
                updateEnhancedWidgetState(context, isConnected)
            }
            ACTION_CONFIG_WIDGET -> {
                // Handle configuration updates
            }
        }
    }
}

// Additional action constants
const val ACTION_CONFIG_WIDGET = "com.udptrigger.CONFIG_WIDGET"
