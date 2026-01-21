package com.udptrigger

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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

class MainActivity : ComponentActivity() {

    // Create the dispatcher that will be shared across the app
    private val keyEventDispatcher = KeyEventDispatcher()

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
        enableEdgeToEdge()
        setContent {
            // Provide the key event dispatcher to all composables
            CompositionLocalProvider(
                LocalKeyEventDispatcher provides keyEventDispatcher
            ) {
                UdpTriggerTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        TriggerScreen()
                    }
                }
            }
        }
    }
}
