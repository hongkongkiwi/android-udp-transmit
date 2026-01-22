package com.udptrigger.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Configuration for a single UDP target
 */
@Serializable
data class UdpTarget(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val enabled: Boolean = true,
    val lastSuccess: Long? = null, // Timestamp of last successful send
    val lastFailure: Long? = null, // Timestamp of last failed send
    val consecutiveFailures: Int = 0
) {
    companion object {
        fun create(name: String, host: String, port: Int): UdpTarget {
            return UdpTarget(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                host = host,
                port = port,
                enabled = true
            )
        }
    }

    /**
     * Get display address
     */
    fun getDisplayAddress(): String = "$host:$port"

    /**
     * Check if target is valid
     */
    fun isValid(): Boolean = port in 1..65535 && host.isNotBlank()

    /**
     * Create a copy with updated status
     */
    fun withStatus(success: Boolean): UdpTarget {
        val now = System.currentTimeMillis()
        return if (success) {
            copy(
                lastSuccess = now,
                consecutiveFailures = 0
            )
        } else {
            copy(
                lastFailure = now,
                consecutiveFailures = consecutiveFailures + 1
            )
        }
    }

    /**
     * Get health status based on recent activity
     */
    fun getHealth(): TargetHealth {
        val now = System.currentTimeMillis()
        return when {
            lastSuccess == null -> TargetHealth.UNKNOWN
            consecutiveFailures >= 3 -> TargetHealth.FAILED
            lastFailure != null && lastFailure > lastSuccess -> TargetHealth.DEGRADED
            (now - (lastSuccess ?: 0)) < 60000 -> TargetHealth.HEALTHY // Last success within 1 minute
            (now - (lastSuccess ?: 0)) < 300000 -> TargetHealth.STALE // Last success within 5 minutes
            else -> TargetHealth.UNKNOWN
        }
    }
}

/**
 * Health status of a target
 */
enum class TargetHealth {
    UNKNOWN,
    HEALTHY,
    STALE,
    DEGRADED,
    FAILED;

    fun getDisplayLabel(): String = when (this) {
        UNKNOWN -> "Unknown"
        HEALTHY -> "Healthy"
        STALE -> "Stale"
        DEGRADED -> "Degraded"
        FAILED -> "Failed"
    }

    fun getColor(): Long = when (this) {
        UNKNOWN -> 0xFF9E9E9E // Gray
        HEALTHY -> 0xFF4CAF50 // Green
        STALE -> 0xFFFFC107 // Amber
        DEGRADED -> 0xFFFF9800 // Orange
        FAILED -> 0xFFF44336 // Red
    }
}

/**
 * Multi-target configuration containing multiple UDP targets
 */
@Serializable
data class MultiTargetConfig(
    val targets: List<UdpTarget> = emptyList(),
    val enabled: Boolean = false,
    val sendMode: SendMode = SendMode.SEQUENTIAL,
    val sequentialDelayMs: Long = 10
) {
    /**
     * Get all enabled targets
     */
    fun getEnabledTargets(): List<UdpTarget> = targets.filter { it.enabled }

    /**
     * Check if multi-target mode is active
     */
    fun isActive(): Boolean = enabled && getEnabledTargets().isNotEmpty()

    /**
     * Get target count
     */
    fun getTargetCount(): Int = targets.size

    /**
     * Get enabled target count
     */
    fun getEnabledTargetCount(): Int = getEnabledTargets().size

    /**
     * Add a target
     */
    fun addTarget(target: UdpTarget): MultiTargetConfig {
        return copy(targets = targets + target)
    }

    /**
     * Remove a target by ID
     */
    fun removeTarget(id: String): MultiTargetConfig {
        return copy(targets = targets.filterNot { it.id == id })
    }

    /**
     * Update a target
     */
    fun updateTarget(id: String, updatedTarget: UdpTarget): MultiTargetConfig {
        return copy(targets = targets.map {
            if (it.id == id) updatedTarget else it
        })
    }

    /**
     * Toggle a target's enabled state
     */
    fun toggleTarget(id: String): MultiTargetConfig {
        return copy(targets = targets.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        })
    }

    /**
     * Reorder targets
     */
    fun reorderTargets(newOrder: List<String>): MultiTargetConfig {
        val targetMap = targets.associateBy { it.id }
        val reordered = newOrder.mapNotNull { targetMap[it] }
        val remaining = targets.filterNot { it.id in newOrder }
        return copy(targets = reordered + remaining)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun toJson(config: MultiTargetConfig): String = json.encodeToString(config)

        fun fromJson(jsonString: String): MultiTargetConfig? {
            return try {
                json.decodeFromString(jsonString)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Send mode for multi-target
 */
enum class SendMode {
    SEQUENTIAL,   // Send to targets one by one with delay
    PARALLEL,     // Send to all targets simultaneously
    ROUND_ROBIN   // Alternate between targets on each trigger
}
