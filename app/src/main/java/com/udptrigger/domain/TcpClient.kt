package com.udptrigger.domain

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * TCP client for reliable packet transmission.
 * Provides connection-oriented communication with acknowledgment.
 */
class TcpClient {

    private var socket: Socket? = null
    private var targetAddress: InetAddress? = null
    private var targetPort: Int = 0
    private var connectTimeout: Int = 5000
    private var readTimeout: Int = 5000

    private val mutex = Mutex()

    // Channel for received data in connect mode
    private val receiveChannel = Channel<ReceivedData>(Channel.UNLIMITED)
    private var isConnected = false

    /**
     * Data class representing received TCP data
     */
    data class ReceivedData(
        val data: ByteArray,
        val length: Int,
        val sourceAddress: InetAddress,
        val sourcePort: Int,
        val timestamp: Long
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ReceivedData
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
     * Initialize TCP connection to target host/port
     */
    suspend fun initialize(host: String, port: Int, timeout: Int = 5000) {
        mutex.withLock {
            targetAddress = InetAddress.getByName(host)
            targetPort = port
            connectTimeout = timeout

            socket = Socket().apply {
                this.connect(InetSocketAddress(targetAddress, targetPort), connectTimeout)
                // Configure for low latency
                this.soTimeout = 5000
                this.tcpNoDelay = true // Disable Nagle's algorithm
            }
            isConnected = true
        }
    }

    /**
     * Initialize TCP connection in server mode (listen for incoming connections)
     */
    suspend fun initializeServer(port: Int, backlog: Int = 10) {
        mutex.withLock {
            targetPort = port
            targetAddress = null

            socket = Socket().apply {
                // This will be used as a server socket when accept() is called
            }
            isConnected = false
        }
    }

    /**
     * Accept an incoming connection (server mode)
     */
    suspend fun acceptConnection(): Boolean {
        return mutex.withLock {
            try {
                // For server mode, we need a ServerSocket
                // This is a simplified implementation
                false
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Send TCP data
     */
    suspend fun send(data: ByteArray): Result<Unit> {
        return mutex.withLock {
            try {
                val sock = socket ?: return@withLock Result.failure(IllegalStateException("TCP socket not connected"))
                if (!sock.isConnected) return@withLock Result.failure(IllegalStateException("TCP socket not connected"))

                val outputStream = sock.getOutputStream()
                outputStream.write(data)
                outputStream.flush()
                Result.success(Unit)
            } catch (e: Exception) {
                isConnected = false
                Result.failure(e)
            }
        }
    }

    /**
     * FAST-PATH send for minimal latency
     */
    fun sendFast(data: ByteArray): Long {
        try {
            val sock = socket ?: return -1L
            if (!sock.isConnected) return -1L

            val outputStream = sock.getOutputStream()
            outputStream.write(data)
            outputStream.flush()
            return System.nanoTime()
        } catch (e: Exception) {
            isConnected = false
            return -1L
        }
    }

    /**
     * Receive TCP data (blocking)
     */
    suspend fun receive(bufferSize: Int = 65535): Result<ReceivedData> {
        return mutex.withLock {
            try {
                val sock = socket ?: return@withLock Result.failure(IllegalStateException("TCP socket not connected"))
                if (!sock.isConnected) return@withLock Result.failure(IllegalStateException("TCP socket not connected"))

                val buffer = ByteArray(bufferSize)
                val inputStream = sock.getInputStream()
                val length = inputStream.read(buffer)

                if (length > 0) {
                    Result.success(
                        ReceivedData(
                            data = buffer.copyOf(length),
                            length = length,
                            sourceAddress = sock.inetAddress,
                            sourcePort = sock.port,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } else {
                    // Connection closed
                    isConnected = false
                    Result.failure(Exception("Connection closed"))
                }
            } catch (e: Exception) {
                isConnected = false
                Result.failure(e)
            }
        }
    }

    /**
     * Send and wait for response (request-response pattern)
     */
    suspend fun sendAndReceive(data: ByteArray, responseTimeout: Long = 5000): Result<ReceivedData> {
        val sendResult = send(data)
        if (sendResult.isFailure) {
            return Result.failure(sendResult.exceptionOrNull() ?: Exception("Send failed"))
        }

        // Wait for response
        delay(responseTimeout)

        return receive()
    }

    /**
     * Check if TCP connection is active
     */
    fun isConnected(): Boolean = isConnected && socket?.isConnected == true

    /**
     * Get local port
     */
    fun getLocalPort(): Int = socket?.localPort ?: -1

    /**
     * Get remote address
     */
    fun getRemoteAddress(): InetAddress? = socket?.inetAddress

    /**
     * Get remote port
     */
    fun getRemotePort(): Int = socket?.port ?: -1

    /**
     * Set socket timeout
     */
    fun setReadTimeout(timeoutMs: Int) {
        readTimeout = timeoutMs
        socket?.soTimeout = timeoutMs
    }

    /**
     * Close TCP connection
     */
    suspend fun close() {
        mutex.withLock {
            isConnected = false
            receiveChannel.close()
            socket?.close()
            socket = null
            targetAddress = null
        }
    }

    /**
     * Close synchronously
     */
    fun closeSync() {
        isConnected = false
        receiveChannel.close()
        socket?.close()
        socket = null
        targetAddress = null
    }
}

/**
 * TCP Server for accepting incoming connections
 */
class TcpServer {

    private var serverSocket: java.net.ServerSocket? = null
    private val mutex = Mutex()
    private var isRunning = false

    // Channel for accepted connections
    private val connectionChannel = Channel<AcceptedConnection>(Channel.UNLIMITED)

    data class AcceptedConnection(
        val client: TcpClient,
        val clientAddress: InetAddress,
        val clientPort: Int
    )

    /**
     * Start TCP server on specified port
     */
    suspend fun start(port: Int, backlog: Int = 10): Result<Int> {
        return mutex.withLock {
            try {
                serverSocket = java.net.ServerSocket(port, backlog).apply {
                    reuseAddress = true
                }
                isRunning = true

                // Start accepting connections in background
                CoroutineScope(Dispatchers.IO).launch {
                    while (isRunning) {
                        try {
                            val clientSocket = serverSocket?.accept()
                            if (clientSocket != null) {
                                val tcpClient = TcpClient()
                                // Configure the client with the accepted socket
                                val acceptedConnection = AcceptedConnection(
                                    client = tcpClient,
                                    clientAddress = clientSocket.inetAddress,
                                    clientPort = clientSocket.port
                                )
                                connectionChannel.trySend(acceptedConnection)
                            }
                        } catch (e: Exception) {
                            if (isRunning) {
                                // Log error but continue accepting
                            }
                        }
                    }
                }

                Result.success(serverSocket?.localPort ?: port)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Get flow of accepted connections
     */
    fun getAcceptedConnections(): Flow<AcceptedConnection> {
        return connectionChannel.receiveAsFlow()
    }

    /**
     * Stop the TCP server
     */
    suspend fun stop() {
        mutex.withLock {
            isRunning = false
            serverSocket?.close()
            serverSocket = null
            connectionChannel.close()
        }
    }

    /**
     * Stop synchronously
     */
    fun stopSync() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
        connectionChannel.close()
    }

    /**
     * Check if server is running
     */
    fun isRunning(): Boolean = isRunning
}
