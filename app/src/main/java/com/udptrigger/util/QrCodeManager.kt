package com.udptrigger.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * QR Code utilities for sharing UDP Trigger configurations.
 */
object QrCodeManager {

    private const val SCHEME = "udptrigger"
    private const val AUTHORITY = "com.udptrigger.fileprovider"

    /**
     * Generate QR code data from configuration
     */
    fun generateConfigData(
        host: String,
        port: Int,
        content: String,
        hexMode: Boolean = false,
        includeTimestamp: Boolean = true,
        presetName: String? = null
    ): String {
        val config = JSONObject().apply {
            put("host", host)
            put("port", port)
            put("content", content)
            put("hex", hexMode)
            put("ts", includeTimestamp)
            presetName?.let { put("preset", it) }
            put("v", 1) // Version for future compatibility
        }
        return config.toString()
    }

    /**
     * Parse QR code data back to configuration
     */
    fun parseConfigData(data: String): QrConfigResult {
        return try {
            val json = JSONObject(data)
            QrConfigResult.Success(
                host = json.getString("host"),
                port = json.getInt("port"),
                content = json.optString("content", ""),
                hexMode = json.optBoolean("hex", false),
                includeTimestamp = json.optBoolean("ts", true),
                presetName = json.optString("preset", null).takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            QrConfigResult.Error("Invalid QR code format: ${e.message}")
        }
    }

    /**
     * Generate QR code bitmap using ZXing
     */
    fun generateQrCode(data: String, size: Int = 512): Bitmap? {
        return try {
            val hints = hashMapOf<EncodeHintType, Any>(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 2
            )

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save QR code to file and return URI
     */
    fun saveQrCodeToFile(context: Context, bitmap: Bitmap, filename: String = "qr_config.png"): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "qr_codes")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val file = File(cacheDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            FileProvider.getUriForFile(context, AUTHORITY, file)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Share QR code image
     */
    fun shareQrCode(context: Context, bitmap: Bitmap, host: String, port: Int) {
        val uri = saveQrCodeToFile(context, bitmap, "config_qr.png") ?: return

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "UDP Trigger config: $host:$port")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
    }

    /**
     * Generate shareable URL for config
     */
    fun generateShareUrl(
        host: String,
        port: Int,
        content: String,
        hexMode: Boolean = false
    ): String {
        return buildString {
            append("$SCHEME://send?")
            append("host=$host&port=$port&content=${Uri.encode(content)}")
            append("&hex=$hexMode")
        }
    }
}

/**
 * Result of parsing QR code configuration
 */
sealed class QrConfigResult {
    data class Success(
        val host: String,
        val port: Int,
        val content: String,
        val hexMode: Boolean,
        val includeTimestamp: Boolean,
        val presetName: String?
    ) : QrConfigResult()

    data class Error(val message: String) : QrConfigResult()
}

/**
 * Configuration data class for QR code
 */
data class QrConfig(
    val host: String,
    val port: Int,
    val content: String,
    val hexMode: Boolean = false,
    val includeTimestamp: Boolean = true,
    val presetName: String? = null
) {
    fun toJson(): String {
        return QrCodeManager.generateConfigData(host, port, content, hexMode, includeTimestamp, presetName)
    }

    companion object {
        fun fromJson(json: String): QrConfigResult {
            return QrCodeManager.parseConfigData(json)
        }
    }
}
