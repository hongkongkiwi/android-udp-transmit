package com.udptrigger.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Packet Template Manager for saving and loading complex packet configurations.
 * Supports templates with variable substitution, hex data, and presets.
 */
class PacketTemplateManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    // Templates storage
    private val _templates = MutableStateFlow<List<PacketTemplate>>(emptyList())
    val templates: StateFlow<List<PacketTemplate>> = _templates.asStateFlow()

    // Active template
    private val _activeTemplate = MutableStateFlow<PacketTemplate?>(null)
    val activeTemplate: StateFlow<PacketTemplate?> = _activeTemplate.asStateFlow()

    @Serializable
    data class PacketTemplate(
        val id: String,
        val name: String,
        val description: String = "",
        val category: String = "General",
        val content: String,
        val isHexMode: Boolean = false,
        val includeTimestamp: Boolean = true,
        val includeBurstIndex: Boolean = false,
        val targetHost: String = "192.168.1.100",
        val targetPort: Int = 5000,
        val variables: List<TemplateVariable> = emptyList(),
        val actions: List<TemplateAction> = emptyList(),
        val isFavorite: Boolean = false,
        val usageCount: Int = 0,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis()
    )

    @Serializable
    data class TemplateVariable(
        val name: String,
        val type: VariableType = VariableType.STRING,
        val defaultValue: String = "",
        val options: List<String> = emptyList(),
        val required: Boolean = false,
        val minLength: Int? = null,
        val maxLength: Int? = null
    )

    enum class VariableType {
        STRING, NUMBER, HEX, BOOLEAN, SELECT, TIMESTAMP, RANDOM
    }

    @Serializable
    data class TemplateAction(
        val name: String,
        val type: ActionType,
        val config: String = ""
    )

    enum class ActionType {
        SEND, DELAY, REPEAT, CONDITION, NOTIFY, WEBHOOK
    }

    /**
     * Load templates from storage
     */
    suspend fun loadTemplates() = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("packet_templates", Context.MODE_PRIVATE)
            val jsonString = prefs.getString("templates", null)
            if (jsonString != null) {
                val templateList = kotlinx.serialization.json.Json.decodeFromString<List<PacketTemplate>>(jsonString)
                _templates.value = templateList
            } else {
                // Load default templates
                loadDefaultTemplates()
            }
        } catch (e: Exception) {
            loadDefaultTemplates()
        }
    }

    private suspend fun loadDefaultTemplates() {
        val defaultTemplates = listOf(
            PacketTemplate(
                id = "default_trigger",
                name = "Standard Trigger",
                description = "Standard trigger packet with timestamp",
                content = "TRIGGER",
                includeTimestamp = true
            ),
            PacketTemplate(
                id = "obs_trigger",
                name = "OBS Trigger",
                description = "Trigger for OBS Studio via WebSocket",
                category = "Broadcast",
                content = "{\"request_type\":\"Trigger\",\"source\":\"UDP Trigger\"}",
                isHexMode = false,
                includeTimestamp = false
            ),
            PacketTemplate(
                id = "show_control",
                name = "Show Control",
                description = "Theatre/show control protocol",
                category = "Show Control",
                content = "GO:{cue_number}",
                targetPort = 6000,
                variables = listOf(
                    TemplateVariable(
                        name = "cue_number",
                        type = VariableType.NUMBER,
                        defaultValue = "1",
                        required = true
                    )
                )
            ),
            PacketTemplate(
                id = "midi_cc",
                name = "MIDI CC",
                description = "MIDI Control Change message",
                category = "MIDI",
                content = "CC{channel}:{controller}:{value}",
                isHexMode = false,
                variables = listOf(
                    TemplateVariable(name = "channel", type = VariableType.NUMBER, defaultValue = "1"),
                    TemplateVariable(name = "controller", type = VariableType.NUMBER, defaultValue = "1"),
                    TemplateVariable(name = "value", type = VariableType.NUMBER, defaultValue = "127")
                )
            ),
            PacketTemplate(
                id = "artnet_dmx",
                name = "Art-Net DMX",
                description = "Art-Net lighting control",
                category = "Lighting",
                content = "Art-Net{universe}:{address}:{value}",
                isHexMode = false,
                targetPort = 6454,
                variables = listOf(
                    TemplateVariable(name = "universe", type = VariableType.NUMBER, defaultValue = "0"),
                    TemplateVariable(name = "address", type = VariableType.NUMBER, defaultValue = "0"),
                    TemplateVariable(name = "value", type = VariableType.NUMBER, defaultValue = "255")
                )
            ),
            PacketTemplate(
                id = "custom_hex",
                name = "Custom Hex",
                description = "Custom hex packet",
                category = "Custom",
                content = "AA BB CC DD",
                isHexMode = true,
                includeTimestamp = false
            ),
            PacketTemplate(
                id = "timestamp_only",
                name = "Timestamp Only",
                description = "Send only current timestamp",
                content = "",
                includeTimestamp = true,
                includeBurstIndex = false
            ),
            PacketTemplate(
                id = "burst_sequence",
                name = "Burst Sequence",
                description = "Multi-packet burst with sequence",
                content = "BURST:{sequence}",
                includeTimestamp = true,
                includeBurstIndex = true,
                actions = listOf(
                    TemplateAction("Burst Mode", ActionType.REPEAT, "count=5")
                )
            )
        )

        _templates.value = defaultTemplates
        saveTemplates()
    }

    /**
     * Save templates to storage
     */
    suspend fun saveTemplates() = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("packet_templates", Context.MODE_PRIVATE)
            val jsonString = kotlinx.serialization.json.Json.encodeToString(_templates.value)
            prefs.edit().putString("templates", jsonString).apply()
        } catch (e: Exception) {
            // Log error
        }
    }

    /**
     * Add new template
     */
    suspend fun addTemplate(template: PacketTemplate) {
        val templates = _templates.value.toMutableList()
        templates.add(template)
        _templates.value = templates
        saveTemplates()
    }

    /**
     * Update existing template
     */
    suspend fun updateTemplate(template: PacketTemplate) {
        val templates = _templates.value.toMutableList()
        val index = templates.indexOfFirst { it.id == template.id }
        if (index >= 0) {
            templates[index] = template.copy(updatedAt = System.currentTimeMillis())
            _templates.value = templates
            saveTemplates()
        }
    }

    /**
     * Delete template by ID
     */
    suspend fun deleteTemplate(templateId: String) {
        val templates = _templates.value.filter { it.id != templateId }
        _templates.value = templates
        saveTemplates()
    }

    /**
     * Get template by ID
     */
    fun getTemplate(templateId: String): PacketTemplate? {
        return _templates.value.find { it.id == templateId }
    }

    /**
     * Get templates by category
     */
    fun getTemplatesByCategory(category: String): List<PacketTemplate> {
        return _templates.value.filter { it.category == category }
    }

    /**
     * Get favorite templates
     */
    fun getFavoriteTemplates(): List<PacketTemplate> {
        return _templates.value.filter { it.isFavorite }
    }

    /**
     * Toggle favorite status
     */
    suspend fun toggleFavorite(templateId: String) {
        val templates = _templates.value.toMutableList()
        val index = templates.indexOfFirst { it.id == templateId }
        if (index >= 0) {
            templates[index] = templates[index].copy(
                isFavorite = !templates[index].isFavorite,
                updatedAt = System.currentTimeMillis()
            )
            _templates.value = templates
            saveTemplates()
        }
    }

    /**
     * Increment usage count
     */
    suspend fun incrementUsage(templateId: String) {
        val templates = _templates.value.toMutableList()
        val index = templates.indexOfFirst { it.id == templateId }
        if (index >= 0) {
            templates[index] = templates[index].copy(
                usageCount = templates[index].usageCount + 1,
                updatedAt = System.currentTimeMillis()
            )
            _templates.value = templates
            saveTemplates()
        }
    }

    /**
     * Set active template
     */
    fun setActiveTemplate(template: PacketTemplate?) {
        _activeTemplate.value = template
    }

    /**
     * Build packet content from template with variable values
     */
    fun buildPacketContent(
        template: PacketTemplate,
        variableValues: Map<String, String> = emptyMap()
    ): String {
        var content = template.content

        // Replace template variables
        template.variables.forEach { variable ->
            val value = variableValues[variable.name] ?: variable.defaultValue
            content = content.replace("{${variable.name}}", value)
        }

        // Apply variable type transformations
        if (template.isHexMode) {
            // In hex mode, content is already handled
        }

        return content
    }

    /**
     * Get default variable values for template
     */
    fun getDefaultVariableValues(template: PacketTemplate): Map<String, String> {
        return template.variables.associate { variable ->
            variable.name to variable.defaultValue
        }
    }

    /**
     * Validate variable value against constraints
     */
    fun validateVariableValue(variable: TemplateVariable, value: String): ValidationResult {
        if (value.isBlank() && variable.required) {
            return ValidationResult(false, "${variable.name} is required")
        }

        when (variable.type) {
            VariableType.NUMBER -> {
                if (value.toLongOrNull() == null) {
                    return ValidationResult(false, "${variable.name} must be a number")
                }
            }
            VariableType.HEX -> {
                if (!value.matches(Regex("^[0-9A-Fa-f]*$"))) {
                    return ValidationResult(false, "${variable.name} must be valid hex")
                }
            }
            VariableType.BOOLEAN -> {
                if (value.lowercase() !in listOf("true", "false", "1", "0")) {
                    return ValidationResult(false, "${variable.name} must be true/false")
                }
            }
            VariableType.SELECT -> {
                if (variable.options.isNotEmpty() && value !in variable.options) {
                    return ValidationResult(false, "${variable.name} must be one of: ${variable.options.joinToString(", ")}")
                }
            }
            VariableType.TIMESTAMP, VariableType.RANDOM, VariableType.STRING -> {
                // No specific validation
            }
        }

        variable.minLength?.let { min ->
            if (value.length < min) {
                return ValidationResult(false, "${variable.name} must be at least $min characters")
            }
        }

        variable.maxLength?.let { max ->
            if (value.length > max) {
                return ValidationResult(false, "${variable.name} must be at most $max characters")
            }
        }

        return ValidationResult(true)
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Get all unique categories
     */
    fun getCategories(): List<String> {
        return _templates.value.map { it.category }.distinct().sorted()
    }

    /**
     * Search templates
     */
    fun searchTemplates(query: String): List<PacketTemplate> {
        val lowerQuery = query.lowercase()
        return _templates.value.filter { template ->
            template.name.lowercase().contains(lowerQuery) ||
                    template.description.lowercase().contains(lowerQuery) ||
                    template.category.lowercase().contains(lowerQuery) ||
                    template.content.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Get most used templates
     */
    fun getMostUsedTemplates(limit: Int = 5): List<PacketTemplate> {
        return _templates.value.sortedByDescending { it.usageCount }.take(limit)
    }

    /**
     * Export templates as JSON
     */
    suspend fun exportTemplates(templateIds: List<String>? = null): String {
        val templatesToExport = if (templateIds != null) {
            _templates.value.filter { it.id in templateIds }
        } else {
            _templates.value
        }
        return kotlinx.serialization.json.Json.encodeToString(templatesToExport)
    }

    /**
     * Import templates from JSON
     */
    suspend fun importTemplates(jsonString: String, replace: Boolean = false) {
        try {
            val importedTemplates = kotlinx.serialization.json.Json.decodeFromString<List<PacketTemplate>>(jsonString)

            if (replace) {
                _templates.value = importedTemplates
            } else {
                // Merge with existing, keeping IDs unique
                val existingIds = _templates.value.map { it.id }.toSet()
                val newTemplates = importedTemplates.filter { it.id !in existingIds }
                _templates.value = _templates.value + newTemplates
            }

            saveTemplates()
        } catch (e: Exception) {
            // Log import error
        }
    }

    /**
     * Clear all templates (except built-in)
     */
    suspend fun clearCustomTemplates() {
        _templates.value = _templates.value.filter { it.createdAt == it.updatedAt }
        saveTemplates()
    }
}
