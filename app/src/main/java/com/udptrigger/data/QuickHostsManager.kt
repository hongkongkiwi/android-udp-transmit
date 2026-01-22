package com.udptrigger.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.quickHostsDataStore: DataStore<Preferences> by preferencesDataStore(name = "quick_hosts_settings")

/**
 * Quick Hosts Manager for managing frequently used hosts.
 * Provides quick access to favorite hosts and automatic tracking of recently used.
 */
class QuickHostsManager(private val context: Context) {

    companion object {
        private val FAVORITES_KEY = stringPreferencesKey("favorites_json")
        private val RECENT_KEY = stringPreferencesKey("recent_json")
        private val MAX_RECENT = intPreferencesKey("max_recent")
        private val MAX_FAVORITES = intPreferencesKey("max_favorites")

        // Default favorites
        val DEFAULT_FAVORITES: List<QuickHost> = listOf(
            QuickHost(
                id = "localhost",
                name = "Localhost",
                host = "127.0.0.1",
                port = 5000,
                content = "TRIGGER",
                color = "#4CAF50"
            ),
            QuickHost(
                id = "broadcast",
                name = "Broadcast",
                host = "255.255.255.255",
                port = 5000,
                content = "BROADCAST",
                color = "#2196F3"
            )
        )
    }

    /**
     * Get all favorite hosts as a Flow
     */
    val favoritesFlow: Flow<List<QuickHost>> = context.quickHostsDataStore.data.map { preferences ->
        val json = preferences[FAVORITES_KEY]
        if (json != null) {
            parseHostsFromJson(json)
        } else {
            DEFAULT_FAVORITES
        }
    }

    /**
     * Get recent hosts as a Flow
     */
    val recentFlow: Flow<List<QuickHost>> = context.quickHostsDataStore.data.map { preferences ->
        val json = preferences[RECENT_KEY]
        if (json != null) {
            parseHostsFromJson(json)
        } else {
            emptyList()
        }
    }

    /**
     * Get all favorites (suspend)
     */
    suspend fun getFavorites(): List<QuickHost> {
        val json = context.quickHostsDataStore.data.first()[FAVORITES_KEY]
        return if (json != null) {
            parseHostsFromJson(json)
        } else {
            DEFAULT_FAVORITES
        }
    }

    /**
     * Get all recent (suspend)
     */
    suspend fun getRecent(): List<QuickHost> {
        val json = context.quickHostsDataStore.data.first()[RECENT_KEY]
        return if (json != null) {
            parseHostsFromJson(json)
        } else {
            emptyList()
        }
    }

    /**
     * Add a host to favorites
     */
    suspend fun addFavorite(host: QuickHost) {
        val favorites = getFavorites().toMutableList()

        // Remove if already exists
        favorites.removeAll { it.host == host.host && it.port == host.port }

        // Add to front
        favorites.add(0, host.copy(id = host.id.ifEmpty { UUID.randomUUID().toString() }))

        // Save
        val json = serializeHostsToJson(favorites)
        context.quickHostsDataStore.edit { preferences ->
            preferences[FAVORITES_KEY] = json
        }
    }

    /**
     * Remove from favorites
     */
    suspend fun removeFavorite(hostId: String) {
        val favorites = getFavorites().toMutableList()
        favorites.removeAll { it.id == hostId }

        val json = serializeHostsToJson(favorites)
        context.quickHostsDataStore.edit { preferences ->
            preferences[FAVORITES_KEY] = json
        }
    }

    /**
     * Update a favorite
     */
    suspend fun updateFavorite(host: QuickHost) {
        val favorites = getFavorites().toMutableList()
        val index = favorites.indexOfFirst { it.id == host.id }
        if (index >= 0) {
            favorites[index] = host
            val json = serializeHostsToJson(favorites)
            context.quickHostsDataStore.edit { preferences ->
                preferences[FAVORITES_KEY] = json
            }
        }
    }

    /**
     * Add to recent hosts (automatically called when connecting/sending)
     */
    suspend fun addToRecent(host: String, port: Int, content: String = "") {
        val recent = getRecent().toMutableList()

        // Create QuickHost from connection info
        val newHost = QuickHost(
            id = UUID.randomUUID().toString(),
            name = "$host:$port",
            host = host,
            port = port,
            content = content,
            lastUsed = System.currentTimeMillis(),
            useCount = 1
        )

        // Remove if already exists
        recent.removeAll { it.host == host && it.port == port }

        // Add to front
        recent.add(0, newHost)

        // Keep only last 20
        val trimmed = recent.take(20)

        val json = serializeHostsToJson(trimmed)
        context.quickHostsDataStore.edit { preferences ->
            preferences[RECENT_KEY] = json
        }
    }

