package com.udptrigger.domain

import android.content.Context
import android.os.PowerManager
import android.util.Log

private const val TAG = "WakeLockManager"

/**
 * Wake lock manager for keeping CPU awake during low-latency operations.
 * When enabled, reduces latency by preventing CPU throttling.
 */
class WakeLockManager(private val context: Context) {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isHeld: Boolean = false

    /**
     * Acquire wake lock for reduced latency operation.
     * This prevents the CPU from going to sleep during critical operations.
     *
     * @param timeoutMs Timeout in milliseconds, use 0 for indefinite wake lock
     */
    fun acquire(timeoutMs: Long = 0) {
        if (isHeld) {
            Log.d(TAG, "Wake lock already held")
            return
        }

        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "UDPTrigger::LowLatencyWakeLock"
            ).apply {
                if (timeoutMs > 0) {
                    setReferenceCounted(false)
                    acquire(timeoutMs)
                } else {
                    acquire()
                }
            }
            isHeld = true
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
        }
    }

    /**
     * Release the wake lock.
     */
    fun release() {
        if (!isHeld) {
            Log.d(TAG, "Wake lock not held")
            return
        }

        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
            isHeld = false
            Log.d(TAG, "Wake lock released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock: ${e.message}")
        }
    }

    /**
     * Check if wake lock is currently held.
     */
    fun isHeld(): Boolean = isHeld

    /**
     * Release all resources. Call when ViewModel is cleared.
     */
    fun cleanup() {
        release()
    }
}
