package com.udptrigger.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MultiTargetConfig data classes
 */
class MultiTargetConfigTest {

    @Test
    fun `test UdpTarget creation with factory method`() {
        val target = UdpTarget.create("Test Target", "192.168.1.100", 5000)

        assertNotNull(target.id)
        assertEquals("Test Target", target.name)
        assertEquals("192.168.1.100", target.host)
        assertEquals(5000, target.port)
        assertTrue(target.enabled)
        assertNull(target.lastSuccess)
        assertNull(target.lastFailure)
        assertEquals(0, target.consecutiveFailures)
    }

    @Test
    fun `test UdpTarget isValid with valid port`() {
        val target = UdpTarget(
            id = "test",
            name = "Test",
            host = "192.168.1.100",
            port = 5000
        )
        assertTrue(target.isValid())
    }

    @Test
    fun `test UdpTarget isValid with invalid port low`() {
        val target = UdpTarget(
            id = "test",
            name = "Test",
            host = "192.168.1.100",
            port = 0
        )
        assertFalse(target.isValid())
    }

    @Test
    fun `test UdpTarget isValid with invalid port high`() {
        val target = UdpTarget(
            id = "test",
            name = "Test",
            host = "192.168.1.100",
            port = 65536
        )
        assertFalse(target.isValid())
    }

    @Test
    fun `test UdpTarget isValid with blank host`() {
        val target = UdpTarget(
            id = "test",
            name = "Test",
            host = "",
            port = 5000
        )
        assertFalse(target.isValid())
    }

    @Test
    fun `test UdpTarget getDisplayAddress`() {
        val target = UdpTarget(
            id = "test",
            name = "Test",
            host = "192.168.1.100",
            port = 5000
        )
        assertEquals("192.168.1.100:5000", target.getDisplayAddress())
    }

    @Test
    fun `test UdpTarget withStatus success`() {
        val target = UdpTarget(
            id = "test",
            name = "Test",
            host = "192.168.1.100",
            port = 5000,
            consecutiveFailures = 3
        )
        val updated = target.withStatus(true)

        assertNotNull(updated.lastSuccess)
        assertEquals(0, updated.consecutiveFailures)
        assertNull(updated.lastFailure)
    }

    @Test
    fun `test UdpTarget withStatus failure`() {
        val target = UdpTarget(
            id = "test",
            name = "Test",
            host = "192.168.1.100",
            port = 5000,
            consecutiveFailures = 0
        )
        val updated = target.withStatus(false)

        assertNotNull(updated.lastFailure)
        assertEquals(1, updated.consecutiveFailures)
    }

    @Test
    fun `test UdpTarget getHealth unknown when no success`() {
        val target = UdpTarget(
            id = "test",
            name = "Test",
            host = "192.168.1.100",
            port = 5000
        )
        assertEquals(TargetHealth.UNKNOWN, target.getHealth())
    }

    @Test
    fun `test UdpTarget getHealth healthy when recent success`() {
        val target = UdpTarget(
            id = "test",
            name = "Test",
            host = "192.168.1.100",
            port = 5000,
            lastSuccess = System.currentTimeMillis() - 30000 // 30 seconds ago
        )
        assertEquals(TargetHealth.HEALTHY, target.getHealth())
    }

    @Test
    fun `test UdpTarget getHealth stale when older success`() {
        val target = UdpTarget(
            id = "test",
            name = "Test",
            host = "192.168.1.100",
            port = 5000,
            lastSuccess = System.currentTimeMillis() - 120000 // 2 minutes ago
        )
        assertEquals(TargetHealth.STALE, target.getHealth())
    }

    @Test
    fun `test UdpTarget getHealth failed with 3 consecutive failures`() {
        val target = UdpTarget(
            id = "test",
            name = "Test",
            host = "192.168.1.100",
            port = 5000,
            lastSuccess = System.currentTimeMillis() - 60000,
            consecutiveFailures = 3
        )
        assertEquals(TargetHealth.FAILED, target.getHealth())
    }

    @Test
    fun `test UdpTarget getHealth degraded when failure after success`() {
        val target = UdpTarget(
            id = "test",
            name = "Test",
            host = "192.168.1.100",
            port = 5000,
            lastSuccess = System.currentTimeMillis() - 300000,
            lastFailure = System.currentTimeMillis() - 1000
        )
        assertEquals(TargetHealth.DEGRADED, target.getHealth())
    }

    @Test
    fun `test TargetHealth getDisplayLabel`() {
        assertEquals("Unknown", TargetHealth.UNKNOWN.getDisplayLabel())
        assertEquals("Healthy", TargetHealth.HEALTHY.getDisplayLabel())
        assertEquals("Stale", TargetHealth.STALE.getDisplayLabel())
        assertEquals("Degraded", TargetHealth.DEGRADED.getDisplayLabel())
        assertEquals("Failed", TargetHealth.FAILED.getDisplayLabel())
    }

    @Test
    fun `test TargetHealth getColor`() {
        assertEquals(0xFF9E9E9E, TargetHealth.UNKNOWN.getColor())
        assertEquals(0xFF4CAF50, TargetHealth.HEALTHY.getColor())
        assertEquals(0xFFFFC107, TargetHealth.STALE.getColor())
        assertEquals(0xFFFF9800, TargetHealth.DEGRADED.getColor())
        assertEquals(0xFFF44336, TargetHealth.FAILED.getColor())
    }

