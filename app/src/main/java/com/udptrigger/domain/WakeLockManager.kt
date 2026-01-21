package com.udptrigger.domain

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages wake locks to prevent CPU sleep during low-latency triggering.
 *
 * A partial wake lock keeps the CPU running even when the screen is off,
 * ensuring minimal latency for UDP packet transmission.
 *
 * IMPORTANT: Wake locks consume significant battery. Always release when not needed.
 */
class WakeLockManager(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /**
     * Acquire a partial wake lock to keep CPU running.
     * Use this during active triggering sessions to ensure minimal latency.
     *
     * @param timeoutMs Optional timeout in milliseconds. If null, must be released manually.
     * @return true if wake lock was acquired successfully
     */
    fun acquire(timeoutMs: Long? = null): Boolean {
        if (_isActive.value) {
            // Already acquired, update timeout if specified
            timeoutMs?.let { wakeLock?.acquire(it) }
            return true
        }

        return try {
            val lock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "UDPTrigger:WakeLock"
            ).apply {
                setReferenceCounted(false) // We manage reference counting manually
            }

            if (timeoutMs != null) {
                lock.acquire(timeoutMs)
            } else {
                lock.acquire()
            }

            wakeLock = lock
            _isActive.value = true
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Release the wake lock to allow CPU to sleep.
     * Always call this when done with active triggering.
     */
    fun release() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
        _isActive.value = false
    }

    /**
     * Check if wake lock is currently held
     */
    fun isHeld(): Boolean = wakeLock?.isHeld == true

    /**
     * Clean up resources
     */
    fun cleanup() {
        release()
    }
}
