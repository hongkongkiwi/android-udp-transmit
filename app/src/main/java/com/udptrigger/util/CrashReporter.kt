package com.udptrigger.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.crashReportDataStore: DataStore<Preferences> by preferencesDataStore(name = "crash_reports")

/**
 * DataStore for crash reporting settings
 */
class CrashReportSettings(private val context: Context) {

    companion object {
        private val CRASH_REPORTING_ENABLED = booleanPreferencesKey("crash_reporting_enabled")
        private val AUTO_REPORT_ENABLED = booleanPreferencesKey("auto_report_enabled")
    }

    val crashReportingEnabled: Flow<Boolean> = context.crashReportDataStore.data.map { preferences ->
        preferences[CRASH_REPORTING_ENABLED] ?: false
    }

    val autoReportEnabled: Flow<Boolean> = context.crashReportDataStore.data.map { preferences ->
        preferences[AUTO_REPORT_ENABLED] ?: false
    }

    suspend fun setCrashReportingEnabled(enabled: Boolean) {
        context.crashReportDataStore.edit { preferences ->
            preferences[CRASH_REPORTING_ENABLED] = enabled
        }
    }

    suspend fun setAutoReportEnabled(enabled: Boolean) {
        context.crashReportDataStore.edit { preferences ->
            preferences[AUTO_REPORT_ENABLED] = enabled
        }
    }
}

/**
 * Crash reporter that captures uncaught exceptions and writes them to file
 */
class CrashReporter(private val context: Context) {

    private val crashReportsDir: File
        get() = File(context.filesDir, "crash_reports").apply {
            mkdirs()
        }

