package com.udptrigger.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import android.view.View

/**
 * Callback interface for key events.
 */
interface KeyEventCallback {
    fun onKeyPressed(keyCode: Int, timestamp: Long)
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
                    onKeyPressed.onKeyPressed(keyCode, System.nanoTime())
                    return true
                }
                return false
            }
        }

        // Make the root view focusable and request focus
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnKeyListener(callback)

        // Request focus for the view
        view.requestFocus()

        onDispose {
            view.setOnKeyListener(null)
        }
    }
}

