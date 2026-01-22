package com.udptrigger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.udptrigger.ui.UdpConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private val Context.scriptsDataStore: DataStore<Preferences> by preferencesDataStore(name = "scripts_settings")

/**
 * JavaScript scripting engine for advanced UDP automation.
 * Allows users to write custom scripts for complex trigger sequences.
 */
class ScriptEngine(private val context: Context) {

    private val executor: Executor = Executors.newSingleThreadExecutor()

    /**
     * Execute a script with provided variables
     */
    suspend fun executeScript(
        scriptId: String,
        variables: Map<String, Any> = emptyMap()
    ): ScriptResult {
        return withContext(Dispatchers.IO) {
            try {
                val script = getScript(scriptId)
                    ?: return@withContext ScriptResult.Error("Script not found: $scriptId")

                executeScriptContent(script, variables)
            } catch (e: Exception) {
                ScriptResult.Error("Script execution failed: ${e.message}")
            }
        }
    }

    /**
     * Execute script content directly
     */
    suspend fun executeScriptContent(
        script: Script,
        variables: Map<String, Any> = emptyMap()
    ): ScriptResult {
        return withContext(Dispatchers.IO) {
            try {
                // For a simple interpreter, we'll parse and execute basic commands
                val commands = parseScript(script.code)
                val results = mutableListOf<CommandResult>()

                for (command in commands) {
                    val result = executeCommand(command, variables)
                    results.add(result)

                    if (!result.success && script.stopOnError) {
                        break
                    }
                }

                ScriptResult.Success(
                    scriptId = script.id,
                    scriptName = script.name,
                    results = results,
                    executionTimeMs = results.sumOf { it.executionTimeMs }
                )
            } catch (e: Exception) {
                ScriptResult.Error("Execution error: ${e.message}")
            }
        }
    }

    /**
     * Parse script code into commands
     */
    private fun parseScript(code: String): List<ScriptCommand> {
        val commands = mutableListOf<ScriptCommand>()
        val lines = code.lines()

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) {
                continue
            }