    /**
     * Initialize the crash reporter as the default uncaught exception handler
     */
    fun initialize() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleCrash(throwable, defaultHandler, thread)
        }
    }

    /**
     * Handle an uncaught exception
     */
    private fun handleCrash(
        throwable: Throwable,
        defaultHandler: Thread.UncaughtExceptionHandler?,
        thread: Thread
    ) {
        try {
            val crashReport = generateCrashReport(throwable, thread)
            saveCrashReport(crashReport)
        } catch (e: Exception) {
            // Ignore errors during crash reporting
        } finally {
            // Call the original handler to perform default crash behavior
            defaultHandler?.uncaughtException(thread, throwable) ?:
                android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    /**
     * Generate a detailed crash report
     */
    private fun generateCrashReport(throwable: Throwable, thread: Thread): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        sb.appendLine("=== UDP Trigger Crash Report ===")
        sb.appendLine("Timestamp: ${dateFormat.format(Date())}")
        sb.appendLine()

        // Device info
        sb.appendLine("--- Device Information ---")
        sb.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("Model: ${Build.MODEL}")
        sb.appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Device: ${Build.DEVICE}")
        sb.appendLine()

        // App info
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            sb.appendLine("--- App Information ---")
            sb.appendLine("App Version: ${packageInfo.versionName}")
            sb.appendLine("Version Code: ${packageInfo.longVersionCode}")
            sb.appendLine()
        } catch (e: PackageManager.NameNotFoundException) {
            sb.appendLine("--- App Information ---")
            sb.appendLine("Unable to retrieve app version info")
            sb.appendLine()
        }

        // Thread info
        sb.appendLine("--- Thread Information ---")
        sb.appendLine("Thread Name: ${thread.name}")
        sb.appendLine("Thread ID: ${thread.id}")
        sb.appendLine("Thread State: ${thread.state}")
        sb.appendLine()

        // Exception info
        sb.appendLine("--- Exception Information ---")
        sb.appendLine("Exception Type: ${throwable.javaClass.name}")
        sb.appendLine("Message: ${throwable.message}")
        sb.appendLine()
        sb.appendLine("--- Stack Trace ---")

        val sw = java.io.StringWriter()
        val pw = java.io.PrintWriter(sw)
        throwable.printStackTrace(pw)
        sb.appendLine(sw.toString())

        // Also print cause if available
        var cause = throwable.cause
        while (cause != null) {
            sb.appendLine("--- Caused By ---")
            sb.appendLine("Exception Type: ${cause.javaClass.name}")
            sb.appendLine("Message: ${cause.message}")
            val sw2 = java.io.StringWriter()
            val pw2 = java.io.PrintWriter(sw2)
            cause.printStackTrace(pw2)
            sb.appendLine(sw2.toString())
            cause = cause.cause
        }

        sb.appendLine()
        sb.appendLine("--- End of Crash Report ---")

        return sb.toString()
    }

    /**
     * Save crash report to file
     */
    private fun saveCrashReport(report: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "crash_$timestamp.log"
        val file = File(crashReportsDir, fileName)
        file.writeText(report)
    }

    /**
     * Get list of all crash reports
     */
    fun getCrashReports(): List<CrashReportInfo> {
        return crashReportsDir.listFiles()?.mapNotNull { file ->
            try {
                val content = file.readText()
                val timestamp = extractTimestamp(content)
                val exceptionType = extractExceptionType(content)
                CrashReportInfo(
                    file = file,
                    fileName = file.name,
                    timestamp = timestamp,
                    exceptionType = exceptionType,
                    content = content
                )
            } catch (e: Exception) {
                null
            }
        }?.sortedByDescending { it.timestamp } ?: emptyList()
    }

    /**
     * Delete a crash report
     */
    fun deleteCrashReport(fileName: String): Boolean {
        val file = File(crashReportsDir, fileName)
        return file.delete()
    }

    /**
     * Delete all crash reports
     */
    fun clearAllCrashReports(): Boolean {
        return crashReportsDir.listFiles()?.all { it.delete() } ?: true
    }

    /**
     * Get crash report count
     */
    fun getCrashReportCount(): Int {
        return crashReportsDir.listFiles()?.size ?: 0
    }

    private fun extractTimestamp(content: String): Long {
        val pattern = Regex("Timestamp: (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})")
        val match = pattern.find(content)
        return if (match != null) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                dateFormat.parse(match.groupValues[1])?.time ?: 0
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
    }

    private fun extractExceptionType(content: String): String {
        val pattern = Regex("Exception Type: ([\\w.]+)")
        val match = pattern.find(content)
        return match?.groupValues?.get(1) ?: "Unknown"
    }

    /**
     * Log a non-fatal error for later review
     */
    fun logNonFatalError(tag: String, message: String, throwable: Throwable? = null) {
        try {
            val sb = StringBuilder()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            sb.appendLine("=== UDP Trigger Non-Fatal Error ===")
            sb.appendLine("Timestamp: ${dateFormat.format(Date())}")
            sb.appendLine("Tag: $tag")
            sb.appendLine("Message: $message")

            if (throwable != null) {
                sb.appendLine("Exception Type: ${throwable.javaClass.name}")
                sb.appendLine("Exception Message: ${throwable.message}")
                sb.appendLine()
                sb.appendLine("--- Stack Trace ---")
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                sb.appendLine(sw.toString())
            }

            sb.appendLine()
            sb.appendLine("--- End of Error Report ---")

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "error_${tag}_${timestamp}.log"
            val file = File(crashReportsDir, fileName)
            file.writeText(sb.toString())
        } catch (e: Exception) {
            // Ignore errors during error logging
        }
    }
}

/**
 * Data class representing a crash report
 */
data class CrashReportInfo(
    val file: File,
    val fileName: String,
    val timestamp: Long,
    val exceptionType: String,
    val content: String
)

/**
 * Singleton instance of the crash reporter
 */
object CrashReporterSingleton {
    private var instance: CrashReporter? = null

    fun initialize(context: Context) {
        if (instance == null) {
            instance = CrashReporter(context.applicationContext)
            instance?.initialize()
        }
    }

    fun get(): CrashReporter? = instance

    fun logNonFatalError(tag: String, message: String, throwable: Throwable? = null) {
        instance?.logNonFatalError(tag, message, throwable)
    }

    fun getCrashReports(): List<CrashReportInfo> = instance?.getCrashReports() ?: emptyList()

    fun deleteCrashReport(fileName: String): Boolean = instance?.deleteCrashReport(fileName) ?: false

    fun clearAllCrashReports(): Boolean = instance?.clearAllCrashReports() ?: false

    fun getCrashReportCount(): Int = instance?.getCrashReportCount() ?: 0
}
