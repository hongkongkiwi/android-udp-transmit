package com.udptrigger.domain

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.util.Log

private const val TAG = "SoundManager"

/**
 * Sound manager for playing feedback sounds when packets are sent.
 * Uses SoundPool for low-latency playback.
 */
class SoundManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var soundId: Int = 0
    private var isLoaded: Boolean = false

    init {
        initializeSoundPool()
    }

    private fun initializeSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build()
        } else {
            @Suppress("DEPRECATION")
            SoundPool(1, android.media.AudioManager.STREAM_NOTIFICATION, 0)
        }

        soundPool?.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) {
                isLoaded = true
                Log.d(TAG, "Sound loaded successfully")
            } else {
                Log.e(TAG, "Failed to load sound, status: $status")
            }
        }

        // Load a simple beep sound from raw resources or generate one
        // For now, we'll try to load from raw resources if available, otherwise skip
        try {
            val resources = context.resources
            val resourceId = resources.getIdentifier("trigger_beep", "raw", context.packageName)
            if (resourceId != 0) {
                soundId = soundPool?.load(context, resourceId, 1) ?: 0
            } else {
                Log.d(TAG, "No trigger_beep resource found, sound feedback disabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sound: ${e.message}")
        }
    }

    /**
     * Play feedback sound. Call this after successful packet send.
     */
    fun playFeedbackSound() {
        if (!isLoaded || soundId == 0) {
            Log.d(TAG, "Sound not loaded, skipping playback")
            return
        }

        try {
            soundPool?.play(soundId, 0.7f, 0.7f, 1, 0, 1.0f)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play sound: ${e.message}")
        }
    }

    /**
     * Release resources when no longer needed.
     */
    fun release() {
        soundPool?.release()
        soundPool = null
        isLoaded = false
    }
}
