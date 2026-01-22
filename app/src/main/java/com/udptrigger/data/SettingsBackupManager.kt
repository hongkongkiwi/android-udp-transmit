package com.udptrigger.data

import android.content.Context
import com.udptrigger.ui.UdpConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings Backup Manager for backup/restore functionality.
 * Provides JSON-based export/import of all settings.
 */
class SettingsBackupManager(private val context: Context) {

    /**
     * Create a complete backup of all settings
     */
    fun createBackup(): BackupResult {
        return try {
            val backupData = collectAllSettings()

            BackupResult.Success(
                data = backupData,
                timestamp = System.currentTimeMillis(),
                size = backupData.toString().length
            )
        } catch (e: Exception) {
            BackupResult.Error("Failed to create backup: ${e.message}")
        }
    }

    /**
     * Collect all settings into a single JSON object
     */
    private fun collectAllSettings(): JSONObject {
        val settings = JSONObject()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        settings.put("app_name", "UDP Trigger")
        settings.put("backup_version", 1)
        settings.put("created_at", timestamp)
        settings.put("android_version", android.os.Build.VERSION.RELEASE)

        // Main settings
        val mainSettings = JSONObject()
        val settingsDataStore = SettingsDataStore(context)

        try {
            val config: UdpConfig = runBlocking { settingsDataStore.configFlow.first() }
            mainSettings.put("host", config.host)
            mainSettings.put("port", config.port)
            mainSettings.put("packet_content", config.packetContent)
            mainSettings.put("hex_mode", config.hexMode)
            mainSettings.put("include_timestamp", config.includeTimestamp)
            mainSettings.put("include_burst_index", config.includeBurstIndex)
        } catch (e: Exception) {
            mainSettings.put("error", "Failed: ${e.message}")
        }

        settings.put("main_config", mainSettings)

        // Presets - use the presets object directly
        val presetsArray = JSONArray()
        val allPresets = PresetsManager.presets
        allPresets.forEach { preset ->
            val customPreset = if (preset.isCustom) {
                PresetsManager.getCustomPreset(preset.name)
            } else {
                null
            }
            val presetJson = JSONObject().apply {
                put("name", preset.name)
                put("description", preset.description)
                put("host", preset.config.host)
                put("port", preset.config.port)
                put("content", preset.config.packetContent)
                put("hex_mode", preset.config.hexMode)
                put("is_custom", preset.isCustom)
            }
            presetsArray.put(presetJson)
        }
        settings.put("presets", presetsArray)

        // Multi-target config
        val multiTarget = JSONObject()
        try {
            val mtConfig: MultiTargetConfig = runBlocking { settingsDataStore.multiTargetConfigFlow.first() }
            multiTarget.put("enabled", mtConfig.enabled)
            multiTarget.put("send_mode", mtConfig.sendMode.name)
            multiTarget.put("sequential_delay_ms", mtConfig.sequentialDelayMs)
        } catch (e: Exception) {
            multiTarget.put("error", "Failed: ${e.message}")
        }
        settings.put("multi_target_config", multiTarget)

        // Macros
        val macroManager = MacroManager(context)
        val macrosJson = runBlocking { macroManager.exportMacros() }
        settings.put("macros", JSONArray(macrosJson))

        // Quick hosts
        val quickHostsManager = QuickHostsManager(context)
        val hostsJson = runBlocking { quickHostsManager.exportFavorites() }
        settings.put("quick_hosts", JSONArray(hostsJson))

        return settings
    }

    /**
     * Restore settings from backup data
     */
    fun restoreBackup(backupData: JSONObject): RestoreResult {
        return try {
            val settingsDataStore = SettingsDataStore(context)

            // Restore main config
            backupData.optJSONObject("main_config")?.let { mainConfig ->
                val config = UdpConfig(
                    host = mainConfig.optString("host", "192.168.1.100"),
                    port = mainConfig.optInt("port", 5000),
                    packetContent = mainConfig.optString("packet_content", "TRIGGER"),
                    hexMode = mainConfig.optBoolean("hex_mode", false),
                    includeTimestamp = mainConfig.optBoolean("include_timestamp", true),
                    includeBurstIndex = mainConfig.optBoolean("include_burst_index", false)
                )
                runBlocking { settingsDataStore.saveConfig(config) }
            }

            RestoreResult.Success(
                itemsRestored = countRestorableItems(backupData),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            RestoreResult.Error("Failed to restore backup: ${e.message}")
        }
    }

    private fun countRestorableItems(backupData: JSONObject): Int {
        var count = 0
        if (backupData.has("main_config")) count++
        if (backupData.has("presets")) count++
        if (backupData.has("multi_target_config")) count++
        if (backupData.has("macros")) count++
        if (backupData.has("quick_hosts")) count++
        return count
    }

    /**
     * Export backup to string
     */
    fun exportBackupToString(): String {
        val backup = createBackup()
        return when (backup) {
            is BackupResult.Success -> backup.data.toString(2)
            is BackupResult.Error -> ""
        }
    }

    /**
     * Export backup to encrypted string
     */
    fun exportEncryptedBackup(password: String): EncryptedBackupResult {
        return try {
            val backupJson = exportBackupToString()
            if (backupJson.isEmpty()) {
                return EncryptedBackupResult.Error("Failed to create backup")
            }

            when (val encrypted = BackupEncryptionManager.encryptWithPassword(backupJson, password)) {
                is BackupEncryptionManager.EncryptionResult.Success -> {
                    EncryptedBackupResult.Success(
                        encryptedData = encrypted.encryptedData,
                        timestamp = System.currentTimeMillis()
                    )
                }
                is BackupEncryptionManager.EncryptionResult.Error -> {
                    EncryptedBackupResult.Error(encrypted.message)
                }
            }
        } catch (e: Exception) {
            EncryptedBackupResult.Error("Encryption failed: ${e.message}")
        }
    }

    /**
     * Import encrypted backup from string
     */
    fun importEncryptedBackup(encryptedJson: String, password: String): RestoreResult {
        return try {
            when (val decrypted = BackupEncryptionManager.decryptWithPassword(encryptedJson, password)) {
                is BackupEncryptionManager.DecryptionResult.Success -> {
                    importBackupFromString(decrypted.decryptedData)
                }
                is BackupEncryptionManager.DecryptionResult.Error -> {
                    RestoreResult.Error(decrypted.message)
                }
            }
        } catch (e: Exception) {
            RestoreResult.Error("Invalid encrypted backup: ${e.message}")
        }
    }

    /**
     * Import backup from string
     */
    fun importBackupFromString(json: String): RestoreResult {
        return try {
            val backupData = JSONObject(json)
            restoreBackup(backupData)
        } catch (e: Exception) {
            RestoreResult.Error("Invalid backup format: ${e.message}")
        }
    }
}

/**
 * Backup result
 */
sealed class BackupResult {
    data class Success(
        val data: JSONObject,
        val timestamp: Long,
        val size: Int
    ) : BackupResult()

    data class Error(val message: String) : BackupResult()
}

/**
 * Restore result
 */
sealed class RestoreResult {
    data class Success(
        val itemsRestored: Int,
        val timestamp: Long
    ) : RestoreResult()

    data class Error(val message: String) : RestoreResult()
}

/**
 * Encrypted backup result
 */
sealed class EncryptedBackupResult {
    data class Success(
        val encryptedData: String,
        val timestamp: Long
    ) : EncryptedBackupResult()

    data class Error(val message: String) : EncryptedBackupResult()
}
