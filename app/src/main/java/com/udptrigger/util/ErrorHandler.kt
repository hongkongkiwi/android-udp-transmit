package com.udptrigger.util

import android.content.Context
import java.net.ConnectException
import java.net.PortUnreachableException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * User-friendly error messages for common UDP operations
 */
data class ErrorInfo(
    val title: String,
    val message: String,
    val recoverySuggestion: String? = null,
    val isCritical: Boolean = false
)

/**
 * Converts technical exceptions into user-friendly error messages
 */
object ErrorHandler {

    private var crashAnalyticsManager: CrashAnalyticsManager? = null

    /**
     * Initialize the error handler with context for crash reporting
     */
    fun initialize(context: Context) {
        crashAnalyticsManager = CrashAnalyticsManager(context)
    }

    /**
     * Record a crash report (internal errors that should be reported)
     */
    fun recordCrash(throwable: Throwable) {
        crashAnalyticsManager?.recordCrash(throwable)
    }

    fun getErrorInfo(throwable: Throwable?, context: String = ""): ErrorInfo {
        val cause = throwable?.cause ?: throwable

        return when (cause) {
            // Network connectivity issues
            is UnknownHostException -> ErrorInfo(
                title = "Host Not Found",
                message = "The destination host '${getHostFromError(cause, context)}' could not be found.",
                recoverySuggestion = "Check that:\n• The hostname/IP address is correct\n• You're connected to the correct network\n• The device is online and reachable"
            )

            is ConnectException -> ErrorInfo(
                title = "Connection Failed",
                message = "Could not establish a connection to the destination.",
                recoverySuggestion = "Verify:\n• The target device is powered on\n• Network connection is active\n• Firewall isn't blocking UDP traffic"
            )

            is PortUnreachableException -> ErrorInfo(
                title = "Port Unreachable",
                message = "The destination port is not accepting connections.",
                recoverySuggestion = "Check:\n• The correct port is configured\n• The receiving application is running\n• No firewall is blocking the port"
            )

            is SocketTimeoutException -> ErrorInfo(
                title = "Connection Timeout",
                message = "The operation timed out waiting for a response.",
                recoverySuggestion = "This may indicate:\n• Network congestion\n• High latency on the network\n• The destination is too busy"
            )

            is SocketException -> {
                val errorMsg = cause.message?.lowercase() ?: ""
                when {
                    "network unreachable" in errorMsg || "unreachable network" in errorMsg -> ErrorInfo(
                        title = "Network Unreachable",
                        message = "The network is not available or the destination cannot be reached.",
                        recoverySuggestion = "Check:\n• WiFi or mobile data is enabled\n• You're connected to the correct network\n• Airplane mode is off"
                    )
                    "permission denied" in errorMsg || "access denied" in errorMsg -> ErrorInfo(
                        title = "Permission Denied",
                        message = "The app doesn't have permission to access the network.",
                        recoverySuggestion = "Grant network permissions in:\nSettings → Apps → UDP Trigger → Permissions",
                        isCritical = true
                    )
                    "address already in use" in errorMsg || "bind" in errorMsg -> ErrorInfo(
                        title = "Port Already in Use",
                        message = "Another application is using this port.",
                        recoverySuggestion = "Either:\n• Close the other application using this port\n• Use a different port number"
                    )
                    else -> ErrorInfo(
                        title = "Network Error",
                        message = "A network error occurred: ${cause.message}",
                        recoverySuggestion = "Try:\n• Checking your network connection\n• Restarting the app\n• Reconnecting to the network"
                    )
                }
            }

            // Security and permission errors
            is SecurityException -> ErrorInfo(
                title = "Permission Required",
                message = "The app needs additional permissions to perform this action.",
                recoverySuggestion = "Grant the required permission in:\nSettings → Apps → UDP Trigger → Permissions",
                isCritical = true
            )

            // Generic error handling
            else -> {
                val errorMsg = cause?.message?.lowercase() ?: ""
                when {
                    "eton" in errorMsg || "network" in errorMsg -> ErrorInfo(
                        title = "Network Error",
                        message = cause?.message ?: "An unknown network error occurred.",
                        recoverySuggestion = "Check your network connection and try again."
                    )
                    "permission" in errorMsg || "denied" in errorMsg -> ErrorInfo(
                        title = "Permission Error",
                        message = cause?.message ?: "Permission was denied.",
                        recoverySuggestion = "Grant the required permissions in app settings.",
                        isCritical = true
                    )
                    "timeout" in errorMsg -> ErrorInfo(
                        title = "Timeout",
                        message = "The operation timed out.",
                        recoverySuggestion = "Try again. If the problem persists, check your network connection."
                    )
                    else -> ErrorInfo(
                        title = "Error",
                        message = cause?.message ?: "An unexpected error occurred.",
                        recoverySuggestion = "Try restarting the app or device."
                    )
                }
            }
        }
    }

    private fun getHostFromError(throwable: Throwable, context: String): String {
        // Try to extract host from error message or context
        val msg = throwable.message?.lowercase() ?: ""
        if (containsHost(msg)) {
            return extractHost(msg)
        }
        return if (context.isNotEmpty()) context else "the destination"
    }

    private fun containsHost(text: String): Boolean {
        return Regex("""[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""").containsMatchIn(text) ||
               Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").containsMatchIn(text)
    }

    private fun extractHost(text: String): String {
        // Try to extract IP or hostname from error message
        val ipMatch = Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""").find(text)
        if (ipMatch != null) return ipMatch.groupValues[1]

        val hostMatch = Regex("""([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})""").find(text)
        if (hostMatch != null) return hostMatch.groupValues[1]

        return "the destination"
    }

    /**
     * Get user-friendly message for UDP send operations
     */
    fun getSendErrorInfo(throwable: Throwable?, host: String, port: Int): ErrorInfo {
        val baseInfo = getErrorInfo(throwable, "$host:$port")
        return baseInfo.copy(
            message = "Failed to send UDP packet to $host:$port. ${baseInfo.message}"
        )
    }

    /**
     * Get user-friendly message for connection errors
     */
    fun getConnectionErrorInfo(throwable: Throwable?, host: String, port: Int): ErrorInfo {
        val baseInfo = getErrorInfo(throwable, "$host:$port")
        return baseInfo.copy(
            message = "Could not connect to $host:$port. ${baseInfo.message}"
        )
    }

    /**
     * Get user-friendly message for listen mode errors
     */
    fun getListenErrorInfo(throwable: Throwable?, port: Int): ErrorInfo {
        val baseInfo = getErrorInfo(throwable, "port $port")
        return when {
            "address already in use" in baseInfo.message.lowercase() ||
            "bind" in baseInfo.message.lowercase() -> ErrorInfo(
                title = "Port Already in Use",
                message = "Port $port is already being used by another application.",
                recoverySuggestion = "Choose a different port or close the application using port $port."
            )
            else -> baseInfo.copy(
                message = "Could not start listening on port $port. ${baseInfo.message}"
            )
        }
    }
}
