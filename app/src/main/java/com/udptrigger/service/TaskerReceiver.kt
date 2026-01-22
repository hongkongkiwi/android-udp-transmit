package com.udptrigger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Tasker/Locale plugin receiver for UDP Trigger.
 * Allows Tasker to send UDP packets and receive results.
 */
class TaskerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskerReceiver"

        // Tasker intent actions
        const val ACTION_SEND_UDP = "com.udptrigger.TASKER_SEND_UDP"
        const val ACTION_CONNECT = "com.udptrigger.TASKER_CONNECT"
        const val ACTION_DISCONNECT = "com.udptrigger.TASKER_DISCONNECT"
        const val ACTION_TRIGGER = "com.udptrigger.TASKER_TRIGGER"
        const val ACTION_GET_STATUS = "com.udptrigger.TASKER_GET_STATUS"

        // Tasker intent extras
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_HEX_MODE = "hex_mode"
        const val EXTRA_INCLUDE_TIMESTAMP = "include_timestamp"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_MESSAGE = "result_message"
        const val EXTRA_IS_CONNECTED = "is_connected"
        const val EXTRA_LAST_LATENCY = "last_latency"
        const val EXTRA_PACKET_COUNT = "packet_count"
        const val EXTRA_ERROR = "error"

        // Locale plugin requirements
        const val EXTRA_MESSAGE = "com.twofortyfouram.locale.intent.extra.MESSAGE"
        const val EXTRA_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"

        // Local broadcast actions (for internal app communication)
        const val LOCAL_ACTION_SEND_UDP = "com.udptrigger.LOCAL_SEND_UDP"
        const val LOCAL_ACTION_CONNECT = "com.udptrigger.LOCAL_CONNECT"
        const val LOCAL_ACTION_DISCONNECT = "com.udptrigger.LOCAL_DISCONNECT"
        const val LOCAL_ACTION_TRIGGER = "com.udptrigger.LOCAL_TRIGGER"

        // Result codes
        const val RESULT_SUCCESS = 0
        const val RESULT_FAILURE = 1
        const val RESULT_NOT_CONNECTED = 2
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        Log.d(TAG, "Tasker intent received: $action")

        when (action) {
            ACTION_SEND_UDP -> handleSendUdp(context, intent)
            ACTION_CONNECT -> handleConnect(context, intent)
            ACTION_DISCONNECT -> handleDisconnect(context, intent)
            ACTION_TRIGGER -> handleTrigger(context, intent)
            ACTION_GET_STATUS -> handleGetStatus(context, intent)
        }
    }

    private fun handleSendUdp(context: Context, intent: Intent) {
        val host = intent.getStringExtra(EXTRA_HOST)
        val port = intent.getIntExtra(EXTRA_PORT, 0)
        val content = intent.getStringExtra(EXTRA_CONTENT) ?: ""
        val hexMode = intent.getBooleanExtra(EXTRA_HEX_MODE, false)
        val includeTimestamp = intent.getBooleanExtra(EXTRA_INCLUDE_TIMESTAMP, true)

        if (host.isNullOrBlank() || port <= 0) {
            setResult(RESULT_FAILURE, "Invalid host or port", false)
            return
        }

        // Forward to local broadcast for main app to handle
        val localIntent = Intent(LOCAL_ACTION_SEND_UDP).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_HOST, host)
            putExtra(EXTRA_PORT, port)
            putExtra(EXTRA_CONTENT, content)
            putExtra(EXTRA_HEX_MODE, hexMode)
            putExtra(EXTRA_INCLUDE_TIMESTAMP, includeTimestamp)
        }

        context.sendBroadcast(localIntent)
        setResult(RESULT_SUCCESS, "UDP packet queued", true)
    }

    private fun handleConnect(context: Context, intent: Intent) {
        val host = intent.getStringExtra(EXTRA_HOST)
        val port = intent.getIntExtra(EXTRA_PORT, 0)

        if (host.isNullOrBlank() || port <= 0) {
            setResult(RESULT_FAILURE, "Invalid host or port", false)
            return
        }

        val localIntent = Intent(LOCAL_ACTION_CONNECT).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_HOST, host)
            putExtra(EXTRA_PORT, port)
        }

        context.sendBroadcast(localIntent)
        setResult(RESULT_SUCCESS, "Connection initiated", true)
    }

    private fun handleDisconnect(context: Context, intent: Intent) {
        val localIntent = Intent(LOCAL_ACTION_DISCONNECT).apply {
            setPackage(context.packageName)
        }

        context.sendBroadcast(localIntent)
        setResult(RESULT_SUCCESS, "Disconnected", true)
    }

    private fun handleTrigger(context: Context, intent: Intent) {
        val content = intent.getStringExtra(EXTRA_CONTENT) ?: ""
        val hexMode = intent.getBooleanExtra(EXTRA_HEX_MODE, false)
        val includeTimestamp = intent.getBooleanExtra(EXTRA_INCLUDE_TIMESTAMP, true)

        val localIntent = Intent(LOCAL_ACTION_TRIGGER).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_CONTENT, content)
            putExtra(EXTRA_HEX_MODE, hexMode)
            putExtra(EXTRA_INCLUDE_TIMESTAMP, includeTimestamp)
        }

        context.sendBroadcast(localIntent)
        setResult(RESULT_SUCCESS, "Trigger sent", true)
    }

    private fun handleGetStatus(context: Context, intent: Intent) {
        // Return current connection status
        setResultExtras(Bundle().apply {
            putInt(EXTRA_RESULT_CODE, RESULT_SUCCESS)
            putString(EXTRA_RESULT_MESSAGE, "Status retrieved")
            putBoolean(EXTRA_IS_CONNECTED, false) // Main app will update
            putLong(EXTRA_LAST_LATENCY, 0)
            putInt(EXTRA_PACKET_COUNT, 0)
        })

        setResult(RESULT_SUCCESS, "Status: Not connected", false)
    }

    private fun setResult(code: Int, message: String, isConnected: Boolean) {
        val extras = Bundle().apply {
            putInt(EXTRA_RESULT_CODE, code)
            putString(EXTRA_RESULT_MESSAGE, message)
            putBoolean(EXTRA_IS_CONNECTED, isConnected)
            putString(EXTRA_BLURB, when (code) {
                RESULT_SUCCESS -> "Success: $message"
                RESULT_FAILURE -> "Failed: $message"
                RESULT_NOT_CONNECTED -> "Not connected"
                else -> message
            })
        }

        setResultExtras(extras)
    }
}

