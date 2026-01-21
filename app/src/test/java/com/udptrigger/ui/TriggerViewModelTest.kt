package com.udptrigger.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import com.udptrigger.data.AppSettings
import com.udptrigger.data.SettingsDataStore
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for TriggerViewModel.
 * Tests configuration management, packet building, and preset operations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TriggerViewModelTest {

    private lateinit var viewModel: TriggerViewModel
    private lateinit var mockContext: Context
    private lateinit var mockDataStore: SettingsDataStore
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockConnectivityManager: ConnectivityManager

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockContext = mockk(relaxed = true)
        mockDataStore = mockk()
        mockSharedPreferences = mockk()
        mockConnectivityManager = mockk()

        // Mock context for VibratorManager (API 31+)
        every { mockContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) } returns null
        every { mockContext.getSystemService(Context.VIBRATOR_SERVICE) } returns null
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
        every { mockContext.applicationContext } returns mockContext

        // Mock SharedPreferences for PresetsManager
        every { mockContext.getSharedPreferences("udp_presets", Context.MODE_PRIVATE) } returns mockSharedPreferences
        every { mockSharedPreferences.getString("custom_presets", null) } returns null
        every { mockSharedPreferences.edit() } returns mockk(relaxed = true) {
            every { putString(any(), any()) } returns this
        }

        // Mock ConnectivityManager registerNetworkCallback
        every {
            mockConnectivityManager.registerNetworkCallback(
                any(),
                any<ConnectivityManager.NetworkCallback>()
            )
        } just Runs

        // Mock DataStore operations - return empty flows to avoid async issues
        coEvery { mockDataStore.configFlow } returns flowOf(UdpConfig())
        coEvery { mockDataStore.settingsFlow } returns flowOf(
            AppSettings(
                hapticFeedbackEnabled = true,
                soundEnabled = false,
                rateLimitEnabled = true,
                rateLimitMs = 50,
                autoReconnect = false,
                keepScreenOn = false
            )
        )
        coEvery { mockDataStore.saveConfig(any()) } just Runs
        coEvery { mockDataStore.saveHapticFeedback(any()) } just Runs
        coEvery { mockDataStore.saveSoundEnabled(any()) } just Runs
        coEvery { mockDataStore.saveRateLimit(any(), any()) } just Runs
        coEvery { mockDataStore.saveAutoReconnect(any()) } just Runs
        coEvery { mockDataStore.saveKeepScreenOn(any()) } just Runs

        viewModel = TriggerViewModel(mockContext, mockDataStore)

        // Advance dispatcher to process init blocks
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state has default values`() {
        val state = viewModel.state.value

        assertFalse(state.isConnected)
        assertNull(state.lastTriggerTime)
        assertNull(state.error)
        assertFalse(state.soundEnabled)
        assertTrue(state.hapticFeedbackEnabled)
        assertTrue(state.rateLimitEnabled)
        assertEquals(50, state.rateLimitMs)
        assertFalse(state.autoReconnect)
        assertFalse(state.keepScreenOn)
    }

    @Test
    fun `updateConfig changes config in state`() {
        val newConfig = UdpConfig(
            host = "192.168.1.50",
            port = 6000,
            packetContent = "NEW_TRIGGER"
        )

        viewModel.updateConfig(newConfig)

        assertEquals(newConfig, viewModel.state.value.config)
    }

    @Test
    fun `updatePacketOptions changes packet options`() {
        viewModel.updatePacketOptions(
            hexMode = true,
            includeTimestamp = false,
            includeBurstIndex = true
        )

        val config = viewModel.state.value.config
        assertTrue(config.hexMode)
        assertFalse(config.includeTimestamp)
        assertTrue(config.includeBurstIndex)
    }

    @Test
    fun `updateHapticFeedback changes haptic setting`() {
        viewModel.updateHapticFeedback(false)

        assertFalse(viewModel.state.value.hapticFeedbackEnabled)
    }

    @Test
    fun `updateSoundEnabled changes sound setting`() {
        viewModel.updateSoundEnabled(true)

        assertTrue(viewModel.state.value.soundEnabled)
    }

    @Test
    fun `updateRateLimit changes rate limit settings`() {
        viewModel.updateRateLimit(false, 100)

        assertFalse(viewModel.state.value.rateLimitEnabled)
        assertEquals(100, viewModel.state.value.rateLimitMs)
    }

    @Test
    fun `updateAutoReconnect changes auto reconnect setting`() {
        viewModel.updateAutoReconnect(true)

        assertTrue(viewModel.state.value.autoReconnect)
    }

    @Test
    fun `updateKeepScreenOn changes keep screen on setting`() {
        viewModel.updateKeepScreenOn(true)

        assertTrue(viewModel.state.value.keepScreenOn)
    }

    @Test
    fun `updateBurstMode changes burst mode settings`() {
        viewModel.updateBurstMode(
            enabled = true,
            packetCount = 10,
            delayMs = 50
        )

        val burstMode = viewModel.state.value.burstMode
        assertTrue(burstMode.enabled)
        assertEquals(10, burstMode.packetCount)
        assertEquals(50, burstMode.delayMs)
    }

    @Test
    fun `updateBurstMode coerces packet count within bounds`() {
        viewModel.updateBurstMode(
            enabled = true,
            packetCount = 200, // Above max of 100
            delayMs = 50
        )

        assertEquals(100, viewModel.state.value.burstMode.packetCount)
    }

    @Test
    fun `updateBurstMode coerces delay within bounds`() {
        viewModel.updateBurstMode(
            enabled = true,
            packetCount = 5,
            delayMs = 5 // Below min of 10
        )

        assertEquals(10, viewModel.state.value.burstMode.delayMs)
    }

    @Test
    fun `clearHistory resets packet history and stats`() {
        viewModel.clearHistory()

        val state = viewModel.state.value
        assertTrue(state.packetHistory.isEmpty())
        assertEquals(0, state.totalPacketsSent)
        assertEquals(0, state.totalPacketsFailed)
    }

    @Test
    fun `applyPreset updates config when preset exists`() {
        val preset = com.udptrigger.data.PresetsManager.presets.first()

        viewModel.applyPreset(preset.name)

        assertEquals(preset.config.host, viewModel.state.value.config.host)
        assertEquals(preset.config.port, viewModel.state.value.config.port)
    }

    @Test
    fun `getPacketSizePreview returns positive size`() {
        val size = viewModel.getPacketSizePreview()

        assertTrue(size > 0)
    }

    @Test
    fun `getPacketSizeBreakdown returns correct breakdown`() {
        viewModel.updateConfig(
            UdpConfig(
                host = "127.0.0.1",
                port = 5000,
                packetContent = "TEST",
                includeTimestamp = true
            )
        )

        val breakdown = viewModel.getPacketSizeBreakdown()

        assertTrue(breakdown.totalSize > 0)
        assertTrue(breakdown.contentSize > 0)
        assertTrue(breakdown.timestampSize > 0)
    }

    @Test
    fun `hex mode packet size calculation is correct`() {
        viewModel.updateConfig(
            UdpConfig(
                host = "127.0.0.1",
                port = 5000,
                packetContent = "48656C6C6F", // "Hello" in hex
                hexMode = true,
                includeTimestamp = false
            )
        )

        val breakdown = viewModel.getPacketSizeBreakdown()

        assertEquals(5, breakdown.contentSize) // 5 bytes for "Hello"
        assertTrue(breakdown.isHexMode)
    }
}
