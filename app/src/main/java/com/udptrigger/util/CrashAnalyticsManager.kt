package com.udptrigger.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Remote Crash Analytics Manager.
 * Collects crash reports and can optionally send them to a remote server.
 * Provides a foundation for integrating with crash reporting services like Firebase Crashlytics.
 */
class CrashAnalyticsManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    /**
     * Crash report data
     */
    data class CrashReport(
        val id: String,
        val timestamp: Long,
        val exceptionName: String,
        val exceptionMessage: String?,
        val stackTrace: String,
        val deviceInfo: DeviceInfo,
        val appInfo: AppInfo,
        val userId: String?
    )

    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val brand: String,
        val device: String,
        val hardware: String,
        val board: String,
        val androidVersion: String,
        val sdkVersion: Int,
        val buildId: String,
        val isEmulator: Boolean
    )

    data class AppInfo(
        val versionName: String,
        val versionCode: Int,
        val packageName: String,
        val buildType: String,
        val isDebug: Boolean
    )

    /**
     * Configuration for remote analytics
     */
    data class AnalyticsConfig(
        val enabled: Boolean = false,
        val serverUrl: String = "",
        val apiKey: String = "",
        val autoSend: Boolean = false,
        val includeDeviceInfo: Boolean = true,
        val includeUserInfo: Boolean = false
    )

    /**
     * Send result
     */
    sealed class SendResult {
        data class Success(val reportId: String) : SendResult()
        data class Error(val message: String) : SendResult()
    }

    /**
     * Get current analytics configuration
     */
    fun getConfig(): AnalyticsConfig {
        return AnalyticsConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            serverUrl = prefs.getString(KEY_SERVER_URL, "") ?: "",
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            autoSend = prefs.getBoolean(KEY_AUTO_SEND, false),
            includeDeviceInfo = prefs.getBoolean(KEY_INCLUDE_DEVICE_INFO, true),
            includeUserInfo = prefs.getBoolean(KEY_INCLUDE_USER_INFO, false)
        )
    }

    /**
     * Update analytics configuration
     */
    fun updateConfig(config: AnalyticsConfig) {
        prefs.edit().apply {
            putBoolean(KEY_ENABLED, config.enabled)
            putString(KEY_SERVER_URL, config.serverUrl)
            putString(KEY_API_KEY, config.apiKey)
            putBoolean(KEY_AUTO_SEND, config.autoSend)
            putBoolean(KEY_INCLUDE_DEVICE_INFO, config.includeDeviceInfo)
            putBoolean(KEY_INCLUDE_USER_INFO, config.includeUserInfo)
            apply()
        }
    }

    /**
     * Get anonymous user ID
     */
    fun getUserId(): String {
        var userId = prefs.getString(KEY_USER_ID, null)
        if (userId == null) {
            userId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        }
        return userId
    }

    /**
     * Record a crash locally
     */
    fun recordCrash(
        exception: Throwable,
        userId: String? = getUserId()
    ): CrashReport {
        val report = CrashReport(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            exceptionName = exception.javaClass.name,
            exceptionMessage = exception.message,
            stackTrace = getStackTraceString(exception),
            deviceInfo = getDeviceInfo(),
            appInfo = getAppInfo(),
            userId = if (getConfig().includeUserInfo) userId else null
        )

        // Save locally
        saveCrashReport(report)

        return report
    }

    /**
     * Send crash report to remote server
     */
    suspend fun sendCrashReport(report: CrashReport): SendResult {
        val config = getConfig()

        if (!config.enabled || config.serverUrl.isEmpty()) {
            return SendResult.Error("Remote analytics not configured")
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = URL(config.serverUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-API-Key", config.apiKey)
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val json = report.toJson(config.includeDeviceInfo, config.includeUserInfo)
                connection.outputStream.use { output ->
                    output.write(json.toByteArray())
                }

                val response = if (connection.responseCode == HttpURLConnection.HTTP_OK ||
                    connection.responseCode == HttpURLConnection.HTTP_CREATED) {
                    SendResult.Success(report.id)
                } else {
                    SendResult.Error("Server returned ${connection.responseCode}")
                }

                connection.disconnect()
                response
            } catch (e: Exception) {
                SendResult.Error("Failed to send: ${e.message}")
            }
        }
    }

    /**
     * Send all pending crash reports
     */
    suspend fun sendPendingReports(): List<SendResult> {
        val reports = getPendingReports()
        return reports.map { sendCrashReport(it) }
    }

    /**
     * Get all pending crash reports
     */
    fun getPendingReports(): List<CrashReport> {
        val reportsJson = prefs.getString(KEY_PENDING_REPORTS, "[]") ?: "[]"
        return try {
            val jsonArray = org.json.JSONArray(reportsJson)
            (0 until jsonArray.length()).map { index ->
                jsonArray.getJSONObject(index).toCrashReport()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Clear all pending reports
     */
    fun clearPendingReports() {
        prefs.edit().remove(KEY_PENDING_REPORTS).apply()
    }

    /**
     * Get crash statistics
     */
    fun getStats(): CrashStats {
        val reports = getPendingReports()
        return CrashStats(
            totalCrashes = reports.size,
            uniqueExceptions = reports.map { it.exceptionName }.distinct().size,
            lastCrashTimestamp = reports.maxOfOrNull { it.timestamp },
            oldestCrashTimestamp = reports.minOfOrNull { it.timestamp }
        )
    }

    /**
     * Test connection to analytics server
     */
    suspend fun testConnection(): SendResult {
        val config = getConfig()
        if (!config.enabled || config.serverUrl.isEmpty()) {
            return SendResult.Error("Analytics not configured")
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = URL("${config.serverUrl}/health")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("X-API-Key", config.apiKey)
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val response = if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    SendResult.Success("connected")
                } else {
                    SendResult.Error("Server returned ${connection.responseCode}")
                }

                connection.disconnect()
                response
            } catch (e: Exception) {
                SendResult.Error("Connection failed: ${e.message}")
            }
        }
    }

    // Private helpers

    private fun saveCrashReport(report: CrashReport) {
        val reports = getPendingReports().toMutableList()
        reports.add(report)

        // Keep max 10 reports
        val trimmed = if (reports.size > 10) reports.takeLast(10) else reports

        val jsonArray = org.json.JSONArray()
        trimmed.forEach { jsonArray.put(it.toJson(false, false)) }

        prefs.edit().putString(KEY_PENDING_REPORTS, jsonArray.toString()).apply()
    }

    private fun getStackTraceString(exception: Throwable): String {
        val sb = StringBuilder()
        sb.appendLine(exception.toString())
        exception.stackTrace.forEach { element ->
            sb.appendLine("\tat $element")
        }
        if (exception.cause != null) {
            sb.appendLine("Caused by:")
            sb.append(getStackTraceString(exception.cause!!))
        }
        return sb.toString()
    }

    private fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            brand = Build.BRAND,
            device = Build.DEVICE,
            hardware = Build.HARDWARE,
            board = Build.BOARD,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            buildId = Build.ID,
            isEmulator = isEmulator()
        )
    }

    private fun getAppInfo(): AppInfo {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            AppInfo(
                versionName = packageInfo.versionName ?: "unknown",
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                },
                packageName = context.packageName,
                buildType = "debug",
                isDebug = true
            )
        } catch (e: Exception) {
            AppInfo("unknown", 0, context.packageName, "unknown", false)
        }
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.contains("generic")
                || Build.FINGERPRINT.contains("emulator")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86"))
    }

    private fun CrashReport.toJson(includeDeviceInfo: Boolean, includeUserInfo: Boolean): String {
        return JSONObject().apply {
            put("id", id)
            put("timestamp", timestamp)
            put("exception_name", exceptionName)
            put("exception_message", exceptionMessage ?: "")
            put("stack_trace", stackTrace)
            put("timestamp_formatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp)))

            if (includeDeviceInfo) {
                put("device", JSONObject().apply {
                    put("manufacturer", deviceInfo.manufacturer)
                    put("model", deviceInfo.model)
                    put("brand", deviceInfo.brand)
                    put("android_version", deviceInfo.androidVersion)
                    put("sdk_version", deviceInfo.sdkVersion)
                    put("is_emulator", deviceInfo.isEmulator)
                })
            }

            put("app", JSONObject().apply {
                put("version_name", appInfo.versionName)
                put("version_code", appInfo.versionCode)
                put("package_name", appInfo.packageName)
                put("build_type", appInfo.buildType)
            })

            if (includeUserInfo && userId != null) {
                put("user_id", userId)
            }
        }.toString()
    }

    private fun JSONObject.toCrashReport(): CrashReport {
        return CrashReport(
            id = getString("id"),
            timestamp = getLong("timestamp"),
            exceptionName = getString("exception_name"),
            exceptionMessage = optString("exception_message", null),
            stackTrace = getString("stack_trace"),
            deviceInfo = DeviceInfo("", "", "", "", "", "", "", 0, "", false),
            appInfo = AppInfo("", 0, "", "", false),
            userId = optString("user_id", null)
        )
    }

    companion object {
        private const val PREFS_NAME = "crash_analytics_prefs"
        private const val KEY_ENABLED = "analytics_enabled"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_AUTO_SEND = "auto_send"
        private const val KEY_INCLUDE_DEVICE_INFO = "include_device_info"
        private const val KEY_INCLUDE_USER_INFO = "include_user_info"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PENDING_REPORTS = "pending_reports"
    }
}

/**
 * Crash statistics
 */
data class CrashStats(
    val totalCrashes: Int,
    val uniqueExceptions: Int,
    val lastCrashTimestamp: Long?,
    val oldestCrashTimestamp: Long?
)
