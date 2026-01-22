package com.udptrigger.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Network discovery result
 */
data class DiscoveredDevice(
    val address: String,
    val port: Int,
    val latencyMs: Long,
    val hostName: String? = null
)

/**
 * Network scanner for discovering UDP devices on the local network
 */
class NetworkScanner(private val context: Context) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Get the local network subnet (e.g., 192.168.1.0)
     */
    suspend fun getLocalSubnet(): String? = withContext(Dispatchers.IO) {
        val network = connectivityManager.activeNetwork ?: return@withContext null
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return@withContext null

        for (linkAddress in linkProperties.linkAddresses) {
            val address = linkAddress.address as? Inet4Address
            if (address != null) {
                val ip = address.hostAddress ?: continue
                // Check if it's a private network address
                when {
                    ip.startsWith("192.168.") -> return@withContext ip.substringBeforeLast(".") + ".0"
                    ip.startsWith("10.") -> return@withContext "10.0.0.0"
                    ip.startsWith("172.") -> {
                        val secondOctet = ip.substringAfter(".").substringBefore(".").toIntOrNull()
                        if (secondOctet != null && secondOctet in 16..31) {
                            return@withContext ip.substringBeforeLast(".") + ".0"
                        }
                    }
                }
            }
        }
        null
    }

    /**
     * Scan common UDP ports on the local subnet
     */
    suspend fun scanCommonPorts(
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onDeviceFound: (DiscoveredDevice) -> Unit
    ): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val subnet = getLocalSubnet() ?: return@withContext emptyList()
        val baseIp = subnet.substringBeforeLast(".")
        val commonPorts = listOf(5000, 4444, 3333, 7000, 8000, 9000, 8888)

        val results = mutableListOf<DiscoveredDevice>()
        var checkedCount = 0
        val totalCount = 254 // Scan 254 IPs (common for /24 networks)

        coroutineScope {
            val jobs = (1..254).map { i ->
                async {
                    val ip = "$baseIp.$i"
                    // Check if host is up with simple ping
                    if (isHostReachable(ip, 200)) {
                        // Scan common UDP ports
                        for (port in commonPorts) {
                            if (isUdpPortOpen(ip, port, 500)) {
                                val device = DiscoveredDevice(
                                    address = ip,
                                    port = port,
                                    latencyMs = measureLatency(ip),
                                    hostName = getHostName(ip)
                                )
                                withContext(Dispatchers.Main) {
                                    onDeviceFound(device)
                                }
                                results.add(device)
                            }
                        }
                    }
                    checkedCount++
                    onProgress(checkedCount, totalCount)
                }
            }
            jobs.awaitAll()
        }

        results.toList()
    }

    /**
     * Scan a specific host:port for UDP responsiveness
     */
    suspend fun scanHostPort(host: String, port: Int, timeoutMs: Long = 1000): DiscoveredDevice? = withContext(Dispatchers.IO) {
        if (!isUdpPortOpen(host, port, timeoutMs)) {
            return@withContext null
        }

        DiscoveredDevice(
            address = host,
            port = port,
            latencyMs = measureLatency(host),
            hostName = getHostName(host)
        )
    }

    /**
     * Check if a host is reachable using ICMP ping
     */
    private fun isHostReachable(host: String, timeoutMs: Long): Boolean {
        return try {
            val address = InetAddress.getByName(host)
            address.isReachable(timeoutMs.toInt())
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if a UDP port is open on a host
     */
    private fun isUdpPortOpen(host: String, port: Int, timeoutMs: Long): Boolean {
        return try {
            val socket = DatagramSocket()
            socket.soTimeout = timeoutMs.toInt()
            socket.connect(InetAddress.getByName(host), port)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Measure latency to a host
     */
    private fun measureLatency(host: String, port: Int = 80): Long {
        val start = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 1000)
                (System.nanoTime() - start) / 1_000_000 // Convert to milliseconds
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Get hostname for an IP address
     */
    private fun getHostName(ip: String): String? {
        return try {
            val address = InetAddress.getByName(ip)
            address.hostName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Send a broadcast discovery packet to find UDP servers on the network
     */
    suspend fun sendDiscoveryPacket(
        port: Int,
        data: ByteArray = "DISCOVER_UDP_TRIGGER".toByteArray(),
        onResponse: (sourceAddress: String, sourcePort: Int, data: ByteArray) -> Unit
    ) = withContext(Dispatchers.IO) {
        val socket = DatagramSocket()
        try {
            socket.broadcast = true
            socket.soTimeout = 5000

            val packet = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), port)
            socket.send(packet)

            // Listen for responses
            val buffer = ByteArray(1024)
            while (true) {
                val response = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(response)
                    onResponse(
                        response.address.hostAddress ?: "Unknown",
                        response.port,
                        response.data.copyOf(response.length)
                    )
                } catch (e: java.net.SocketTimeoutException) {
                    break // Timeout, expected
                }
            }
        } catch (e: Exception) {
            // Error during discovery
        } finally {
            socket.close()
        }
    }

    /**
     * Get local IP address
     */
    fun getLocalIpAddress(): String? {
        return try {
            val network = connectivityManager.activeNetwork ?: return null
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return null

            for (linkAddress in linkProperties.linkAddresses) {
                val address = linkAddress.address as? Inet4Address
                if (address != null) {
                    val ip = address.hostAddress ?: continue
                    if (!ip.startsWith("127.") && ip != "0.0.0.0") {
                        return ip
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get network SSID (WiFi network name)
     * Note: This requires additional permissions and may not work on all Android versions
     */
    fun getNetworkSSID(): String? {
        return try {
            // Try to get SSID from NetworkCapabilities
            val network = connectivityManager.activeNetwork ?: return null
            connectivityManager.getNetworkCapabilities(network) ?: return null

            // SSID access is restricted on Android 9+, return null
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                null
            } else {
                // For older Android versions, try alternative method
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
