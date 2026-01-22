package com.udptrigger.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * ADB Shell command support for UDP Trigger.
 * Provides shell commands for testing, triggering, and debugging.
 *
 * Usage:
 * adb shell am broadcast -a com.udptrigger.ADB_SEND --es host 192.168.1.100 --ei port 5000 --es content TRIGGER
 * adb shell am start -a com.udptrigger.ADB_TRIGGER
 * adb shell am broadcast -a com.udptrigger.ADB_CONNECT --es host 192.168.1.100 --ei port 5000
 */
object AdbCommands {

    const val ACTION_SEND = "com.udptrigger.ADB_SEND"
    const val ACTION_CONNECT = "com.udptrigger.ADB_CONNECT"
    const val ACTION_DISCONNECT = "com.udptrigger.ADB_DISCONNECT"
    const val ACTION_TRIGGER = "com.udptrigger.ADB_TRIGGER"
    const val ACTION_STATUS = "com.udptrigger.ADB_STATUS"
    const val ACTION_PRESET = "com.udptrigger.ADB_PRESET"
    const val ACTION_BROADCAST = "com.udptrigger.ADB_BROADCAST"
    const val ACTION_HELP = "com.udptrigger.ADB_HELP"

    // Extras
    const val EXTRA_HOST = "host"
    const val EXTRA_PORT = "port"
    const val EXTRA_CONTENT = "content"
    const val EXTRA_HEX = "hex"
    const val EXTRA_PRESET = "preset"
    const val EXTRA_COUNT = "count"
    const val EXTRA_DELAY = "delay"
    const val EXTRA_FORMAT = "format"

    /**
     * Build ADB shell command to send UDP packet
     */
    fun buildSendCommand(
        host: String,
        port: Int,
        content: String,
        hex: Boolean = false
    ): String {
        return buildString {
            append("adb shell am broadcast -a $ACTION_SEND ")
            append("--es $EXTRA_HOST '$host' ")
            append("--ei $EXTRA_PORT $port ")
            append("--es $EXTRA_CONTENT '$content' ")
            append("--ez $EXTRA_HEX $hex")
        }
    }

    /**
     * Build ADB shell command to connect
     */
    fun buildConnectCommand(host: String, port: Int): String {
        return "adb shell am broadcast -a $ACTION_CONNECT --es $EXTRA_HOST '$host' --ei $EXTRA_PORT $port"
    }

    /**
     * Build ADB shell command to trigger
     */
    fun buildTriggerCommand(content: String = "", count: Int = 1): String {
        return buildString {
            append("adb shell am broadcast -a $ACTION_TRIGGER")
            if (content.isNotEmpty()) {
                append(" --es $EXTRA_CONTENT '$content'")
            }
            if (count > 1) {
                append(" --ei $EXTRA_COUNT $count")
            }
        }
    }

    /**
     * Build ADB shell command to get status
     */
    fun buildStatusCommand(format: String = "json"): String {
        return "adb shell am broadcast -a $ACTION_STATUS --es $EXTRA_FORMAT $format"
    }

    /**
     * Build ADB shell command to load preset
     */
    fun buildPresetCommand(presetName: String): String {
        return "adb shell am broadcast -a $ACTION_PRESET --es $EXTRA_PRESET '$presetName'"
    }

    /**
     * Build ADB shell command to broadcast
     */
    fun buildBroadcastCommand(port: Int, content: String): String {
        return "adb shell am broadcast -a $ACTION_BROADCAST --ei $EXTRA_PORT $port --es $EXTRA_CONTENT '$content'"
    }

    /**
     * Build ADB shell command to start app
     */
    fun buildStartCommand(): String {
        return "adb shell am start -n com.udptrigger/.MainActivity"
    }

    /**
     * Build ADB shell command to force stop
     */
    fun buildStopCommand(): String {
        return "adb shell am force-stop com.udptrigger"
    }

