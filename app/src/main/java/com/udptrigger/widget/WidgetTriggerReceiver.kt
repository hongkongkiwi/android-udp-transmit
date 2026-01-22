package com.udptrigger.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.udptrigger.MainActivity
import com.udptrigger.R
import com.udptrigger.data.SettingsDataStore
import com.udptrigger.ui.UdpConfig
import com.udptrigger.domain.UdpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Broadcast receiver for instant widget triggers.
 * Receives TRIGGER_UDP broadcast from widget and sends UDP packet immediately
 * without opening the main activity, providing minimal latency.
 *
 * This receiver is triggered when the user taps the widget button.
 * It reads the saved configuration from DataStore and sends the packet.
 */
class WidgetTriggerReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER_UDP) {
            return
        }

        // Show notification that trigger was received
        showTriggerNotification(context)

        // Send UDP packet in background
        receiverScope.launch {
            sendUdpPacket(context)
        }
    }

    /**
     * Send UDP packet using saved configuration
     */
    private suspend fun sendUdpPacket(context: Context) {
        try {
            val dataStore = SettingsDataStore(context)
            val savedConfig = withContext(Dispatchers.IO) {
                dataStore.configFlow.first()
            }

            // Validate config
            if (savedConfig.host.isBlank() || savedConfig.port <= 0) {
                updateNotification(context, "Config invalid", false)
                return
            }

            // Build packet content
            val packetContent = buildPacketContent(savedConfig, 0)

            // Convert to bytes (handle hex mode)
            val packetBytes = if (savedConfig.hexMode) {
                packetContent.toByteArray(Charsets.ISO_8859_1)
            } else {
                packetContent.toByteArray(Charsets.UTF_8)
            }

            // Create UDP client and send
            val udpClient = UdpClient()
            try {
                udpClient.initialize(savedConfig.host, savedConfig.port)
                val result = udpClient.sendFast(packetBytes)

                if (result > 0) {
                    updateNotification(context, "Packet sent!", true)
                } else {
                    updateNotification(context, "Send failed", false)
                }
            } finally {
                udpClient.closeSync()
            }

        } catch (e: Exception) {
            updateNotification(context, "Error: ${e.message}", false)
        }
    }

    /**
     * Build packet content from config
     */
    private fun buildPacketContent(config: UdpConfig, burstIndex: Int): String {
        val content = StringBuilder()

        // Add timestamp if enabled
        if (config.includeTimestamp) {
            content.append(System.currentTimeMillis()).append("|")
        }

        // Add burst index if enabled
        if (config.includeBurstIndex && burstIndex > 0) {
            content.append(burstIndex).append("|")
        }

        // Add main content
        content.append(config.packetContent)

        return content.toString()
    }

    /**
     * Show notification that trigger was received
     */
    private fun showTriggerNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Widget Triggers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for widget trigger actions"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open app
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("UDP Trigger")
            .setContentText("Sending packet...")
            .setSmallIcon(R.drawable.ic_notification_send)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SENDING, notification)
    }

    /**
     * Update notification with result
     */
    private fun updateNotification(context: Context, message: String, success: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Cancel sending notification
        notificationManager.cancel(NOTIFICATION_ID_SENDING)

        // Create result notification
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val icon = if (success) R.drawable.ic_notification_send else android.R.drawable.stat_notify_error

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("UDP Trigger")
            .setContentText(message)
            .setSmallIcon(icon)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID_RESULT, notification)
    }

    companion object {
        const val ACTION_TRIGGER_UDP = "com.udptrigger.TRIGGER_UDP"
        private const val CHANNEL_ID = "widget_trigger_channel"
        private const val NOTIFICATION_ID_SENDING = 2001
        private const val NOTIFICATION_ID_RESULT = 2002
    }
}
