package com.udptrigger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.statisticsDataStore: DataStore<Preferences> by preferencesDataStore(name = "statistics")

/**
 * Statistics data class for tracking app usage
 */
@Serializable
data class UsageStatistics(
    val totalSessions: Long = 0,
    val totalPacketsSent: Long = 0,
    val totalPacketsReceived: Long = 0,
    val totalPacketsFailed: Long = 0,
    val totalConnectionTimeMs: Long = 0,
    val lastSessionTime: Long = 0,
    val mostUsedPreset: String = "",
    val presetUsageCounts: Map<String, Int> = emptyMap(),
    val dailyPacketCounts: Map<String, Int> = emptyMap(), // "yyyy-MM-dd" -> count
    val hourlyUsage: Map<String, Long> = emptyMap() // "HH" -> usage minutes
)

/**
 * DataStore for managing usage statistics
 */
class StatisticsDataStore(private val context: Context) {

    companion object {
        private val TOTAL_SESSIONS = longPreferencesKey("total_sessions")
        private val TOTAL_PACKETS_SENT = longPreferencesKey("total_packets_sent")
        private val TOTAL_PACKETS_RECEIVED = longPreferencesKey("total_packets_received")
        private val TOTAL_PACKETS_FAILED = longPreferencesKey("total_packets_failed")
        private val TOTAL_CONNECTION_TIME_MS = longPreferencesKey("total_connection_time_ms")
        val LAST_SESSION_TIME = longPreferencesKey("last_session_time")
        private val MOST_USED_PRESET = stringPreferencesKey("most_used_preset")
    }

    val statisticsFlow: Flow<UsageStatistics> = context.statisticsDataStore.data.map { preferences ->
        UsageStatistics(
            totalSessions = preferences[TOTAL_SESSIONS] ?: 0,
            totalPacketsSent = preferences[TOTAL_PACKETS_SENT] ?: 0,
            totalPacketsReceived = preferences[TOTAL_PACKETS_RECEIVED] ?: 0,
            totalPacketsFailed = preferences[TOTAL_PACKETS_FAILED] ?: 0,
            totalConnectionTimeMs = preferences[TOTAL_CONNECTION_TIME_MS] ?: 0,
            lastSessionTime = preferences[LAST_SESSION_TIME] ?: 0,
            mostUsedPreset = preferences[MOST_USED_PRESET] ?: ""
        )
    }

    suspend fun incrementSessions() {
        context.statisticsDataStore.edit { preferences ->
            val current = preferences[TOTAL_SESSIONS] ?: 0
            preferences[TOTAL_SESSIONS] = current + 1
            preferences[LAST_SESSION_TIME] = System.currentTimeMillis()
        }
    }

    suspend fun recordPacketsSent(count: Int) {
        context.statisticsDataStore.edit { preferences ->
            val current = preferences[TOTAL_PACKETS_SENT] ?: 0
            preferences[TOTAL_PACKETS_SENT] = current + count
        }
    }

    suspend fun recordPacketsReceived(count: Int) {
        context.statisticsDataStore.edit { preferences ->
            val current = preferences[TOTAL_PACKETS_RECEIVED] ?: 0
            preferences[TOTAL_PACKETS_RECEIVED] = current + count
        }
    }

    suspend fun recordPacketsFailed(count: Int) {
        context.statisticsDataStore.edit { preferences ->
            val current = preferences[TOTAL_PACKETS_FAILED] ?: 0
            preferences[TOTAL_PACKETS_FAILED] = current + count
        }
    }

    suspend fun addConnectionTimeMs(timeMs: Long) {
        context.statisticsDataStore.edit { preferences ->
            val current = preferences[TOTAL_CONNECTION_TIME_MS] ?: 0
            preferences[TOTAL_CONNECTION_TIME_MS] = current + timeMs
        }
    }

    suspend fun recordPresetUsage(presetName: String) {
        // Update most used preset
        val currentStats = statisticsFlow.first()
        val currentCounts = currentStats.presetUsageCounts.toMutableMap()
        currentCounts[presetName] = (currentCounts[presetName] ?: 0) + 1

        val mostUsed = currentCounts.maxByOrNull { it.value }?.key ?: ""

        context.statisticsDataStore.edit { preferences ->
            preferences[MOST_USED_PRESET] = mostUsed
        }
    }

    suspend fun recordDailyPacketCount(date: String, count: Int) {
        context.statisticsDataStore.edit { preferences ->
            val key = "daily_$date"
            val current = preferences[intPreferencesKey(key)] ?: 0
            preferences[intPreferencesKey(key)] = current + count
        }
    }

    suspend fun recordHourlyUsage(hour: String, minutes: Long) {
        context.statisticsDataStore.edit { preferences ->
            val key = "hourly_$hour"
            val current = preferences[longPreferencesKey(key)] ?: 0L
            preferences[longPreferencesKey(key)] = current + minutes
        }
    }

    suspend fun getDailyPacketCounts(): Map<String, Int> {
        return context.statisticsDataStore.data.map { preferences ->
            val counts = mutableMapOf<String, Int>()
            preferences.asMap().keys.filter { it.name.startsWith("daily_") }.forEach { key ->
                val date = key.name.removePrefix("daily_")
                val count = preferences[key] as? Int ?: 0
                if (count > 0) {
                    counts[date] = count
                }
            }
            counts.toMap()
        }.first()
    }

    suspend fun getHourlyUsage(): Map<String, Long> {
        return context.statisticsDataStore.data.map { preferences ->
            val usage = mutableMapOf<String, Long>()
            preferences.asMap().keys.filter { it.name.startsWith("hourly_") }.forEach { key ->
                val hour = key.name.removePrefix("hourly_")
                val minutes = preferences[key] as? Long ?: 0L
                if (minutes > 0) {
                    usage[hour] = minutes
                }
            }
            usage.toMap()
        }.first()
    }

    suspend fun clearAllStatistics() {
        context.statisticsDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
