package com.udptrigger.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AutomationManager data classes and logic
 */
class AutomationManagerTest {

    @Test
    fun `test Condition equality operators`() {
        // Test equals operator
        val condition1 = AutomationManager.Condition("count", "==", "5")
        val condition2 = AutomationManager.Condition("count", "==", "5")
        assertEquals(condition1, condition2)

        // Test not equals operator
        val condition3 = AutomationManager.Condition("count", "!=", "5")
        assertNotEquals(condition1, condition3)
    }

    @Test
    fun `test ExecutionResult creation`() {
        val result = AutomationManager.ExecutionResult(
            success = true,
            message = "Test passed",
            actionsExecuted = 5
        )
        assertTrue(result.success)
        assertEquals("Test passed", result.message)
        assertEquals(5, result.actionsExecuted)
    }

    @Test
    fun `test ExecutionLog creation`() {
        val log = AutomationManager.ExecutionLog(
            automationId = "test_auto",
            automationName = "Test Automation",
            timestamp = System.currentTimeMillis(),
            status = AutomationManager.ExecutionStatus.SUCCESS,
            message = "Execution successful",
            actionsExecuted = 3,
            durationMs = 100
        )
        assertEquals("test_auto", log.automationId)
        assertEquals("Test Automation", log.automationName)
        assertEquals(AutomationManager.ExecutionStatus.SUCCESS, log.status)
        assertEquals(3, log.actionsExecuted)
        assertEquals(100L, log.durationMs)
    }

    @Test
    fun `test Automation creation with defaults`() {
        val automation = AutomationManager.Automation(
            id = "test_id",
            name = "Test Name",
            description = "Test Description",
            trigger = AutomationManager.TriggerCondition.ButtonPressed("main"),
            actions = emptyList()
        )
        assertEquals("test_id", automation.id)
        assertEquals("Test Name", automation.name)
        assertTrue(automation.enabled)
        assertEquals(0, automation.priority)
        assertEquals(0L, automation.cooldownMs)
        assertEquals(0, automation.executionCount)
    }

    @Test
    fun `test SendUdp action with hex mode`() {
        val action = AutomationManager.AutomationAction.SendUdp(
            host = "192.168.1.100",
            port = 5000,
            content = "AA BB CC DD",
            hexMode = true
        )
        assertEquals("192.168.1.100", action.host)
        assertEquals(5000, action.port)
        assertEquals("AA BB CC DD", action.content)
        assertTrue(action.hexMode)
    }

    @Test
    fun `test HttpRequest action defaults`() {
        val action = AutomationManager.AutomationAction.HttpRequest(
            url = "https://api.example.com/data"
        )
        assertEquals("GET", action.method)
        assertNull(action.body)
        assertTrue(action.headers.isEmpty())
    }

    @Test
    fun `test IncrementVariable action defaults`() {
        val action = AutomationManager.AutomationAction.IncrementVariable(
            name = "counter"
        )
        assertEquals("counter", action.name)
        assertEquals(1, action.by)
    }

    @Test
    fun `test Delay action`() {
        val action = AutomationManager.AutomationAction.Delay(
            durationMs = 1000
        )
        assertEquals(1000L, action.durationMs)
    }

    @Test
    fun `test ShowNotification action defaults`() {
        val action = AutomationManager.AutomationAction.ShowNotification(
            title = "Test Title",
            content = "Test Content"
        )
        assertEquals(0, action.priority)
    }

    @Test
    fun `test Vibrate action defaults`() {
        val action = AutomationManager.AutomationAction.Vibrate()
        assertEquals("default", action.pattern)
        assertEquals(0, action.repeat)
    }

    @Test
    fun `test PlaySound action defaults`() {
        val action = AutomationManager.AutomationAction.PlaySound()
        assertEquals("click", action.soundId)
        assertEquals(1.0f, action.volume, 0.001f)
    }

    @Test
    fun `test LaunchApp action with null package`() {
        val action = AutomationManager.AutomationAction.LaunchApp()
        assertNull(action.packageName)
        assertNull(action.action)
    }

    @Test
    fun `test RunAutomation action`() {
        val action = AutomationManager.AutomationAction.RunAutomation(
            automationId = "nested_automation"
        )
        assertEquals("nested_automation", action.automationId)
    }

    @Test
    fun `test Conditional action with then and else`() {
        val condition = AutomationManager.Condition("count", ">", "0")
        val thenAction = AutomationManager.AutomationAction.SendUdp(
            host = "127.0.0.1",
            port = 5000,
            content = "test"
        )
        val elseAction = AutomationManager.AutomationAction.Delay(100)

        val conditional = AutomationManager.AutomationAction.Conditional(
            condition = condition,
            thenActions = listOf(thenAction),
            elseActions = listOf(elseAction)
        )
        assertEquals(1, conditional.thenActions.size)
        assertEquals(1, conditional.elseActions.size)
    }

