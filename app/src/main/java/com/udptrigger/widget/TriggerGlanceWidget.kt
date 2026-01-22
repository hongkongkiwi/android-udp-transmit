package com.udptrigger.widget

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.sp
import com.udptrigger.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * DataStore for widget preferences
 */
private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_preferences")

/**
 * Widget preference keys
 */
object WidgetKeys {
    val KEY_CONNECTED = booleanPreferencesKey("widget_connected")
}

/**
 * Widget action constants
 */
const val ACTION_TRIGGER_UDP = "com.udptrigger.TRIGGER_UDP"
const val ACTION_UPDATE_CONNECTION = "com.udptrigger.UPDATE_CONNECTION"
const val EXTRA_CONNECTION_STATE = "connection_state"

/**
 * Update widget connection state from the app
 */
fun updateWidgetConnectionState(context: Context, isConnected: Boolean) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    scope.launch {
        context.widgetDataStore.edit { preferences ->
            preferences[WidgetKeys.KEY_CONNECTED] = isConnected
        }
        TriggerGlanceWidget().updateAll(context)
    }
}

/**
 * Simple Glance App Widget that shows connection status and opens app on tap
 * When tapped, opens the app with TRIGGER_UDP action which auto-triggers if connected
 */
class TriggerGlanceWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read current connection state
        val appContext = context.applicationContext ?: context
        val isConnected = runBlocking {
            appContext.widgetDataStore.data
                .map { it[WidgetKeys.KEY_CONNECTED] ?: false }
                .first()
        }

        val statusText = if (isConnected) "Connected" else "Ready"
        val statusColor = if (isConnected) {
            ColorProvider(0xFF4CAF50.toInt()) // Green
        } else {
            ColorProvider(0xFF1A73E8.toInt()) // Blue
        }

        // Intent to open app with trigger action
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = ACTION_TRIGGER_UDP
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(statusColor)
                    .padding(16),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Status
                Text(
                    text = statusText,
                    style = TextStyle(
                        color = ColorProvider(0xFFFFFFFF.toInt()),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Trigger button using Button composable with action
                androidx.glance.Button(
                    text = "Tap to Trigger",
                    onClick = actionStartActivity(appIntent)
                )
            }
        }
    }
}

/**
 * Receiver for widget updates
 */
class TriggerGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TriggerGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_UPDATE_CONNECTION -> {
                val isConnected = intent.getBooleanExtra(EXTRA_CONNECTION_STATE, false)
                updateWidgetConnectionState(context, isConnected)
            }
        }
    }
}
