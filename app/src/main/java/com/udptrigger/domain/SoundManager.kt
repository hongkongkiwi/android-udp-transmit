package com.udptrigger.domain

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build

/**
 * Manages sound effects for trigger feedback.
 * Uses ToneGenerator for low-latency click sounds.
 */
class SoundManager(context: Context) {

    // We use ToneGenerator for immediate sound feedback without resource loading
    private var toneGenerator: ToneGenerator? = null

    init {
        // ToneGenerator is created lazily to avoid AudioFocus issues
    }

    /**
     * Play a click sound for trigger feedback
     * Uses ToneGenerator for minimal latency
     */
    fun playClickSound() {
        try {
            // Create ToneGenerator on first use or reuse existing
            val tg = toneGenerator ?: ToneGenerator(
                android.media.AudioManager.STREAM_NOTIFICATION,
                50 // Volume
            ).also {
                toneGenerator = it
            }

            // Play a short click tone
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
        } catch (e: Exception) {
            // Silently fail if audio is not available
        }
    }

    /**
     * Play a success sound (e.g., after successful connection)
     */
    fun playSuccessSound() {
        try {
            val tg = toneGenerator ?: ToneGenerator(
                android.media.AudioManager.STREAM_NOTIFICATION,
                50
            ).also {
                toneGenerator = it
            }

            // Play a two-tone success sound
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 100)
        } catch (e: Exception) {
            // Silently fail
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
