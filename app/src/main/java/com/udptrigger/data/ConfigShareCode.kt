package com.udptrigger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.udptrigger.ui.UdpConfig
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Data class for a shareable configuration code
 */
@Serializable
data class ShareableConfig(
    val host: String,
    val port: Int,
    val packetContent: String,
    val hexMode: Boolean,
    val includeTimestamp: Boolean,
    val includeBurstIndex: Boolean,
    val version: Int = 1
) {
    companion object {
        const val CURRENT_VERSION = 1

        /**
         * Convert from UdpConfig
         */
        fun fromUdpConfig(config: UdpConfig): ShareableConfig {
            return ShareableConfig(
                host = config.host,
                port = config.port,
                packetContent = config.packetContent,
                hexMode = config.hexMode,
                includeTimestamp = config.includeTimestamp,
                includeBurstIndex = config.includeBurstIndex,
                version = CURRENT_VERSION
            )
        }

        /**
         * Parse a share code string
         */
        fun fromShareCode(code: String): Result<ShareableConfig> {
            return try {
                val jsonString = Base64.getUrlDecoder()
                    .decode(code)
                    .toString(Charsets.UTF_8)
                val json = Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }
                Result.success(json.decodeFromString<ShareableConfig>(jsonString))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Convert to a shareable string (Base64 encoded JSON)
     */
    fun toShareCode(): String {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val jsonString = json.encodeToString(this)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(jsonString.toByteArray(Charsets.UTF_8))
    }

    /**
     * Convert to UdpConfig
     */
    fun toUdpConfig(): UdpConfig {
        return UdpConfig(
            host = host,
            port = port,
            packetContent = packetContent,
            hexMode = hexMode,
            includeTimestamp = includeTimestamp,
            includeBurstIndex = includeBurstIndex
        )
    }
}

/**
 * Generate a shareable configuration code from the current settings
 */
suspend fun generateShareCode(context: Context): Result<String> {
    return try {
        val dataStore = SettingsDataStore(context)
        val config = dataStore.configFlow.first()
        val shareableConfig = ShareableConfig.fromUdpConfig(config)
        Result.success(shareableConfig.toShareCode())
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Import configuration from a share code
 */
suspend fun importShareCode(context: Context, code: String): Result<UdpConfig> {
    return try {
        val shareableConfig = ShareableConfig.fromShareCode(code).getOrThrow()
        Result.success(shareableConfig.toUdpConfig())
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Validate a share code format
 */
fun isValidShareCode(code: String): Boolean {
    return try {
        val decoded = Base64.getUrlDecoder().decode(code)
        val jsonString = decoded.toString(Charsets.UTF_8)
        jsonString.contains("\"host\"") && jsonString.contains("\"port\"")
    } catch (e: Exception) {
        false
    }
}