    /**
     * Increment use count for a host
     */
    suspend fun incrementUseCount(hostId: String) {
        val favorites = getFavorites().toMutableList()
        val index = favorites.indexOfFirst { it.id == hostId }
        if (index >= 0) {
            val host = favorites[index]
            favorites[index] = host.copy(
                useCount = host.useCount + 1,
                lastUsed = System.currentTimeMillis()
            )
            val json = serializeHostsToJson(favorites)
            context.quickHostsDataStore.edit { preferences ->
                preferences[FAVORITES_KEY] = json
            }
        }
    }

    /**
     * Clear recent history
     */
    suspend fun clearRecent() {
        context.quickHostsDataStore.edit { preferences ->
            preferences[RECENT_KEY] = "[]"
        }
    }

    /**
     * Get frequently used hosts (sorted by use count)
     */
    suspend fun getFrequentlyUsed(limit: Int = 5): List<QuickHost> {
        return getFavorites()
            .sortedByDescending { it.useCount }
            .take(limit)
    }

    /**
     * Search hosts by name or address
     */
    suspend fun searchHosts(query: String): List<QuickHost> {
        val favorites = getFavorites()
        val recent = getRecent()
        val all = (favorites + recent).distinctBy { "${it.host}:${it.port}" }

        return all.filter { host ->
            host.name.contains(query, ignoreCase = true) ||
            host.host.contains(query, ignoreCase = true) ||
            host.content.contains(query, ignoreCase = true)
        }
    }

    /**
     * Export favorites to JSON
     */
    suspend fun exportFavorites(): String {
        return serializeHostsToJson(getFavorites())
    }

    /**
     * Import favorites from JSON
     */
    suspend fun importFavorites(json: String, replace: Boolean = false) {
        val imported = parseHostsFromJson(json)

        if (replace) {
            context.quickHostsDataStore.edit { preferences ->
                preferences[FAVORITES_KEY] = json
            }
        } else {
            val existing = getFavorites().toMutableList()
            val existingHosts = existing.map { "${it.host}:${it.port}" }.toSet()

            imported.forEach { host ->
                if ("${host.host}:${host.port}" !in existingHosts) {
                    existing.add(0, host)
                }
            }

            val outputJson = serializeHostsToJson(existing)
            context.quickHostsDataStore.edit { preferences ->
                preferences[FAVORITES_KEY] = outputJson
            }
        }
    }

    /**
     * Reset to defaults
     */
    suspend fun resetToDefaults() {
        val json = serializeHostsToJson(DEFAULT_FAVORITES)
        context.quickHostsDataStore.edit { preferences ->
            preferences[FAVORITES_KEY] = json
        }
    }

    private fun parseHostsFromJson(json: String): List<QuickHost> {
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { index ->
                val hostJson = jsonArray.getJSONObject(index)
                parseHostFromJson(hostJson)
            }
        } catch (e: Exception) {
            DEFAULT_FAVORITES
        }
    }

    private fun parseHostFromJson(json: JSONObject): QuickHost {
        return QuickHost(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", json.optString("host", "Unknown")),
            host = json.optString("host", ""),
            port = json.optInt("port", 5000),
            content = json.optString("content", "TRIGGER"),
            color = json.optString("color", "#607D8B"),
            icon = json.optString("icon", ""),
            description = json.optString("description", ""),
            useCount = json.optInt("useCount", 0),
            lastUsed = json.optLong("lastUsed", 0),
            isPinned = json.optBoolean("isPinned", false),
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }

    private fun serializeHostsToJson(hosts: List<QuickHost>): String {
        val jsonArray = JSONArray()
        hosts.forEach { host ->
            val json = JSONObject().apply {
                put("id", host.id)
                put("name", host.name)
                put("host", host.host)
                put("port", host.port)
                put("content", host.content)
                put("color", host.color)
                put("icon", host.icon)
                put("description", host.description)
                put("useCount", host.useCount)
                put("lastUsed", host.lastUsed)
                put("isPinned", host.isPinned)
                put("createdAt", host.createdAt)
            }
            jsonArray.put(json)
        }
        return jsonArray.toString()
    }
}

/**
 * Quick host entry
 */
data class QuickHost(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 5000,
    val content: String = "TRIGGER",
    val color: String = "#607D8B",
    val icon: String = "",
    val description: String = "",
    val useCount: Int = 0,
    val lastUsed: Long = 0,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Get display string for the host
     */
    fun displayAddress(): String = "$host:$port"

    /**
     * Check if this is a valid host configuration
     */
    fun isValid(): Boolean = host.isNotBlank() && port in 1..65535
}

/**
 * Host group for organizing favorites
 */
data class HostGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: String = "#607D8B",
    val hostIds: List<String> = emptyList(),
    val isExpanded: Boolean = true
)
