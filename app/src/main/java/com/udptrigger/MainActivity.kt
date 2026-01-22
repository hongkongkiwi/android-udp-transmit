package com.udptrigger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.udptrigger.ui.TriggerScreen
import com.udptrigger.ui.theme.UdpTriggerTheme

/**
 * CompositionLocal for providing key event callback to composables.
 * This allows deep composables to register/unregister key event handlers.
 */
val LocalKeyEventDispatcher = staticCompositionLocalOf<KeyEventDispatcher> {
    error("No KeyEventDispatcher provided")
}

/**
 * CompositionLocal for providing permission requester to composables.
 * This allows requesting notification permission from any composable.
 */
val LocalPermissionRequester = staticCompositionLocalOf<PermissionRequester> {
    error("No PermissionRequester provided")
}

/**
 * Dispatcher for key events that allows composables to register callbacks.
 * This enables capturing ALL key events including volume keys, media keys, etc.
 */
class KeyEventDispatcher {
    private var callback: ((KeyEvent) -> Boolean)? = null

    /**
     * Register a callback to receive key events.
     * Returns true if the event was consumed.
     */
    fun setCallback(cb: ((KeyEvent) -> Boolean)?) {
        callback = cb
    }

    /**
     * Dispatch a key event to the registered callback.
     * @return true if the event was consumed
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return callback?.invoke(event) ?: false
    }
}

/**
 * Handles runtime permission requests for Android 13+ notification permission.
 */
class PermissionRequester(
    private val activity: ComponentActivity
) {
    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val notificationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onPermissionResult?.invoke(isGranted)
    }

    /**
     * Check if notification permission is granted
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed on Android 12 and below
        }
    }

    /**
     * Request notification permission
     * @param callback Called with the result (true = granted, false = denied)
     */
    fun requestNotificationPermission(callback: (Boolean) -> Unit) {
        onPermissionResult = callback

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            callback(true) // Auto-grant on Android 12 and below
        }
    }

    /**
     * Show an explanation dialog before requesting permission (recommended best practice)
     */
    fun showNotificationPermissionExplanation(callback: (Boolean) -> Unit) {
        callback(true) // User acknowledged, proceed with request
        requestNotificationPermission { isGranted ->
            callback(isGranted)
        }
    }
}

class MainActivity : ComponentActivity() {

    // Create the dispatcher that will be shared across the app
    private val keyEventDispatcher = KeyEventDispatcher()
    private val permissionRequester = PermissionRequester(this)

    // Intent action state for automation integration
    var pendingIntentAction: String? = null
        private set

    // Companion object for constants
    companion object {
        const val ACTION_CONNECT = "com.udptrigger.CONNECT"
        const val ACTION_DISCONNECT = "com.udptrigger.DISCONNECT"
        const val ACTION_TRIGGER_UDP = "com.udptrigger.TRIGGER_UDP"
        const val EXTRA_AUTO_CONNECT = "auto_connect"
        const val EXTRA_AUTO_DISCONNECT = "auto_disconnect"
        const val EXTRA_AUTO_TRIGGER = "auto_trigger"
    }

    /**
     * Intercept ALL key events including volume keys, media keys, and hardware buttons.
     * This is called before any other handlers and captures keys that OnKeyListener misses.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // First try the registered callback (our UDP trigger)
        if (keyEventDispatcher.dispatchKeyEvent(event)) {
            return true // Event was consumed by our handler
        }

        // Let the system handle the event normally (volume, back, etc.)
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize crash reporter first
        com.udptrigger.util.CrashReporterSingleton.initialize(this)
        // Initialize error handler with crash analytics
        com.udptrigger.util.ErrorHandler.initialize(this)
        // Check for pending intent actions from automation
        handleIntentAction(intent)
        enableEdgeToEdge()
        setContent {
            // Provide the key event dispatcher and permission requester to all composables
            CompositionLocalProvider(
                LocalKeyEventDispatcher provides keyEventDispatcher,
                LocalPermissionRequester provides permissionRequester
            ) {
                UdpTriggerTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        TriggerScreen(pendingIntentAction = pendingIntentAction)
                    }
                }
            }
        }
    }

    /**
     * Handle new intents, including widget trigger requests and automation actions
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentAction(intent)
    }

    private fun handleIntentAction(intent: Intent?) {
        when (intent?.action) {
            ACTION_CONNECT -> {
                if (intent.getBooleanExtra(EXTRA_AUTO_CONNECT, false)) {
                    pendingIntentAction = EXTRA_AUTO_CONNECT
                }
            }
            ACTION_DISCONNECT -> {
                if (intent.getBooleanExtra(EXTRA_AUTO_DISCONNECT, false)) {
                    pendingIntentAction = EXTRA_AUTO_DISCONNECT
                }
            }
            ACTION_TRIGGER_UDP -> {
                if (intent.getBooleanExtra(EXTRA_AUTO_TRIGGER, false)) {
                    pendingIntentAction = EXTRA_AUTO_TRIGGER
                }
            }
        }
    }
}
