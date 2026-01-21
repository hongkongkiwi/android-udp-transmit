package com.udptrigger.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import android.view.View

/**
 * Callback interface for key events.
 */
interface KeyEventCallback {
    /**
     * Called when a key is pressed.
     * @return true if the event was consumed/handled, false to pass it to other handlers
     */
    fun onKeyPressed(keyCode: Int, timestamp: Long): Boolean
}

/**
 * Composable that captures key events from both hardware and software keyboards.
 * Uses a transparent focusable view to capture all key events.
 */
@Composable
fun KeyEventListener(
    onKeyPressed: KeyEventCallback
) {
    val view = LocalView.current

    DisposableEffect(Unit) {
        val callback = object : View.OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if (event?.action == KeyEvent.ACTION_DOWN) {
                    // Use System.nanoTime() for accurate timestamp
                    val consumed = onKeyPressed.onKeyPressed(keyCode, System.nanoTime())
                    return consumed
                }
                return false
            }
        }

        // Make the root view focusable for key capture
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnKeyListener(callback)

        onDispose {
            view.setOnKeyListener(null)
        }
    }
}

