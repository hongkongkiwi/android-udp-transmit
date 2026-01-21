package com.udptrigger.domain

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Low-latency UDP client for sending trigger packets.
 * Optimized for minimal delay between trigger and transmission.
 */
class UdpClient {

    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private var targetPort: Int = 0

    private val mutex = Mutex()

    /**
     * Initialize the UDP socket with target destination.
     * Pre-resolving the address to minimize latency on send.
     */
    suspend fun initialize(host: String, port: Int) {
        mutex.withLock {
            targetAddress = InetAddress.getByName(host)
            targetPort = port
            // Create socket without timeout for immediate sending
            socket = DatagramSocket().apply {
                // Disable Nagle's algorithm for lower latency
                trafficClass = 0x04 // IPTOS_LOWDELAY
            }
        }
    }

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
     * Send a packet with the current timestamp embedded.
     * Returns the timestamp used for transmission wrapped in a Result.
     */
    suspend fun sendWithTimestamp(): Result<Long> {
        val timestamp = System.nanoTime()
        val message = "TRIGGER:$timestamp".toByteArray(Charsets.UTF_8)
        val sendResult = send(message)
        return if (sendResult.isSuccess) {
            Result.success(timestamp)
        } else {
            Result.failure(sendResult.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    /**
     * Close the UDP socket.
     */
    suspend fun close() {
        mutex.withLock {
            socket?.close()
            socket = null
            targetAddress = null
        }
    }
}