    @Test
    fun `test Loop action with count`() {
        val actions = listOf(
            AutomationManager.AutomationAction.Delay(100)
        )
        val loop = AutomationManager.AutomationAction.Loop(
            count = 5,
            actions = actions
        )
        assertEquals(5, loop.count)
        assertNull(loop.condition)
        assertEquals(1, loop.actions.size)
    }

    @Test
    fun `test Log action defaults`() {
        val action = AutomationManager.AutomationAction.Log(
            message = "Test log message"
        )
        assertEquals("INFO", action.level)
    }

    @Test
    fun `test Comment action`() {
        val action = AutomationManager.AutomationAction.Comment(
            text = "This is a comment"
        )
        assertEquals("This is a comment", action.text)
    }

    @Test
    fun `test TriggerCondition PacketReceived with regex`() {
        val trigger = AutomationManager.TriggerCondition.PacketReceived(
            pattern = "^START.*",
            useRegex = true,
            sourceAddress = "192.168.1.50",
            sourcePort = 4000
        )
        assertEquals("^START.*", trigger.pattern)
        assertTrue(trigger.useRegex)
        assertEquals("192.168.1.50", trigger.sourceAddress)
        assertEquals(4000, trigger.sourcePort)
    }

    @Test
    fun `test TriggerCondition Schedule`() {
        val trigger = AutomationManager.TriggerCondition.Schedule(
            cronExpression = "0 9 * * 1-5",
            timezone = "America/New_York"
        )
        assertEquals("0 9 * * 1-5", trigger.cronExpression)
        assertEquals("America/New_York", trigger.timezone)
    }

    @Test
    fun `test TriggerCondition Interval`() {
        val trigger = AutomationManager.TriggerCondition.Interval(
            intervalMs = 60000
        )
        assertEquals(60000L, trigger.intervalMs)
    }

    @Test
    fun `test TriggerCondition Gesture`() {
        val trigger = AutomationManager.TriggerCondition.Gesture(
            gestureType = "double_tap"
        )
        assertEquals("double_tap", trigger.gestureType)
    }

    @Test
    fun `test TriggerCondition NetworkState`() {
        val trigger = AutomationManager.TriggerCondition.NetworkState(
            connected = true,
            ssid = "MyNetwork"
        )
        assertEquals(true, trigger.connected)
        assertEquals("MyNetwork", trigger.ssid)
    }

    @Test
    fun `test TriggerCondition NetworkState null connected for any`() {
        val trigger = AutomationManager.TriggerCondition.NetworkState()
        assertNull(trigger.connected)
        assertNull(trigger.ssid)
    }

    @Test
    fun `test TriggerCondition TimeRange`() {
        val trigger = AutomationManager.TriggerCondition.TimeRange(
            startTime = "09:00",
            endTime = "17:00",
            daysOfWeek = listOf(1, 2, 3, 4, 5) // weekdays only
        )
        assertEquals("09:00", trigger.startTime)
        assertEquals("17:00", trigger.endTime)
        assertEquals(5, trigger.daysOfWeek.size)
        assertFalse(trigger.daysOfWeek.contains(6))
        assertFalse(trigger.daysOfWeek.contains(7))
    }

    @Test
    fun `test TriggerCondition VariableChanged with contains operator`() {
        val trigger = AutomationManager.TriggerCondition.VariableChanged(
            variableName = "status",
            value = "error",
            operator = "contains"
        )
        assertEquals("status", trigger.variableName)
        assertEquals("error", trigger.value)
        assertEquals("contains", trigger.operator)
    }

    @Test
    fun `test TriggerCondition AnyOf`() {
        val condition1 = AutomationManager.TriggerCondition.ButtonPressed("main")
        val condition2 = AutomationManager.TriggerCondition.Gesture("swipe_up")
        val trigger = AutomationManager.TriggerCondition.AnyOf(
            conditions = listOf(condition1, condition2)
        )
        assertEquals(2, trigger.conditions.size)
    }

    @Test
    fun `test TriggerCondition AllOf`() {
        val condition1 = AutomationManager.TriggerCondition.NetworkState(connected = true)
        val condition2 = AutomationManager.TriggerCondition.TimeRange("09:00", "17:00")
        val trigger = AutomationManager.TriggerCondition.AllOf(
            conditions = listOf(condition1, condition2)
        )
        assertEquals(2, trigger.conditions.size)
    }

    @Test
    fun `test ExecutionStatus enum values`() {
        val statuses = AutomationManager.ExecutionStatus.values()
        assertEquals(5, statuses.size)
        assertTrue(statuses.contains(AutomationManager.ExecutionStatus.STARTED))
        assertTrue(statuses.contains(AutomationManager.ExecutionStatus.SUCCESS))
        assertTrue(statuses.contains(AutomationManager.ExecutionStatus.FAILED))
        assertTrue(statuses.contains(AutomationManager.ExecutionStatus.CANCELLED))
        assertTrue(statuses.contains(AutomationManager.ExecutionStatus.TIMEOUT))
    }
}
