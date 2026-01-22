package com.udptrigger.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * QR Code Scanner using ML Kit for camera-based scanning.
 * Provides easy integration for scanning UDP config QR codes.
 */
class QrCodeScanner(private val context: Context) {

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var scanner: BarcodeScanner? = null

    sealed class ScanResult {
        data class Success(val data: String) : ScanResult()
        data class Error(val message: String) : ScanResult()
    }

    interface ScanCallback {
        fun onScanResult(result: ScanResult)
    }

    init {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        scanner = BarcodeScanning.getClient(options)
    }

    /**
     * Check if camera permission is granted
     */
    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start camera preview and scanning
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        callback: ScanCallback
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(scanner!!, imageProxy, callback)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                callback.onScanResult(ScanResult.Error("Failed to start camera: ${e.message}"))
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Process image from camera for QR codes
     */
    private fun processImageProxy(
        scanner: BarcodeScanner,
        imageProxy: androidx.camera.core.ImageProxy,
        callback: ScanCallback
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull()?.rawValue?.let { value ->
                        callback.onScanResult(ScanResult.Success(value))
                    }
                }
                .addOnFailureListener { e ->
                    callback.onScanResult(ScanResult.Error("Scan failed: ${e.message}"))
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    /**
     * Stop camera and release resources
     */
    fun stop() {
        scanner?.close()
        scanner = null
        cameraExecutor.shutdown()
    }

    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
