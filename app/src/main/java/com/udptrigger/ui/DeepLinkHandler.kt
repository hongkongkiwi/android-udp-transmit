package com.udptrigger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Deep link handler for UDP Trigger URL schemes.
 *
 * Supported URL formats:
 * - udptrigger://send?host=192.168.1.100&port=5000&content=TRIGGER
 * - udptrigger://connect?host=192.168.1.100&port=5000
 * - udptrigger://trigger
 * - udptrigger://disconnect
 * - udptrigger://preset?name=Local
 * - udptrigger://settings
 * - udptrigger://broadcast?port=5000&content=PING
 */
class DeepLinkHandler(private val context: Context) {

    companion object {
        const val SCHEME = "udptrigger"
        const val ACTION_SEND = "send"
        const val ACTION_CONNECT = "connect"
        const val ACTION_DISCONNECT = "disconnect"
        const val ACTION_TRIGGER = "trigger"
        const val ACTION_PRESET = "preset"
        const val ACTION_SETTINGS = "settings"
        const val ACTION_BROADCAST = "broadcast"
        const val ACTION_HELP = "help"

        // Parameter keys
        const val PARAM_HOST = "host"
        const val PARAM_PORT = "port"
        const val PARAM_CONTENT = "content"
        const val PARAM_HEX = "hex"
        const val PARAM_TIMESTAMP = "timestamp"
        const val PARAM_NAME = "name"
        const val PARAM_COUNT = "count"
        const val PARAM_DELAY = "delay"
    }

    /**
     * Result of deep link processing
     */
    sealed class DeepLinkResult {
        data class Success(val action: String, val message: String) : DeepLinkResult()
        data class Error(val message: String, val errorCode: Int = -1) : DeepLinkResult()
        data object Ignored : DeepLinkResult()
    }

    /**
     * Process an incoming intent or URI
     */
    fun handleIntent(intent: Intent): DeepLinkResult {
        val uri = intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            try { Uri.parse(it) } catch (e: Exception) { null }
        } ?: return DeepLinkResult.Ignored

