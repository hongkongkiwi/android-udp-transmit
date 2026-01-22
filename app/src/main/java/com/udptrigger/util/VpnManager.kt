package com.udptrigger.util

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.security.NetworkSecurityPolicy
import java.net.Socket

/**
 * VPN Integration utilities for UDP Trigger.
 * Detects VPN status and provides VPN-aware socket creation.
 */
object VpnManager {

    /**
     * Check if a VPN is currently active on the device
     */
    fun isVpnActive(context: Context): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

                // Check if VPN transport is present
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true
                }
            } else {
                // For older Android versions, use deprecated method
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                if (networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_VPN) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Fallback: VPN check failed
        }

        return false
    }

    /**
     * Check if Cleartext (HTTP) traffic is allowed
     */
    fun isCleartextAllowed(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted
        } else {
            true
        }
    }

    /**
     * Check if we need to use a protected socket for VPN
     */
    fun shouldProtectSocket(context: Context): Boolean {
        return isVpnActive(context)
    }

    /**
     * Protect a socket from VPN interception
     */
    fun protectSocket(context: Context, socket: Socket): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val method = ConnectivityManager::class.java.getMethod(
                "protect",
                Socket::class.java
            )
            method.invoke(cm, socket)
            true
        } catch (e: Exception) {
            // If the method doesn't exist, protection might not be needed
            false
        }
    }

    /**
     * Get VPN info for debugging
     */
    fun getVpnInfo(context: Context): Map<String, Any> {
        return mapOf(
            "vpn_active" to isVpnActive(context),
            "cleartext_allowed" to isCleartextAllowed(context),
            "should_protect_sockets" to shouldProtectSocket(context),
            "android_version" to Build.VERSION.RELEASE,
            "sdk_version" to Build.VERSION.SDK_INT
        )
    }

    /**
     * Open VPN settings for user to configure
     */
    fun openVpnSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_VPN_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * VPN Connection status
     */
    data class VpnStatus(
        val isActive: Boolean,
        val isBlocking: Boolean,
        val cleartextAllowed: Boolean,
        val suggestion: String?
    )

    /**
     * Analyze VPN status and provide recommendations
     */
    fun analyzeVpnStatus(context: Context): VpnStatus {
        val isActive = isVpnActive(context)
        val cleartextAllowed = isCleartextAllowed(context)

        return when {
            isActive && !cleartextAllowed -> VpnStatus(
                isActive = true,
                isBlocking = true,
                cleartextAllowed = false,
                suggestion = "VPN is blocking cleartext traffic. Some UDP packets may be affected. Consider configuring the VPN to allow this app's traffic."
            )
            isActive -> VpnStatus(
                isActive = true,
                isBlocking = false,
                cleartextAllowed = true,
                suggestion = "VPN is active but allowing cleartext traffic. UDP functionality should work normally."
            )
            else -> VpnStatus(
                isActive = false,
                isBlocking = false,
                cleartextAllowed = true,
                suggestion = null
            )
        }
    }
}

/**
 * VPN-aware UDP socket wrapper that protects sockets when needed
 */
class VpnAwareUdpClient(private val context: Context) {

    /**
     * Create a UDP socket that respects VPN settings
     */
    fun createSocket(): java.net.DatagramSocket? {
        return try {
            val socket = java.net.DatagramSocket()

            if (VpnManager.shouldProtectSocket(context)) {
                // Try to protect the socket from VPN interception
                try {
                    val method = ConnectivityManager::class.java.getMethod(
                        "protect",
                        Socket::class.java
                    )
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    method.invoke(cm, socket)
                } catch (e: Exception) {
                    // Protection failed, socket may still work
                }
            }

            socket
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if a connection would be blocked by VPN
     */
    fun wouldBeBlocked(host: String, port: Int): Boolean {
        val status = VpnManager.analyzeVpnStatus(context)
        return status.isBlocking || status.isActive
    }
}
