package com.udptrigger.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.udptrigger.ui.UdpConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

/**
 * GDPR Data Deletion Manager.
 * Provides comprehensive data clearing for GDPR compliance and user privacy.
 */
class DataDeletionManager(private val context: Context) {

    private val dataStore: DataStore<Preferences> = context.dataStore

    /**
     * Result of data deletion operation
     */
    sealed class DeletionResult {
        data class Success(
            val deletedItems: List<String>,
            val timestamp: Long
        ) : DeletionResult()
        data class Error(val message: String) : DeletionResult()
    }

    /**
     * Get list of all data categories stored by the app
     */
    fun getDataCategories(): List<DataCategory> {
        return listOf(
            DataCategory(
                id = "config",
                name = "Connection Configuration",
                description = "Host, port, packet content, and mode settings",
                icon = "settings"
            ),
            DataCategory(
                id = "presets",
                name = "Custom Presets",
                description = "User-created presets and favorites",
                icon = "bookmark"
            ),
            DataCategory(
                id = "history",
                name = "Packet History",
                description = "Sent and received packet records",
                icon = "history"
            ),
            DataCategory(
                id = "statistics",
                name = "Usage Statistics",
                description = "Packet counts, success rates, latency data",
                icon = "analytics"
            ),
            DataCategory(
                id = "macros",
                name = "Macros & Scripts",
                description = "Custom automation macros and scripts",
                icon = "code"
            ),
            DataCategory(
                id = "quick_hosts",
                name = "Quick Hosts",
                description = "Saved host favorites and quick connections",
                icon = "network"
            ),
            DataCategory(
                id = "crash_reports",
                name = "Crash Reports",
                description = "Local crash logs and error reports",
                icon = "bug_report"
            ),
            DataCategory(
                id = "templates",
                name = "Packet Templates",
                description = "Custom packet templates",
                icon = "description"
            )
        )
    }