        return handleUri(uri)
    }

    /**
     * Process a URI directly
     */
    fun handleUri(uri: Uri): DeepLinkResult {
        // Check scheme
        if (uri.scheme != SCHEME && uri.scheme != "https" && uri.scheme != "http") {
            return DeepLinkResult.Ignored
        }

        // Handle https/http as web URLs (for documentation)
        if (uri.scheme == "https" || uri.scheme == "http") {
            if (uri.host == "udptrigger.app" || uri.host == "www.udptrigger.app") {
                return DeepLinkResult.Success("help", "Opening documentation")
            }
            return DeepLinkResult.Ignored
        }

        val host = uri.host ?: return DeepLinkResult.Error("Invalid URL: missing host", -1)
        val params = parseQueryParameters(uri)

        return when (host) {
            ACTION_SEND -> handleSend(params)
            ACTION_CONNECT -> handleConnect(params)
            ACTION_DISCONNECT -> handleDisconnect()
            ACTION_TRIGGER -> handleTrigger(params)
            ACTION_PRESET -> handlePreset(params)
            ACTION_SETTINGS -> handleSettings()
            ACTION_BROADCAST -> handleBroadcast(params)
            ACTION_HELP -> handleHelp()
            else -> DeepLinkResult.Error("Unknown action: $host", -1)
        }
    }

    private fun parseQueryParameters(uri: Uri): Map<String, String> {
        val params = mutableMapOf<String, String>()
        uri.queryParameterNames?.forEach { key ->
            uri.getQueryParameter(key)?.let { value ->
                params[key] = value
            }
        }
        return params
    }

    private fun handleSend(params: Map<String, String>): DeepLinkResult {
        val host = params[PARAM_HOST] ?: return DeepLinkResult.Error("Missing host parameter", 1)
        val port = params[PARAM_PORT]?.toIntOrNull() ?: return DeepLinkResult.Error("Missing or invalid port parameter", 2)
        val content = params[PARAM_CONTENT] ?: ""

        // Broadcast intent to main app
        val intent = Intent("com.udptrigger.DEEPLINK_SEND").apply {
            setPackage(context.packageName)
            putExtra(PARAM_HOST, host)
            putExtra(PARAM_PORT, port)
            putExtra(PARAM_CONTENT, content)
            params[PARAM_HEX]?.let { putExtra(PARAM_HEX, it.toBooleanStrictOrNull() ?: false) }
            params[PARAM_TIMESTAMP]?.let { putExtra(PARAM_TIMESTAMP, it.toBooleanStrictOrNull() ?: true) }
        }
        context.sendBroadcast(intent)

        return DeepLinkResult.Success(
            ACTION_SEND,
            "Sending packet to $host:$port"
        )
    }

    private fun handleConnect(params: Map<String, String>): DeepLinkResult {
        val host = params[PARAM_HOST] ?: return DeepLinkResult.Error("Missing host parameter", 1)
        val port = params[PARAM_PORT]?.toIntOrNull() ?: return DeepLinkResult.Error("Missing or invalid port parameter", 2)

        val intent = Intent("com.udptrigger.DEEPLINK_CONNECT").apply {
            setPackage(context.packageName)
            putExtra(PARAM_HOST, host)
            putExtra(PARAM_PORT, port)
        }
        context.sendBroadcast(intent)

        return DeepLinkResult.Success(
            ACTION_CONNECT,
            "Connecting to $host:$port"
        )
    }

    private fun handleDisconnect(): DeepLinkResult {
        val intent = Intent("com.udptrigger.DEEPLINK_DISCONNECT").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)

        return DeepLinkResult.Success(ACTION_DISCONNECT, "Disconnecting")
    }

    private fun handleTrigger(params: Map<String, String>): DeepLinkResult {
        val content = params[PARAM_CONTENT] ?: ""
        val count = params[PARAM_COUNT]?.toIntOrNull() ?: 1
        val delay = params[PARAM_DELAY]?.toLongOrNull() ?: 0L

        val intent = Intent("com.udptrigger.DEEPLINK_TRIGGER").apply {
            setPackage(context.packageName)
            putExtra(PARAM_CONTENT, content)
            putExtra(PARAM_COUNT, count)
            putExtra(PARAM_DELAY, delay)
        }
        context.sendBroadcast(intent)

        return DeepLinkResult.Success(
            ACTION_TRIGGER,
            if (count > 1) "Triggering burst of $count packets" else "Trigger sent"
        )
    }

    private fun handlePreset(params: Map<String, String>): DeepLinkResult {
        val name = params[PARAM_NAME] ?: return DeepLinkResult.Error("Missing preset name parameter", 1)

        val intent = Intent("com.udptrigger.DEEPLINK_PRESET").apply {
            setPackage(context.packageName)
            putExtra(PARAM_NAME, name)
        }
        context.sendBroadcast(intent)

        return DeepLinkResult.Success(ACTION_PRESET, "Loading preset: $name")
    }

    private fun handleSettings(): DeepLinkResult {
        val intent = Intent("com.udptrigger.DEEPLINK_SETTINGS").apply {
            setPackage(context.packageName)
            action = Intent.ACTION_VIEW
        }
        context.startActivity(intent)

        return DeepLinkResult.Success(ACTION_SETTINGS, "Opening settings")
    }

    private fun handleBroadcast(params: Map<String, String>): DeepLinkResult {
        val port = params[PARAM_PORT]?.toIntOrNull() ?: return DeepLinkResult.Error("Missing port parameter", 1)
        val content = params[PARAM_CONTENT] ?: ""

        val intent = Intent("com.udptrigger.DEEPLINK_BROADCAST").apply {
            setPackage(context.packageName)
            putExtra(PARAM_PORT, port)
            putExtra(PARAM_CONTENT, content)
        }
        context.sendBroadcast(intent)

        return DeepLinkResult.Success(
            ACTION_BROADCAST,
            "Broadcasting to port $port"
        )
    }

    private fun handleHelp(): DeepLinkResult {
        val intent = Intent("com.udptrigger.DEEPLINK_HELP").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)

        return DeepLinkResult.Success(ACTION_HELP, "Showing help")
    }

    /**
     * Build a trigger URL programmatically
     */
    fun buildTriggerUrl(
        host: String,
        port: Int,
        content: String,
        hexMode: Boolean = false,
        includeTimestamp: Boolean = true
    ): String {
        return buildString {
            append("$SCHEME://$ACTION_SEND?")
            append("$PARAM_HOST=$host&$PARAM_PORT=$port&$PARAM_CONTENT=${Uri.encode(content)}")
            append("&$PARAM_HEX=$hexMode&$PARAM_TIMESTAMP=$includeTimestamp")
        }
    }

    /**
     * Build a connect URL
     */
    fun buildConnectUrl(host: String, port: Int): String {
        return "$SCHEME://$ACTION_CONNECT?$PARAM_HOST=$host&$PARAM_PORT=$port"
    }

    /**
     * Build a preset URL
     */
    fun buildPresetUrl(presetName: String): String {
        return "$SCHEME://$ACTION_PRESET?$PARAM_NAME=${Uri.encode(presetName)}"
    }

    /**
     * Build a broadcast URL
     */
    fun buildBroadcastUrl(port: Int, content: String): String {
        return "$SCHEME://$ACTION_BROADCAST?$PARAM_PORT=$port&$PARAM_CONTENT=${Uri.encode(content)}"
    }

    /**
     * Parse a URL from a string
     */
    fun parseUrl(url: String): Uri? {
        return try {
            Uri.parse(url)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Validate a URL
     */
    fun validateUrl(url: String): DeepLinkResult {
        val uri = parseUrl(url) ?: return DeepLinkResult.Error("Invalid URL format", -1)
        return handleUri(uri)
    }
}

/**
 * Broadcast receiver for deep link intents
 */
class DeepLinkReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.udptrigger.DEEPLINK_SEND" -> {
                val host = intent.getStringExtra(DeepLinkHandler.PARAM_HOST) ?: return
                val port = intent.getIntExtra(DeepLinkHandler.PARAM_PORT, 0)
                val content = intent.getStringExtra(DeepLinkHandler.PARAM_CONTENT) ?: ""
                val hexMode = intent.getBooleanExtra(DeepLinkHandler.PARAM_HEX, false)
                val includeTimestamp = intent.getBooleanExtra(DeepLinkHandler.PARAM_TIMESTAMP, true)

                // Forward to main app's ViewModel via LocalBroadcastManager
                val localIntent = Intent("com.udptrigger.LOCAL_SEND_UDP").apply {
                    setPackage(context.packageName)
                    putExtra(DeepLinkHandler.PARAM_HOST, host)
                    putExtra(DeepLinkHandler.PARAM_PORT, port)
                    putExtra(DeepLinkHandler.PARAM_CONTENT, content)
                    putExtra(DeepLinkHandler.PARAM_HEX, hexMode)
                    putExtra(DeepLinkHandler.PARAM_TIMESTAMP, includeTimestamp)
                }
                context.sendBroadcast(localIntent)
            }

            "com.udptrigger.DEEPLINK_CONNECT" -> {
                val host = intent.getStringExtra(DeepLinkHandler.PARAM_HOST) ?: return
                val port = intent.getIntExtra(DeepLinkHandler.PARAM_PORT, 0)

                val localIntent = Intent("com.udptrigger.LOCAL_CONNECT").apply {
                    setPackage(context.packageName)
                    putExtra(DeepLinkHandler.PARAM_HOST, host)
                    putExtra(DeepLinkHandler.PARAM_PORT, port)
                }
                context.sendBroadcast(localIntent)
            }

            "com.udptrigger.DEEPLINK_DISCONNECT" -> {
                val localIntent = Intent("com.udptrigger.LOCAL_DISCONNECT").apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(localIntent)
            }

            "com.udptrigger.DEEPLINK_TRIGGER" -> {
                val content = intent.getStringExtra(DeepLinkHandler.PARAM_CONTENT) ?: ""
                val count = intent.getIntExtra(DeepLinkHandler.PARAM_COUNT, 1)
                val delay = intent.getLongExtra(DeepLinkHandler.PARAM_DELAY, 0L)

                val localIntent = Intent("com.udptrigger.LOCAL_TRIGGER").apply {
                    setPackage(context.packageName)
                    putExtra(DeepLinkHandler.PARAM_CONTENT, content)
                    putExtra(DeepLinkHandler.PARAM_COUNT, count)
                    putExtra(DeepLinkHandler.PARAM_DELAY, delay)
                }
                context.sendBroadcast(localIntent)
            }

            "com.udptrigger.DEEPLINK_PRESET" -> {
                val name = intent.getStringExtra(DeepLinkHandler.PARAM_NAME) ?: return

                val localIntent = Intent("com.udptrigger.LOCAL_PRESET").apply {
                    setPackage(context.packageName)
                    putExtra(DeepLinkHandler.PARAM_NAME, name)
                }
                context.sendBroadcast(localIntent)
            }
        }
    }
}