/**
 * Helper class for Tasker plugin integration
 */
object TaskerHelper {

    /**
     * Build intent to send UDP packet from Tasker
     */
    fun buildSendUdpIntent(
        context: Context,
        host: String,
        port: Int,
        content: String,
        hexMode: Boolean = false,
        includeTimestamp: Boolean = true
    ): Intent {
        return Intent(TaskerReceiver.ACTION_SEND_UDP).apply {
            setPackage(context.packageName)
            putExtra(TaskerReceiver.EXTRA_HOST, host)
            putExtra(TaskerReceiver.EXTRA_PORT, port)
            putExtra(TaskerReceiver.EXTRA_CONTENT, content)
            putExtra(TaskerReceiver.EXTRA_HEX_MODE, hexMode)
            putExtra(TaskerReceiver.EXTRA_INCLUDE_TIMESTAMP, includeTimestamp)
        }
    }

    /**
     * Build intent to connect from Tasker
     */
    fun buildConnectIntent(context: Context, host: String, port: Int): Intent {
        return Intent(TaskerReceiver.ACTION_CONNECT).apply {
            setPackage(context.packageName)
            putExtra(TaskerReceiver.EXTRA_HOST, host)
            putExtra(TaskerReceiver.EXTRA_PORT, port)
        }
    }

    /**
     * Build intent to disconnect from Tasker
     */
    fun buildDisconnectIntent(context: Context): Intent {
        return Intent(TaskerReceiver.ACTION_DISCONNECT).apply {
            setPackage(context.packageName)
        }
    }

    /**
     * Build intent to trigger from Tasker
     */
    fun buildTriggerIntent(
        context: Context,
        content: String = "",
        hexMode: Boolean = false,
        includeTimestamp: Boolean = true
    ): Intent {
        return Intent(TaskerReceiver.ACTION_TRIGGER).apply {
            setPackage(context.packageName)
            putExtra(TaskerReceiver.EXTRA_CONTENT, content)
            putExtra(TaskerReceiver.EXTRA_HEX_MODE, hexMode)
            putExtra(TaskerReceiver.EXTRA_INCLUDE_TIMESTAMP, includeTimestamp)
        }
    }

    /**
     * Build locale plugin setting for Tasker
     */
    fun buildLocaleSetting(name: String, host: String, port: Int, content: String): String {
        return """
            UDP Trigger: $name
            Host: $host
            Port: $port
            Content: ${content.take(50)}${if (content.length > 50) "..." else ""}
        """.trimIndent()
    }

    /**
     * Parse locale plugin setting
     */
    fun parseLocaleSetting(setting: String): LocaleSetting? {
        return try {
            val lines = setting.split("\n")
            var host: String? = null
            var port: Int? = null
            var content: String? = null

            for (line in lines) {
                when {
                    line.startsWith("Host:") -> host = line.substringAfter("Host:").trim()
                    line.startsWith("Port:") -> port = line.substringAfter("Port:").trim().toIntOrNull()
                    line.startsWith("Content:") -> content = line.substringAfter("Content:").trim()
                }
            }

            if (host != null && port != null && content != null) {
                LocaleSetting(host, port, content)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    data class LocaleSetting(
        val host: String,
        val port: Int,
        val content: String
    )
}

/**
 * Locale plugin activity for Tasker integration
 * Allows users to configure UDP Trigger from Tasker's locale settings
 */
class TaskerLocaleActivity : android.app.Activity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        val setting = intent.getStringExtra(TaskerReceiver.EXTRA_MESSAGE) ?: ""

        // Parse setting and show configuration UI
        val parsed = TaskerHelper.parseLocaleSetting(setting)

        if (parsed != null) {
            // Already configured, just show confirmation
            setResult(RESULT_OK, intent.putExtra(TaskerReceiver.EXTRA_BLURB,
                TaskerHelper.buildLocaleSetting("UDP", parsed.host, parsed.port, parsed.content)))
        } else {
            // Show configuration dialog
            showConfigurationDialog()
        }

        finish()
    }

    private fun showConfigurationDialog() {
        // For simplicity, use default values or show a dialog
        // In production, this would be a full configuration UI
        val defaultHost = "192.168.1.100"
        val defaultPort = 5000
        val defaultContent = "TRIGGER"

        val blurb = TaskerHelper.buildLocaleSetting("Default", defaultHost, defaultPort, defaultContent)

        setResult(RESULT_OK, intent.putExtra(TaskerReceiver.EXTRA_MESSAGE,
            "Host:$defaultHost\nPort:$defaultPort\nContent:$defaultContent")
            .putExtra(TaskerReceiver.EXTRA_BLURB, blurb))
    }
}
