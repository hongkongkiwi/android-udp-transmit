package com.udptrigger.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.OutputStream

/**
 * Data class for exporting/importing app configuration and data
 */
@Serializable
data class AppExport(
    val version: Int = 1,
    val exportDate: Long = System.currentTimeMillis(),
    val config: UdpConfigExport,
    val settings: AppSettingsExport,
    val customPresets: List<CustomPreset>
)

@Serializable
data class UdpConfigExport(
    val host: String,
    val port: Int,
    val packetContent: String,
    val hexMode: Boolean,
    val includeTimestamp: Boolean,
    val includeBurstIndex: Boolean
)

@Serializable
data class AppSettingsExport(
    val hapticFeedbackEnabled: Boolean,
    val soundEnabled: Boolean,
    val rateLimitEnabled: Boolean,
    val rateLimitMs: Long,
    val autoReconnect: Boolean,
    val keepScreenOn: Boolean
)

/**
 * Manages export and import of application data
 */
class DataManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Export all app data to JSON format
     */
    suspend fun exportData(
        config: com.udptrigger.ui.UdpConfig,
        settings: AppSettings,
        customPresets: List<CustomPreset>,
        outputStream: OutputStream
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val export = AppExport(
                config = UdpConfigExport(
                    host = config.host,
                    port = config.port,
                    packetContent = config.packetContent,
                    hexMode = config.hexMode,
                    includeTimestamp = config.includeTimestamp,
                    includeBurstIndex = config.includeBurstIndex
                ),
                settings = AppSettingsExport(
                    hapticFeedbackEnabled = settings.hapticFeedbackEnabled,
                    soundEnabled = settings.soundEnabled,
                    rateLimitEnabled = settings.rateLimitEnabled,
                    rateLimitMs = settings.rateLimitMs,
                    autoReconnect = settings.autoReconnect,
                    keepScreenOn = settings.keepScreenOn
                ),
                customPresets = customPresets
            )

            val jsonString = json.encodeToString(export)
            outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
            outputStream.close()

            Result.success("Export successful: ${jsonString.length} bytes")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import app data from JSON format
     */
    suspend fun importData(
        inputStream: java.io.InputStream
    ): Result<AppImport> = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(inputStream.reader())
            val jsonString = reader.use { it.readText() }
            val export = json.decodeFromString<AppExport>(jsonString)

            val import = AppImport(
                config = com.udptrigger.ui.UdpConfig(
                    host = export.config.host,
                    port = export.config.port,
                    packetContent = export.config.packetContent,
                    hexMode = export.config.hexMode,
                    includeTimestamp = export.config.includeTimestamp,
                    includeBurstIndex = export.config.includeBurstIndex
                ),
                settings = AppSettings(
                    hapticFeedbackEnabled = export.settings.hapticFeedbackEnabled,
                    soundEnabled = export.settings.soundEnabled,
                    rateLimitEnabled = export.settings.rateLimitEnabled,
                    rateLimitMs = export.settings.rateLimitMs,
                    autoReconnect = export.settings.autoReconnect,
                    keepScreenOn = export.settings.keepScreenOn
                ),
                customPresets = export.customPresets,
                exportDate = export.exportDate
            )

            Result.success(import)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export packet history to CSV
     */
    suspend fun exportHistoryToCsv(
        history: List<com.udptrigger.ui.PacketHistoryEntry>,
        outputStream: OutputStream
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val csv = buildString {
                appendLine("Timestamp,NanoTime,Type,Success,ErrorMessage,SourceAddress,SourcePort,Data")

                history.forEach { entry ->
                    val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
                        .format(java.util.Date(entry.timestamp))
                    val type = entry.type.name
                    val success = if (entry.success) "true" else "false"
                    val error = entry.errorMessage?.replace("\"", "\"\"") ?: ""
                    val sourceAddr = entry.sourceAddress ?: ""
                    val sourcePort = entry.sourcePort?.toString() ?: ""
                    val data = (entry.data ?: "").replace("\"", "\"\"")

                    appendLine("\"$timestamp\",${entry.nanoTime},$type,$success,\"$error\",\"$sourceAddr\",$sourcePort,\"$data\"")
                }
            }

            outputStream.write(csv.toByteArray(Charsets.UTF_8))
            outputStream.close()

            Result.success("Exported ${history.size} history entries")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class AppImport(
    val config: com.udptrigger.ui.UdpConfig,
    val settings: AppSettings,
    val customPresets: List<CustomPreset>,
    val exportDate: Long
)