    /**
     * Delete all app data (factory reset)
     */
    fun deleteAllData(): DeletionResult {
        return try {
            val deletedItems = mutableListOf<String>()

            // Clear all DataStore data
            runBlocking {
                dataStore.edit { preferences ->
                    preferences.clear()
                }
            }
            deletedItems.add("All preferences")

            // Clear SharedPreferences
            clearSharedPreferences()
            deletedItems.add("Shared preferences")

            // Clear custom preset data
            clearCustomPresets()
            deletedItems.add("Custom presets")

            // Clear crash reports
            clearCrashReports()
            deletedItems.add("Crash reports")

            // Clear logs
            clearLogs()
            deletedItems.add("Logs")

            DeletionResult.Success(
                deletedItems = deletedItems,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            DeletionResult.Error("Failed to delete data: ${e.message}")
        }
    }

    /**
     * Delete specific data category
     */
    fun deleteCategory(categoryId: String): DeletionResult {
        return when (categoryId) {
            "config" -> deleteConfig()
            "presets" -> deletePresets()
            "history" -> deleteHistory()
            "statistics" -> deleteStatistics()
            "macros" -> deleteMacros()
            "quick_hosts" -> deleteQuickHosts()
            "crash_reports" -> deleteCrashReports()
            "templates" -> deleteTemplates()
            else -> DeletionResult.Error("Unknown category: $categoryId")
        }
    }

    /**
     * Export user data for GDPR data portability
     */
    fun exportUserData(): String {
        return buildString {
            appendLine("=== UDP Trigger User Data Export ===")
            appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            appendLine()

            // Export configuration
            runBlocking {
                val config = dataStore.data.first()
                appendLine("=== Connection Configuration ===")
                appendLine("Host: ${config[ConfigKeys.HOST] ?: "not set"}")
                appendLine("Port: ${config[ConfigKeys.PORT] ?: "5000"}")
                appendLine("Packet Content: ${config[ConfigKeys.PACKET_CONTENT] ?: "TRIGGER"}")
                appendLine("Hex Mode: ${config[ConfigKeys.HEX_MODE] ?: "false"}")
                appendLine("Include Timestamp: ${config[ConfigKeys.INCLUDE_TIMESTAMP] ?: "true"}")
                appendLine("Include Burst Index: ${config[ConfigKeys.INCLUDE_BURST_INDEX] ?: "false"}")
                appendLine()

                // Export connection settings
                appendLine("=== Connection Settings ===")
                appendLine("Auto Reconnect: ${config[DataStoreKeys.AUTO_RECONNECT] ?: "false"}")
                appendLine("Auto Connect On Startup: ${config[DataStoreKeys.AUTO_CONNECT_ON_STARTUP] ?: "false"}")
                appendLine("Keep Screen On: ${config[DataStoreKeys.KEEP_SCREEN_ON] ?: "false"}")
                appendLine("Wake Lock Enabled: ${config[DataStoreKeys.WAKE_LOCK_ENABLED] ?: "false"}")
                appendLine()

                // Export feedback settings
                appendLine("=== Feedback Settings ===")
                appendLine("Haptic Feedback: ${config[DataStoreKeys.HAPTIC_FEEDBACK] ?: "true"}")
                appendLine("Sound Enabled: ${config[DataStoreKeys.SOUND_ENABLED] ?: "false"}")
                appendLine("Rate Limit Enabled: ${config[DataStoreKeys.RATE_LIMIT_ENABLED] ?: "true"}")
                appendLine("Rate Limit (ms): ${config[DataStoreKeys.RATE_LIMIT_MS] ?: "50"}")
                appendLine()

                // Export theme settings
                appendLine("=== Theme Settings ===")
                appendLine("Dark Theme: ${config[DataStoreKeys.DARK_THEME] ?: "system"}")
                appendLine("Use Dynamic Colors: ${config[DataStoreKeys.USE_DYNAMIC_COLORS] ?: "true"}")
                appendLine()

                // Export packet history count
                val historyJson = config[DataStoreKeys.PACKET_HISTORY]
                if (historyJson != null && historyJson.isNotEmpty()) {
                    appendLine("=== Packet History ===")
                    appendLine("History data exists (JSON format)")
                    appendLine("History size: ${historyJson.length} characters")
                    appendLine()
                }

                // Export statistics
                val statsSent = config[DataStoreKeys.STATS_SENT]
                val statsReceived = config[DataStoreKeys.STATS_RECEIVED]
                val statsFailed = config[DataStoreKeys.STATS_FAILED]
                if (statsSent != null || statsReceived != null || statsFailed != null) {
                    appendLine("=== Usage Statistics ===")
                    appendLine("Packets Sent: ${statsSent ?: "0"}")
                    appendLine("Packets Received: ${statsReceived ?: "0"}")
                    appendLine("Packets Failed: ${statsFailed ?: "0"}")
                    appendLine()
                }

                // Export quick hosts
                val quickHostsJson = config[DataStoreKeys.QUICK_HOSTS]
                if (quickHostsJson != null && quickHostsJson.isNotEmpty()) {
                    appendLine("=== Quick Hosts ===")
                    appendLine("Quick hosts data exists (JSON format)")
                    appendLine("Data size: ${quickHostsJson.length} characters")
                    appendLine()
                }

                // Export macros
                val macrosJson = config[DataStoreKeys.MACROS]
                if (macrosJson != null && macrosJson.isNotEmpty()) {
                    appendLine("=== Macros ===")
                    appendLine("Macros data exists (JSON format)")
                    appendLine("Data size: ${macrosJson.length} characters")
                    appendLine()
                }

                // Export templates
                val templatesJson = config[DataStoreKeys.PACKET_TEMPLATES]
                if (templatesJson != null && templatesJson.isNotEmpty()) {
                    appendLine("=== Packet Templates ===")
                    appendLine("Templates data exists (JSON format)")
                    appendLine("Data size: ${templatesJson.length} characters")
                    appendLine()
                }

                // Export automation rules
                val automationJson = config[DataStoreKeys.AUTOMATION_RULES]
                if (automationJson != null && automationJson.isNotEmpty()) {
                    appendLine("=== Automation Rules ===")
                    appendLine("Automation rules data exists (JSON format)")
                    appendLine("Data size: ${automationJson.length} characters")
                    appendLine()
                }
            }

            // Export presets from SharedPreferences
            appendLine("=== Custom Presets ===")
            val presetsPrefs = context.getSharedPreferences("custom_presets", Context.MODE_PRIVATE)
            val presetsJson = presetsPrefs.getString("presets", null)
            if (presetsJson != null) {
                appendLine("Custom presets data exists (JSON format)")
                appendLine("Data size: ${presetsJson.length} characters")
            } else {
                appendLine("No custom presets saved")
            }
            appendLine()

            // Export search history
            appendLine("=== Search History ===")
            val searchPrefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
            val searchHistoryJson = searchPrefs.getString("search_history", null)
            if (searchHistoryJson != null) {
                appendLine("Search history data exists (JSON format)")
                appendLine("Data size: ${searchHistoryJson.length} characters")
            } else {
                appendLine("No search history saved")
            }
            appendLine()

            appendLine("=== End of Export ===")
        }
    }

    // Private deletion methods

    private fun deleteConfig(): DeletionResult {
        return try {
            runBlocking {
                dataStore.edit { preferences ->
                    // Keep some safe defaults but clear user config
                    preferences[ConfigKeys.HOST] = "192.168.1.100"
                    preferences[ConfigKeys.PORT] = "5000"
                    preferences[ConfigKeys.PACKET_CONTENT] = "TRIGGER"
                }
            }
            DeletionResult.Success(
                deletedItems = listOf("Connection configuration"),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            DeletionResult.Error("Failed to delete config: ${e.message}")
        }
    }

    private fun deletePresets(): DeletionResult {
        return try {
            clearSharedPreferences()
            DeletionResult.Success(
                deletedItems = listOf("Custom presets"),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            DeletionResult.Error("Failed to delete presets: ${e.message}")
        }
    }

    private fun deleteHistory(): DeletionResult {
        return try {
            runBlocking {
                dataStore.edit { preferences ->
                    preferences.remove(DataStoreKeys.PACKET_HISTORY)
                }
            }
            DeletionResult.Success(
                deletedItems = listOf("Packet history"),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            DeletionResult.Error("Failed to delete history: ${e.message}")
        }
    }

    private fun deleteStatistics(): DeletionResult {
        return try {
            runBlocking {
                dataStore.edit { preferences ->
                    preferences.remove(DataStoreKeys.STATS_SENT)
                    preferences.remove(DataStoreKeys.STATS_RECEIVED)
                    preferences.remove(DataStoreKeys.STATS_FAILED)
                    preferences.remove(DataStoreKeys.LATENCY_HISTORY)
                }
            }
            DeletionResult.Success(
                deletedItems = listOf("Usage statistics"),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            DeletionResult.Error("Failed to delete statistics: ${e.message}")
        }
    }

    private fun deleteMacros(): DeletionResult {
        return try {
            runBlocking {
                dataStore.edit { preferences ->
                    preferences.remove(DataStoreKeys.MACROS)
                }
            }
            DeletionResult.Success(
                deletedItems = listOf("Macros"),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            DeletionResult.Error("Failed to delete macros: ${e.message}")
        }
    }

    private fun deleteQuickHosts(): DeletionResult {
        return try {
            runBlocking {
                dataStore.edit { preferences ->
                    preferences.remove(DataStoreKeys.QUICK_HOSTS)
                }
            }
            DeletionResult.Success(
                deletedItems = listOf("Quick hosts"),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            DeletionResult.Error("Failed to delete quick hosts: ${e.message}")
        }
    }

    private fun deleteTemplates(): DeletionResult {
        return try {
            runBlocking {
                dataStore.edit { preferences ->
                    preferences.remove(DataStoreKeys.PACKET_TEMPLATES)
                }
            }
            DeletionResult.Success(
                deletedItems = listOf("Packet templates"),
                timestamp = System.currentTimeMillis()
            )
        } catch ( e: Exception) {
            DeletionResult.Error("Failed to delete templates: ${e.message}")
        }
    }

    private fun deleteCrashReports(): DeletionResult {
        return try {
            clearCrashReports()
            DeletionResult.Success(
                deletedItems = listOf("Crash reports"),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            DeletionResult.Error("Failed to delete crash reports: ${e.message}")
        }
    }

    private fun clearSharedPreferences() {
        val prefs = context.getSharedPreferences("udp_presets", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    private fun clearCustomPresets() {
        val prefs = context.getSharedPreferences("custom_presets", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    private fun clearCrashReports() {
        val crashDir = context.getDir("crashes", Context.MODE_PRIVATE)
        crashDir.listFiles()?.forEach { it.delete() }
    }

    private fun clearLogs() {
        val logDir = context.getDir("logs", Context.MODE_PRIVATE)
        logDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private object ConfigKeys {
            val HOST = stringPreferencesKey("host")
            val PORT = stringPreferencesKey("port")
            val PACKET_CONTENT = stringPreferencesKey("packet_content")
            val HEX_MODE = stringPreferencesKey("hex_mode")
            val INCLUDE_TIMESTAMP = stringPreferencesKey("include_timestamp")
            val INCLUDE_BURST_INDEX = stringPreferencesKey("include_burst_index")
        }

        private object DataStoreKeys {
            // Connection settings
            val AUTO_RECONNECT = stringPreferencesKey("auto_reconnect")
            val AUTO_CONNECT_ON_STARTUP = stringPreferencesKey("auto_connect_on_startup")
            val KEEP_SCREEN_ON = stringPreferencesKey("keep_screen_on")
            val WAKE_LOCK_ENABLED = stringPreferencesKey("wake_lock_enabled")
            // Feedback settings
            val HAPTIC_FEEDBACK = stringPreferencesKey("haptic_feedback")
            val SOUND_ENABLED = stringPreferencesKey("sound_enabled")
            val RATE_LIMIT_ENABLED = stringPreferencesKey("rate_limit_enabled")
            val RATE_LIMIT_MS = stringPreferencesKey("rate_limit_ms")
            // Theme settings
            val DARK_THEME = stringPreferencesKey("dark_theme")
            val USE_DYNAMIC_COLORS = stringPreferencesKey("use_dynamic_colors")
            // Data keys
            val PACKET_HISTORY = stringPreferencesKey("packet_history")
            val STATS_SENT = stringPreferencesKey("stats_sent")
            val STATS_RECEIVED = stringPreferencesKey("stats_received")
            val STATS_FAILED = stringPreferencesKey("stats_failed")
            val LATENCY_HISTORY = stringPreferencesKey("latency_history")
            val MACROS = stringPreferencesKey("macros")
            val QUICK_HOSTS = stringPreferencesKey("quick_hosts")
            val PACKET_TEMPLATES = stringPreferencesKey("packet_templates")
            val AUTOMATION_RULES = stringPreferencesKey("automation_rules")
        }
    }
}

/**
 * Data category for display in settings
 */
data class DataCategory(
    val id: String,
    val name: String,
    val description: String,
    val icon: String
)
