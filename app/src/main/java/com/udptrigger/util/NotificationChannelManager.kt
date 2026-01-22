package com.udptrigger.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Notification Channel Manager for Android 8+ (API 26+).
 * Creates and manages notification channels for proper notification behavior.
 */
object NotificationChannelManager {

    const val CHANNEL_ID_FOREGROUND = "udp_trigger_foreground"
    const val CHANNEL_ID_PACKET_SENT = "udp_trigger_packet_sent"
    const val CHANNEL_ID_PACKET_RECEIVED = "udp_trigger_packet_received"
    const val CHANNEL_ID_ERRORS = "udp_trigger_errors"
    const val CHANNEL_ID_GENERAL = "udp_trigger_general"

    /**
     * Create all notification channels required by the app
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)

            // Foreground Service Channel (High importance)
            val foregroundChannel = NotificationChannel(
                CHANNEL_ID_FOREGROUND,
                context.getString(com.udptrigger.R.string.channel_foreground),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(com.udptrigger.R.string.channel_foreground_desc)
                setShowBadge(false)
            }

            // Packet Sent Channel (Low importance - just for logging)
            val sentChannel = NotificationChannel(
                CHANNEL_ID_PACKET_SENT,
                context.getString(com.udptrigger.R.string.channel_sent),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(com.udptrigger.R.string.channel_sent_desc)
                setShowBadge(false)
            }

            // Packet Received Channel (High importance for real-time monitoring)
            val receivedChannel = NotificationChannel(
                CHANNEL_ID_PACKET_RECEIVED,
                context.getString(com.udptrigger.R.string.channel_received),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(com.udptrigger.R.string.channel_received_desc)
                setShowBadge(true)
            }

            // Errors Channel (Highest importance - critical alerts)
            val errorChannel = NotificationChannel(
                CHANNEL_ID_ERRORS,
                context.getString(com.udptrigger.R.string.channel_errors),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(com.udptrigger.R.string.channel_errors_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            // General Channel (Default importance)
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                context.getString(com.udptrigger.R.string.channel_general),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(com.udptrigger.R.string.channel_general_desc)
            }

            notificationManager.createNotificationChannels(
                listOf(foregroundChannel, sentChannel, receivedChannel, errorChannel, generalChannel)
            )
        }
    }

    /**
     * Delete all notification channels (for testing or reset)
     */
    fun deleteChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.deleteNotificationChannel(CHANNEL_ID_FOREGROUND)
            notificationManager.deleteNotificationChannel(CHANNEL_ID_PACKET_SENT)
            notificationManager.deleteNotificationChannel(CHANNEL_ID_PACKET_RECEIVED)
            notificationManager.deleteNotificationChannel(CHANNEL_ID_ERRORS)
            notificationManager.deleteNotificationChannel(CHANNEL_ID_GENERAL)
        }
    }

    /**
     * Check if a channel exists
     */
    fun channelExists(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            return notificationManager.getNotificationChannel(channelId) != null
        }
        return false
    }
}
