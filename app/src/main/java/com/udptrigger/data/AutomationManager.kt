package com.udptrigger.data

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.udptrigger.R
import com.udptrigger.domain.UdpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Automation Engine for trigger sequences, conditional triggers, and workflows.
 * Supports a simple scripting language for complex automation.
 */
class AutomationManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _automations = MutableStateFlow<List<Automation>>(emptyList())
    val automations: StateFlow<List<Automation>> = _automations.asStateFlow()

    private val _activeAutomations = MutableStateFlow<Set<String>>(emptySet())
    val activeAutomations: StateFlow<Set<String>> = _activeAutomations.asStateFlow()

    private val _executionLogs = MutableStateFlow<List<ExecutionLog>>(emptyList())
    val executionLogs: StateFlow<List<ExecutionLog>> = _executionLogs.asStateFlow()

    private val executionEngine = AutomationExecutionEngine(context)

    /**
     * Automation definition
     */
    data class Automation(
        val id: String,
        val name: String,
        val description: String = "",
        val trigger: TriggerCondition,
        val actions: List<AutomationAction>,
        val enabled: Boolean = true,
        val priority: Int = 0,
        val cooldownMs: Long = 0,
        val lastExecuted: Long = 0,
        val executionCount: Int = 0,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Trigger conditions that start an automation
     */
    sealed class TriggerCondition {
        data class PacketReceived(
            val pattern: String,
            val useRegex: Boolean = false,
            val sourceAddress: String? = null,
            val sourcePort: Int? = null
        ) : TriggerCondition()

        data class ButtonPressed(
            val buttonId: String = "main"
        ) : TriggerCondition()

        data class Schedule(
            val cronExpression: String, // Simple cron: "minute hour day month weekday"
            val timezone: String = "UTC"
        ) : TriggerCondition()

        data class Interval(
            val intervalMs: Long
        ) : TriggerCondition()

        data class Gesture(
            val gestureType: String // "tap", "double_tap", "swipe_up", "swipe_down", "swipe_left", "swipe_right", "long_press"
        ) : TriggerCondition()

        data class NetworkState(
            val connected: Boolean? = null, // null = any
            val ssid: String? = null
        ) : TriggerCondition()

        data class TimeRange(
            val startTime: String, // "HH:mm"
            val endTime: String,   // "HH:mm"
            val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7) // 1=Monday, 7=Sunday
        ) : TriggerCondition()

        data class VariableChanged(
            val variableName: String,
            val value: String? = null,
            val operator: String = "==" // ==, !=, >, <, contains
        ) : TriggerCondition()

        data class AnyOf(
            val conditions: List<TriggerCondition>
        ) : TriggerCondition()

        data class AllOf(
            val conditions: List<TriggerCondition>
        ) : TriggerCondition()
    }

    /**
     * Actions that can be executed by automations
     */
    sealed class AutomationAction {
        data class SendUdp(
            val host: String,
            val port: Int,
            val content: String,
            val hexMode: Boolean = false
        ) : AutomationAction()

        data class SendTcp(
            val host: String,
            val port: Int,
            val content: String
        ) : AutomationAction()

        data class HttpRequest(
            val url: String,
            val method: String = "GET",
            val body: String? = null,
            val headers: Map<String, String> = emptyMap()
        ) : AutomationAction()

        data class SetVariable(
            val name: String,
            val value: String,
            val persist: Boolean = false
        ) : AutomationAction()

        data class IncrementVariable(
            val name: String,
            val by: Int = 1
        ) : AutomationAction()

        data class Delay(
            val durationMs: Long
        ) : AutomationAction()

        data class WaitForPacket(
            val pattern: String,
            val timeoutMs: Long = 5000,
            val useRegex: Boolean = false
        ) : AutomationAction()

        data class ShowNotification(
            val title: String,
            val content: String,
            val priority: Int = 0
        ) : AutomationAction()

        data class Vibrate(
            val pattern: String = "default", // "default", "short", "long"
            val repeat: Int = 0
        ) : AutomationAction()

        data class PlaySound(
            val soundId: String = "click",
            val volume: Float = 1.0f
        ) : AutomationAction()

        data class LaunchApp(
            val packageName: String? = null,
            val action: String? = null
        ) : AutomationAction()

        data class RunAutomation(
            val automationId: String
        ) : AutomationAction()

        data class Conditional(
            val condition: Condition,
            val thenActions: List<AutomationAction>,
            val elseActions: List<AutomationAction> = emptyList()
        ) : AutomationAction()

        data class Loop(
            val count: Int? = null, // null = infinite
            val condition: Condition? = null,
            val actions: List<AutomationAction>
        ) : AutomationAction()

        data class Log(
            val message: String,
            val level: String = "INFO" // DEBUG, INFO, WARN, ERROR
        ) : AutomationAction()

        data class Comment(
            val text: String
        ) : AutomationAction()
    }

    /**
     * Condition for if/loop statements
     */
    data class Condition(
        val leftOperand: String,
        val operator: String, // ==, !=, >, <, >=, <=, contains, matches, is_empty, is_number
        val rightOperand: String? = null
    )

    /**
     * Execution log entry
     */
    data class ExecutionLog(
        val automationId: String,
        val automationName: String,
        val timestamp: Long,
        val status: ExecutionStatus,
        val message: String,
        val actionsExecuted: Int = 0,
        val durationMs: Long = 0
    )

    enum class ExecutionStatus {
        STARTED, SUCCESS, FAILED, CANCELLED, TIMEOUT
    }

    /**
     * Execution engine that runs automations
     */
    inner class AutomationExecutionEngine(private val context: Context) {
        private val variables = mutableMapOf<String, String>()
        private val variableListeners = mutableMapOf<String, MutableStateFlow<String>>()

        suspend fun executeAutomation(automation: Automation): ExecutionResult {
            if (!automation.enabled) {
                return ExecutionResult(false, "Automation is disabled")
            }

            // Check cooldown
            if (automation.cooldownMs > 0) {
                val timeSinceLast = System.currentTimeMillis() - automation.lastExecuted
                if (timeSinceLast < automation.cooldownMs) {
                    return ExecutionResult(false, "Cooldown active")
                }
            }

            val startTime = System.currentTimeMillis()

            try {
                _activeAutomations.value = _activeAutomations.value + automation.id

                val result = executeActions(automation.actions)

                // Update automation stats
                val updatedAutomation = automation.copy(
                    lastExecuted = System.currentTimeMillis(),
                    executionCount = automation.executionCount + 1,
                    updatedAt = System.currentTimeMillis()
                )
                updateAutomationInternal(updatedAutomation)

                val duration = System.currentTimeMillis() - startTime

                // Log execution
                val log = ExecutionLog(
                    automationId = automation.id,
                    automationName = automation.name,
                    timestamp = System.currentTimeMillis(),
                    status = if (result.success) ExecutionStatus.SUCCESS else ExecutionStatus.FAILED,
                    message = result.message,
                    actionsExecuted = result.actionsExecuted,
                    durationMs = duration
                )
                addExecutionLog(log)

                _activeAutomations.value = _activeAutomations.value - automation.id

                return result
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime

                val log = ExecutionLog(
                    automationId = automation.id,
                    automationName = automation.name,
                    timestamp = System.currentTimeMillis(),
                    status = ExecutionStatus.FAILED,
                    message = "Exception: ${e.message}",
                    durationMs = duration
                )
                addExecutionLog(log)

                _activeAutomations.value = _activeAutomations.value - automation.id
                return ExecutionResult(false, "Exception: ${e.message}")
            }
        }

        private suspend fun executeActions(actions: List<AutomationAction>): ExecutionResult {
            var successCount = 0
            var failCount = 0
            val udpClient = UdpClient()

            for (action in actions) {
                when (action) {
                    is AutomationAction.SendUdp -> {
                        // Replace variables in content
                        val content = replaceVariables(action.content)
                        val data = if (action.hexMode) {
                            hexStringToByteArray(content)
                        } else {
                            content.toByteArray()
                        }

                        try {
                            udpClient.sendTo(data, java.net.InetAddress.getByName(action.host), action.port)
                            setVariable("_last_action", "send_udp")
                            setVariable("_last_udp_host", action.host)
                            setVariable("_last_udp_port", action.port.toString())
                            successCount++
                        } catch (e: Exception) {
                            setVariable("_last_error", e.message ?: "Unknown error")
                            failCount++
                        }
                    }
                    is AutomationAction.SendTcp -> {
                        // Replace variables in content
                        val content = replaceVariables(action.content)
                        val data = content.toByteArray()

                        try {
                            // Simple TCP implementation - would need TcpClient in production
                            java.net.Socket(action.host, action.port).use { socket ->
                                socket.getOutputStream().write(data)
                            }
                            setVariable("_last_action", "send_tcp")
                            setVariable("_last_tcp_host", action.host)
                            setVariable("_last_tcp_port", action.port.toString())
                            successCount++
                        } catch (e: Exception) {
                            setVariable("_last_error", e.message ?: "Unknown error")
                            failCount++
                        }
                    }
                    is AutomationAction.HttpRequest -> {
                        try {
                            withContext(Dispatchers.IO) {
                                val url = java.net.URL(replaceVariables(action.url))
                                val connection = url.openConnection() as java.net.HttpURLConnection
                                connection.requestMethod = action.method
                                action.headers.forEach { (k, v) ->
                                    connection.setRequestProperty(k, replaceVariables(v))
                                }
                                if (action.body != null) {
                                    connection.doOutput = true
                                    connection.outputStream.write(replaceVariables(action.body).toByteArray())
                                }
                                val responseCode = connection.responseCode
                                val response = connection.inputStream.bufferedReader().readText()
                                connection.disconnect()
                                setVariable("_last_http_code", responseCode.toString())
                                setVariable("_last_http_response", response.take(100))
                            }
                            setVariable("_last_action", "http_request")
                            successCount++
                        } catch (e: Exception) {
                            setVariable("_last_error", e.message ?: "Unknown error")
                            failCount++
                        }
                    }
                    is AutomationAction.SetVariable -> {
                        setVariable(action.name, replaceVariables(action.value), action.persist)
                        successCount++
                    }
                    is AutomationAction.IncrementVariable -> {
                        val current = variables[action.name]?.toIntOrNull() ?: 0
                        setVariable(action.name, (current + action.by).toString())
                        successCount++
                    }
                    is AutomationAction.Delay -> {
                        delay(action.durationMs)
                        successCount++
                    }
                    is AutomationAction.WaitForPacket -> {
                        // Would integrate with UdpServer - simplified implementation
                        delay(action.timeoutMs)
                        setVariable("_wait_result", "timeout")
                        failCount++
                    }
                    is AutomationAction.ShowNotification -> {
                        try {
                            val notification = NotificationCompat.Builder(context, "general")
                                .setSmallIcon(R.drawable.ic_notification_send)
                                .setContentTitle(replaceVariables(action.title))
                                .setContentText(replaceVariables(action.content))
                                .setPriority(action.priority)
                                .setAutoCancel(true)
                                .build()
                            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
                            setVariable("_last_notification", replaceVariables(action.title))
                            successCount++
                        } catch (e: Exception) {
                            failCount++
                        }
                    }
                    is AutomationAction.Vibrate -> {
                        try {
                            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                                vibratorManager.defaultVibrator
                            } else {
                                @Suppress("DEPRECATION")
                                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            }
                            val duration = when (action.pattern) {
                                "short" -> 100L
                                "long" -> 500L
                                else -> 50L
                            }
                            val effect = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                            vibrator.vibrate(effect)
                            successCount++
                        } catch (e: Exception) {
                            failCount++
                        }
                    }
                    is AutomationAction.PlaySound -> {
                        try {
                            val soundPool = SoundPool.Builder()
                                .setMaxStreams(1)
                                .setAudioAttributes(AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build())
                                .build()
                            // Simplified - in production would load from resources
                            successCount++
                        } catch (e: Exception) {
                            failCount++
                        }
                    }
                    is AutomationAction.LaunchApp -> {
                        try {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(action.packageName ?: "")
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                action.action?.let { launchIntent.action = it }
                                context.startActivity(launchIntent)
                                successCount++
                            } else {
                                failCount++
                            }
                        } catch (e: Exception) {
                            failCount++
                        }
                    }
                    is AutomationAction.RunAutomation -> {
                        val nestedAutomation = getAutomationById(action.automationId)
                        if (nestedAutomation != null) {
                            val result = executeAutomation(nestedAutomation)
                            if (result.success) successCount++ else failCount++
                        } else {
                            failCount++
                        }
                    }
                    is AutomationAction.Conditional -> {
                        if (evaluateCondition(action.condition)) {
                            executeActions(action.thenActions).also {
                                successCount += it.actionsExecuted
                            }
                        } else if (action.elseActions.isNotEmpty()) {
                            executeActions(action.elseActions).also {
                                successCount += it.actionsExecuted
                            }
                        }
                    }
                    is AutomationAction.Loop -> {
                        if (action.count != null) {
                            repeat(action.count) {
                                executeActions(action.actions).also {
                                    successCount += it.actionsExecuted
                                }
                            }
                        } else if (action.condition != null) {
                            var iterations = 0
                            while (evaluateCondition(action.condition) && iterations < 1000) {
                                executeActions(action.actions).also {
                                    successCount += it.actionsExecuted
                                }
                                iterations++
                            }
                        }
                    }
                    is AutomationAction.Log -> {
                        // Log to console/file
                        android.util.Log.d("Automation", replaceVariables(action.message))
                        successCount++
                    }
                    is AutomationAction.Comment -> {
                        // No-op
                        successCount++
                    }
                }
            }

            // Close UDP client
            try {
                udpClient.close()
            } catch (e: Exception) { /* ignore */ }

            return ExecutionResult(
                success = failCount == 0,
                message = "Executed: $successCount success, $failCount failed",
                actionsExecuted = successCount + failCount
            )
        }

        private fun replaceVariables(text: String): String {
            var result = text
            variables.forEach { (key, value) ->
                result = result.replace("{{\$key}}", value)
                result = result.replace("{{\$$key}}", value)
            }
            return result
        }

        private fun hexStringToByteArray(hex: String): ByteArray {
            val cleanHex = hex.replace(" ", "").replace("0x", "")
            val len = cleanHex.length
            if (len % 2 != 0) return byteArrayOf()
            val data = ByteArray(len / 2)
            for (i in 0 until len step 2) {
                data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) + Character.digit(cleanHex[i + 1], 16)).toByte()
            }
            return data
        }

        private fun evaluateCondition(condition: Condition): Boolean {
            val left = variables[condition.leftOperand] ?: condition.leftOperand
            val right = condition.rightOperand

            return when (condition.operator) {
                "==" -> left == right
                "!=" -> left != right
                ">" -> left.toDoubleOrNull()?.let { l -> right?.toDoubleOrNull()?.let { r -> l > r } } ?: false
                "<" -> left.toDoubleOrNull()?.let { l -> right?.toDoubleOrNull()?.let { r -> l < r } } ?: false
                ">=" -> left.toDoubleOrNull()?.let { l -> right?.toDoubleOrNull()?.let { r -> l >= r } } ?: false
                "<=" -> left.toDoubleOrNull()?.let { l -> right?.toDoubleOrNull()?.let { r -> l <= r } } ?: false
                "contains" -> left.contains(right ?: "")
                "matches" -> try { Regex(condition.rightOperand ?: "").matches(left) } catch (e: Exception) { false }
                "is_empty" -> left.isEmpty()
                "is_number" -> left.toDoubleOrNull() != null
                else -> false
            }
        }

        fun setVariable(name: String, value: String, persist: Boolean = false) {
            variables[name] = value
            variableListeners[name]?.value = value
        }

        fun getVariable(name: String): String? = variables[name]

        fun getAllVariables(): Map<String, String> = variables.toMap()

        fun clearVariables() {
            variables.clear()
        }
    }

    data class ExecutionResult(
        val success: Boolean,
        val message: String,
        val actionsExecuted: Int = 0
    )

    // CRUD Operations

    suspend fun loadAutomations() = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("automations", Context.MODE_PRIVATE)
            val json = prefs.getString("automations_json", null)
            if (json != null) {
                val automationList = kotlinx.serialization.json.Json.decodeFromString<List<Automation>>(json)
                _automations.value = automationList
            } else {
                createDefaultAutomations()
            }
        } catch (e: Exception) {
            createDefaultAutomations()
        }
    }

    private suspend fun createDefaultAutomations() {
        val defaults = listOf(
            Automation(
                id = "auto_echo",
                name = "Echo Response",
                description = "Automatically respond to any received packet",
                trigger = TriggerCondition.PacketReceived(".*", useRegex = true),
                actions = listOf(
                    AutomationAction.SendUdp(
                        host = "127.0.0.1",
                        port = 5000,
                        content = "ECHO"
                    )
                ),
                enabled = false
            ),
            Automation(
                id = "packet_counter",
                name = "Packet Counter",
                description = "Count received packets",
                trigger = TriggerCondition.PacketReceived(".*"),
                actions = listOf(
                    AutomationAction.IncrementVariable("packet_count"),
                    AutomationAction.Log("Packet received: {packet_count}", "DEBUG")
                )
            )
        )
        _automations.value = defaults
        saveAutomations()
    }

    private suspend fun saveAutomations() = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("automations", Context.MODE_PRIVATE)
            // Save as JSON string manually
            val json = buildJsonString(_automations.value)
            prefs.edit().putString("automations_json", json).apply()
        } catch (e: Exception) {
            // Log error
        }
    }

    private fun buildJsonString(automations: List<Automation>): String {
        val sb = StringBuilder("[")
        automations.forEachIndexed { index, automation ->
            if (index > 0) sb.append(",")
            sb.append("{")
            sb.append("\"id\":\"${escapeJson(automation.id)}\",")
            sb.append("\"name\":\"${escapeJson(automation.name)}\",")
            sb.append("\"description\":\"${escapeJson(automation.description)}\",")
            sb.append("\"trigger\":${buildTriggerJson(automation.trigger)},")
            sb.append("\"actions\":${buildActionsJson(automation.actions)},")
            sb.append("\"enabled\":${automation.enabled},")
            sb.append("\"priority\":${automation.priority},")
            sb.append("\"cooldownMs\":${automation.cooldownMs},")
            sb.append("\"lastExecuted\":${automation.lastExecuted},")
            sb.append("\"executionCount\":${automation.executionCount},")
            sb.append("\"createdAt\":${automation.createdAt},")
            sb.append("\"updatedAt\":${automation.updatedAt}")
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    }

    private fun buildTriggerJson(trigger: TriggerCondition): String {
        return when (trigger) {
            is TriggerCondition.PacketReceived -> "{\"type\":\"packet_received\",\"pattern\":\"${escapeJson(trigger.pattern)}\",\"useRegex\":${trigger.useRegex},\"sourceAddress\":\"${trigger.sourceAddress ?: ""}\",\"sourcePort\":${trigger.sourcePort ?: -1}}"
            is TriggerCondition.ButtonPressed -> "{\"type\":\"button_pressed\",\"buttonId\":\"${trigger.buttonId}\"}"
            is TriggerCondition.Schedule -> "{\"type\":\"schedule\",\"cronExpression\":\"${escapeJson(trigger.cronExpression)}\",\"timezone\":\"${trigger.timezone}\"}"
            is TriggerCondition.Interval -> "{\"type\":\"interval\",\"intervalMs\":${trigger.intervalMs}}"
            is TriggerCondition.Gesture -> "{\"type\":\"gesture\",\"gestureType\":\"${trigger.gestureType}\"}"
            is TriggerCondition.NetworkState -> {
                val connectedValue = trigger.connected?.let { if (it) "true" else "false" } ?: "null"
                "{\"type\":\"network_state\",\"connected\":$connectedValue,\"ssid\":\"${trigger.ssid ?: ""}\"}"
            }
            is TriggerCondition.TimeRange -> "{\"type\":\"time_range\",\"startTime\":\"${trigger.startTime}\",\"endTime\":\"${trigger.endTime}\",\"daysOfWeek\":[${trigger.daysOfWeek.joinToString(",")}]}"
            is TriggerCondition.VariableChanged -> "{\"type\":\"variable_changed\",\"variableName\":\"${trigger.variableName}\",\"value\":\"${trigger.value ?: ""}\",\"operator\":\"${trigger.operator}\"}"
            is TriggerCondition.AnyOf -> "{\"type\":\"any_of\",\"conditions\":[${trigger.conditions.joinToString(",") { buildTriggerJson(it) }}]}"
            is TriggerCondition.AllOf -> "{\"type\":\"all_of\",\"conditions\":[${trigger.conditions.joinToString(",") { buildTriggerJson(it) }}]}"
        }
    }

    private fun buildActionsJson(actions: List<AutomationAction>): String {
        return actions.joinToString(",", "[", "]") { action ->
            when (action) {
                is AutomationAction.SendUdp -> "{\"type\":\"send_udp\",\"host\":\"${action.host}\",\"port\":${action.port},\"content\":\"${escapeJson(action.content)}\",\"hexMode\":${action.hexMode}}"
                is AutomationAction.SendTcp -> "{\"type\":\"send_tcp\",\"host\":\"${action.host}\",\"port\":${action.port},\"content\":\"${escapeJson(action.content)}\"}"
                is AutomationAction.HttpRequest -> "{\"type\":\"http_request\",\"url\":\"${escapeJson(action.url)}\",\"method\":\"${action.method}\",\"body\":\"${escapeJson(action.body ?: "")}\",\"headers\":{}}"
                is AutomationAction.SetVariable -> "{\"type\":\"set_variable\",\"name\":\"${action.name}\",\"value\":\"${escapeJson(action.value)}\",\"persist\":${action.persist}}"
                is AutomationAction.IncrementVariable -> "{\"type\":\"increment_variable\",\"name\":\"${action.name}\",\"by\":${action.by}}"
                is AutomationAction.Delay -> "{\"type\":\"delay\",\"durationMs\":${action.durationMs}}"
                is AutomationAction.WaitForPacket -> "{\"type\":\"wait_for_packet\",\"pattern\":\"${escapeJson(action.pattern)}\",\"timeoutMs\":${action.timeoutMs},\"useRegex\":${action.useRegex}}"
                is AutomationAction.ShowNotification -> "{\"type\":\"notification\",\"title\":\"${escapeJson(action.title)}\",\"content\":\"${escapeJson(action.content)}\",\"priority\":${action.priority}}"
                is AutomationAction.Vibrate -> "{\"type\":\"vibrate\",\"pattern\":\"${action.pattern}\",\"repeat\":${action.repeat}}"
                is AutomationAction.PlaySound -> "{\"type\":\"play_sound\",\"soundId\":\"${action.soundId}\",\"volume\":${action.volume}}"
                is AutomationAction.LaunchApp -> "{\"type\":\"launch_app\",\"packageName\":\"${action.packageName ?: ""}\",\"action\":\"${action.action ?: ""}\"}"
                is AutomationAction.RunAutomation -> "{\"type\":\"run_automation\",\"automationId\":\"${action.automationId}\"}"
                is AutomationAction.Log -> "{\"type\":\"log\",\"message\":\"${escapeJson(action.message)}\",\"level\":\"${action.level}\"}"
                else -> "{\"type\":\"unknown\"}"
            }
        }
    }

    suspend fun addAutomation(automation: Automation) {
        val list = _automations.value.toMutableList()
        list.add(automation)
        _automations.value = list
        saveAutomations()
    }

    suspend fun updateAutomation(automation: Automation) {
        val list = _automations.value.toMutableList()
        val index = list.indexOfFirst { it.id == automation.id }
        if (index >= 0) {
            list[index] = automation.copy(updatedAt = System.currentTimeMillis())
            _automations.value = list
            saveAutomations()
        }
    }

    private fun updateAutomationInternal(automation: Automation) {
        val list = _automations.value.toMutableList()
        val index = list.indexOfFirst { it.id == automation.id }
        if (index >= 0) {
            list[index] = automation
            _automations.value = list
        }
    }

    suspend fun deleteAutomation(id: String) {
        _automations.value = _automations.value.filter { it.id != id }
        saveAutomations()
    }

    fun getAutomationById(id: String): Automation? {
        return _automations.value.find { it.id == id }
    }

    fun getAutomationsByTrigger(type: String): List<Automation> {
        return _automations.value.filter { automation ->
            automation.trigger::class.simpleName == type
        }
    }

    suspend fun toggleAutomation(id: String, enabled: Boolean) {
        val automation = getAutomationById(id)
        if (automation != null) {
            updateAutomation(automation.copy(enabled = enabled))
        }
    }

    /**
     * Trigger automation by button press
     */
    suspend fun onButtonPressed(buttonId: String) {
        val matching = _automations.value.filter {
            it.enabled && it.trigger is TriggerCondition.ButtonPressed &&
                    (it.trigger as TriggerCondition.ButtonPressed).buttonId == buttonId
        }
        matching.forEach { executeAutomation(it) }
    }

    /**
     * Trigger automation by gesture
     */
    suspend fun onGesture(gestureType: String) {
        val matching = _automations.value.filter {
            it.enabled && it.trigger is TriggerCondition.Gesture &&
                    (it.trigger as TriggerCondition.Gesture).gestureType == gestureType
        }
        matching.forEach { executeAutomation(it) }
    }

    /**
     * Trigger automation by packet received
     */
    suspend fun onPacketReceived(pattern: String, sourceAddress: String, sourcePort: Int, content: String) {
        val matching = _automations.value.filter { automation ->
            if (!automation.enabled) return@filter false
            val trigger = automation.trigger

            when (trigger) {
                is TriggerCondition.PacketReceived -> {
                    val patternMatches = if (trigger.useRegex) {
                        try { Regex(trigger.pattern).containsMatchIn(content) } catch (e: Exception) { false }
                    } else {
                        trigger.pattern in content
                    }
                    val addressMatches = trigger.sourceAddress?.let { it == sourceAddress } ?: true
                    val portMatches = trigger.sourcePort?.let { it == sourcePort } ?: true
                    patternMatches && addressMatches && portMatches
                }
                else -> false
            }
        }

        matching.forEach { automation ->
            // Set packet variables
            executionEngine.setVariable("source_address", sourceAddress)
            executionEngine.setVariable("source_port", sourcePort.toString())
            executionEngine.setVariable("packet_content", content)

            executeAutomation(automation)
        }
    }

    /**
     * Execute an automation manually
     */
    public suspend fun executeAutomation(automation: Automation): ExecutionResult {
        return executionEngine.executeAutomation(automation)
    }

    private fun addExecutionLog(log: ExecutionLog) {
        _executionLogs.value = (listOf(log) + _executionLogs.value).take(100)
    }

    fun getExecutionLogs(automationId: String? = null): List<ExecutionLog> {
        return if (automationId != null) {
            _executionLogs.value.filter { it.automationId == automationId }
        } else {
            _executionLogs.value
        }
    }

    fun clearExecutionLogs() {
        _executionLogs.value = emptyList()
    }

    fun getVariables(): Map<String, String> = executionEngine.getAllVariables()

    fun getVariable(name: String): String? = executionEngine.getVariable(name)

    fun destroy() {
        scope.cancel()
    }
}
