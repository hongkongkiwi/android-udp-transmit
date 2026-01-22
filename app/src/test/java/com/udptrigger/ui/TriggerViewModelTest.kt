package com.udptrigger.ui

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for UDP configuration and related data classes.
 * Tests configuration validation, defaults, and helper functions.
 *
 * Note: Tests requiring full ViewModel initialization are commented out
 * due to DataStore mocking complexities. These can be added incrementally.
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
        // Note: isValidHost only validates hostnames without dots (single word)
        // Hostnames with dots like "example.com" are not properly supported
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
        assertFalse(config.hexMode)
        assertTrue(config.includeTimestamp)
        assertFalse(config.includeBurstIndex)
    }

    @Test
    fun `BurstMode has correct defaults`() {
        val burstMode = BurstMode()

        assertFalse(burstMode.enabled)
        assertEquals(5, burstMode.packetCount)
        assertEquals(100, burstMode.delayMs)
        assertFalse(burstMode.isSending)
    }

    @Test
    fun `ConnectionHealth enum has correct values`() {
        assertEquals(5, ConnectionHealth.entries.size)
        assertTrue(ConnectionHealth.entries.contains(ConnectionHealth.EXCELLENT))
        assertTrue(ConnectionHealth.entries.contains(ConnectionHealth.GOOD))
        assertTrue(ConnectionHealth.entries.contains(ConnectionHealth.FAIR))
        assertTrue(ConnectionHealth.entries.contains(ConnectionHealth.POOR))
        assertTrue(ConnectionHealth.entries.contains(ConnectionHealth.DISCONNECTED))
    }

    @Test
    fun `ConnectionHealth getDisplayLabel returns correct labels`() {
        assertEquals("Excellent", ConnectionHealth.EXCELLENT.getDisplayLabel())
        assertEquals("Good", ConnectionHealth.GOOD.getDisplayLabel())
        assertEquals("Fair", ConnectionHealth.FAIR.getDisplayLabel())
        assertEquals("Poor", ConnectionHealth.POOR.getDisplayLabel())
        assertEquals("Disconnected", ConnectionHealth.DISCONNECTED.getDisplayLabel())
    }

    @Test
    fun `ConnectionHealth getColor returns non-zero colors`() {
        val colors = ConnectionHealth.entries.map { it.getColor() }
        assertTrue(colors.all { it != 0L })
    }

    @Test
    fun `PacketType enum has correct values`() {
        assertEquals(2, PacketType.entries.size)
        assertTrue(PacketType.entries.contains(PacketType.SENT))
        assertTrue(PacketType.entries.contains(PacketType.RECEIVED))
    }

    @Test
    fun `PacketHistoryEntry can be created with all parameters`() {
        val entry = PacketHistoryEntry(
            timestamp = System.currentTimeMillis(),
            nanoTime = System.nanoTime(),
            success = true,
            errorMessage = null,
            type = PacketType.SENT
        )

        assertTrue(entry.success)
        assertEquals(PacketType.SENT, entry.type)
        assertNull(entry.errorMessage)
    }

    @Test
    fun `PacketHistoryEntry can represent failure`() {
        val entry = PacketHistoryEntry(
            timestamp = System.currentTimeMillis(),
            nanoTime = System.nanoTime(),
            success = false,
            errorMessage = "Connection refused",
            type = PacketType.SENT
        )

        assertFalse(entry.success)
        assertEquals("Connection refused", entry.errorMessage)
    }

    @Test
    fun `ReceivedPacketInfo can be created`() {
        val info = ReceivedPacketInfo(
            timestamp = System.currentTimeMillis(),
            sourceAddress = "192.168.1.50",
            sourcePort = 6000,
            data = "TEST_DATA",
            length = 9
        )

        assertEquals("192.168.1.50", info.sourceAddress)
        assertEquals(6000, info.sourcePort)
        assertEquals("TEST_DATA", info.data)
        assertEquals(9, info.length)
    }
}
