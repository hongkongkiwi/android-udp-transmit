package com.udptrigger.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.udptrigger.data.SettingsDataStore
import com.udptrigger.ui.UdpConfig
import com.udptrigger.ui.TriggerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Broadcast receiver for automation integration (Tasker, MacroDroid, etc.)
 * Allows external apps to trigger UDP packets and control connection state.
 *
 * Intent Actions:
 * - com.udptrigger.SEND_PACKET: Send a UDP packet
 *   Extras:
 *   - "host" (String): Target host (optional, uses saved config if not provided)
 *   - "port" (int): Target port (optional, uses saved config if not provided)
 *   - "data" (String): Packet content (optional, uses saved config if not provided)
 *   - "hex" (boolean): Hex mode (optional, default false)
 *
 * - com.udptrigger.CONNECT: Connect to saved UDP target
 *
 * - com.udptrigger.DISCONNECT: Disconnect from current target
 *
 * - com.udptrigger.GET_CONNECTION_STATUS: Request connection status
 *   Returns broadcast with:
 *   - "is_connected" (boolean): Connection status
 *   - "host" (String): Connected host
 *   - "port" (int): Connected port
 */
class UdpIntentReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SEND_PACKET -> handleSendPacket(context, intent)
            ACTION_CONNECT -> handleConnect(context)
            ACTION_DISCONNECT -> handleDisconnect(context)
            ACTION_GET_STATUS -> handleGetStatus(context)
        }
    }

    private fun handleSendPacket(context: Context, intent: Intent) {
        receiverScope.launch {
            try {
                val dataStore = SettingsDataStore(context)

                // Get config from intent extras or use saved config
                val savedConfig = dataStore.configFlow.first()

                val host = intent.getStringExtra(EXTRA_HOST) ?: savedConfig.host
                val port = intent.getIntExtra(EXTRA_PORT, savedConfig.port)
                val data = intent.getStringExtra(EXTRA_DATA) ?: savedConfig.packetContent
                val hexMode = intent.getBooleanExtra(EXTRA_HEX, savedConfig.hexMode)

                val config = UdpConfig(
                    host = host,
                    port = port,
                    packetContent = data,
                    hexMode = hexMode,
                    includeTimestamp = savedConfig.includeTimestamp,
                    includeBurstIndex = savedConfig.includeBurstIndex
                )

                // Create a temporary UDP client to send the packet
                val udpClient = com.udptrigger.domain.UdpClient()

                // Initialize the client
                udpClient.initialize(config.host, config.port)

                // Build packet content
                val packetContent = buildPacketContent(config, 0)

                // Convert to bytes (handle hex mode)
                val packetBytes = if (config.hexMode) {
                    packetContent.toByteArray(Charsets.ISO_8859_1)
                } else {
                    packetContent.toByteArray(Charsets.UTF_8)
                }

                // Send the packet
                val result = udpClient.send(packetBytes)

                // Close the client
                udpClient.closeSync()

                // Broadcast result
                val resultIntent = Intent(ACTION_PACKET_RESULT).apply {
                    putExtra(EXTRA_SUCCESS, result.isSuccess)
                    if (result.isFailure) {
                        putExtra(EXTRA_ERROR, result.exceptionOrNull()?.message)
                    }
                    putExtra(EXTRA_HOST, host)
                    putExtra(EXTRA_PORT, port)
                    putExtra(EXTRA_DATA_SENT, packetContent)
                }
                context.sendBroadcast(resultIntent)

            } catch (e: Exception) {
                // Send error result
                val resultIntent = Intent(ACTION_PACKET_RESULT).apply {
                    putExtra(EXTRA_SUCCESS, false)
                    putExtra(EXTRA_ERROR, e.message)
                }
                context.sendBroadcast(resultIntent)
            }
        }
    }

    private fun handleConnect(context: Context) {
        // Launch MainActivity with connect action
        val launchIntent = Intent(context, com.udptrigger.MainActivity::class.java).apply {
            action = ACTION_CONNECT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_AUTO_CONNECT, true)
        }
        context.startActivity(launchIntent)
    }

    private fun handleDisconnect(context: Context) {
        // Launch MainActivity with disconnect action
        val launchIntent = Intent(context, com.udptrigger.MainActivity::class.java).apply {
            action = ACTION_DISCONNECT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_AUTO_DISCONNECT, true)
        }
        context.startActivity(launchIntent)
    }

    private fun handleGetStatus(context: Context) {
        receiverScope.launch {
            try {
                val dataStore = SettingsDataStore(context)
                val lastConnection = dataStore.lastConnectionFlow.first()

                val statusIntent = Intent(ACTION_STATUS_RESULT).apply {
                    if (lastConnection != null) {
                        putExtra(EXTRA_IS_CONNECTED, true)
                        putExtra(EXTRA_HOST, lastConnection.host)
                        putExtra(EXTRA_PORT, lastConnection.port)
                    } else {
                        putExtra(EXTRA_IS_CONNECTED, false)
                    }
                }
                context.sendBroadcast(statusIntent)
            } catch (e: Exception) {
                val statusIntent = Intent(ACTION_STATUS_RESULT).apply {
                    putExtra(EXTRA_IS_CONNECTED, false)
                    putExtra(EXTRA_ERROR, e.message)
                }
                context.sendBroadcast(statusIntent)
            }
        }
    }

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

    companion object {
        const val ACTION_SEND_PACKET = "com.udptrigger.SEND_PACKET"
        const val ACTION_CONNECT = "com.udptrigger.CONNECT"
        const val ACTION_DISCONNECT = "com.udptrigger.DISCONNECT"
        const val ACTION_GET_STATUS = "com.udptrigger.GET_CONNECTION_STATUS"

        const val ACTION_PACKET_RESULT = "com.udptrigger.PACKET_RESULT"
        const val ACTION_STATUS_RESULT = "com.udptrigger.STATUS_RESULT"

        // Extras for SEND_PACKET
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_DATA = "data"
        const val EXTRA_HEX = "hex"

        // Extras for results
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_ERROR = "error"
        const val EXTRA_DATA_SENT = "data_sent"
        const val EXTRA_IS_CONNECTED = "is_connected"

        // Extras for CONNECT/DISCONNECT
        const val EXTRA_AUTO_CONNECT = "auto_connect"
        const val EXTRA_AUTO_DISCONNECT = "auto_disconnect"
    }
}
