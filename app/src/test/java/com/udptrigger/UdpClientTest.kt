package com.udptrigger

import com.udptrigger.domain.UdpClient
import com.udptrigger.ui.UdpConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the UDP client.
 * Note: These tests use runBlocking for suspend function compatibility.
 */
class UdpClientTest {

    @Test
    fun `UdpClient initializes correctly`() = runBlocking {
        val client = UdpClient()
        client.initialize("127.0.0.1", 5000)
        // Should not throw, initialization successful
        client.close()
    }

    @Test
    fun `UdpClient send fails before initialization`() = runBlocking {
        val client = UdpClient()
        val data = "test".toByteArray()
        val result = client.send(data)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `UdpClient sendWithTimestamp fails before initialization`() = runBlocking {
        val client = UdpClient()
        val result = client.sendWithTimestamp()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `UdpClient can be closed multiple times safely`() = runBlocking {
        val client = UdpClient()
        client.initialize("127.0.0.1", 5000)
        client.close()
        client.close() // Should not throw
    }

    @Test
    fun `Config is stored correctly`() {
        val config = UdpConfig(
            host = "192.168.1.100",
            port = 8080,
            packetContent = "TEST"
        )
        assertEquals("192.168.1.100", config.host)
        assertEquals(8080, config.port)
        assertEquals("TEST", config.packetContent)
    }

    @Test
    fun `Config validation accepts valid ports`() {
        val config = UdpConfig(host = "localhost", port = 5000)
        assertTrue(config.isValid())
    }

    @Test
    fun `Config validation rejects port 0`() {
        val config = UdpConfig(host = "localhost", port = 0)
        assertFalse(config.isValid())
    }

    @Test
    fun `Config validation rejects port above 65535`() {
        val config = UdpConfig(host = "localhost", port = 70000)
        assertFalse(config.isValid())
    }

    @Test
    fun `Config validation rejects empty host`() {
        val config = UdpConfig(host = "", port = 5000)
        assertFalse(config.isValid())
    }
}
