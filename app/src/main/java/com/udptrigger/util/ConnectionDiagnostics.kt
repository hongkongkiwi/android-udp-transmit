package com.udptrigger.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Connection Diagnostics Tool for UDP Trigger.
 * Provides comprehensive network testing and troubleshooting.
 */
class ConnectionDiagnostics(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Run full diagnostics on a target host/port
     */
    suspend fun runDiagnostics(
        host: String,
        port: Int,
        timeoutMs: Int = 5000
    ): DiagnosticReport {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            val tests = mutableListOf<DiagnosticTest>()

            // DNS Lookup
            tests.add(testDnsLookup(host))

            // TCP Port Check
            tests.add(testTcpPort(host, port, timeoutMs))

            // Local IP Detection
            tests.add(detectLocalIp())

            // Network Info
            tests.add(getNetworkInfo())

            // Latency Test (if port is open)
            if (tests.any { it.type == TestType.TCP_PORT && it.passed }) {
                tests.add(testLatency(host, port, 3))
            }

            DiagnosticReport(
                targetHost = host,
                targetPort = port,
                tests = tests,
                totalTimeMs = System.currentTimeMillis() - startTime,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Test DNS lookup
     */
    private fun testDnsLookup(host: String): DiagnosticTest {
        val startTime = System.currentTimeMillis()

        return try {
            val addresses = InetAddress.getAllByName(host)
            val resolved = addresses.map { it.hostAddress }.filterNotNull()

            DiagnosticTest(
                type = TestType.DNS_LOOKUP,
                name = "DNS Lookup",
                passed = resolved.isNotEmpty(),
                message = if (resolved.isNotEmpty()) {
                    "Resolved to: ${resolved.take(3).joinToString(", ")}${if (resolved.size > 3) "..." else ""}"
                } else {
                    "Could not resolve hostname"
                },
                details = mapOf(
                    "host" to host,
                    "addresses" to resolved,
                    "time_ms" to (System.currentTimeMillis() - startTime)
                ),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            DiagnosticTest(
                type = TestType.DNS_LOOKUP,
                name = "DNS Lookup",
                passed = false,
                message = "DNS lookup failed: ${e.message}",
                error = e.message,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Test TCP port connectivity
     */
    private fun testTcpPort(host: String, port: Int, timeoutMs: Int): DiagnosticTest {
        val startTime = System.currentTimeMillis()

        return try {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)

                DiagnosticTest(
                    type = TestType.TCP_PORT,
                    name = "TCP Port Test",
                    passed = true,
                    message = "Port $port is open on $host",
                    details = mapOf(
                        "host" to host,
                        "port" to port,
                        "timeout_ms" to timeoutMs,
                        "local_address" to socket.localAddress.hostAddress,
                        "remote_address" to socket.inetAddress.hostAddress
                    ),
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            } finally {
                socket?.close()
            }
        } catch (e: Exception) {
            DiagnosticTest(
                type = TestType.TCP_PORT,
                name = "TCP Port Test",
                passed = false,
                message = "Port $port on $host is not reachable: ${e.message}",
                error = e.javaClass.simpleName,
                details = mapOf(
                    "host" to host,
                    "port" to port,
                    "timeout_ms" to timeoutMs
                ),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Detect local IP addresses
     */
    private fun detectLocalIp(): DiagnosticTest {
        val startTime = System.currentTimeMillis()

        return try {
            val localIPs = mutableListOf<String>()

            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    networkInterface.interfaceAddresses.forEach { address ->
                        val addr = address.address
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            localIPs.add(addr.hostAddress ?: "")
                        }
                    }
                }
            }

            DiagnosticTest(
                type = TestType.LOCAL_IP,
                name = "Local IP Detection",
                passed = localIPs.isNotEmpty(),
                message = if (localIPs.isNotEmpty()) {
                    "Found ${localIPs.size} local IP(s): ${localIPs.joinToString(", ")}"
                } else {
                    "No local IP addresses found"
                },
                details = mapOf<String, Any>(
                    "local_ips" to localIPs,
                    "network_interfaces" to (NetworkInterface.getNetworkInterfaces()?.toList()?.mapNotNull { it.name } ?: emptyList<String>())
                ),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            DiagnosticTest(
                type = TestType.LOCAL_IP,
                name = "Local IP Detection",
                passed = false,
                message = "Failed to detect local IP: ${e.message}",
                error = e.message,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Get network information
     */
    private fun getNetworkInfo(): DiagnosticTest {
        val startTime = System.currentTimeMillis()

        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val info = mutableMapOf<String, Any>()

            // Network state
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

            info["is_connected"] = capabilities != null
            info["network_type"] = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> "Unknown"
            }

            // WiFi specific
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                try {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val wifiInfo = wifiManager.connectionInfo
                    info["wifi_ssid"] = wifiInfo.ssid?.removeSurrounding("\"") ?: "Unknown"
                    info["wifi_signal_dbm"] = wifiInfo.rssi
                    info["wifi_link_speed"] = wifiInfo.linkSpeed
                } catch (e: Exception) {
                    info["wifi_error"] = (e.message ?: "Unknown error")
                }
            }

            // VPN detection
            info["vpn_active"] = VpnManager.isVpnActive(context)

            // Cleartext allowed
            info["cleartext_allowed"] = VpnManager.isCleartextAllowed(context)

            DiagnosticTest(
                type = TestType.NETWORK_INFO,
                name = "Network Information",
                passed = capabilities != null,
                message = "Network: ${info["network_type"]}, Connected: ${info["is_connected"]}",
                details = info,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            DiagnosticTest(
                type = TestType.NETWORK_INFO,
                name = "Network Information",
                passed = false,
                message = "Failed to get network info: ${e.message}",
                error = e.message,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Test latency to host
     */
    private suspend fun testLatency(host: String, port: Int, packets: Int): DiagnosticTest {
        val startTime = System.currentTimeMillis()
        val latencies = mutableListOf<Long>()

        repeat(packets) {
            val packetStart = System.currentTimeMillis()
            try {
                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(host, port), 3000)
                latencies.add(System.currentTimeMillis() - packetStart)
                socket.close()
            } catch (e: Exception) {
                latencies.add(-1)
            }
            if (it < packets - 1) delay(100)
        }

        val validLatencies = latencies.filter { it > 0 }
        val avgLatency = if (validLatencies.isNotEmpty()) validLatencies.average() else 0.0

        return DiagnosticTest(
            type = TestType.LATENCY,
            name = "Latency Test",
            passed = validLatencies.isNotEmpty(),
            message = buildString {
                append("Sent $packets packets, ")
                append("${validLatencies.size} received. ")
                append("Avg: ${"%.1f".format(avgLatency)}ms")
            },
            details = mapOf<String, Any>(
                "packets_sent" to packets,
                "packets_received" to validLatencies.size,
                "latencies_ms" to latencies,
                "avg_latency_ms" to avgLatency,
                "min_latency_ms" to (validLatencies.minOrNull() ?: 0L),
                "max_latency_ms" to (validLatencies.maxOrNull() ?: 0L)
            ),
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Ping a host
     */
    suspend fun ping(host: String, count: Int = 4, timeout: Int = 5000): PingResult {
        return withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("ping", "-c", count.toString(), "-W", (timeout / 1000).toString(), host)
                )

                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                val latencyRegex = "([0-9.]+)/([0-9.]+)/([0-9.]+)".toRegex()
                val match = latencyRegex.find(output)

                val stats = match?.let {
                    val values = it.groupValues
                    PingStats(
                        min = values[1].toDoubleOrNull() ?: 0.0,
                        avg = values[2].toDoubleOrNull() ?: 0.0,
                        max = values[3].toDoubleOrNull() ?: 0.0,
                        loss = output.contains("100% packet loss")
                    )
                }

                PingResult(
                    host = host,
                    success = exitCode == 0,
                    output = output,
                    stats = stats,
                    error = if (exitCode != 0) "Ping failed with exit code $exitCode" else null
                )
            } catch (e: Exception) {
                PingResult(
                    host = host,
                    success = false,
                    output = null,
                    error = "Ping failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Scan ports on a host
     */
    suspend fun scanPorts(
        host: String,
        ports: List<Int>,
        timeoutMs: Int = 1000
    ): List<PortScanResult> = coroutineScope {
        ports.map { port ->
            async {
                val startTime = System.currentTimeMillis()
                try {
                    val socket = Socket()
                    socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
                    socket.close()

                    PortScanResult(
                        port = port,
                        isOpen = true,
                        responseTimeMs = System.currentTimeMillis() - startTime,
                        service = getCommonServiceName(port)
                    )
                } catch (e: Exception) {
                    PortScanResult(
                        port = port,
                        isOpen = false,
                        responseTimeMs = null,
                        service = null
                    )
                }
            }
        }.awaitAll()
    }

    /**
     * Get common service name for a port
     */
    private fun getCommonServiceName(port: Int): String {
        return when (port) {
            21 -> "FTP"
            22 -> "SSH"
            23 -> "Telnet"
            25 -> "SMTP"
            53 -> "DNS"
            80 -> "HTTP"
            110 -> "POP3"
            143 -> "IMAP"
            443 -> "HTTPS"
            465 -> "SMTPS"
            587 -> "SMTP/TLS"
            993 -> "IMAPS"
            995 -> "POP3S"
            3306 -> "MySQL"
            3389 -> "RDP"
            5000 -> "UDP/General"
            5060 -> "SIP"
            8080 -> "HTTP-Proxy"
            8443 -> "HTTPS-Alt"
            else -> "Unknown"
        }
    }

    /**
     * Get recommended fixes based on test results
     */
    fun getRecommendedFixes(report: DiagnosticReport): List<FixRecommendation> {
        val fixes = mutableListOf<FixRecommendation>()

        // Check DNS
        val dnsTest = report.tests.find { it.type == TestType.DNS_LOOKUP }
        if (dnsTest?.passed == false) {
            fixes.add(
                FixRecommendation(
                    title = "Check hostname",
                    description = "The hostname '$report.targetHost' could not be resolved. Check for typos or use an IP address instead.",
                    action = "Replace hostname with IP address in configuration"
                )
            )
        }

        // Check TCP port
        val tcpTest = report.tests.find { it.type == TestType.TCP_PORT }
        if (tcpTest?.passed == false) {
            fixes.add(
                FixRecommendation(
                    title = "Check target port",
                    description = "Port ${report.targetPort} on ${report.targetHost} is not reachable. The host may be offline or the port may be closed.",
                    action = "Verify the target device is on and the port is open"
                )
            )
        }

        // Check VPN
        val networkTest = report.tests.find { it.type == TestType.NETWORK_INFO }
        if (networkTest?.details?.get("vpn_active") == true) {
            fixes.add(
                FixRecommendation(
                    title = "VPN may affect connectivity",
                    description = "A VPN is active which may be blocking or intercepting traffic.",
                    action = "Consider disabling VPN or configuring it to allow UDP Trigger traffic"
                )
            )
        }

        // Check cleartext
        if (networkTest?.details?.get("cleartext_allowed") == false) {
            fixes.add(
                FixRecommendation(
                    title = "Cleartext traffic blocked",
                    description = "Cleartext (non-HTTPS) traffic is not allowed on this device.",
                    action = "This may affect some UDP operations. Consider using a VPN."
                )
            )
        }

        return fixes
    }
}

/**
 * Diagnostic report
 */
data class DiagnosticReport(
    val targetHost: String,
    val targetPort: Int,
    val tests: List<DiagnosticTest>,
    val totalTimeMs: Long,
    val timestamp: Long
) {
    val allPassed: Boolean get() = tests.all { it.passed }
    val passedTests: Int get() = tests.count { it.passed }
    val failedTests: Int get() = tests.count { !it.passed }
}

/**
 * Individual diagnostic test result
 */
data class DiagnosticTest(
    val type: TestType,
    val name: String,
    val passed: Boolean,
    val message: String,
    val details: Map<String, Any> = emptyMap(),
    val error: String? = null,
    val executionTimeMs: Long
)

/**
 * Test types
 */
enum class TestType {
    DNS_LOOKUP,
    TCP_PORT,
    LOCAL_IP,
    NETWORK_INFO,
    LATENCY,
    UDP_TEST
}

/**
 * Ping result
 */
data class PingResult(
    val host: String,
    val success: Boolean,
    val output: String? = null,
    val stats: PingStats? = null,
    val error: String? = null
)

/**
 * Ping statistics
 */
data class PingStats(
    val min: Double,
    val avg: Double,
    val max: Double,
    val loss: Boolean
)

/**
 * Port scan result
 */
data class PortScanResult(
    val port: Int,
    val isOpen: Boolean,
    val responseTimeMs: Long?,
    val service: String?
)

/**
 * Fix recommendation
 */
data class FixRecommendation(
    val title: String,
    val description: String,
    val action: String
)
