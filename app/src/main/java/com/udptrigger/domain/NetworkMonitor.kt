package com.udptrigger.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class NetworkMonitor(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isNetworkAvailableFlow = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(hasInternet)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Send initial state
        trySend(isCurrentlyConnected())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun getWifiIpAddress(): String? {
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return getIpAddress()
        }
        return null
    }

    private fun getIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            for (networkInterface in interfaces) {
                if (networkInterface.isUp) {
                    val addresses = networkInterface.inetAddresses?.toList() ?: continue
                    for (address in addresses) {
                        if (!address.isLoopbackAddress) {
                            val hostAddress = address.hostAddress
                            if (hostAddress != null && hostAddress.contains(':')) continue // Skip IPv6 for now
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    /**
     * Get all IP addresses (IPv4) from all network interfaces
     */
    fun getAllIpAddresses(): List<String> {
        val addresses = mutableListOf<String>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()?.toList() ?: return emptyList()
            for (networkInterface in interfaces) {
                if (networkInterface.isUp) {
                    val interfaceAddresses = networkInterface.inetAddresses?.toList() ?: continue
                    for (address in interfaceAddresses) {
                        if (!address.isLoopbackAddress && address.hostAddress?.contains(':') == false) {
                            addresses.add(address.hostAddress ?: "")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return addresses.distinct()
    }

    /**
     * Get the device's primary IP address (first non-loopback IPv4)
     */
    fun getDeviceIpAddress(): String? {
        return getIpAddress()
    }
}
