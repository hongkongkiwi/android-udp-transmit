package com.udptrigger.util

import android.content.Intent
import android.net.Uri
import com.udptrigger.ui.UdpConfig

/**
 * Deep Link Manager for handling udptrigger:// URLs.
 * Allows sharing and importing UDP configurations via URL links.
 */
object DeepLinkManager {

    const val SCHEME = "udptrigger"

    /**
     * Supported deep link actions
     */
    enum class Action {
        SEND,      // udptrigger://send?host=...&port=...&content=...
        CONNECT,   // udptrigger://connect?host=...&port=...
        DISCONNECT,// udptrigger://disconnect
        TRIGGER,   // udptrigger://trigger
        PRESET,    // udptrigger://preset?name=...
        CONFIG,    // udptrigger://config?host=...&port=... (import full config)
        UNKNOWN
    }

    /**
     * Parsed deep link data
     */
    data class DeepLinkResult(
        val action: Action,
        val config: UdpConfig?,
        val presetName: String?,
        val error: String?
    ) {
        val isValid: Boolean get() = error == null && (action != Action.UNKNOWN)
    }

    /**
     * Parse a deep link URI
     */
    fun parseUri(uri: Uri): DeepLinkResult {
        return try {
            val action = when (uri.host?.lowercase()) {
                "send" -> Action.SEND
                "connect" -> Action.CONNECT
                "disconnect" -> Action.DISCONNECT
                "trigger" -> Action.TRIGGER
                "preset" -> Action.PRESET
                "config" -> Action.CONFIG
                else -> Action.UNKNOWN
            }

            if (action == Action.UNKNOWN) {
                return DeepLinkResult(Action.UNKNOWN, null, null, "Unknown action: ${uri.host}")
            }

            when (action) {
                Action.SEND, Action.CONNECT -> parseSendOrConnect(uri)
                Action.PRESET -> parsePreset(uri)
                Action.CONFIG -> parseConfig(uri)
                Action.TRIGGER, Action.DISCONNECT -> DeepLinkResult(action, null, null, null)
                Action.UNKNOWN -> DeepLinkResult(Action.UNKNOWN, null, null, "Unknown action")
            }
        } catch (e: Exception) {
            DeepLinkResult(Action.UNKNOWN, null, null, "Failed to parse URI: ${e.message}")
        }
    }

    /**
     * Parse an intent's data
     */
    fun parseIntent(intent: Intent?): DeepLinkResult {
        return intent?.data?.let { parseUri(it) }
            ?: DeepLinkResult(Action.UNKNOWN, null, null, "No URI in intent")
    }

    /**
     * Generate a send deep link
     */
    fun generateSendLink(
        host: String,
        port: Int,
        content: String,
        hexMode: Boolean = false
    ): String {
        return buildString {
            append("$SCHEME://send?")
            append("host=${Uri.encode(host)}")
            append("&port=$port")
            append("&content=${Uri.encode(content)}")
            append("&hex=$hexMode")
        }
    }

    /**
     * Generate a connect deep link
     */
    fun generateConnectLink(
        host: String,
        port: Int
    ): String {
        return "$SCHEME://connect?host=${Uri.encode(host)}&port=$port"
    }

    /**
     * Generate a preset deep link
     */
    fun generatePresetLink(presetName: String): String {
        return "$SCHEME://preset?name=${Uri.encode(presetName)}"
    }

    /**
     * Generate a full config deep link
     */
    fun generateConfigLink(config: UdpConfig): String {
        return buildString {
            append("$SCHEME://config?")
            append("host=${Uri.encode(config.host)}")
            append("&port=${config.port}")
            append("&content=${Uri.encode(config.packetContent)}")
            append("&hex=${config.hexMode}")
            append("&ts=${config.includeTimestamp}")
            append("&burst=${config.includeBurstIndex}")
        }
    }

