package com.udptrigger.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * UDP Discovery Service for auto-discovering listening UDP services on the network.
 * Sends discovery probes and listens for responses.
 */
class UdpDiscoveryService {

    private var discoverySocket: DatagramSocket? = null
    private var listenSocket: DatagramSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Discovered devices
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    // Discovery status
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    // Discovery settings
    var discoveryPort: Int = 5000
    var broadcastAddress: String = "255.255.255.255"
    var probeMessage: String = "UDP_TRIGGER_DISCOVERY_PROBE"
    var responseTimeout: Long = 2000L
    var scanDuration: Long = 5000L

    data class DiscoveredDevice(
        val address: InetAddress,
        val port: Int,
        val deviceName: String,
        val serviceType: String,
        val responseData: String?,
        val timestamp: Long,
        val isLocal: Boolean = false,
        val extraInfo: Map<String, String> = emptyMap()
    ) {
        val hostAddress: String get() = address.hostAddress ?: address.toString()
        val displayName: String get() = if (deviceName.isNotBlank()) deviceName else hostAddress
    }

    // Device cache with timestamps
    private val deviceCache = ConcurrentHashMap<String, DiscoveredDevice>()

    // Known multicast groups for discovery
    private val multicastGroups = listOf(
        "239.0.0.1",  // Local network discovery
        "239.0.0.2",  // Service discovery
        "224.0.0.251" // mDNS (local)
    )

    /**
     * Start discovering UDP services on the network
     */
    suspend fun startDiscovery(): Result<List<DiscoveredDevice>> = withContext(Dispatchers.IO) {
        if (isRunning) {
            stopDiscovery()
        }

        _isDiscovering.value = true
        isRunning = true

        // Clear previous discoveries
        deviceCache.clear()
        _discoveredDevices.value = emptyList()

        try {
            // Start listening for responses
            startResponseListener()

            // Send discovery probes
            sendDiscoveryProbes()

            // Wait for responses
            delay(scanDuration)

            // Stop listening
            stopResponseListener()

            _isDiscovering.value = false
            isRunning = false

            Result.success(_discoveredDevices.value)
        } catch (e: Exception) {
            _isDiscovering.value = false
            isRunning = false
            Result.failure(e)
        }
    }

    /**
     * Quick discovery using multicast
     */
    suspend fun quickDiscovery(): Result<List<DiscoveredDevice>> = withContext(Dispatchers.IO) {
        if (isRunning) {
            stopDiscovery()
        }

        _isDiscovering.value = true
        isRunning = true

        deviceCache.clear()
        _discoveredDevices.value = emptyList()

        try {
            // Send multicast probes
            sendMulticastProbes()

            // Short wait for responses
            delay(1000)

            _isDiscovering.value = false
            isRunning = false

            Result.success(_discoveredDevices.value)
        } catch (e: Exception) {
            _isDiscovering.value = false
            isRunning = false
            Result.failure(e)
        }
    }

    /**
     * Scan specific port range on network
     */
    suspend fun scanPortRange(
        baseAddress: String,
        startPort: Int,
        endPort: Int,
        timeout: Long = 500L
    ): Result<List<DiscoveredDevice>> = withContext(Dispatchers.IO) {
        val foundDevices = mutableListOf<DiscoveredDevice>()

        // Parse base address
        val addressParts = baseAddress.split(".")
        if (addressParts.size != 4) {
            return@withContext Result.failure(IllegalArgumentException("Invalid base address"))
        }

        val networkPrefix = addressParts.dropLast(1).joinToString(".")

        // Scan ports
        for (port in startPort..endPort step 10) {
            val batchEnd = minOf(port + 9, endPort)

            coroutineScope {
                (port..batchEnd).forEach { p ->
                    launch {
                        val address = "$networkPrefix.${addressParts[3]}"
                        try {
                            val socket = DatagramSocket()
                            socket.soTimeout = timeout.toInt()
                            socket.broadcast = true

                            val packet = DatagramPacket(
                                probeMessage.toByteArray(),
                                probeMessage.length,
                                InetAddress.getByName(address),
                                p
                            )
                            socket.send(packet)

                            // Wait briefly for response
                            val responseBuffer = ByteArray(1024)
                            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                            try {
                                socket.receive(responsePacket)
                                val responseData = String(responsePacket.data, 0, responsePacket.length)

                                val device = DiscoveredDevice(
                                    address = responsePacket.address,
                                    port = p,
                                    deviceName = parseDeviceName(responseData),
                                    serviceType = parseServiceType(responseData),
                                    responseData = responseData,
                                    timestamp = System.currentTimeMillis(),
                                    isLocal = isLocalAddress(responsePacket.address),
                                    extraInfo = parseExtraInfo(responseData)
                                )

                                val key = "${responsePacket.address.hostAddress}:$p"
                                if (deviceCache.putIfAbsent(key, device) == null) {
                                    foundDevices.add(device)
                                }
                            } catch (e: Exception) {
                                // No response, continue
                            }
                            socket.close()
                        } catch (e: Exception) {
                            // Skip this port
                        }
                    }
                }
            }
        }

        // Update discovered devices
        val allDevices = deviceCache.values.toList()
        _discoveredDevices.value = allDevices

        Result.success(allDevices)
    }