    /**
     * Get all ADB commands as help text
     */
    fun getHelpText(): String {
        return """
            |UDP Trigger ADB Commands
            |
            |Send UDP Packet:
            |  ${buildSendCommand("192.168.1.100", 5000, "TRIGGER")}
            |
            |Connect to Host:
            |  ${buildConnectCommand("192.168.1.100", 5000)}
            |
            |Disconnect:
            |  adb shell am broadcast -a $ACTION_DISCONNECT
            |
            |Trigger (send packet with current config):
            |  ${buildTriggerCommand()}
            |
            |Trigger with custom content:
            |  ${buildTriggerCommand("CUSTOM_TRIGGER")}
            |
            |Burst trigger (3 packets):
            |  ${buildTriggerCommand("TRIGGER", 3)}
            |
            |Get Status (JSON):
            |  ${buildStatusCommand("json")}
            |
            |Get Status (text):
            |  ${buildStatusCommand("text")}
            |
            |Load Preset:
            |  ${buildPresetCommand("Local")}
            |
            |Broadcast Packet:
            |  ${buildBroadcastCommand(5000, "PING")}
            |
            |Start App:
            |  ${buildStartCommand()}
            |
            |Stop App:
            |  ${buildStopCommand()}
            |
            |Help:
            |  adb shell am broadcast -a $ACTION_HELP
        """.trimMargin()
    }
}

/**
 * ADB Command receiver - processes ADB shell broadcast intents
 */
class AdbCommandReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AdbCommands.ACTION_SEND -> handleSend(context, intent)
            AdbCommands.ACTION_CONNECT -> handleConnect(context, intent)
            AdbCommands.ACTION_DISCONNECT -> handleDisconnect(context, intent)
            AdbCommands.ACTION_TRIGGER -> handleTrigger(context, intent)
            AdbCommands.ACTION_STATUS -> handleStatus(context, intent)
            AdbCommands.ACTION_PRESET -> handlePreset(context, intent)
            AdbCommands.ACTION_BROADCAST -> handleBroadcast(context, intent)
            AdbCommands.ACTION_HELP -> handleHelp(context, intent)
        }
    }

    private fun handleSend(context: Context, intent: Intent) {
        val host = intent.getStringExtra(AdbCommands.EXTRA_HOST) ?: return
        val port = intent.getIntExtra(AdbCommands.EXTRA_PORT, 0)
        val content = intent.getStringExtra(AdbCommands.EXTRA_CONTENT) ?: ""
        val hexMode = intent.getBooleanExtra(AdbCommands.EXTRA_HEX, false)

        if (port <= 0) {
            logResult("Error: Invalid port")
            return
        }

        val localIntent = Intent("com.udptrigger.LOCAL_SEND_UDP").apply {
            setPackage(context.packageName)
            putExtra(AdbCommands.EXTRA_HOST, host)
            putExtra(AdbCommands.EXTRA_PORT, port)
            putExtra(AdbCommands.EXTRA_CONTENT, content)
            putExtra(AdbCommands.EXTRA_HEX, hexMode)
        }
        context.sendBroadcast(localIntent)

        logResult("Sending to $host:$port: $content")
    }

    private fun handleConnect(context: Context, intent: Intent) {
        val host = intent.getStringExtra(AdbCommands.EXTRA_HOST) ?: return
        val port = intent.getIntExtra(AdbCommands.EXTRA_PORT, 0)

        if (port <= 0) {
            logResult("Error: Invalid port")
            return
        }

        val localIntent = Intent("com.udptrigger.LOCAL_CONNECT").apply {
            setPackage(context.packageName)
            putExtra(AdbCommands.EXTRA_HOST, host)
            putExtra(AdbCommands.EXTRA_PORT, port)
        }
        context.sendBroadcast(localIntent)

        logResult("Connecting to $host:$port")
    }

    private fun handleDisconnect(context: Context, intent: Intent) {
        val localIntent = Intent("com.udptrigger.LOCAL_DISCONNECT").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(localIntent)

        logResult("Disconnected")
    }

    private fun handleTrigger(context: Context, intent: Intent) {
        val content = intent.getStringExtra(AdbCommands.EXTRA_CONTENT) ?: ""
        val count = intent.getIntExtra(AdbCommands.EXTRA_COUNT, 1)

        val localIntent = Intent("com.udptrigger.LOCAL_TRIGGER").apply {
            setPackage(context.packageName)
            putExtra(AdbCommands.EXTRA_CONTENT, content)
            putExtra(AdbCommands.EXTRA_COUNT, count)
        }
        context.sendBroadcast(localIntent)

        logResult(if (count > 1) "Triggered $count packets" else "Trigger sent")
    }

    private fun handleStatus(context: Context, intent: Intent) {
        val format = intent.getStringExtra(AdbCommands.EXTRA_FORMAT) ?: "text"

        val localIntent = Intent("com.udptrigger.LOCAL_STATUS").apply {
            setPackage(context.packageName)
            putExtra(AdbCommands.EXTRA_FORMAT, format)
        }
        context.sendBroadcast(localIntent)
    }

    private fun handlePreset(context: Context, intent: Intent) {
        val preset = intent.getStringExtra(AdbCommands.EXTRA_PRESET) ?: return

        val localIntent = Intent("com.udptrigger.LOCAL_PRESET").apply {
            setPackage(context.packageName)
            putExtra(AdbCommands.EXTRA_PRESET, preset)
        }
        context.sendBroadcast(localIntent)

        logResult("Loading preset: $preset")
    }

    private fun handleBroadcast(context: Context, intent: Intent) {
        val port = intent.getIntExtra(AdbCommands.EXTRA_PORT, 0)
        val content = intent.getStringExtra(AdbCommands.EXTRA_CONTENT) ?: ""

        if (port <= 0) {
            logResult("Error: Invalid port")
            return
        }

        val localIntent = Intent("com.udptrigger.LOCAL_BROADCAST").apply {
            setPackage(context.packageName)
            putExtra(AdbCommands.EXTRA_PORT, port)
            putExtra(AdbCommands.EXTRA_CONTENT, content)
        }
        context.sendBroadcast(localIntent)

        logResult("Broadcasting to port $port")
    }

    private fun handleHelp(context: Context, intent: Intent) {
        logResult(AdbCommands.getHelpText())
    }

    private fun logResult(message: String) {
        android.util.Log.d("ADBCommand", message)
    }
}

