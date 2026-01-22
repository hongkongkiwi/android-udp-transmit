package com.udptrigger.domain

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "UdpClient"

/**
 * Low-latency UDP client for sending trigger packets.
 * Optimized for minimal delay between trigger and transmission.
 * Preserves all low-latency optimizations from the original implementation.
 */
class UdpClient {

    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private var targetPort: Int = 0

    // Pre-allocated packet for low-latency sending
    private var preallocatedPacket: DatagramPacket? = null
    private val mutex = Mutex()

    /**
     * Initialize the UDP socket with target destination.
     * Pre-resolving the address to minimize latency on send.
     * @param host Target hostname or IP address (supports broadcast)
     * @param port Target UDP port
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
                if (host == "255.255.255.255" || host.endsWith(".255")) {
                    broadcast = true
                }
            }
        }
    }

    /**
     * Send a UDP packet with the given content.
     * @param data Packet data to send
     * @return Success or failure
     */
    suspend fun send(data: ByteArray): Result<Unit> {
        return mutex.withLock {
            try {
                val address = targetAddress ?: return@withLock Result.failure(
                    IllegalStateException("UDP client not initialized")
                )
                val sock = socket ?: return@withLock Result.failure(
                    IllegalStateException("UDP socket not created")
                )

                val packet = DatagramPacket(data, data.size, address, targetPort)
                sock.send(packet)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Send failed: ${e.message}", e)
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
            Log.e(TAG, "Fast send failed: ${e.message}", e)
            return -1L
        }
    }

    /**
     * Close the UDP socket synchronously.
     * Use this for cleanup in lifecycle methods.
     */
    fun closeSync() {
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
            socket?.close()
            socket = null
            targetAddress = null
            preallocatedPacket = null
        }
    }
}
