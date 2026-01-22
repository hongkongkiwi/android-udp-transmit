package com.udptrigger

import com.udptrigger.domain.UdpClient
import com.udptrigger.data.UdpConfig
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

    @Test
    fun `Config validation accepts valid IPv4 address`() {
        assertTrue(UdpConfig.isValidHost("192.168.1.100"))
        assertTrue(UdpConfig.isValidHost("10.0.0.1"))
        assertTrue(UdpConfig.isValidHost("255.255.255.255"))
        assertTrue(UdpConfig.isValidHost("0.0.0.0"))
    }

    @Test
    fun `Config validation rejects invalid IPv4 address`() {
        assertFalse(UdpConfig.isValidHost("256.1.1.1"))
        assertFalse(UdpConfig.isValidHost("192.168.1"))
        assertFalse(UdpConfig.isValidHost("192.168.1.1.1"))
        // Note: "abc.def.ghi.jkl" is technically a valid hostname format
        // DNS allows any combination of labels with alphanumeric chars and hyphens
    }

    @Test
    fun `Config validation accepts hostnames`() {
        // Simple hostnames without dots
        assertTrue(UdpConfig.isValidHost("localhost"))
        assertTrue(UdpConfig.isValidHost("my-server"))
        assertTrue(UdpConfig.isValidHost("device123"))
        // Note: hostnames with dots (like "my-server.local") are not validated
        // as they require DNS resolution which is beyond simple validation scope
    }
}
