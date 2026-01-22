package com.udptrigger.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.macroDataStore: DataStore<Preferences> by preferencesDataStore(name = "macro_settings")

/**
 * Macro Manager for recording and playing back packet sequences.
 * Supports recording triggers with timing and playing back sequences.
 */
class MacroManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("macros", Context.MODE_PRIVATE)

    companion object {
        private val MACROS_KEY = stringPreferencesKey("macros_json")

        // Default macros
        val DEFAULT_MACROS: List<Macro> = listOf(
            Macro(
                id = "test_sequence",
                name = "Test Sequence",
                description = "Quick test with 3 packets",
                steps = listOf(
                    MacroStep(1, "192.168.1.100", 5000, "TEST1", 0),
                    MacroStep(2, "192.168.1.100", 5000, "TEST2", 100),
                    MacroStep(3, "192.168.1.100", 5000, "TEST3", 200)
                ),
                isBuiltIn = true
            ),
            Macro(
                id = "lighting_cue_1",
                name = "Lighting Cue 1",
                description = "Standard lighting sequence",
                steps = listOf(
                    MacroStep(1, "192.168.1.100", 5000, "CUE_1_UP", 0),
                    MacroStep(2, "192.168.1.100", 5000, "CUE_1_DIM", 500),
                    MacroStep(3, "192.168.1.100", 5000, "CUE_1_DOWN", 1000)
                ),
                isBuiltIn = true
            )
        )
    }

    /**
     * Get all macros as a Flow
     */
    val macrosFlow: Flow<List<Macro>> = context.macroDataStore.data.map { preferences ->
        val json = preferences[MACROS_KEY]
        if (json != null) {
            parseMacrosFromJson(json)
        } else {
            DEFAULT_MACROS
        }
    }

    /**
     * Get all macros (suspend function)
     */
    suspend fun getMacros(): List<Macro> {
        val json = context.macroDataStore.data.map { preferences ->
            preferences[MACROS_KEY]
        }.first()
        return if (json != null) {
            parseMacrosFromJson(json)
        } else {
            DEFAULT_MACROS
        }
    }

    /**
     * Save a macro (create or update)
     */
    suspend fun saveMacro(macro: Macro) {
        val macros = getMacros().toMutableList()

        // Remove existing macro with same ID
        macros.removeAll { it.id == macro.id }

        // Add updated macro
        macros.add(macro)

        // Save to storage
        val json = serializeMacrosToJson(macros)
        context.macroDataStore.edit { preferences ->
            preferences[MACROS_KEY] = json
        }
    }

    /**
     * Delete a macro
     */
    suspend fun deleteMacro(macroId: String) {
        val macros = getMacros().toMutableList()
        macros.removeAll { it.id == macroId }

        val json = serializeMacrosToJson(macros)
        context.macroDataStore.edit { preferences ->
            preferences[MACROS_KEY] = json
        }
    }

    /**
     * Get a specific macro by ID
     */
    suspend fun getMacro(macroId: String): Macro? {
        return getMacros().find { it.id == macroId }
    }

    /**
     * Duplicate a macro
     */
    suspend fun duplicateMacro(macroId: String): Macro? {
        val original = getMacro(macroId) ?: return null
        val newId = "${original.id}_copy_${UUID.randomUUID().toString().take(8)}"
        val duplicate = original.copy(
            id = newId,
            name = "${original.name} (Copy)",
            isBuiltIn = false
        )
        saveMacro(duplicate)
        return duplicate
    }

    /**
     * Export macros to JSON string
     */
    suspend fun exportMacros(): String {
        return serializeMacrosToJson(getMacros())
    }

    /**
     * Import macros from JSON string
     */
    suspend fun importMacros(json: String, replace: Boolean = false) {
        val imported = parseMacrosFromJson(json)

        if (replace) {
            // Clear existing and import
            context.macroDataStore.edit { preferences ->
                preferences[MACROS_KEY] = json
            }
        } else {
            // Merge with existing
            val existing = getMacros().toMutableList()
            val existingIds = existing.map { it.id }.toSet()

            imported.forEach { macro ->
                if (macro.id !in existingIds) {
                    existing.add(macro)
                }
            }

            val json = serializeMacrosToJson(existing)
            context.macroDataStore.edit { preferences ->
                preferences[MACROS_KEY] = json
            }
        }
    }

    /**
     * Reset to default macros
     */
    suspend fun resetToDefaults() {
        val json = serializeMacrosToJson(DEFAULT_MACROS)
        context.macroDataStore.edit { preferences ->
            preferences[MACROS_KEY] = json
        }
    }

    private fun parseMacrosFromJson(json: String): List<Macro> {
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { index ->
                val macroJson = jsonArray.getJSONObject(index)
                parseMacroFromJson(macroJson)
            }
        } catch (e: Exception) {
            DEFAULT_MACROS
        }
    }

    private fun parseMacroFromJson(json: JSONObject): Macro {
        val stepsArray = json.optJSONArray("steps") ?: JSONArray()
        val steps = (0 until stepsArray.length()).map { index ->
            val stepJson = stepsArray.getJSONObject(index)
            MacroStep(
                index = stepJson.optInt("index", index + 1),
                host = stepJson.optString("host", ""),
                port = stepJson.optInt("port", 5000),
                content = stepJson.optString("content", ""),
                delayMs = stepJson.optLong("delayMs", 0)
            )
        }

        return Macro(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", "Unnamed Macro"),
            description = json.optString("description", ""),
            steps = steps,
            repeatCount = json.optInt("repeatCount", 1),
            repeatDelayMs = json.optLong("repeatDelayMs", 1000),
            isBuiltIn = json.optBoolean("isBuiltIn", false),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun serializeMacrosToJson(macros: List<Macro>): String {
        val jsonArray = JSONArray()
        macros.forEach { macro ->
            val macroJson = JSONObject().apply {
                put("id", macro.id)
                put("name", macro.name)
                put("description", macro.description)
                put("repeatCount", macro.repeatCount)
                put("repeatDelayMs", macro.repeatDelayMs)
                put("isBuiltIn", macro.isBuiltIn)
                put("createdAt", macro.createdAt)
                put("updatedAt", macro.updatedAt)

                val stepsArray = JSONArray()
                macro.steps.forEach { step ->
                    val stepJson = JSONObject().apply {
                        put("index", step.index)
                        put("host", step.host)
                        put("port", step.port)
                        put("content", step.content)
                        put("delayMs", step.delayMs)
                    }
                    stepsArray.put(stepJson)
                }
                put("steps", stepsArray)
            }
            jsonArray.put(macroJson)
        }
        return jsonArray.toString()
    }
}

/**
 * Macro definition
 */
data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val steps: List<MacroStep>,
    val repeatCount: Int = 1,
    val repeatDelayMs: Long = 1000,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Individual step in a macro
 */
data class MacroStep(
    val index: Int,
    val host: String,
    val port: Int,
    val content: String,
    val delayMs: Long = 0 // Delay before this step executes
)

/**
 * Macro playback state
 */
data class MacroPlaybackState(
    val macro: Macro,
    val currentStep: Int = 0,
    val currentRepeat: Int = 0,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val progress: Float = 0f, // 0.0 to 1.0
    val elapsedTimeMs: Long = 0,
    val status: MacroPlaybackStatus = MacroPlaybackStatus.IDLE
)

/**
 * Macro playback status
 */
enum class MacroPlaybackStatus {
    IDLE,
    PLAYING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    ERROR
}

/**
 * Result of macro execution
 */
data class MacroExecutionResult(
    val macroId: String,
    val macroName: String,
    val totalSteps: Int,
    val completedSteps: Int,
    val failedSteps: Int,
    val totalDurationMs: Long,
    val wasSuccessful: Boolean,
    val errorMessage: String? = null,
    val executedAt: Long = System.currentTimeMillis()
)
