package com.udptrigger.data

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for PresetsManager.
 * Tests preset loading and basic operations.
 * Note: Full CRUD testing is limited by singleton state management.
 */
class PresetsManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        mockContext = mockk()
        mockPrefs = mockk()
        mockEditor = mockk()

        every { mockContext.getSharedPreferences("udp_presets", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.getString("custom_presets", null) } returns null
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.apply() } just Runs

        // Clear any existing custom presets
        PresetsManager.loadCustomPresets(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `built-in presets are available`() {
        val presets = PresetsManager.presets

        assertTrue(presets.size >= 5)
    }

    @Test
    fun `built-in preset names include expected presets`() {
        val names = PresetsManager.getPresetNames()

        assertTrue(names.contains("Local Network"))
        assertTrue(names.contains("Broadcast"))
        assertTrue(names.contains("Localhost"))
        assertTrue(names.contains("OBS Studio"))
        assertTrue(names.contains("Show Control"))
    }

    @Test
    fun `getPreset returns correct preset by name`() {
        val preset = PresetsManager.getPreset("Localhost")

        assertNotNull(preset)
        assertEquals("Localhost", preset?.name)
        assertEquals("127.0.0.1", preset?.config?.host)
        assertEquals(5000, preset?.config?.port)
    }

    @Test
    fun `getPreset returns null for non-existent preset`() {
        val preset = PresetsManager.getPreset("NonExistent")

        assertNull(preset)
    }

    @Test
    fun `built-in presets are not custom`() {
        val presets = PresetsManager.presets.filter { it.name == "Localhost" }

        assertEquals(1, presets.size)
        assertFalse(presets.first().isCustom)
    }

    @Test
    fun `addCustomPreset adds new preset successfully`() {
        val customPreset = CustomPreset(
            name = "Test_Preset_${System.currentTimeMillis()}",
            host = "10.0.0.1",
            port = 7000,
            packetContent = "CUSTOM",
            description = "Test description"
        )

        val result = PresetsManager.addCustomPreset(mockContext, customPreset)

        assertTrue(result)
    }

    @Test
    fun `addCustomPreset rejects duplicate names`() {
        val name = "Duplicate_Test_${System.currentTimeMillis()}"
        val preset1 = CustomPreset(
            name = name,
            host = "10.0.0.1",
            port = 7000,
            packetContent = "FIRST"
        )

        PresetsManager.addCustomPreset(mockContext, preset1)

        val preset2 = CustomPreset(
            name = name,
            host = "10.0.0.2",
            port = 8000,
            packetContent = "SECOND"
        )

        val result = PresetsManager.addCustomPreset(mockContext, preset2)

        assertFalse(result)
    }

    @Test
    fun `addCustomPreset rejects built-in preset names`() {
        val customPreset = CustomPreset(
            name = "Localhost", // Built-in name
            host = "10.0.0.1",
            port = 7000,
            packetContent = "CUSTOM"
        )

        val result = PresetsManager.addCustomPreset(mockContext, customPreset)

        assertFalse(result)
    }

    @Test
    fun `deleteCustomPreset cannot delete built-in presets`() {
        val result = PresetsManager.deleteCustomPreset(mockContext, "Localhost")

        assertFalse(result)
        // Built-in preset should still exist
        assertNotNull(PresetsManager.getPreset("Localhost"))
    }

    @Test
    fun `deleteCustomPreset returns false for non-existent preset`() {
        val result = PresetsManager.deleteCustomPreset(mockContext, "NonExistent")

        assertFalse(result)
    }

    @Test
    fun `isCustomPreset returns false for built-in presets`() {
        assertFalse(PresetsManager.isCustomPreset("Localhost"))
        assertFalse(PresetsManager.isCustomPreset("Broadcast"))
    }
}