    /**
     * Create an intent for a deep link action
     */
    fun createIntent(action: Action, config: UdpConfig? = null, presetName: String? = null): Intent {
        val uri = when (action) {
            Action.SEND -> config?.let { generateSendLink(it.host, it.port, it.packetContent, it.hexMode) }
                ?: "$SCHEME://trigger"
            Action.CONNECT -> config?.let { generateConnectLink(it.host, it.port) }
                ?: "$SCHEME://disconnect"
            Action.DISCONNECT -> "$SCHEME://disconnect"
            Action.TRIGGER -> "$SCHEME://trigger"
            Action.PRESET -> presetName?.let { generatePresetLink(it) } ?: "$SCHEME://trigger"
            Action.CONFIG -> config?.let { generateConfigLink(it) } ?: "$SCHEME://trigger"
            Action.UNKNOWN -> "$SCHEME://trigger"
        }

        return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            `package` = "com.udptrigger"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // Private parsing methods

    private fun parseSendOrConnect(uri: Uri): DeepLinkResult {
        val host = uri.getQueryParameter("host") ?: return DeepLinkResult(
            Action.UNKNOWN, null, null, "Missing host parameter"
        )
        val portStr = uri.getQueryParameter("port") ?: "5000"
        val port = portStr.toIntOrNull() ?: return DeepLinkResult(
            Action.UNKNOWN, null, null, "Invalid port: $portStr"
        )

        val content = uri.getQueryParameter("content") ?: "TRIGGER"
        val hexMode = uri.getQueryParameter("hex")?.toBooleanStrictOrNull() ?: false

        val action = when (uri.host?.lowercase()) {
            "send" -> Action.SEND
            "connect" -> Action.CONNECT
            else -> Action.UNKNOWN
        }

        return DeepLinkResult(
            action = action,
            config = UdpConfig(host, port, content, hexMode, true, false),
            presetName = null,
            error = null
        )
    }

    private fun parsePreset(uri: Uri): DeepLinkResult {
        val presetName = uri.getQueryParameter("name") ?: return DeepLinkResult(
            Action.UNKNOWN, null, null, "Missing preset name"
        )

        return DeepLinkResult(
            action = Action.PRESET,
            config = null,
            presetName = presetName,
            error = null
        )
    }

    private fun parseConfig(uri: Uri): DeepLinkResult {
        val host = uri.getQueryParameter("host") ?: return DeepLinkResult(
            Action.UNKNOWN, null, null, "Missing host parameter"
        )
        val portStr = uri.getQueryParameter("port") ?: "5000"
        val port = portStr.toIntOrNull() ?: return DeepLinkResult(
            Action.UNKNOWN, null, null, "Invalid port: $portStr"
        )

        val content = uri.getQueryParameter("content") ?: "TRIGGER"
        val hexMode = uri.getQueryParameter("hex")?.toBooleanStrictOrNull() ?: false
        val timestamp = uri.getQueryParameter("ts")?.toBooleanStrictOrNull() ?: true
        val burstIndex = uri.getQueryParameter("burst")?.toBooleanStrictOrNull() ?: false

        return DeepLinkResult(
            action = Action.CONFIG,
            config = UdpConfig(host, port, content, hexMode, timestamp, burstIndex),
            presetName = null,
            error = null
        )
    }

    /**
     * Validate a deep link format
     */
    fun isValidDeepLink(uri: Uri): Boolean {
        return uri.scheme?.lowercase() == SCHEME
    }

    /**
     * Get human-readable description of a deep link
     */
    fun getDescription(uri: Uri): String {
        val result = parseUri(uri)
        return when (result.action) {
            Action.SEND -> "Send UDP packet to ${result.config?.host}:${result.config?.port}"
            Action.CONNECT -> "Connect to ${result.config?.host}:${result.config?.port}"
            Action.DISCONNECT -> "Disconnect from current host"
            Action.TRIGGER -> "Trigger UDP packet"
            Action.PRESET -> "Load preset: ${result.presetName}"
            Action.CONFIG -> "Import configuration"
            Action.UNKNOWN -> "Unknown action"
        }
    }
}
