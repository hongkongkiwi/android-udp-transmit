package com.udptrigger.domain

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Low-latency UDP client for sending trigger packets.
 * Optimized for minimal delay between trigger and transmission.
 * Supports unicast, broadcast, and listen modes.
 */
class UdpClient {

    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private var targetPort: Int = 0

    // Pre-allocated packet for low-latency sending
    private var preallocatedPacket: DatagramPacket? = null

    private val mutex = Mutex()

    // Channel for received packets in listen mode
    private val receiveChannel = Channel<ReceivedPacket>(Channel.UNLIMITED)
    private var isListening = false

    /**
     * Data class representing a received UDP packet
     */
    data class ReceivedPacket(
        val data: ByteArray,
        val length: Int,
        val sourceAddress: InetAddress,
        val sourcePort: Int,
        val timestamp: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ReceivedPacket

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

    /**
     * Initialize the UDP socket in multicast mode.
     * @param multicastAddress Multicast group address (e.g., 230.0.0.1)
     * @param port Multicast port
     * @param ttl Time-to-live for multicast packets (0-255, default 1 for local network)
     */
    suspend fun initializeMulticast(multicastAddress: String, port: Int, ttl: Int = 1): Result<Unit> {
        return mutex.withLock {
            try {
                targetAddress = InetAddress.getByName(multicastAddress)
                targetPort = port

                // Verify this is a multicast address
                if (!targetAddress!!.isMulticastAddress) {
                    return@withLock Result.failure(IllegalArgumentException("Address $multicastAddress is not a multicast address"))
                }

                // Create MulticastSocket for multicast support
                val multicastSocket = MulticastSocket(port).apply {
                    // Set TTL for multicast
                    timeToLive = ttl.coerceIn(0, 255)

                    // Enable multicast loopback for local testing (false = enabled, true = disabled)
                    loopbackMode = false
                }
                socket = multicastSocket
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Join a multicast group (for receiving multicast packets)
     * @param multicastAddress Multicast group address to join
     * @param bindAddress Interface address to join on (null for default)
     */
    suspend fun joinMulticastGroup(multicastAddress: String, bindAddress: InetAddress? = null): Result<Unit> {
        return mutex.withLock {
            try {
                val groupAddress = InetAddress.getByName(multicastAddress)

                // Get or create MulticastSocket
                val multicastSocket = when (val existingSocket = socket) {
                    is MulticastSocket -> existingSocket
                    null -> {
                        MulticastSocket().apply {
                            reuseAddress = true
                            receiveBufferSize = 65536
                        }
                    }
                    else -> {
                        // Close existing non-multicast socket and create new one
                        existingSocket.close()
                        MulticastSocket().apply {
                            reuseAddress = true
                            receiveBufferSize = 65536
                        }
                    }
                }

                socket = multicastSocket

                // Get the network interface to use
                val networkInterface = bindAddress?.let {
                    NetworkInterface.getByInetAddress(it)
                } ?: run {
                    // Try to find a suitable interface
                    val interfaces = NetworkInterface.getNetworkInterfaces().toList()
                    interfaces.firstOrNull { it.isUp && !it.isLoopback }
                        ?: interfaces.firstOrNull()
                }

                if (networkInterface != null) {
                    multicastSocket.joinGroup(groupAddress)
                } else {
                    // Fallback: join without specifying interface
                    multicastSocket.joinGroup(groupAddress)
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Leave a multicast group
     */
    suspend fun leaveMulticastGroup(multicastAddress: String): Result<Unit> {
        return mutex.withLock {
            try {
                val groupAddress = InetAddress.getByName(multicastAddress)

                try {
                    (socket as? java.net.MulticastSocket)?.leaveGroup(groupAddress)
                } catch (e: Exception) {
                    // Ignore if not a MulticastSocket
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Send to multicast group
     */
    suspend fun sendMulticast(data: ByteArray): Result<Unit> {
        return mutex.withLock {
            try {
                val address = targetAddress ?: return@withLock Result.failure(IllegalStateException("Multicast not initialized"))
                val sock = socket ?: return@withLock Result.failure(IllegalStateException("UDP socket not created"))

                val packet = DatagramPacket(data, data.size, address, targetPort)
                sock.send(packet)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Initialize the UDP socket with target destination.
     * Pre-resolving the address to minimize latency on send.
     * @param host Target hostname or IP address (supports 255.255.255.255 for broadcast)
     * @param port Target UDP port
     * @param qosDscp DSCP value for Quality of Service (0-63, default 0 = IPTOS_LOWDELAY)
     */
    suspend fun initialize(host: String, port: Int) {
        mutex.withLock {
            targetAddress = InetAddress.getByName(host)
            targetPort = port

            // Create socket with broadcast enabled if needed
            socket = DatagramSocket().apply {
                // Disable Nagle's algorithm for lower latency
                trafficClass = 0x04 // IPTOS_LOWDELAY

                // Enable broadcast if target is broadcast address
                if (host == "255.255.255.255" || host == "192.168.255.255" ||
                    host.endsWith(".255") || host.endsWith(".255.255")) {
                    broadcast = true
                }
            }
        }
    }

    /**
     * Initialize the UDP socket in listen mode for receiving packets.
     * @param port Local port to listen on
     * @param bindAddress Optional address to bind to (null for all interfaces)
     */
    suspend fun initializeListen(port: Int, bindAddress: InetAddress? = null) {
        mutex.withLock {
            targetPort = port
            targetAddress = null

            // Create socket bound to specified port
            socket = DatagramSocket(port, bindAddress).apply {
                // Set receive buffer size for better performance
                receiveBufferSize = 65536
                // Enable broadcast reception
                broadcast = true
                // Set reuse address to allow quick rebinding
                reuseAddress = true
            }
            isListening = false
        }
    }

    /**
     * Start listening for UDP packets.
     * Returns a Flow that emits received packets.
     */
    suspend fun startListening(): Flow<ReceivedPacket> {
        mutex.withLock {
            isListening = true
        }

        // Start receive loop in a coroutine
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val buffer = ByteArray(65535)
            while (isListening) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    val received = ReceivedPacket(
                        data = packet.data.copyOf(packet.length),
                        length = packet.length,
                        sourceAddress = packet.address,
                        sourcePort = packet.port,
                        timestamp = System.currentTimeMillis()
                    )

                    receiveChannel.trySend(received)
                } catch (e: Exception) {
                    if (isListening) {
                        // Send error as a special packet
                        val errorPacket = ReceivedPacket(
                            data = "ERROR: ${e.message}".toByteArray(),
                            length = 0,
                            sourceAddress = InetAddress.getByName("0.0.0.0"),
                            sourcePort = 0,
                            timestamp = System.currentTimeMillis()
                        )
                        receiveChannel.trySend(errorPacket)
                    }
                    break
                }
            }
        }

        return receiveChannel.receiveAsFlow()
    }

    /**
     * Stop listening for UDP packets.
     */
    suspend fun stopListening() {
        mutex.withLock {
            isListening = false
        }
    }

    /**
     * Check if currently in listen mode
     */
    fun getIsListening(): Boolean = isListening

    /**
     * Send a UDP packet with the given content.
     * Should be called immediately after a key press for minimal latency.
     */
    suspend fun send(data: ByteArray): Result<Unit> {
        return mutex.withLock {
            try {
                val address = targetAddress ?: return@withLock Result.failure(IllegalStateException("UDP client not initialized"))
                val sock = socket ?: return@withLock Result.failure(IllegalStateException("UDP socket not created"))

                val packet = DatagramPacket(data, data.size, address, targetPort)
                sock.send(packet)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * FAST-PATH send for minimal latency.
     * This bypasses the mutex and coroutine overhead for the critical trigger path.
     * Thread-safe for single-threaded sends (which is our use case).
     *
     * IMPORTANT: This must only be called from the same thread that handles key events
     * to avoid race conditions. The ViewModel ensures this by calling from key event handler.
     *
     * @param data The packet data to send (will be copied into pre-allocated buffer)
     * @return The timestamp (nanos) when send completed, or -1 on error
     */
    fun sendFast(data: ByteArray): Long {
        try {
            val sock = socket ?: return -1L
            val address = targetAddress ?: return -1L

            // Reuse pre-allocated packet or create new one
            var packet = preallocatedPacket
            if (packet == null || packet.data.size < data.size) {
                val buffer = ByteArray(data.size.coerceAtLeast(256))
                packet = DatagramPacket(buffer, data.size, address, targetPort)
                preallocatedPacket = packet
            } else {
                // Copy data into pre-allocated buffer
                System.arraycopy(data, 0, packet.data, 0, data.size)
                packet.length = data.size
                packet.address = address
                packet.port = targetPort
            }

            sock.send(packet)
            return System.nanoTime()
        } catch (e: Exception) {
            return -1L
        }
    }

    /**
     * Send a UDP packet to a specific address and port (useful for responding).
     */
    suspend fun sendTo(data: ByteArray, address: InetAddress, port: Int): Result<Unit> {
        return mutex.withLock {
            try {
                val sock = socket ?: return@withLock Result.failure(IllegalStateException("UDP socket not created"))

                val packet = DatagramPacket(data, data.size, address, port)
                sock.send(packet)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Send a UDP packet to multiple targets.
     * @param data Packet data to send
     * @param targets List of (address, port) pairs
     * @param delayMs Delay between sends for sequential mode (0 for parallel)
     * @return List of results for each target
     */
    suspend fun sendToMultiple(
        data: ByteArray,
        targets: List<Pair<InetAddress, Int>>,
        delayMs: Long = 0
    ): List<Result<Unit>> {
        val results = mutableListOf<Result<Unit>>()

        if (delayMs > 0) {
            // Sequential sending with delay
            for ((address, port) in targets) {
                val result = mutex.withLock {
                    try {
                        val sock = socket ?: return@withLock Result.failure(IllegalStateException("UDP socket not created"))
                        val packet = DatagramPacket(data, data.size, address, port)
                        sock.send(packet)
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
                results.add(result)
                if (targets.indexOf(Pair(address, port)) < targets.size - 1) {
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        } else {
            // Parallel sending
            val jobs = targets.map { (address, port) ->
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async {
                    mutex.withLock {
                        try {
                            val sock = socket ?: return@async Result.failure<Unit>(IllegalStateException("UDP socket not created"))
                            val packet = DatagramPacket(data, data.size, address, port)
                            sock.send(packet)
                            Result.success(Unit)
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                }
            }
            results.addAll(jobs.map { it.await() })
        }

        return results
    }

    /**
     * FAST-PATH send to multiple targets.
     * This bypasses the mutex and coroutine overhead for the critical trigger path.
     * @param data Packet data to send
     * @param targets List of (address, port) pairs
     * @return Map of target string to send result timestamp (or -1 if failed)
     */
    fun sendFastMultiple(
        data: ByteArray,
        targets: List<Pair<InetAddress, Int>>
    ): Map<String, Long> {
        val results = mutableMapOf<String, Long>()
        val sock = socket ?: return emptyMap()

        for ((address, port) in targets) {
            val targetKey = "${address.hostAddress}:$port"
            try {
                val packet = DatagramPacket(data, data.size, address, port)
                sock.send(packet)
                results[targetKey] = System.nanoTime()
            } catch (e: Exception) {
                results[targetKey] = -1L
            }
        }

        return results
    }

    /**
     * Get the local port this socket is bound to.
     */
    fun getLocalPort(): Int {
        return socket?.localPort ?: -1
    }

    /**
     * Get the local address this socket is bound to.
     */
    fun getLocalAddress(): InetAddress? {
        return socket?.localAddress
    }

    /**
     * Close the UDP socket synchronously.
     * Use this for cleanup in lifecycle methods.
     */
    fun closeSync() {
        isListening = false
        receiveChannel.close()
        socket?.close()
        socket = null
        targetAddress = null
        preallocatedPacket = null
    }

    /**
     * Close the UDP socket asynchronously.
     */
    suspend fun close() {
        mutex.withLock {
            isListening = false
            receiveChannel.close()
            socket?.close()
            socket = null
            targetAddress = null
            preallocatedPacket = null
        }
    }
}
