package com.udptrigger.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * In-app log viewer for debugging UDP Trigger.
 * Captures and displays logs with filtering and export capabilities.
 */
class LogViewer(private val context: Context) {

    companion object {
        private const val TAG = "UdpTrigger"
        private const val MAX_LOG_ENTRIES = 1000
        private const val LOG_FILE_NAME = "udp_trigger_logs.txt"
        private const val MAX_LOG_FILE_SIZE = 1024 * 1024 // 1MB
    }

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _filteredLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val filteredLogs: StateFlow<List<LogEntry>> = _filteredLogs.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var isCapturing = false

    private var currentFilter = LogFilter()

    // Log levels
    enum class LogLevel(val priority: Int, val symbol: String) {
        VERBOSE(2, "V"),
        DEBUG(3, "D"),
        INFO(4, "I"),
        WARN(5, "W"),
        ERROR(6, "E"),
        FATAL(7, "F")
    }

    /**
     * Start capturing logs
     */
    fun startCapturing() {
        if (isCapturing) return
        isCapturing = true

        // Capture Android logs
        captureAndroidLogs()

        // Start periodic flush
        startPeriodicFlush()
    }

    /**
     * Stop capturing logs
     */
    fun stopCapturing() {
        isCapturing = false
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Add a log entry manually
     */
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )
        addLogEntry(entry)
    }

    /**
     * Log verbose
     */
    fun v(tag: String, message: String) {
        log(LogLevel.VERBOSE, tag, message)
        Log.v(TAG, message)
    }

    /**
     * Log debug
     */
    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message)
        Log.d(TAG, message)
    }

    /**
     * Log info
     */
    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message)
        Log.i(TAG, message)
    }

    /**
     * Log warning
     */
    fun w(tag: String, message: String) {
        log(LogLevel.WARN, tag, message)
        Log.w(TAG, message)
    }

    /**
     * Log error
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
        Log.e(TAG, message, throwable)
    }

    /**
     * Log fatal
     */
    fun f(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.FATAL, tag, message, throwable)
        Log.wtf(TAG, message, throwable)
    }

    /**
     * Add a log entry
     */
    private fun addLogEntry(entry: LogEntry) {
        logQueue.offer(entry)

        // Update filtered logs immediately if filter matches
        if (matchesFilter(entry)) {
            val currentFiltered = _filteredLogs.value.toMutableList()
            currentFiltered.add(0, entry)
            if (currentFiltered.size > MAX_LOG_ENTRIES) {
                currentFiltered.removeAt(currentFiltered.lastIndex)
            }
            _filteredLogs.value = currentFiltered
        }

        // Periodically flush the queue
        if (logQueue.size >= 10) {
            flushQueue()
        }
    }

    /**
     * Capture Android system logs
     */
    private fun captureAndroidLogs() {
        executor.execute {
            try {
                val process = Runtime.getRuntime().exec("logcat -v threadtime")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                while (isCapturing) {
                    line = reader.readLine()
                    if (line == null) break
                    parseLogcatLine(line)
                }
            } catch (e: Exception) {
                log(LogLevel.ERROR, "LogViewer", "Failed to capture logs: ${e.message}")
            }
        }
    }

    /**
     * Parse logcat line
     */
    private fun parseLogcatLine(line: String) {
        try {
            // Parse standard logcat format: "timestamp PID TID level tag: message"
            val parts = line.split(" ", limit = 5)
            if (parts.size >= 4) {
                val levelChar = parts.getOrNull(2)?.firstOrNull() ?: 'I'
                val level = when (levelChar) {
                    'V' -> LogLevel.VERBOSE
                    'D' -> LogLevel.DEBUG
                    'I' -> LogLevel.INFO
                    'W' -> LogLevel.WARN
                    'E' -> LogLevel.ERROR
                    'F' -> LogLevel.FATAL
                    else -> LogLevel.INFO
                }

                val tagMessage = parts.getOrNull(4) ?: ""
                val colonIndex = tagMessage.indexOf(":")
                val tag: String = if (colonIndex > 0) {
                    tagMessage.substring(0, colonIndex).trim()
                } else {
                    "System"
                }
                val msg: String = if (colonIndex > 0 && colonIndex < tagMessage.length - 1) {
                    tagMessage.substring(colonIndex + 1).trim()
                } else {
                    tagMessage
                }

                // Only capture our app's logs
                if (tag.contains("UdpTrigger", ignoreCase = true) ||
                    tag.contains("UDP", ignoreCase = true) ||
                    tag.contains("Trigger", ignoreCase = true)) {

                    val entry = LogEntry(
                        timestamp = System.currentTimeMillis(),
                        level = level,
                        tag = tag,
                        message = msg
                    )
                    addLogEntry(entry)
                }
            }
        } catch (e: Exception) {
            // Skip malformed lines
        }
    }

    /**
     * Start periodic flush
     */
    private fun startPeriodicFlush() {
        handler.post(object : Runnable {
            override fun run() {
                if (isCapturing) {
                    flushQueue()
                    handler.postDelayed(this, 5000) // Flush every 5 seconds
                }
            }
        })
    }

    /**
     * Flush the log queue
     */
    private fun flushQueue() {
        val entries = mutableListOf<LogEntry>()
        var entry: LogEntry?
        while (logQueue.poll().also { entry = it } != null) {
            entry?.let { entries.add(it) }
        }

        if (entries.isNotEmpty()) {
            val currentLogs = _logs.value.toMutableList()
            currentLogs.addAll(entries)
            if (currentLogs.size > MAX_LOG_ENTRIES) {
                currentLogs.subList(0, currentLogs.size - MAX_LOG_ENTRIES).clear()
            }
            _logs.value = currentLogs

            // Also update filtered logs
            val filtered = entries.filter { matchesFilter(it) }
            if (filtered.isNotEmpty()) {
                val currentFiltered = _filteredLogs.value.toMutableList()
                currentFiltered.addAll(filtered)
                if (currentFiltered.size > MAX_LOG_ENTRIES) {
                    currentFiltered.subList(0, currentFiltered.size - MAX_LOG_ENTRIES).clear()
                }
                _filteredLogs.value = currentFiltered
            }

            // Write to file
            writeToFile(entries)
        }
    }

    /**
     * Apply filter
     */
    fun setFilter(filter: LogFilter) {
        currentFilter = filter
        _filteredLogs.value = _logs.value.filter { matchesFilter(it) }
    }

    /**
     * Check if entry matches current filter
     */
    private fun matchesFilter(entry: LogEntry): Boolean {
        return with(currentFilter) {
            // Level filter
            if (minLevel != null && entry.level.priority < minLevel.priority) {
                return@with false
            }
            if (maxLevel != null && entry.level.priority > maxLevel.priority) {
                return@with false
            }

            // Tag filter
            if (tagPattern != null && !entry.tag.contains(tagPattern!!, ignoreCase = true)) {
                return@with false
            }

            // Message filter
            if (messagePattern != null && !entry.message.contains(messagePattern!!, ignoreCase = true)) {
                return@with false
            }

            // Search filter
            if (searchQuery != null) {
                val matches = entry.tag.contains(searchQuery!!, ignoreCase = true) ||
                        entry.message.contains(searchQuery!!, ignoreCase = true)
                if (!matches) return@with false
            }

            true
        }
    }

    /**
     * Clear all logs
     */
    fun clearLogs() {
        logQueue.clear()
        _logs.value = emptyList()
        _filteredLogs.value = emptyList()

        // Clear log file
        executor.execute {
            try {
                File(context.filesDir, LOG_FILE_NAME).delete()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Export logs to string
     */
    fun exportLogs(): String {
        val logsToExport = if (currentFilter.isEmpty()) {
            _logs.value
        } else {
            _filteredLogs.value
        }

        return buildString {
            appendLine("=== UDP Trigger Logs ===")
            appendLine("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Total entries: ${logsToExport.size}")
            appendLine()

            logsToExport.forEach { entry ->
                appendLine(formatEntry(entry))
            }
        }
    }

    /**
     * Export logs to file
     */
    fun exportToFile(): File? {
        return try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            logFile.writeText(exportLogs())
            logFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get logs for sharing
     */
    fun getShareableLogs(): String {
        return exportLogs()
    }

    /**
     * Format a log entry for display
     */
    fun formatEntry(entry: LogEntry): String {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
        return "${entry.level.symbol} $time ${entry.tag}: ${entry.message}"
    }

    /**
     * Write entries to file
     */
    private fun writeToFile(entries: List<LogEntry>) {
        executor.execute {
            try {
                val logFile = File(context.filesDir, LOG_FILE_NAME)

                if (logFile.exists() && logFile.length() > MAX_LOG_FILE_SIZE) {
                    logFile.delete()
                }

                val content = entries.joinToString("\n") { formatEntry(it) }
                logFile.appendText("$content\n")
            } catch (e: Exception) {
                // Ignore write errors
            }
        }
    }

    /**
     * Get log statistics
     */
    fun getStatistics(): LogStatistics {
        val logs = _logs.value
        return LogStatistics(
            totalEntries = logs.size,
            byLevel = logs.groupBy { it.level }.mapValues { it.value.size },
            oldestTimestamp = logs.minOfOrNull { it.timestamp },
            newestTimestamp = logs.maxOfOrNull { it.timestamp }
        )
    }

    /**
     * Get all unique tags
     */
    fun getTags(): Set<String> {
        return _logs.value.map { it.tag }.toSet()
    }
}

/**
 * Log entry
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogViewer.LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)

/**
 * Log filter
 */
data class LogFilter(
    val minLevel: LogViewer.LogLevel? = null,
    val maxLevel: LogViewer.LogLevel? = null,
    val tagPattern: String? = null,
    val messagePattern: String? = null,
    val searchQuery: String? = null
) {
    fun isEmpty(): Boolean {
        return minLevel == null && maxLevel == null &&
                tagPattern == null && messagePattern == null && searchQuery == null
    }
}

/**
 * Log statistics
 */
data class LogStatistics(
    val totalEntries: Int,
    val byLevel: Map<LogViewer.LogLevel, Int>,
    val oldestTimestamp: Long?,
    val newestTimestamp: Long?
)
