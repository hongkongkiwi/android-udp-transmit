package com.udptrigger.data

import com.udptrigger.ui.UdpConfig
import com.udptrigger.ui.PacketHistoryEntry

/**
 * Search Manager for filtering presets and history.
 * Provides search functionality across the application.
 */
object SearchManager {

    /**
     * Search result containing the matched item and relevance score
     */
    data class SearchResult<T>(
        val item: T,
        val score: Float,
        val matchedFields: List<String>
    )

    /**
     * Search presets by name, description, host, port, or content
     */
    fun searchPresets(
        presets: List<UdpPreset>,
        query: String,
        limit: Int = 20
    ): List<SearchResult<UdpPreset>> {
        if (query.isBlank()) {
            return presets.take(limit).map { SearchResult(it, 1f, emptyList()) }
        }

        val lowerQuery = query.lowercase()
        return presets.mapNotNull { preset ->
            var score = 0f
            val matchedFields = mutableListOf<String>()

            // Name match (highest priority)
            if (preset.name.lowercase().contains(lowerQuery)) {
                score += 10f
                matchedFields.add("name")
            }

            // Description match
            if (preset.description.lowercase().contains(lowerQuery)) {
                score += 5f
                matchedFields.add("description")
            }

            // Host match
            if (preset.config.host.lowercase().contains(lowerQuery)) {
                score += 3f
                matchedFields.add("host")
            }

            // Content match
            if (preset.config.packetContent.lowercase().contains(lowerQuery)) {
                score += 2f
                matchedFields.add("content")
            }

            // Port exact match
            if (preset.config.port.toString() == query) {
                score += 4f
                matchedFields.add("port")
            }

            if (score > 0) {
                SearchResult(preset, score, matchedFields)
            } else {
                null
            }
        }.sortedByDescending { it.score }.take(limit)
    }

    /**
     * Search history entries by data, sourceAddress, or sourcePort
     */
    fun searchHistory(
        history: List<PacketHistoryEntry>,
        query: String,
        limit: Int = 50
    ): List<SearchResult<PacketHistoryEntry>> {
        if (query.isBlank()) {
            return history.take(limit).map { SearchResult(it, 1f, emptyList()) }
        }

        val lowerQuery = query.lowercase()
        return history.mapNotNull { entry: PacketHistoryEntry ->
            var score = 0f
            val matchedFields = mutableListOf<String>()

            // Data match
            val data = entry.data ?: ""
            if (data.lowercase().contains(lowerQuery)) {
                score += 5f
                matchedFields.add("data")
            }

            // Source address match
            val sourceAddress = entry.sourceAddress ?: ""
            if (sourceAddress.lowercase().contains(lowerQuery)) {
                score += 3f
                matchedFields.add("sourceAddress")
            }

            // Source port match
            val sourcePort = entry.sourcePort?.toString() ?: ""
            if (sourcePort == query) {
                score += 3f
                matchedFields.add("sourcePort")
            }

            // Type match (SENT/RECEIVED)
            if (entry.type.name.lowercase().contains(lowerQuery)) {
                score += 2f
                matchedFields.add("type")
            }

            if (score > 0) {
                SearchResult(entry, score, matchedFields)
            } else {
                null
            }
        }.sortedByDescending { it.score }.take(limit)
    }

    /**
     * Search quick hosts by name or host address
     */
    fun searchQuickHosts(
        hosts: List<QuickHost>,
        query: String,
        limit: Int = 20
    ): List<SearchResult<QuickHost>> {
        if (query.isBlank()) {
            return hosts.take(limit).map { SearchResult(it, 1f, emptyList()) }
        }

        val lowerQuery = query.lowercase()
        return hosts.mapNotNull { host: QuickHost ->
            var score = 0f
            val matchedFields = mutableListOf<String>()

            // Name match
            if (host.name.lowercase().contains(lowerQuery)) {
                score += 10f
                matchedFields.add("name")
            }

            // Address match
            if (host.host.lowercase().contains(lowerQuery)) {
                score += 5f
                matchedFields.add("host")
            }

            if (score > 0) {
                SearchResult(host, score, matchedFields)
            } else {
                null
            }
        }.sortedByDescending { it.score }.take(limit)
    }

    /**
     * Global search across all item types
     */
    sealed class GlobalSearchItem {
        data class Preset(val preset: UdpPreset) : GlobalSearchItem()
        data class History(val entry: PacketHistoryEntry) : GlobalSearchItem()
        data class Host(val host: QuickHost) : GlobalSearchItem()
    }

    data class GlobalResult(
        val item: GlobalSearchItem,
        val score: Float,
        val type: String
    )

    fun globalSearch(
        presets: List<UdpPreset>,
        history: List<PacketHistoryEntry>,
        hosts: List<QuickHost>,
        query: String,
        limit: Int = 30
    ): List<GlobalResult> {
        val results = mutableListOf<GlobalResult>()

        results.addAll(searchPresets(presets, query, limit).map {
            GlobalResult(GlobalSearchItem.Preset(it.item), it.score, "preset")
        })

        results.addAll(searchHistory(history, query, limit).map {
            GlobalResult(GlobalSearchItem.History(it.item), it.score, "history")
        })

        results.addAll(searchQuickHosts(hosts, query, limit).map {
            GlobalResult(GlobalSearchItem.Host(it.item), it.score, "host")
        })

        return results.sortedByDescending { it.score }.take(limit)
    }
}
