package com.udptrigger.data

import android.content.Context
import android.content.SharedPreferences
import com.udptrigger.ui.UdpConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Pre-configured UDP presets for common use cases
 */
data class UdpPreset(
    val name: String,
    val config: UdpConfig,
    val description: String,
    val isCustom: Boolean = false
)

@Serializable
data class CustomPreset(
    val name: String,
    val host: String,
    val port: Int,
    val packetContent: String,
    val hexMode: Boolean = false,
    val includeTimestamp: Boolean = true,
    val includeBurstIndex: Boolean = false,
    val description: String = ""
)

object PresetsManager {

    private val json = Json { ignoreUnknownKeys = true }
    private const val CUSTOM_PRESETS_KEY = "custom_presets"

    // Built-in presets (read-only)
    private val builtInPresets: List<UdpPreset> = listOf(
        UdpPreset(
            name = "Local Network",
            config = UdpConfig(
                host = "192.168.1.100",
                port = 5000,
                packetContent = "TRIGGER"
            ),
            description = "Default local network destination"
        ),
        UdpPreset(
            name = "Broadcast",
            config = UdpConfig(
                host = "255.255.255.255",
                port = 5000,
                packetContent = "TRIGGER"
            ),
            description = "Broadcast to all devices on network"
        ),
        UdpPreset(
            name = "Localhost",
            config = UdpConfig(
                host = "127.0.0.1",
                port = 5000,
                packetContent = "TRIGGER"
            ),
            description = "Local loopback for testing"
        ),
        UdpPreset(
            name = "OBS Studio",
            config = UdpConfig(
                host = "127.0.0.1",
                port = 4444,
                packetContent = "TRIGGER"
            ),
            description = "OBS WebSocket/UDP plugin"
        ),
        UdpPreset(
            name = "Show Control",
            config = UdpConfig(
                host = "192.168.1.50",
                port = 3333,
                packetContent = "GO"
            ),
            description = "Typical show control system"
        )
    )

    private val _customPresets = MutableStateFlow<List<CustomPreset>>(emptyList())
    val customPresets: StateFlow<List<CustomPreset>> = _customPresets.asStateFlow()

    // Combined presets (built-in + custom)
    val presets: List<UdpPreset>
        get() = builtInPresets + _customPresets.value.map { custom ->
            UdpPreset(
                name = custom.name,
                config = UdpConfig(
                    host = custom.host,
                    port = custom.port,
                    packetContent = custom.packetContent,
                    hexMode = custom.hexMode,
                    includeTimestamp = custom.includeTimestamp,
                    includeBurstIndex = custom.includeBurstIndex
                ),
                description = custom.description.ifEmpty { "Custom preset" },
                isCustom = true
            )
        }

    fun getPreset(name: String): UdpPreset? {
        return presets.find { it.name == name }
    }

    fun getPresetNames(): List<String> {
        return presets.map { it.name }
    }

    /**
     * Load custom presets from SharedPreferences
     */
    fun loadCustomPresets(context: Context) {
        val prefs = context.getSharedPreferences("udp_presets", Context.MODE_PRIVATE)
        val customPresetsJson = prefs.getString(CUSTOM_PRESETS_KEY, null)
        if (customPresetsJson != null) {
            try {
                _customPresets.value = json.decodeFromString<List<CustomPreset>>(customPresetsJson)
            } catch (e: Exception) {
                _customPresets.value = emptyList()
            }
        }
    }

    /**
     * Save custom presets to SharedPreferences
     */
    private fun saveCustomPresets(context: Context) {
        val prefs = context.getSharedPreferences("udp_presets", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val presetsJson = json.encodeToString(_customPresets.value)
        editor.putString(CUSTOM_PRESETS_KEY, presetsJson)
        editor.apply()
    }

    /**
     * Add a new custom preset
     * @return true if added successfully, false if name already exists
     */
    fun addCustomPreset(context: Context, preset: CustomPreset): Boolean {
        // Check for duplicate names
        if (_customPresets.value.any { it.name == preset.name }) {
            return false
        }
        // Also check against built-in presets
        if (builtInPresets.any { it.name == preset.name }) {
            return false
        }
        _customPresets.value = _customPresets.value + preset
        saveCustomPresets(context)
        return true
    }

    /**
     * Update an existing custom preset
     */
    fun updateCustomPreset(context: Context, oldName: String, preset: CustomPreset): Boolean {
        val existing = _customPresets.value.find { it.name == oldName }
        if (existing == null) return false

        val updated = _customPresets.value.map {
            if (it.name == oldName) preset else it
        }
        _customPresets.value = updated
        saveCustomPresets(context)
        return true
    }

    /**
     * Delete a custom preset
     * @return true if deleted successfully, false if preset not found or is built-in
     */
    fun deleteCustomPreset(context: Context, name: String): Boolean {
        // Can't delete built-in presets
        if (builtInPresets.any { it.name == name }) {
            return false
        }
        val filtered = _customPresets.value.filterNot { it.name == name }
        if (filtered.size == _customPresets.value.size) {
            return false // Not found
        }
        _customPresets.value = filtered
        saveCustomPresets(context)
        return true
    }

    /**
     * Get a custom preset by name
     */
    fun getCustomPreset(name: String): CustomPreset? {
        return _customPresets.value.find { it.name == name }
    }

    /**
     * Check if a preset name is a custom preset
     */
    fun isCustomPreset(name: String): Boolean {
        return _customPresets.value.any { it.name == name }
    }
}