    /**
     * Send UDP probe to discover services
     */
    suspend fun sendDiscoveryProbes() {
        try {
            discoverySocket = DatagramSocket().apply {
                broadcast = true
                reuseAddress = true
            }

            // Send to broadcast address
            val broadcastPacket = DatagramPacket(
                probeMessage.toByteArray(),
                probeMessage.length,
                InetAddress.getByName(broadcastAddress),
                discoveryPort
            )
            discoverySocket?.send(broadcastPacket)

            // Also send to common multicast groups
            for (multicastGroup in multicastGroups) {
                try {
                    val multicastPacket = DatagramPacket(
                        probeMessage.toByteArray(),
                        probeMessage.length,
                        InetAddress.getByName(multicastGroup),
                        discoveryPort
                    )
                    discoverySocket?.send(multicastPacket)
                } catch (e: Exception) {
                    // Skip if multicast not available
                }
            }
        } catch (e: Exception) {
            // Log but continue
        }
    }

    /**
     * Send multicast discovery probes
     */
    private suspend fun sendMulticastProbes() {
        try {
            discoverySocket = DatagramSocket().apply {
                broadcast = true
            }

            for (multicastGroup in multicastGroups) {
                try {
                    val packet = DatagramPacket(
                        probeMessage.toByteArray(),
                        probeMessage.length,
                        InetAddress.getByName(multicastGroup),
                        discoveryPort
                    )
                    discoverySocket?.send(packet)
                } catch (e: Exception) {
                    // Skip
                }
            }
        } catch (e: Exception) {
            // Log
        }
    }

    /**
     * Start listening for discovery responses
     */
    private suspend fun startResponseListener() {
        listenSocket = DatagramSocket(discoveryPort + 1).apply {
            reuseAddress = true
            receiveBufferSize = 65536
        }

        scope.launch {
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)

            while (isRunning) {
                try {
                    listenSocket?.receive(packet)
                    if (!isRunning) break

                    val responseData = String(packet.data, 0, packet.length)

                    val device = DiscoveredDevice(
                        address = packet.address,
                        port = packet.port,
                        deviceName = parseDeviceName(responseData),
                        serviceType = parseServiceType(responseData),
                        responseData = responseData,
                        timestamp = System.currentTimeMillis(),
                        isLocal = isLocalAddress(packet.address),
                        extraInfo = parseExtraInfo(responseData)
                    )

                    val key = "${packet.address.hostAddress}:${packet.port}"
                    if (deviceCache.putIfAbsent(key, device) == null) {
                        val devices = _discoveredDevices.value + device
                        _discoveredDevices.value = devices
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        // Continue listening
                    }
                    break
                }
            }
        }
    }

    private fun stopResponseListener() {
        listenSocket?.close()
        listenSocket = null
    }

    /**
     * Announce this device as a UDP service
     */
    suspend fun announceService(
        serviceName: String,
        serviceType: String,
        port: Int,
        extraInfo: Map<String, String> = emptyMap()
    ) = withContext(Dispatchers.IO) {
        val announceMessage = buildString {
            append("UDP_TRIGGER_SERVICE_ANNOUNCE|")
            append("$serviceName|")
            append("$serviceType|")
            append("$port|")
            append(extraInfo.entries.joinToString(";") { "${it.key}=${it.value}" })
        }

        try {
            val socket = DatagramSocket().apply {
                broadcast = true
                reuseAddress = true
            }

            // Broadcast announcement
            val broadcastPacket = DatagramPacket(
                announceMessage.toByteArray(),
                announceMessage.length,
                InetAddress.getByName(broadcastAddress),
                discoveryPort
            )
            socket.send(broadcastPacket)

            // Multicast announcement
            for (multicastGroup in multicastGroups) {
                try {
                    val multicastPacket = DatagramPacket(
                        announceMessage.toByteArray(),
                        announceMessage.length,
                        InetAddress.getByName(multicastGroup),
                        discoveryPort
                    )
                    socket.send(multicastPacket)
                } catch (e: Exception) {
                    // Skip
                }
            }

            socket.close()
        } catch (e: Exception) {
            // Log error
        }
    }

    /**
     * Get list of local IP addresses
     */
    fun getLocalAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()

        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    networkInterface.inetAddresses.toList().forEach { address ->
                        if (!address.isLoopbackAddress && address.hostAddress?.contains('.') == true) {
                            addresses.add(address)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Return empty list
        }

        return addresses
    }

    private fun parseDeviceName(response: String): String {
        val parts = response.split("|")
        return if (parts.isNotEmpty()) parts[0] else ""
    }

    private fun parseServiceType(response: String): String {
        val parts = response.split("|")
        return if (parts.size > 1) parts[1] else "unknown"
    }

    private fun parseExtraInfo(response: String): Map<String, String> {
        val parts = response.split("|")
        return if (parts.size > 4) {
            parts[4].split(";")
                .filter { it.contains("=") }
                .associate {
                    val (key, value) = it.split("=")
                    key to value
                }
        } else {
            emptyMap()
        }
    }

    private fun isLocalAddress(address: InetAddress): Boolean {
        val localAddresses = getLocalAddresses()
        return localAddresses.any { it.hostAddress == address.hostAddress }
    }

    /**
     * Clear discovered devices
     */
    fun clearDevices() {
        deviceCache.clear()
        _discoveredDevices.value = emptyList()
    }

    /**
     * Remove device from cache
     */
    fun removeDevice(address: String, port: Int) {
        val key = "$address:$port"
        deviceCache.remove(key)
        _discoveredDevices.value = deviceCache.values.toList()
    }

    /**
     * Stop discovery
     */
    suspend fun stopDiscovery() {
        isRunning = false
        _isDiscovering.value = false

        stopResponseListener()

        discoverySocket?.close()
        discoverySocket = null
    }

    /**
     * Stop synchronously
     */
    fun stopDiscoverySync() {
        isRunning = false
        _isDiscovering.value = false

        listenSocket?.close()
        listenSocket = null

        discoverySocket?.close()
        discoverySocket = null
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        stopDiscoverySync()
        scope.cancel()
    }
}
