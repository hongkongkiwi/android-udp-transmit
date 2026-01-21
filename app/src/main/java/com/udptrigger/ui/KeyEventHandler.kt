package com.udptrigger.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.udptrigger.LocalKeyEventDispatcher

/**
 * Callback interface for key events.
 */
interface KeyEventCallback {
    /**
     * Called when a key is pressed.
     * @param keyCode The KeyEvent key code (e.g., KeyEvent.KEYCODE_VOLUME_UP)
     * @param timestamp The nanosecond timestamp when the key event occurred
     * @return true if the event was consumed/handled, false to pass it to other handlers
     */
    fun onKeyPressed(keyCode: Int, timestamp: Long): Boolean
}

/**
 * Composable that captures ALL key events including:
 * - Hardware keyboard keys (USB/BT)
 * - Volume keys (VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE)
 * - Media keys (MEDIA_PLAY, MEDIA_PAUSE, MEDIA_NEXT, etc.)
 * - Special keys (HOME, BACK, MENU, SEARCH - if not consumed by system)
 *
 * Uses Activity-level dispatchKeyEvent interception which captures keys
 * that OnKeyListener misses.
 *
 * @param onKeyPressed Callback that receives key events. Return true to consume the event.
 */
@Composable
fun KeyEventListener(
    onKeyPressed: KeyEventCallback
) {
    val dispatcher = LocalKeyEventDispatcher.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val keyCallback = { event: KeyEvent ->
            // Only process ACTION_DOWN (key press, not release)
            if (event.action == KeyEvent.ACTION_DOWN) {
                // Use the event's own timestamp for maximum accuracy
                val timestamp = event.eventTime
                // Convert milliseconds to nanoseconds for consistency
                val timestampNanos = timestamp * 1_000_000

                onKeyPressed.onKeyPressed(event.keyCode, timestampNanos)
            } else {
                false
            }
        }

        // Register the callback
        dispatcher.setCallback(keyCallback)

        // Create a lifecycle observer to clean up
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> {
                    // Optionally pause key capture when app is not focused
                    // Uncomment below to disable capture when paused:
                    // dispatcher.setCallback(null)
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Re-register callback when app resumes
                    dispatcher.setCallback(keyCallback)
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            // Unregister callback when composable is disposed
            dispatcher.setCallback(null)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