/**
 * Utility to check if running in ADB shell
 */
object AdbUtils {

    /**
     * Check if the app was launched via ADB
     */
    fun isLaunchedViaAdb(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val processes = activityManager.runningAppProcesses
            val myPid = android.os.Process.myPid()

            for (process in processes) {
                if (process.pid == myPid) {
                    return process.processName?.endsWith(":adb") == true ||
                            android.os.Build.TYPE == "eng" ||
                            android.os.Build.TYPE == "userdebug"
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get device info useful for debugging
     */
    fun getDeviceInfo(context: Context): Map<String, Any> {
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) { null }

        return mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "device" to Build.DEVICE,
            "android_version" to Build.VERSION.RELEASE,
            "sdk_version" to Build.VERSION.SDK_INT,
            "build_type" to Build.TYPE,
            "app_version" to (packageInfo?.versionName ?: "unknown")
        )
    }

    /**
     * Check if app has required permissions
     */
    fun checkPermissions(context: Context): Map<String, Boolean> {
        val permissions = listOf(
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.VIBRATE,
            android.Manifest.permission.FOREGROUND_SERVICE
        )

        return permissions.associateWith { permission ->
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}

/**
 * Response builder for ADB status commands
 */
data class StatusResponse(
    val isConnected: Boolean,
    val host: String,
    val port: Int,
    val packetsSent: Long,
    val packetsReceived: Long,
    val packetsFailed: Long,
    val connectionHealth: String,
    val lastPacketTime: Long?,
    val currentTime: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return """
            |{
            |  "connected": $isConnected,
            |  "host": "$host",
            |  "port": $port,
            |  "packets_sent": $packetsSent,
            |  "packets_received": $packetsReceived,
            |  "packets_failed": $packetsFailed,
            |  "connection_health": "$connectionHealth",
            |  "last_packet_time": ${lastPacketTime ?: "null"},
            |  "current_time": $currentTime
            |}
        """.trimMargin()
    }

    fun toText(): String {
        return buildString {
            appendLine("UDP Trigger Status")
            appendLine("=================")
            appendLine("Connection: ${if (isConnected) "Connected to $host:$port" else "Disconnected"}")
            appendLine("Packets: Sent=$packetsSent, Received=$packetsReceived, Failed=$packetsFailed")
            appendLine("Health: $connectionHealth")
            lastPacketTime?.let {
                val secondsAgo = (currentTime - it) / 1000
                appendLine("Last packet: ${secondsAgo}s ago")
            }
        }
    }
}
