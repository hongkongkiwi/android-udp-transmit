package com.udptrigger.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.udptrigger.MainActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Action types that can be triggered when a packet rule matches
 */
enum class PacketActionType {
    NOTIFICATION,
    VIBRATE,
    PLAY_SOUND,
    SEND_REPLY,
    RUN_INTENT
}

/**
 * Rule for triggering actions based on received UDP packet content
 */
@Serializable
data class PacketActionRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val matchPattern: String, // Text to match in packet content (supports contains)
    val useRegex: Boolean = false,
    val actionType: PacketActionType,
    val actionData: String = "", // Additional data based on action type:
                                 // - NOTIFICATION: message text
                                 // - SEND_REPLY: reply packet content
                                 // - RUN_INTENT: intent URI
    val replyHost: String = "", // For SEND_REPLY: target host (uses source if empty)
    val replyPort: Int = 0      // For SEND_REPLY: target port (uses source port if 0)
)

private val Context.packetRulesDataStore: DataStore<Preferences> by preferencesDataStore(name = "packet_rules")

/**
 * DataStore for managing packet action rules
 */
class PacketRulesDataStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val RULES_KEY = stringPreferencesKey("packet_rules")
    }

    /**
     * Flow of all packet action rules
     */
    val rulesFlow: Flow<List<PacketActionRule>> = context.packetRulesDataStore.data.map { preferences ->
        val rulesJson = preferences[RULES_KEY] ?: "[]"
        try {
            json.decodeFromString<List<PacketActionRule>>(rulesJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Add a new rule
     */
    suspend fun addRule(rule: PacketActionRule) {
        val currentRules = rulesFlow.first()
        val updatedRules = currentRules + rule
        saveRules(updatedRules)
    }

    /**
     * Update an existing rule
     */
    suspend fun updateRule(rule: PacketActionRule) {
        val currentRules = rulesFlow.first()
        val updatedRules = currentRules.map { if (it.id == rule.id) rule else it }
        saveRules(updatedRules)
    }

    /**
     * Delete a rule
     */
    suspend fun deleteRule(ruleId: String) {
        val currentRules = rulesFlow.first()
        val updatedRules = currentRules.filter { it.id != ruleId }
        saveRules(updatedRules)
    }

    /**
     * Toggle rule enabled state
     */
    suspend fun toggleRule(ruleId: String, enabled: Boolean) {
        val currentRules = rulesFlow.first()
        val updatedRules = currentRules.map { rule ->
            if (rule.id == ruleId) rule.copy(enabled = enabled) else rule
        }
        saveRules(updatedRules)
    }

    private suspend fun saveRules(rules: List<PacketActionRule>) {
        context.packetRulesDataStore.edit { preferences ->
            preferences[RULES_KEY] = json.encodeToString(rules)
        }
    }
}

/**
 * Notification helper for packet action notifications
 */
private const val NOTIFICATION_CHANNEL_ID = "packet_action_notifications"
private const val NOTIFICATION_ID_BASE = 10000

/**
 * Create notification channel for packet actions (required for Android O+)
 */
private fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Packet Actions",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows notifications when UDP packet rules match"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        notificationManager?.createNotificationChannel(channel)
    }
}

/**
 * Show a notification for a packet action
 */
private fun showPacketActionNotification(
    context: Context,
    ruleId: String,
    ruleName: String,
    message: String,
    packetContent: String,
    sourceAddress: String,
    sourcePort: Int
) {
    ensureNotificationChannel(context)

    val notificationId = NOTIFICATION_ID_BASE + ruleId.hashCode().mod(1000)

    // Intent to open app when notification is tapped
    val pendingIntent = PendingIntent.getActivity(
        context,
        notificationId,
        Intent(context, MainActivity::class.java).apply {
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setContentTitle("Packet Rule: $ruleName")
        .setContentText(message)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    try {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(notificationId, notification)
    } catch (e: Exception) {
        // Notification failed, log silently
    }
}

/**
 * Execute action when a packet rule matches
 */
suspend fun executePacketAction(
    context: Context,
    rule: PacketActionRule,
    packetContent: String,
    sourceAddress: String,
    sourcePort: Int
) {
    when (rule.actionType) {
        PacketActionType.NOTIFICATION -> {
            // Show actual Android notification
            val message = rule.actionData.ifEmpty { "Packet matched: ${rule.name}" }
            showPacketActionNotification(
                context = context,
                ruleId = rule.id,
                ruleName = rule.name,
                message = message,
                packetContent = packetContent,
                sourceAddress = sourceAddress,
                sourcePort = sourcePort
            )
        }

        PacketActionType.VIBRATE -> {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            vibrator?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    it.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(200)
                }
            }
        }

        PacketActionType.PLAY_SOUND -> {
            val intent = Intent("com.udptrigger.PACKET_ACTION_SOUND").apply {
                putExtra("rule_name", rule.name)
            }
            context.sendBroadcast(intent)
        }

        PacketActionType.SEND_REPLY -> {
            val replyData = rule.actionData.ifEmpty { "ACK" }
            val replyHost = rule.replyHost.ifEmpty { sourceAddress }
            val replyPort = if (rule.replyPort > 0) rule.replyPort else sourcePort

            // Actually send UDP reply packet
            try {
                val udpClient = com.udptrigger.domain.UdpClient()
                udpClient.initialize(replyHost, replyPort)
                val packetBytes = replyData.toByteArray(Charsets.UTF_8)
                val result = udpClient.send(packetBytes)
                udpClient.closeSync()

                // Also send broadcast for UI update
                val broadcastIntent = Intent("com.udptrigger.PACKET_SENT").apply {
                    putExtra("host", replyHost)
                    putExtra("port", replyPort)
                    putExtra("data", replyData)
                    putExtra("success", result.isSuccess)
                }
                context.sendBroadcast(broadcastIntent)
            } catch (e: Exception) {
                // Send failed
            }
        }

        PacketActionType.RUN_INTENT -> {
            try {
                val intent = Intent.parseUri(rule.actionData, Intent.URI_INTENT_SCHEME)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                // Invalid intent URI
            }
        }
    }
}
