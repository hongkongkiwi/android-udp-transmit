package com.udptrigger

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * Splash screen activity that displays during app startup.
 * Uses Android 12+ SplashScreen API for proper splash screen behavior.
 */
class SplashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen before calling super.onCreate()
        // This handles Android 12+ splash screen requirements automatically
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Navigate to main activity immediately
        // The splash screen will be managed by the OS on Android 12+
        // For older versions, the theme background provides the splash effect
        navigateToMain()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
