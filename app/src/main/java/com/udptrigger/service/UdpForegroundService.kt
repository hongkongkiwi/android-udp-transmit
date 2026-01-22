package com.udptrigger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.udptrigger.MainActivity
import com.udptrigger.R

/**
 * Foreground service that keeps the UDP trigger app alive in the background.
 * This ensures the app can send packets even when the screen is off or app is backgrounded.
 *
 * Features:
 * - Persistent notification showing connection status
 * - Tap notification to return to app
 * - Stop button in notification
 */
class UdpForegroundService : Service() {

    private val binder = LocalBinder()
    private var notificationId = 1
    private var isForeground = false

    companion object {
        const val CHANNEL_ID = "udp_trigger_service"
        const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.udptrigger.START_SERVICE"
        const val ACTION_STOP = "com.udptrigger.STOP_SERVICE"
        const val ACTION_UPDATE_STATUS = "com.udptrigger.UPDATE_STATUS"

        const val EXTRA_IS_CONNECTED = "is_connected"
        const val EXTRA_TARGET_HOST = "target_host"
        const val EXTRA_TARGET_PORT = "target_port"
    }

    inner class LocalBinder : Binder() {
        fun getService(): UdpForegroundService = this@UdpForegroundService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val isConnected = intent.getBooleanExtra(EXTRA_IS_CONNECTED, false)
                val targetHost = intent.getStringExtra(EXTRA_TARGET_HOST) ?: "Unknown"
                val targetPort = intent.getIntExtra(EXTRA_TARGET_PORT, 0)
                startForeground(isConnected, targetHost, targetPort)
            }
            ACTION_STOP -> {
                stopForeground()
            }
            ACTION_UPDATE_STATUS -> {
                val isConnected = intent.getBooleanExtra(EXTRA_IS_CONNECTED, false)
                val targetHost = intent.getStringExtra(EXTRA_TARGET_HOST) ?: "Unknown"
                val targetPort = intent.getIntExtra(EXTRA_TARGET_PORT, 0)
                updateNotification(isConnected, targetHost, targetPort)
            }
        }
        // START_STICKY ensures service restarts if killed by system
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "UDP Trigger Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps UDP trigger running in background for low-latency operation"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(isConnected: Boolean, targetHost: String, targetPort: Int): Notification {
        createNotificationChannel()

        // Intent to open app when notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to stop service
        val stopIntent = Intent(this, UdpForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (isConnected) "Connected to $targetHost:$targetPort" else "Disconnected"
        val statusColor = if (isConnected) 0xFF4CAF50.toInt() else 0xFFFFC107.toInt()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UDP Trigger Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification_send)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Cannot be swiped away
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_notification_stop,
                "Stop",
                stopPendingIntent
            )

        // Add color indicator for connection status
        builder.setColor(statusColor)

        return builder.build()
    }

    private fun startForeground(isConnected: Boolean, targetHost: String, targetPort: Int) {
        val notification = createNotification(isConnected, targetHost, targetPort)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) requires service type
            startForeground(
                notificationId,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(notificationId, notification)
        }
        isForeground = true
    }

    fun updateNotification(isConnected: Boolean, targetHost: String, targetPort: Int) {
        if (!isForeground) {
            startForeground(isConnected, targetHost, targetPort)
        } else {
            val notification = createNotification(isConnected, targetHost, targetPort)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(notificationId, notification)
        }
    }

    private fun stopForeground() {
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isForeground = false
        }
    }

    override fun onDestroy() {
        stopForeground()
        super.onDestroy()
    }
}
