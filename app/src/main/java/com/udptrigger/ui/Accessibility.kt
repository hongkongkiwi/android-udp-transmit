package com.udptrigger.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.udptrigger.data.SettingsDataStore
import kotlinx.coroutines.runBlocking

/**
 * Accessibility utilities for UDP Trigger app.
 * Provides comprehensive TalkBack support and screen reader optimizations.
 */
object AccessibilityUtils {

    /**
     * Accessibility labels for main screen elements
     */
    object Labels {
        const val TRIGGER_BUTTON = "Trigger button. Double tap to send UDP packet."
        const val CONNECT_BUTTON = "Connect button. Double tap to connect to host."
        const val DISCONNECT_BUTTON = "Disconnect button. Double tap to disconnect."
        const val SETTINGS_BUTTON = "Settings button. Double tap to open settings."
        const val PRESETS_MENU = "Presets menu. Double tap to open preset options."
        const val HISTORY_LIST = "Packet history list. Contains sent and received packets."
        const val STATS_SECTION = "Statistics section. Shows connection health and packet counts."
        const val CONFIG_SECTION = "Configuration section. Contains host, port, and packet content."
        const val HOST_FIELD = "Host address field. Enter the target IP address or hostname."
        const val PORT_FIELD = "Port field. Enter the target UDP port number."
        const val CONTENT_FIELD = "Packet content field. Enter the message to send."
        const val HEX_MODE_TOGGLE = "Hex mode toggle. When enabled, packet content is sent as hexadecimal."
        const val TIMESTAMP_TOGGLE = "Timestamp toggle. When enabled, current timestamp is appended to packet."
        const val BURST_MODE_TOGGLE = "Burst mode toggle. When enabled, multiple packets are sent rapidly."
        const val SCHEDULED_TRIGGER_TOGGLE = "Scheduled trigger toggle. When enabled, packets are sent at regular intervals."
        const val MULTI_TARGET_TOGGLE = "Multi-target toggle. When enabled, packets are sent to multiple targets."
        const val THEME_TOGGLE = "Theme toggle. Double tap to switch between light and dark mode."
        const val WIDGET_INFO = "Home screen widget. Provides quick trigger access from home screen."
    }

    /**
     * Generate status announcement for screen readers
     */
    fun generateStatusAnnouncement(
        isConnected: Boolean,
        host: String,
        port: Int,
        lastPacketTime: Long?,
        packetCount: Int
    ): String {
        return buildString {
            append(if (isConnected) "Connected to $host port $port. " else "Disconnected. ")
            append("$packetCount packets sent. ")
            lastPacketTime?.let {
                val secondsAgo = (System.currentTimeMillis() - it) / 1000
                append("Last packet $secondsAgo seconds ago.")
            }
        }
    }

    /**
     * Generate error announcement
     */
    fun generateErrorAnnouncement(error: String): String {
        return "Error: $error"
    }

    /**
     * Generate connection status description
     */
    fun getConnectionStatusDescription(isConnected: Boolean, health: String): String {
        return when {
            !isConnected -> "Not connected"
            health == "GOOD" -> "Connected. Connection quality is good."
            health == "DEGRADED" -> "Connected. Connection quality is degraded."
            health == "POOR" -> "Connected. Connection quality is poor."
            else -> "Connected. Connection status unknown."
        }
    }

    /**
     * Generate packet announcement
     */
    fun generatePacketAnnouncement(
        sent: Boolean,
        content: String,
        success: Boolean,
        latencyMs: Double?
    ): String {
        return buildString {
            append(if (sent) "Packet sent. " else "Packet received. ")
            append("Content: ${content.take(50)}${if (content.length > 50) "..." else ""}. ")
            append(if (success) "Success. " else "Failed. ")
            latencyMs?.let { append("Latency ${it.toInt()} milliseconds.") }
        }
    }
}

/**
 * Accessible section header with heading semantics
 */
@Composable
fun AccessibleSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Text(
        text = title,
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        modifier = modifier.semantics {
            heading()
            contentDescription = "$title section"
        }
    )
}

/**
 * Accessibility settings data class
 */
data class AccessibilitySettings(
    val highContrastMode: Boolean = false,
    val largeText: Boolean = false,
    val announceErrors: Boolean = true,
    val announcePacketStatus: Boolean = true,
    val announceConnectionChanges: Boolean = true,
    val hapticFeedback: Boolean = true,
    val soundFeedback: Boolean = false
)

/**
 * Accessibility preferences manager using DataStore
 */
class AccessibilityPreferences(private val context: android.content.Context) {

    private val dataStore = SettingsDataStore(context)

    /**
     * Get accessibility settings as a Flow
     */
    val accessibilitySettingsFlow = dataStore.accessibilitySettingsFlow

    /**
     * Get current accessibility settings synchronously
     */
    fun getSettings(): AccessibilitySettings {
        var settings = AccessibilitySettings()
        runBlocking {
            dataStore.accessibilitySettingsFlow.collect {
                settings = it
            }
        }
        return settings
    }

    var highContrastMode: Boolean
        get() = getSettings().highContrastMode
        set(value) = runBlocking { dataStore.updateHighContrastMode(value) }

    var largeText: Boolean
        get() = getSettings().largeText
        set(value) = runBlocking { dataStore.updateLargeText(value) }

    var announceErrors: Boolean
        get() = getSettings().announceErrors
        set(value) = runBlocking { dataStore.updateAnnounceErrors(value) }

    var announcePacketStatus: Boolean
        get() = getSettings().announcePacketStatus
        set(value) = runBlocking { dataStore.updateAnnouncePacketStatus(value) }

    var announceConnectionChanges: Boolean
        get() = getSettings().announceConnectionChanges
        set(value) = runBlocking { dataStore.updateAnnounceConnectionChanges(value) }

    /**
     * Save all accessibility settings at once
     */
    fun saveSettings(settings: AccessibilitySettings) = runBlocking {
        dataStore.saveAccessibilitySettings(settings)
    }
}