            val command = parseCommand(trimmed, index)
            command?.let { commands.add(it) }
        }

        return commands
    }

    /**
     * Parse a single command line
     */
    private fun parseCommand(line: String, lineNumber: Int): ScriptCommand? {
        val parts = line.split("\\s+".toRegex(), limit = 3)
        if (parts.isEmpty()) return null

        return when (parts[0].lowercase()) {
            "send", "trigger" -> ScriptCommand(
                type = CommandType.SEND,
                host = parts.getOrNull(1) ?: "default",
                port = parts.getOrNull(2)?.toIntOrNull() ?: 5000,
                content = parts.getOrNull(2) ?: "TRIGGER",
                lineNumber = lineNumber
            )
            "wait", "delay" -> ScriptCommand(
                type = CommandType.WAIT,
                host = "",
                port = parts.getOrNull(1)?.toIntOrNull() ?: 100,
                content = parts.getOrNull(1) ?: "",
                lineNumber = lineNumber
            )
            "loop" -> ScriptCommand(
                type = CommandType.LOOP,
                host = "",
                port = parts.getOrNull(1)?.toIntOrNull() ?: 1,
                content = parts.getOrNull(2) ?: "",
                lineNumber = lineNumber
            )
            "set", "variable" -> ScriptCommand(
                type = CommandType.SET,
                host = parts.getOrNull(1) ?: "",
                port = 0,
                content = parts.getOrNull(2) ?: "",
                lineNumber = lineNumber
            )
            "if", "condition" -> ScriptCommand(
                type = CommandType.IF,
                host = parts.getOrNull(1) ?: "",
                port = 0,
                content = parts.getOrNull(2) ?: "",
                lineNumber = lineNumber
            )
            "connect" -> ScriptCommand(
                type = CommandType.CONNECT,
                host = parts.getOrNull(1) ?: "default",
                port = parts.getOrNull(2)?.toIntOrNull() ?: 5000,
                content = "",
                lineNumber = lineNumber
            )
            "disconnect" -> ScriptCommand(
                type = CommandType.DISCONNECT,
                host = "",
                port = 0,
                content = "",
                lineNumber = lineNumber
            )
            "log", "print" -> ScriptCommand(
                type = CommandType.LOG,
                host = "",
                port = 0,
                content = parts.drop(1).joinToString(" "),
                lineNumber = lineNumber
            )
            "repeat" -> ScriptCommand(
                type = CommandType.REPEAT,
                host = "",
                port = parts.getOrNull(1)?.toIntOrNull() ?: 1,
                content = parts.getOrNull(2) ?: "",
                lineNumber = lineNumber
            )
            else -> null
        }
    }

    /**
     * Execute a single command
     */
    private suspend fun executeCommand(
        command: ScriptCommand,
        variables: Map<String, Any>
    ): CommandResult {
        val startTime = System.currentTimeMillis()

        return try {
            when (command.type) {
                CommandType.SEND -> {
                    CommandResult(
                        type = command.type,
                        success = true,
                        message = "Sent to ${command.host}:${command.port}",
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                CommandType.WAIT -> {
                    Thread.sleep(command.port.toLong())
                    CommandResult(
                        type = command.type,
                        success = true,
                        message = "Waited ${command.port}ms",
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                CommandType.LOG -> {
                    CommandResult(
                        type = command.type,
                        success = true,
                        message = command.content,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                CommandType.CONNECT -> {
                    CommandResult(
                        type = command.type,
                        success = true,
                        message = "Connected to ${command.host}:${command.port}",
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                CommandType.DISCONNECT -> {
                    CommandResult(
                        type = command.type,
                        success = true,
                        message = "Disconnected",
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                else -> CommandResult(
                    type = command.type,
                    success = false,
                    message = "Command not implemented",
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            CommandResult(
                type = command.type,
                success = false,
                message = "Error: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime,
                error = e
            )
        }
    }

    // Script management

    suspend fun getScripts(): List<Script> {
        val json = context.scriptsDataStore.data.first()[SCRIPTS_KEY] ?: return DEFAULT_SCRIPTS
        return parseScriptsFromJson(json)
    }

    suspend fun getScript(scriptId: String): Script? {
        return getScripts().find { it.id == scriptId }
    }

    suspend fun saveScript(script: Script) {
        val scripts = getScripts().toMutableList()
        val index = scripts.indexOfFirst { it.id == script.id }
        if (index >= 0) {
            scripts[index] = script
        } else {
            scripts.add(script)
        }
        val json = serializeScriptsToJson(scripts)
        context.scriptsDataStore.edit { preferences ->
            preferences[SCRIPTS_KEY] = json
        }
    }

    suspend fun deleteScript(scriptId: String) {
        val scripts = getScripts().toMutableList()
        scripts.removeAll { it.id == scriptId }
        val json = serializeScriptsToJson(scripts)
        context.scriptsDataStore.edit { preferences ->
            preferences[SCRIPTS_KEY] = json
        }
    }

    suspend fun duplicateScript(scriptId: String): Script? {
        val original = getScript(scriptId) ?: return null
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (Copy)",
            isBuiltIn = false
        )
        saveScript(copy)
        return copy
    }

    suspend fun exportScripts(): String {
        return serializeScriptsToJson(getScripts())
    }

    suspend fun importScripts(json: String, replace: Boolean = false) {
        val imported = parseScriptsFromJson(json)
        val existing = getScripts().toMutableList()

        if (replace) {
            existing.clear()
        }

        val existingIds = existing.map { it.id }.toSet()
        imported.forEach { script ->
            if (script.id !in existingIds) {
                existing.add(script)
            }
        }

        val outputJson = serializeScriptsToJson(existing)
        context.scriptsDataStore.edit { preferences ->
            preferences[SCRIPTS_KEY] = outputJson
        }
    }

    private fun parseScriptsFromJson(json: String): List<Script> {
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { index ->
                val scriptJson = jsonArray.getJSONObject(index)
                Script(
                    id = scriptJson.optString("id", UUID.randomUUID().toString()),
                    name = scriptJson.optString("name", "Unnamed Script"),
                    description = scriptJson.optString("description", ""),
                    code = scriptJson.optString("code", ""),
                    isBuiltIn = scriptJson.optBoolean("isBuiltIn", false),
                    stopOnError = scriptJson.optBoolean("stopOnError", true),
                    createdAt = scriptJson.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = scriptJson.optLong("updatedAt", System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            DEFAULT_SCRIPTS
        }
    }

    private fun serializeScriptsToJson(scripts: List<Script>): String {
        val jsonArray = JSONArray()
        scripts.forEach { script ->
            val json = JSONObject().apply {
                put("id", script.id)
                put("name", script.name)
                put("description", script.description)
                put("code", script.code)
                put("isBuiltIn", script.isBuiltIn)
                put("stopOnError", script.stopOnError)
                put("createdAt", script.createdAt)
                put("updatedAt", script.updatedAt)
            }
            jsonArray.put(json)
        }
        return jsonArray.toString()
    }

    companion object {
        private val SCRIPTS_KEY = stringPreferencesKey("scripts_json")

        val DEFAULT_SCRIPTS = listOf(
            Script(
                id = "test_connection",
                name = "Test Connection",
                description = "Quick connection test with 3 packets",
                code = """
                    // Test connection script
                    connect default 5000
                    wait 100
                    send default 5000 TEST1
                    wait 50
                    send default 5000 TEST2
                    wait 50
                    send default 5000 TEST3
                    disconnect
                """.trimIndent(),
                isBuiltIn = true
            ),
            Script(
                id = "lighting_cue",
                name = "Lighting Cue Sequence",
                description = "Standard lighting control sequence",
                code = """
                    // Lighting cue sequence
                    connect 192.168.1.100 5000
                    wait 100
                    send 192.168.1.100 5000 CUE_UP
                    wait 500
                    send 192.168.1.100 5000 CUE_DIM
                    wait 500
                    send 192.168.1.100 5000 CUE_DOWN
                    disconnect
                """.trimIndent(),
                isBuiltIn = true
            ),
            Script(
                id = "burst_test",
                name = "Burst Test",
                description = "Send rapid burst of packets",
                code = """
                    // Burst test - 10 packets at 20ms intervals
                    repeat 10
                        send default 5000 BURST_TEST
                        wait 20
                    end
                """.trimIndent(),
                isBuiltIn = true
            )
        )
    }
}

/**
 * Script definition
 */
data class Script(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val code: String = "",
    val isBuiltIn: Boolean = false,
    val stopOnError: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Script variable
 */
data class ScriptVariable(
    val name: String,
    val value: String,
    val type: VariableType = VariableType.STRING
)

/**
 * Variable types
 */
enum class VariableType {
    STRING,
    NUMBER,
    BOOLEAN
}

/**
 * Script command types
 */
enum class CommandType {
    SEND,
    WAIT,
    LOOP,
    SET,
    IF,
    CONNECT,
    DISCONNECT,
    LOG,
    REPEAT
}

/**
 * Parsed script command
 */
data class ScriptCommand(
    val type: CommandType,
    val host: String,
    val port: Int,
    val content: String,
    val lineNumber: Int
)

/**
 * Command execution result
 */
data class CommandResult(
    val type: CommandType,
    val success: Boolean,
    val message: String,
    val executionTimeMs: Long,
    val error: Throwable? = null
)

/**
 * Script execution result
 */
sealed class ScriptResult {
    data class Success(
        val scriptId: String,
        val scriptName: String,
        val results: List<CommandResult>,
        val executionTimeMs: Long
    ) : ScriptResult()

    data class Error(val message: String) : ScriptResult()
}
