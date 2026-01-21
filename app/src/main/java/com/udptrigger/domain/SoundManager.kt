package com.udptrigger.domain

import android.content.Context
import android.media.SoundPool
import android.os.Build

class SoundManager(context: Context) {

    private val soundPool: SoundPool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        SoundPool.Builder().setMaxStreams(1).build()
    } else {
        @Suppress("DEPRECATION")
        SoundPool(1, android.media.AudioManager.STREAM_NOTIFICATION, 0)
    }

    private var clickSoundId: Int = -1

    init {
        // Load a simple click sound - using 0 as placeholder (no sound loaded)
        // In a production app, you would load custom sounds from resources
        // For now we'll use the system's default click sound via SoundPool
        clickSoundId = 0
    }

    fun playClickSound() {
        // Play system default click sound using ToneGenerator instead
        val toneGenerator = android.media.ToneGenerator(
            android.media.AudioManager.STREAM_NOTIFICATION,
            50
        )
        toneGenerator.startTone(android.media.ToneGenerator.TONE_PROP_BEEP)
    }

    fun release() {
        soundPool.release()
    }
}