    @Test
    fun `test MultiTargetConfig creation with defaults`() {
        val config = MultiTargetConfig()

        assertTrue(config.targets.isEmpty())
        assertFalse(config.enabled)
        assertEquals(SendMode.SEQUENTIAL, config.sendMode)
        assertEquals(10L, config.sequentialDelayMs)
    }

    @Test
    fun `test MultiTargetConfig getEnabledTargets`() {
        val target1 = UdpTarget(id = "1", name = "T1", host = "h1", port = 1, enabled = true)
        val target2 = UdpTarget(id = "2", name = "T2", host = "h2", port = 2, enabled = false)
        val target3 = UdpTarget(id = "3", name = "T3", host = "h3", port = 3, enabled = true)

        val config = MultiTargetConfig(targets = listOf(target1, target2, target3))
        val enabled = config.getEnabledTargets()

        assertEquals(2, enabled.size)
        assertEquals("1", enabled[0].id)
        assertEquals("3", enabled[1].id)
    }

    @Test
    fun `test MultiTargetConfig isActive when enabled with targets`() {
        val target = UdpTarget(id = "1", name = "T1", host = "h1", port = 1, enabled = true)
        val config = MultiTargetConfig(targets = listOf(target), enabled = true)

        assertTrue(config.isActive())
    }

    @Test
    fun `test MultiTargetConfig isActive when disabled`() {
        val target = UdpTarget(id = "1", name = "T1", host = "h1", port = 1, enabled = true)
        val config = MultiTargetConfig(targets = listOf(target), enabled = false)

        assertFalse(config.isActive())
    }

    @Test
    fun `test MultiTargetConfig isActive when no enabled targets`() {
        val target = UdpTarget(id = "1", name = "T1", host = "h1", port = 1, enabled = false)
        val config = MultiTargetConfig(targets = listOf(target), enabled = true)

        assertFalse(config.isActive())
    }

    @Test
    fun `test MultiTargetConfig getTargetCount`() {
        val targets = listOf(
            UdpTarget(id = "1", name = "T1", host = "h1", port = 1),
            UdpTarget(id = "2", name = "T2", host = "h2", port = 2)
        )
        val config = MultiTargetConfig(targets = targets)

        assertEquals(2, config.getTargetCount())
        assertEquals(2, config.getEnabledTargetCount())
    }

    @Test
    fun `test MultiTargetConfig addTarget`() {
        val config = MultiTargetConfig()
        val target = UdpTarget.create("New Target", "192.168.1.1", 5000)

        val updated = config.addTarget(target)

        assertEquals(1, updated.targets.size)
        assertEquals("New Target", updated.targets[0].name)
    }

    @Test
    fun `test MultiTargetConfig removeTarget`() {
        val target = UdpTarget(id = "1", name = "T1", host = "h1", port = 1)
        val config = MultiTargetConfig(targets = listOf(target))

        val updated = config.removeTarget("1")

        assertTrue(updated.targets.isEmpty())
    }

    @Test
    fun `test MultiTargetConfig updateTarget`() {
        val target = UdpTarget(id = "1", name = "T1", host = "h1", port = 1)
        val config = MultiTargetConfig(targets = listOf(target))

        val updatedTarget = target.copy(name = "Updated", host = "192.168.1.100")
        val updated = config.updateTarget("1", updatedTarget)

        assertEquals("Updated", updated.targets[0].name)
        assertEquals("192.168.1.100", updated.targets[0].host)
    }

    @Test
    fun `test MultiTargetConfig toggleTarget`() {
        val target = UdpTarget(id = "1", name = "T1", host = "h1", port = 1, enabled = true)
        val config = MultiTargetConfig(targets = listOf(target))

        val toggled = config.toggleTarget("1")

        assertFalse(toggled.targets[0].enabled)
    }

    @Test
    fun `test MultiTargetConfig reorderTargets`() {
        val target1 = UdpTarget(id = "1", name = "T1", host = "h1", port = 1)
        val target2 = UdpTarget(id = "2", name = "T2", host = "h2", port = 2)
        val target3 = UdpTarget(id = "3", name = "T3", host = "h3", port = 3)
        val config = MultiTargetConfig(targets = listOf(target1, target2, target3))

        val reordered = config.reorderTargets(listOf("3", "1", "2"))

        assertEquals("3", reordered.targets[0].id)
        assertEquals("1", reordered.targets[1].id)
        assertEquals("2", reordered.targets[2].id)
    }

    @Test
    fun `test MultiTargetConfig toJson and fromJson`() {
        val target = UdpTarget(id = "1", name = "T1", host = "192.168.1.1", port = 5000)
        val config = MultiTargetConfig(
            targets = listOf(target),
            enabled = true,
            sendMode = SendMode.PARALLEL,
            sequentialDelayMs = 50
        )

        val json = MultiTargetConfig.toJson(config)
        val decoded = MultiTargetConfig.fromJson(json)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.targets.size)
        assertEquals("T1", decoded.targets[0].name)
        assertTrue(decoded.enabled)
        assertEquals(SendMode.PARALLEL, decoded.sendMode)
        assertEquals(50L, decoded.sequentialDelayMs)
    }

    @Test
    fun `test MultiTargetConfig fromJson with invalid json returns null`() {
        val result = MultiTargetConfig.fromJson("invalid json")
        assertNull(result)
    }

    @Test
    fun `test SendMode enum values`() {
        val modes = SendMode.values()
        assertEquals(3, modes.size)
        assertTrue(modes.contains(SendMode.SEQUENTIAL))
        assertTrue(modes.contains(SendMode.PARALLEL))
        assertTrue(modes.contains(SendMode.ROUND_ROBIN))
    }
}
