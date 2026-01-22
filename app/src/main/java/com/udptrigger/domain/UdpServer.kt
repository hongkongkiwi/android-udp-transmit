package com.udptrigger.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

/**
 * UDP Server for persistent listening and auto-response.
 * Supports unicast, multicast, and broadcast reception.
 */
class UdpServer {

    private var socket: DatagramSocket? = null
    private var multicastSocket: MulticastSocket? = null
    private var mServerRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // State flows for server status and received packets
    private val _serverRunning = MutableStateFlow(false)
    val serverRunning: StateFlow<Boolean> = _serverRunning.asStateFlow()

    private val _receivedPackets = MutableStateFlow<List<ServerPacket>>(emptyList())
    val receivedPackets: StateFlow<List<ServerPacket>> = _receivedPackets.asStateFlow()

    private val _serverStats = MutableStateFlow(ServerStats())
    val serverStats: StateFlow<ServerStats> = _serverStats.asStateFlow()

    data class ServerPacket(
        val data: ByteArray,
        val length: Int,
        val sourceAddress: InetAddress,
        val sourcePort: Int,
        val timestamp: Long,
        val isMulticast: Boolean = false,
        val multicastGroup: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ServerPacket
            if (!data.contentEquals(other.data)) return false
            if (length != other.length) return false
            if (sourceAddress != other.sourceAddress) return false
            if (sourcePort != other.sourcePort) return false
            if (timestamp != other.timestamp) return false
            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + length
            result = 31 * result + sourceAddress.hashCode()
            result = 31 * result + sourcePort
            result = 31 * result + timestamp.hashCode()
            return result
        }
    }

    data class ServerStats(
        val totalPacketsReceived: Long = 0,
        val totalBytesReceived: Long = 0,
        val lastPacketTime: Long = 0,
        val averagePacketSize: Double = 0.0,
        val connectedClients: Int = 0
    )

    // Auto-response rules
    data class AutoResponseRule(
        val name: String,
        val matchPattern: String,
        val useRegex: Boolean = false,
        val responseContent: String,
        val responseHex: Boolean = false,
        val includeOriginalData: Boolean = false,
        val enabled: Boolean = true,
        val delayMs: Long = 0
    )

    private val autoResponseRules = mutableListOf<AutoResponseRule>()

    /**
     * Start UDP server on specified port
     */
    suspend fun start(port: Int, bindAddress: InetAddress? = null): Result<Int> {
        return try {
            socket = DatagramSocket(port, bindAddress).apply {
                reuseAddress = true
                receiveBufferSize = 65536
                broadcast = true
                soTimeout = 0 // No timeout for blocking receive
            }

            mServerRunning = true
            _serverRunning.value = true

            // Start receiving in background
            scope.launch {
                receivePackets()
            }

            Result.success(socket?.localPort ?: port)
        } catch (e: Exception) {
            mServerRunning = false
            _serverRunning.value = false
            Result.failure(e)
        }
    }

    /**
     * Start multicast server (join group and listen)
     */
    suspend fun startMulticast(port: Int, multicastAddress: String): Result<Int> {
        return try {
            val groupAddress = InetAddress.getByName(multicastAddress)

            multicastSocket = MulticastSocket(port).apply {
                reuseAddress = true
                receiveBufferSize = 65536
                broadcast = true
            }

            // Join multicast group
            try {
                @Suppress("DEPRECATION")
                multicastSocket?.joinGroup(groupAddress)
            } catch (e: Exception) {
                // Final fallback
                @Suppress("DEPRECATION")
                multicastSocket?.joinGroup(groupAddress)
            }

            mServerRunning = true
            _serverRunning.value = true

            // Start receiving in background
            scope.launch {
                receiveMulticastPackets(multicastAddress)
            }

            Result.success(multicastSocket?.localPort ?: port)
        } catch (e: Exception) {
            mServerRunning = false
            _serverRunning.value = false
            Result.failure(e)
        }
    }

    private suspend fun receivePackets() {
        val buffer = ByteArray(65535)
        val packet = DatagramPacket(buffer, buffer.size)

        while (mServerRunning) {
            try {
                socket?.receive(packet)
                if (!mServerRunning) break

                val receivedPacket = ServerPacket(
                    data = packet.data.copyOf(packet.length),
                    length = packet.length,
                    sourceAddress = packet.address,
                    sourcePort = packet.port,
                    timestamp = System.currentTimeMillis()
                )

                processReceivedPacket(receivedPacket)
            } catch (e: Exception) {
                if (mServerRunning) {
                    // Log but continue
                }
                break
            }
        }
    }

    private suspend fun receiveMulticastPackets(multicastGroup: String) {
        val buffer = ByteArray(65535)
        val packet = DatagramPacket(buffer, buffer.size)

        while (mServerRunning) {
            try {
                multicastSocket?.receive(packet)
                if (!mServerRunning) break

                val receivedPacket = ServerPacket(
                    data = packet.data.copyOf(packet.length),
                    length = packet.length,
                    sourceAddress = packet.address,
                    sourcePort = packet.port,
                    timestamp = System.currentTimeMillis(),
                    isMulticast = true,
                    multicastGroup = multicastGroup
                )

                processReceivedPacket(receivedPacket)
            } catch (e: Exception) {
                if (mServerRunning) {
                    // Log but continue
                }
                break
            }
        }
    }

    private fun processReceivedPacket(packet: ServerPacket) {
        // Update stats
        val currentStats = _serverStats.value
        val newTotalPackets = currentStats.totalPacketsReceived + 1
        val newTotalBytes = currentStats.totalBytesReceived + packet.length
        val newAvgSize = newTotalBytes.toDouble() / newTotalPackets

        _serverStats.value = currentStats.copy(
            totalPacketsReceived = newTotalPackets,
            totalBytesReceived = newTotalBytes,
            lastPacketTime = packet.timestamp,
            averagePacketSize = newAvgSize
        )

        // Add to packet list (keep last 100)
        val packets = (_receivedPackets.value + packet).takeLast(100)
        _receivedPackets.value = packets

        // Check auto-response rules
        checkAutoResponseRules(packet)
    }

    private fun checkAutoResponseRules(packet: ServerPacket) {
        val packetContent = String(packet.data, Charsets.UTF_8)
        val packetContentHex = packet.data.joinToString("") { "%02X".format(it) }

        for (rule in autoResponseRules) {
            if (!rule.enabled) continue

            val matches = if (rule.useRegex) {
                try {
                    Regex(rule.matchPattern).containsMatchIn(packetContent)
                } catch (e: Exception) {
                    false
                }
            } else {
                rule.matchPattern in packetContent
            }

            if (matches) {
                // Execute response
                scope.launch {
                    delay(rule.delayMs)
                    sendResponse(packet, rule)
                }
            }
        }
    }

    private suspend fun sendResponse(packet: ServerPacket, rule: AutoResponseRule) {
        val responseData = when {
            rule.responseHex -> {
                val hex = rule.responseContent.replace("\\s".toRegex(), "")
                hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
            rule.includeOriginalData -> {
                val original = String(packet.data, Charsets.UTF_8)
                "$original${rule.responseContent}".toByteArray(Charsets.UTF_8)
            }
            else -> rule.responseContent.toByteArray(Charsets.UTF_8)
        }

        val targetSocket = multicastSocket ?: socket
        targetSocket?.let {
            val responsePacket = DatagramPacket(
                responseData,
                responseData.size,
                packet.sourceAddress,
                packet.sourcePort
            )
            it.send(responsePacket)
        }
    }

    /**
     * Add auto-response rule
     */
    fun addAutoResponseRule(rule: AutoResponseRule) {
        autoResponseRules.add(rule)
    }

    /**
     * Remove auto-response rule by name
     */
    fun removeAutoResponseRule(name: String) {
        autoResponseRules.removeAll { it.name == name }
    }

    /**
     * Clear all auto-response rules
     */
    fun clearAutoResponseRules() {
        autoResponseRules.clear()
    }

    /**
     * Get all auto-response rules
     */
    fun getAutoResponseRules(): List<AutoResponseRule> = autoResponseRules.toList()

    /**
     * Send packet to specific client
     */
    suspend fun sendTo(data: ByteArray, address: InetAddress, port: Int): Result<Unit> {
        return try {
            val targetSocket = multicastSocket ?: socket
            targetSocket?.let {
                val packet = DatagramPacket(data, data.size, address, port)
                it.send(packet)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Broadcast packet to all recent senders
     */
    suspend fun broadcast(data: ByteArray, ports: List<Int> = listOf(5000, 5001, 5002)): Result<Int> {
        return try {
            var sentCount = 0
            for (port in ports) {
                val packet = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), port)
                socket?.send(packet)
                sentCount++
            }
            Result.success(sentCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear received packets history
     */
    fun clearPackets() {
        _receivedPackets.value = emptyList()
        _serverStats.value = _serverStats.value.copy(
            totalPacketsReceived = 0,
            totalBytesReceived = 0,
            averagePacketSize = 0.0
        )
    }

    /**
     * Stop the server
     */
    suspend fun stop() {
        mServerRunning = false
        _serverRunning.value = false

        multicastSocket?.close()
        multicastSocket = null

        socket?.close()
        socket = null
    }

    /**
     * Stop synchronously (for cleanup)
     */
    fun stopSync() {
        mServerRunning = false
        _serverRunning.value = false

        multicastSocket?.close()
        multicastSocket = null

        socket?.close()
        socket = null
    }

    /**
     * Check if server is active
     */
    fun isActive(): Boolean = mServerRunning

    /**
     * Get local port
     */
    fun getLocalPort(): Int = socket?.localPort ?: multicastSocket?.localPort ?: -1

    /**
     * Get bound address
     */
    fun getBoundAddress(): InetAddress? = socket?.localAddress ?: multicastSocket?.localAddress

    /**
     * Clean up resources
     */
    fun destroy() {
        stopSync()
        scope.cancel()
    }
}
