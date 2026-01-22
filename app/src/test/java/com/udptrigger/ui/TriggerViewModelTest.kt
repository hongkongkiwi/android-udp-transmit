package com.udptrigger.ui

import com.udptrigger.data.UdpConfig
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for UDP configuration.
 * Tests configuration validation, defaults, and helper functions.
 */
class TriggerViewModelTest {

    @Test
    fun `UdpConfig isValid returns true for valid config`() {
        val config = UdpConfig(
            host = "192.168.1.100",
            port = 5000,
            packetContent = "TEST"
        )

        assertTrue(config.isValid())
    }

    @Test
    fun `UdpConfig isValid returns false for invalid port`() {
        val config = UdpConfig(
            host = "192.168.1.100",
            port = 0, // Invalid port
            packetContent = "TEST"
        )

        assertFalse(config.isValid())
    }

    @Test
    fun `UdpConfig isValid returns false for port above 65535`() {
        val config = UdpConfig(
            host = "192.168.1.100",
            port = 70000, // Above max
            packetContent = "TEST"
        )

        assertFalse(config.isValid())
    }

    @Test
    fun `UdpConfig isValid returns false for invalid host`() {
        val config = UdpConfig(
            host = "", // Empty host
            port = 5000,
            packetContent = "TEST"
        )

        assertFalse(config.isValid())
    }

    @Test
    fun `UdpConfig isValidHost returns true for valid IP`() {
        assertTrue(UdpConfig.isValidHost("192.168.1.1"))
        assertTrue(UdpConfig.isValidHost("255.255.255.255"))
        assertTrue(UdpConfig.isValidHost("10.0.0.1"))
        assertTrue(UdpConfig.isValidHost("0.0.0.0"))
        assertTrue(UdpConfig.isValidHost("127.0.0.1"))
    }

    @Test
    fun `UdpConfig isValidHost returns true for valid hostname`() {
        assertTrue(UdpConfig.isValidHost("localhost"))
        assertTrue(UdpConfig.isValidHost("myserver"))
        assertTrue(UdpConfig.isValidHost("server-name"))
    }

    @Test
    fun `UdpConfig isValidHost returns false for invalid host`() {
        assertFalse(UdpConfig.isValidHost(""))
        assertFalse(UdpConfig.isValidHost("   "))
        assertFalse(UdpConfig.isValidHost("192.168.1.999")) // Invalid octet
        assertFalse(UdpConfig.isValidHost("192.168.1")) // Incomplete IP
        assertFalse(UdpConfig.isValidHost("192.168.1.1.1")) // Too many octets
    }

    @Test
    fun `UdpConfig defaults are correct`() {
        val config = UdpConfig()

        assertEquals("192.168.1.100", config.host)
        assertEquals(5000, config.port)
        assertEquals("TRIGGER", config.packetContent)
        assertTrue(config.includeTimestamp)
    }
}
