package com.udptrigger

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.app.Activity

/**
 * Splash screen activity that displays during app startup.
 * Shows branding and initializes core components before transitioning to main activity.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : Activity() {

    private val splashDuration = 1500L // 1.5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Navigate to main activity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToMain()
        }, splashDuration)
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
