package com.udptrigger.domain

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Packet Analyzer for deep inspection of UDP/TCP packets.
 * Provides detailed analysis, hex viewing, and protocol detection.
 */
class PacketAnalyzer {

    /**
     * Analysis result with detailed packet information
     */
    data class PacketAnalysis(
        val rawData: ByteArray,
        val hexRepresentation: String,
        val hexGroups: List<HexGroup>,
        val asciiRepresentation: String,
        val textDecodings: Map<String, String>,
        val byteStats: ByteStats,
        val protocolHints: List<ProtocolHint>,
        val structure: PacketStructure?,
        val checksum: ChecksumResult?,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as PacketAnalysis
            if (!rawData.contentEquals(other.rawData)) return false
            return true
        }

        override fun hashCode(): Int {
            return rawData.contentHashCode()
        }
    }

    data class HexGroup(
        val offset: Int,
        val bytes: String,          // "AA BB CC DD"
        val ascii: String,          // ".ABC"
        val hasAnsi: Boolean
    )

    data class ByteStats(
        val totalBytes: Int,
        val printableAscii: Int,
        val controlChars: Int,
        val extendedAscii: Int,
        val nullBytes: Int,
        val uniqueBytes: Int,
        val entropy: Double,
        val isLikelyText: Boolean,
        val isLikelyBinary: Boolean,
        val dominantCharset: Charset?
    )

    data class ProtocolHint(
        val protocol: String,
        val confidence: Double, // 0.0 to 1.0
        val details: String,
        val matchedPattern: String?
    )

    data class PacketStructure(
        val hasHeader: Boolean,
        val headerSize: Int?,
        val hasPayload: Boolean,
        val payloadSize: Int?,
        val hasFooter: Boolean,
        val footerSize: Int?,
        val suggestedStructure: String?
    )

    data class ChecksumResult(
        val algorithm: String,
        val calculatedValue: String,
        val isValid: Boolean?,
        val rawChecksum: String?
    )

    /**
     * Analyze a packet and return detailed information
     */
    fun analyze(data: ByteArray): PacketAnalysis {
        val hex = bytesToHex(data)
        val hexGroups = hexGroups(data)
        val ascii = bytesToAscii(data)
        val textDecodings = decodeText(data)
        val byteStats = analyzeBytes(data)
        val protocolHints = detectProtocols(data, textDecodings)
        val structure = analyzeStructure(data, textDecodings)
        val checksum = analyzeChecksum(data)
        val timestamp = System.currentTimeMillis()

        return PacketAnalysis(
            rawData = data,
            hexRepresentation = hex,
            hexGroups = hexGroups,
            asciiRepresentation = ascii,
            textDecodings = textDecodings,
            byteStats = byteStats,
            protocolHints = protocolHints,
            structure = structure,
            checksum = checksum,
            timestamp = timestamp
        )
    }

    /**
     * Convert bytes to hex string
     */
    fun bytesToHex(data: ByteArray, separator: String = " "): String {
        return data.joinToString(separator) { "%02X".format(it) }
    }

    /**
     * Convert bytes to hex string (lowercase)
     */
    fun bytesToHexLower(data: ByteArray, separator: String = " "): String {
        return data.joinToString(separator) { "%02x".format(it) }
    }

    /**
     * Convert hex string to bytes
     */
    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace("\\s".toRegex(), "")
        return cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    /**
     * Convert bytes to ASCII representation (non-printable as dots)
     */
    fun bytesToAscii(data: ByteArray): String {
        return data.map { byte ->
            when {
                byte in 32..126 -> byte.toChar()
                byte == 9.toByte() -> '\t'  // Tab
                byte == 10.toByte() -> '\n' // Line feed
                byte == 13.toByte() -> '\r' // Carriage return
                else -> '.'
            }
        }.joinToString("")
    }

    /**
     * Group bytes into 16-byte rows for display
     */
    fun hexGroups(data: ByteArray): List<HexGroup> {
        val groups = mutableListOf<HexGroup>()
        val charsets = listOf(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1, StandardCharsets.US_ASCII)

        for (i in data.indices step 16) {
            val end = minOf(i + 16, data.size)
            val chunk = data.copyOfRange(i, end)

            // Build hex representation
            val hex = chunk.joinToString(" ") { "%02X".format(it) }
            val paddedHex = if (chunk.size < 16) {
                hex + "   ".repeat(16 - chunk.size)
            } else hex

            // Build ASCII representation
            val ascii = chunk.map { byte ->
                when {
                    byte in 32..126 -> byte.toChar()
                    byte == 9.toByte() || byte == 10.toByte() || byte == 13.toByte() -> byte.toChar()
                    else -> '.'
                }
            }.joinToString("")

            val hasAnsi = chunk.any { byte -> byte in 0x1B..0x1F }

            groups.add(HexGroup(i, paddedHex, ascii, hasAnsi))
        }

        return groups
    }

    /**
     * Decode bytes as various text encodings
     */
    fun decodeText(data: ByteArray): Map<String, String> {
        val results = mutableMapOf<String, String>()

        // Try various encodings
        val encodings = listOf(
            "UTF-8" to StandardCharsets.UTF_8,
            "UTF-16LE" to StandardCharsets.UTF_16LE,
            "UTF-16BE" to Charsets.UTF_16BE,
            "ASCII" to StandardCharsets.US_ASCII,
            "ISO-8859-1" to StandardCharsets.ISO_8859_1,
            "Windows-1252" to Charsets.ISO_8859_1 // Close approximation
        )

        for ((name, charset) in encodings) {
            try {
                val decoded = String(data, charset)
                if (isValidDecodedText(decoded, data.size)) {
                    results[name] = decoded
                }
            } catch (e: Exception) {
                // Skip invalid encoding
            }
        }

        // Also add raw timestamp if data looks like a number
        if (data.isNotEmpty()) {
            val numericString = String(data, StandardCharsets.US_ASCII).trim()
            if (numericString.matches(Regex("^\\d+$"))) {
                try {
                    val timestamp = numericString.toLong()
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    results["Timestamp"] = "${timestamp} (${dateFormat.format(Date(timestamp))})"
                } catch (e: Exception) {
                    // Not a valid timestamp
                }
            }
        }

        return results
    }

    private fun isValidDecodedText(text: String, originalLength: Int): Boolean {
        // A valid decode should have reasonable ratio of printable characters
        if (text.isEmpty()) return false
        if (text.length < originalLength * 0.5) return false // Too much data lost

        val printableCount = text.count { ch ->
            ch.code in 32..126 || ch == '\t' || ch == '\n' || ch == '\r'
        }
        val ratio = printableCount.toDouble() / text.length

        return ratio > 0.7 // At least 70% printable
    }

    /**
     * Analyze byte statistics
     */
    fun analyzeBytes(data: ByteArray): ByteStats {
        if (data.isEmpty()) {
            return ByteStats(
                totalBytes = 0,
                printableAscii = 0,
                controlChars = 0,
                extendedAscii = 0,
                nullBytes = 0,
                uniqueBytes = 0,
                entropy = 0.0,
                isLikelyText = false,
                isLikelyBinary = true,
                dominantCharset = null
            )
        }

        var printableAscii = 0
        var controlChars = 0
        var extendedAscii = 0
        var nullBytes = 0
        val uniqueBytesSet = mutableSetOf<Byte>()

        for (byte in data) {
            when {
                byte == 0.toByte() -> nullBytes++
                byte in 1..31 || byte == 127.toByte() -> controlChars++
                byte in 32..126 -> printableAscii++
                byte > 0 -> extendedAscii++
            }
            uniqueBytesSet.add(byte)
        }

        val totalBytes = data.size
        val uniqueBytes = uniqueBytesSet.size
        val entropy = calculateEntropy(data)
        val isLikelyText = printableAscii.toDouble() / totalBytes > 0.7
        val isLikelyBinary = !isLikelyText

        // Determine dominant charset
        val dominantCharset = when {
            isLikelyText && data.all { it < 128.toByte() } -> StandardCharsets.US_ASCII
            isLikelyText -> StandardCharsets.UTF_8
            else -> null
        }

        return ByteStats(
            totalBytes = totalBytes,
            printableAscii = printableAscii,
            controlChars = controlChars,
            extendedAscii = extendedAscii,
            nullBytes = nullBytes,
            uniqueBytes = uniqueBytes,
            entropy = entropy,
            isLikelyText = isLikelyText,
            isLikelyBinary = isLikelyBinary,
            dominantCharset = dominantCharset
        )
    }

    /**
     * Calculate Shannon entropy of data
     */
    private fun calculateEntropy(data: ByteArray): Double {
        if (data.isEmpty()) return 0.0

        val byteCounts = IntArray(256)
        for (byte in data) {
            byte.toInt() and 0xFF
            byteCounts[byte.toInt() and 0xFF]++
        }

        var entropy = 0.0
        val total = data.size.toDouble()

        for (count in byteCounts) {
            if (count > 0) {
                val probability = count / total
                entropy -= probability * kotlin.math.log2(probability)
            }
        }

        return entropy
    }

    /**
     * Detect common protocols based on packet content
     */
    fun detectProtocols(data: ByteArray, textDecodings: Map<String, String>): List<ProtocolHint> {
        val hints = mutableListOf<ProtocolHint>()
        val content = textDecodings["UTF-8"] ?: textDecodings["ASCII"] ?: ""
        val contentLower = content.lowercase()

        // OSC (Open Sound Control) - starts with /
        if (content.startsWith("/")) {
            hints.add(ProtocolHint("OSC", 0.9, "Open Sound Control message", content.take(50)))
        }

        // Art-Net - starts with "Art-Net"
        if (content.startsWith("Art-Net") || content.startsWith("Art-")) {
            hints.add(ProtocolHint("Art-Net", 0.95, "DMX lighting control protocol", "Art-Net"))
        }

        // SACN (Streaming ACN) - starts with 0x4143 0x4E53 (ACNS)
        if (data.size >= 4 && data[0] == 0x41.toByte() && data[1] == 0x43.toByte()) {
            hints.add(ProtocolHint("sACN", 0.9, "E1.31 Streaming ACN", "ACN header detected"))
        }

        // MIDI - specific byte patterns
        if (data.size >= 2 && (data[0].toInt() and 0xF0) in 0x80..0xEF) {
            hints.add(ProtocolHint("MIDI", 0.85, "MIDI message detected", "Status byte: 0x%02X".format(data[0].toInt() and 0xF0)))
        }

        // HTTP
        if (contentLower.startsWith("http/") || contentLower.startsWith("get ") ||
            contentLower.startsWith("post ") || contentLower.startsWith("head ")) {
            hints.add(ProtocolHint("HTTP", 0.9, "HTTP request detected", content.take(50)))
        }

        // JSON
        if (content.trim().startsWith("{") && content.trim().endsWith("}")) {
            hints.add(ProtocolHint("JSON", 0.8, "JSON data detected", content.take(30)))
        }

        // XML
        if (contentLower.contains("<") && contentLower.contains(">") && contentLower.contains("<?xml")) {
            hints.add(ProtocolHint("XML", 0.7, "XML data detected", "XML declaration found"))
        }

        // SysEx (MIDI System Exclusive) - starts with 0xF0
        if (data.isNotEmpty() && data[0] == 0xF0.toByte()) {
            hints.add(ProtocolHint("MIDI SysEx", 0.85, "MIDI System Exclusive message", "F0 header"))
        }

        // Note On/Off
        if (content.matches(Regex(".*NOTE\\s*(ON|OFF).*", RegexOption.IGNORE_CASE))) {
            hints.add(ProtocolHint("MIDI Note", 0.75, "Contains MIDI note command", null))
        }

        // Timestamp format (nanoseconds)
        if (content.matches(Regex("^\\d{18,19}$"))) {
            hints.add(ProtocolHint("Nanosecond Timestamp", 0.8, "Likely nanosecond timestamp", content))
        }

        // Trigger keyword
        if (contentLower.contains("trigger")) {
            hints.add(ProtocolHint("Trigger", 0.6, "Contains trigger keyword", "trigger"))
        }

        // Broadcast address detection
        if (contentLower.contains("255.255.255.255") || contentLower.contains("broadcast")) {
            hints.add(ProtocolHint("Broadcast", 0.5, "Broadcast packet", null))
        }

        return hints.sortedByDescending { it.confidence }
    }

    /**
     * Analyze packet structure
     */
    fun analyzeStructure(data: ByteArray, textDecodings: Map<String, String>): PacketStructure {
        val content = textDecodings["UTF-8"] ?: ""

        var hasHeader = false
        var headerSize: Int? = null
        var hasPayload = false
        var payloadSize: Int? = null
        var hasFooter = false
        var footerSize: Int? = null
        var suggestedStructure: String? = null

        // Check for common structures

        // JSON structure
        if (content.trim().startsWith("{") && content.trim().endsWith("}")) {
            suggestedStructure = "JSON Object"
        }

        // Key:value pairs
        if (content.contains(":") && content.contains(",")) {
            suggestedStructure = "Key-Value Pairs"
        }

        // Timestamp prefix
        if (content.matches(Regex("^\\d+:")) || content.matches(Regex("^\\d+\\.\\d+:"))) {
            suggestedStructure = "Timestamp:Data"
            hasHeader = true
            headerSize = content.indexOf(":") + 1
            hasPayload = true
            payloadSize = content.length - headerSize
        }

        // Burst format
        if (content.contains(":") && content.count { it == ':' } >= 2) {
            suggestedStructure = "Timestamp:Index:Data"
        }

        // Empty packet
        if (data.isEmpty()) {
            suggestedStructure = "Empty Packet"
        }

        // Single line
        if (content.contains("\n") || content.contains("\r")) {
            suggestedStructure = "Multi-line Text"
        }

        return PacketStructure(
            hasHeader = hasHeader,
            headerSize = headerSize,
            hasPayload = hasPayload,
            payloadSize = payloadSize,
            hasFooter = hasFooter,
            footerSize = footerSize,
            suggestedStructure = suggestedStructure
        )
    }

    /**
     * Try to calculate checksums for common protocols
     */
    fun analyzeChecksum(data: ByteArray): ChecksumResult {
        if (data.isEmpty()) {
            return ChecksumResult("None", "N/A", null, null)
        }

        // Art-Net checksum (simple sum of ID + opcode)
        if (data.size >= 10 && String(data.sliceArray(0..7)) == "Art-Net") {
            val opcode = (data[8].toInt() and 0xFF) or ((data[9].toInt() and 0xFF) shl 8)
            return ChecksumResult(
                algorithm = "Art-Net",
                calculatedValue = "Opcode: 0x%04X".format(opcode),
                isValid = null,
                rawChecksum = null
            )
        }

        // MIDI has no checksum for most messages
        if (data.size >= 1 && (data[0].toInt() and 0xF0) in 0x80..0xEF) {
            return ChecksumResult("None", "N/A", null, null)
        }

        // Generic checksum
        val sum = data.fold(0L) { acc, byte -> acc + (byte.toInt() and 0xFF) }
        val checksum = sum and 0xFFFF

        return ChecksumResult(
            algorithm = "Sum8",
            calculatedValue = "0x%04X".format(checksum),
            isValid = null,
            rawChecksum = null
        )
    }

    /**
     * Compare two packets and show differences
     */
    fun comparePackets(packet1: ByteArray, packet2: ByteArray): PacketComparison {
        val analysis1 = analyze(packet1)
        val analysis2 = analyze(packet2)

        val differences = mutableListOf<ByteDifference>()
        val maxLen = maxOf(packet1.size, packet2.size)

        for (i in 0 until maxLen) {
            val byte1 = if (i < packet1.size) packet1[i] else null
            val byte2 = if (i < packet2.size) packet2[i] else null

            if (byte1 != byte2) {
                differences.add(ByteDifference(
                    offset = i,
                    byte1 = byte1?.let { "0x%02X".format(it.toInt() and 0xFF) },
                    byte2 = byte2?.let { "0x%02X".format(it.toInt() and 0xFF) }
                ))
            }
        }

        return PacketComparison(
            analysis1 = analysis1,
            analysis2 = analysis2,
            differences = differences,
            totalDifferences = differences.size,
            similarityPercent = if (maxLen > 0) {
                ((maxLen - differences.size).toDouble() / maxLen * 100)
            } else 100.0
        )
    }

    data class PacketComparison(
        val analysis1: PacketAnalysis,
        val analysis2: PacketAnalysis,
        val differences: List<ByteDifference>,
        val totalDifferences: Int,
        val similarityPercent: Double
    )

    data class ByteDifference(
        val offset: Int,
        val byte1: String?,
        val byte2: String?
    )

    /**
     * Generate human-readable summary
     */
    fun generateSummary(analysis: PacketAnalysis): String {
        return buildString {
            appendLine("=== Packet Analysis Summary ===")
            appendLine()
            appendLine("Size: ${analysis.rawData.size} bytes")
            appendLine("Entropy: ${"%.2f".format(analysis.byteStats.entropy)} bits/byte")
            appendLine()

            if (analysis.protocolHints.isNotEmpty()) {
                appendLine("Detected Protocols:")
                for (hint in analysis.protocolHints.take(3)) {
                    appendLine("  - ${hint.protocol} (${"%.0f".format(hint.confidence * 100)}% confidence)")
                }
                appendLine()
            }

            if (analysis.byteStats.isLikelyText) {
                appendLine("Content Type: Text")
                analysis.textDecodings.forEach { (encoding, text) ->
                    if (text.length <= 100) {
                        appendLine("  $encoding: \"$text\"")
                    } else {
                        appendLine("  $encoding: \"${text.take(100)}...\"")
                    }
                }
            } else {
                appendLine("Content Type: Binary")
                appendLine("  Hex: ${analysis.hexRepresentation.take(100)}${if (analysis.hexRepresentation.length > 100) "..." else ""}")
            }

            if (analysis.structure?.suggestedStructure != null) {
                appendLine()
                appendLine("Suggested Structure: ${analysis.structure.suggestedStructure}")
            }

            if (analysis.checksum != null) {
                appendLine()
                appendLine("Checksum: ${analysis.checksum.algorithm} = ${analysis.checksum.calculatedValue}")
            }
        }
    }
}
